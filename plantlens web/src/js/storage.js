// Storage State & JSON Persistence Manager
import { SAMPLE_PLANTS } from './data.js';
import { getFirebaseAuth, getFirebaseDb } from './firebase.js';
import { compressImage } from './utils.js';

const STORAGE_KEY_GARDEN = 'plantlens_garden_db';
const STORAGE_KEY_SETTINGS = 'plantlens_settings_db';

export const StorageManager = {
  gardenCache: null,

  getGarden() {
    if (this.gardenCache !== null) return this.gardenCache;

    try {
      const data = localStorage.getItem(STORAGE_KEY_GARDEN);
      if (data) {
        this.gardenCache = JSON.parse(data);
        return this.gardenCache;
      }
      this.gardenCache = [];
      return [];
    } catch (e) {
      console.error('Failed to read garden storage:', e);
      this.gardenCache = [];
      return [];
    }
  },

  clearGarden() {
    this.gardenCache = [];
    try {
      localStorage.removeItem(STORAGE_KEY_GARDEN);
    } catch (e) {
      console.error('Failed to clear garden storage:', e);
    }
  },

  async syncFromFirestore() {
    try {
      const auth = await getFirebaseAuth();
      const user = auth.currentUser;
      if (!user) {
        this.clearGarden();
        window.dispatchEvent(new CustomEvent('garden-sync-completed'));
        return;
      }

      const db = await getFirebaseDb();
      const { collection, getDocs } = await import('firebase/firestore');

      const existingLocalGarden = this.getGarden();
      // User-scoped collection: users/{uid}/plants
      const querySnapshot = await getDocs(collection(db, "users", user.uid, "plants"));
      const firestoreGarden = [];

      querySnapshot.forEach((docSnap) => {
        const d = docSnap.data();
        const docId = docSnap.id;
        const localMatch = existingLocalGarden.find(p => p.id === docId || p.firebaseDocId === docId || (p.name === (d.plantName || d.name) && p.createdAt === d.createdAt));

        const resolvedImage = (d.image && String(d.image).trim() !== '') ? d.image :
                             (d.imageUrl && String(d.imageUrl).trim() !== '') ? d.imageUrl :
                             (d.image_url && String(d.image_url).trim() !== '') ? d.image_url :
                             (d.photoUrl && String(d.photoUrl).trim() !== '') ? d.photoUrl :
                             (d.photo_url && String(d.photo_url).trim() !== '') ? d.photo_url :
                             (d.plantImage && String(d.plantImage).trim() !== '') ? d.plantImage :
                             (localMatch && localMatch.image) ? localMatch.image : '';

        const soilType = d.soilType || d.soil_type || (localMatch ? (localMatch.soilType || localMatch.soil_type) : '') || 'Loamy soil';
        const soilPh = d.soilPh || d.soil_ph || (localMatch ? (localMatch.soilPh || localMatch.soil_ph) : '') || '6.0 - 6.8';
        const soilDrainage = d.soilDrainage || d.soil_drainage || (localMatch ? (localMatch.soilDrainage || localMatch.soil_drainage) : '') || 'Well-drained';
        const soilRecommendation = d.soilRecommendation || d.soil_recommendation || (localMatch ? (localMatch.soilRecommendation || localMatch.soil_recommendation) : '') || '';

        firestoreGarden.push({
          ...d,
          name: d.plantName || d.name || "Plant",
          scientificName: d.scientificName || "",
          diseaseName: d.disease || d.diseaseName || "Healthy Plant",
          healthStatus: d.healthStatus || (d.disease && String(d.disease).toLowerCase().includes('healthy') ? 'healthy' : 'diseased'),
          image: resolvedImage,
          imageUrl: resolvedImage,
          soilType,
          soil_type: soilType,
          soilPh,
          soil_ph: soilPh,
          soilDrainage,
          soil_drainage: soilDrainage,
          soilRecommendation,
          soil_recommendation: soilRecommendation,
          firebaseDocId: docId,
          id: docId
        });
      });

      this.gardenCache = firestoreGarden;
      localStorage.setItem(STORAGE_KEY_GARDEN, JSON.stringify(firestoreGarden));
      // Dispatch custom event to tell UI to refresh the garden grid
      window.dispatchEvent(new CustomEvent('garden-sync-completed'));
    } catch (err) {
      console.warn('Firestore sync failed (offline or unauthenticated):', err);
    }
  },

  saveGarden(gardenArray) {
    this.gardenCache = gardenArray;
    try {
      localStorage.setItem(STORAGE_KEY_GARDEN, JSON.stringify(gardenArray));
    } catch (e) {
      console.error('Failed to write garden storage:', e);
    }
  },

  async addPlantToGarden(plantObject) {
    const auth = await getFirebaseAuth();
    const user = auth.currentUser;
    const garden = this.getGarden();
    const rawImage = plantObject.image || plantObject.imageUrl || plantObject.image_url || '';

    const resolvedSoilType = plantObject.soilType || plantObject.soil_type || 'Loamy soil';
    const resolvedSoilPh = plantObject.soilPh || plantObject.soil_ph || '6.0 - 6.8';
    const resolvedSoilDrainage = plantObject.soilDrainage || plantObject.soil_drainage || 'Well-drained';
    const resolvedSoilRec = plantObject.soilRecommendation || plantObject.soil_recommendation || '';

    const newEntry = {
      ...plantObject,
      name: plantObject.name || plantObject.plantName || 'Plant',
      scientificName: plantObject.scientificName || '',
      diseaseName: plantObject.diseaseName || plantObject.disease || 'Healthy Plant',
      image: rawImage,
      imageUrl: rawImage,
      soilType: resolvedSoilType,
      soil_type: resolvedSoilType,
      soilPh: resolvedSoilPh,
      soil_ph: resolvedSoilPh,
      soilDrainage: resolvedSoilDrainage,
      soil_drainage: resolvedSoilDrainage,
      soilRecommendation: resolvedSoilRec,
      soil_recommendation: resolvedSoilRec,
      uid: user ? user.uid : 'guest',
      id: 'plant-' + Date.now(),
      addedDate: new Date().toISOString().split('T')[0],
      nextWaterDate: new Date(Date.now() + 86400000 * 3).toISOString().split('T')[0],
      category: plantObject.category || 'Indoor',
      timeline: [
        { date: 'Just now', event: 'Identified & Saved via PlantLens AI' },
        { date: 'Scheduled', event: 'Watering due in 3 days' }
      ]
    };

    // Avoid duplicate if same plant was just added in the last 5 seconds
    const existingIdx = garden.findIndex(p => p.id === newEntry.id || (p.name === newEntry.name && p.image === newEntry.image && Date.now() - (p._addedTimestamp || 0) < 5000));
    if (existingIdx === -1) {
      newEntry._addedTimestamp = Date.now();
      garden.unshift(newEntry);
      this.saveGarden(garden);
    }

    // Asynchronously save compressed thumbnail to Firestore under users/{uid}/plants
    if (user) {
      try {
        let firestoreImage = rawImage;
        if (rawImage && rawImage.startsWith('data:image')) {
          try {
            // Compress to ~25KB thumbnail so it never exceeds Firestore document limit
            firestoreImage = await compressImage(rawImage, 450, 0.65);
          } catch (compErr) {
            console.warn('Image thumbnail compression fallback:', compErr);
          }
        }

        const db = await getFirebaseDb();
        const { collection, addDoc, doc, setDoc, increment } = await import('firebase/firestore');
        const docRef = await addDoc(collection(db, "users", user.uid, "plants"), {
          plantName: newEntry.name || newEntry.plantName,
          scientificName: newEntry.scientificName || '',
          disease: newEntry.diseaseName || newEntry.disease || 'Healthy Plant',
          diseaseName: newEntry.diseaseName || newEntry.disease || 'Healthy Plant',
          confidence: newEntry.confidence || 95,
          healthStatus: newEntry.healthStatus || 'healthy',
          healthScore: newEntry.healthScore || 85,
          image: firestoreImage || '',
          imageUrl: firestoreImage || '',
          waterSchedule: newEntry.waterSchedule || 'Every 2-3 days',
          category: newEntry.category || 'Indoor',
          soilType: resolvedSoilType,
          soil_type: resolvedSoilType,
          soilPh: resolvedSoilPh,
          soil_ph: resolvedSoilPh,
          soilDrainage: resolvedSoilDrainage,
          soil_drainage: resolvedSoilDrainage,
          soilRecommendation: resolvedSoilRec,
          soil_recommendation: resolvedSoilRec,
          createdAt: new Date().toISOString()
        });
        newEntry.firebaseDocId = docRef.id;
        this.saveGarden(garden);

        await setDoc(doc(db, "users", user.uid), {
          totalScans: increment(1)
        }, { merge: true });
      } catch (err) {
        console.warn('Firestore write failed in background:', err);
      }
    }

    return newEntry;
  },

  async deletePlant(id) {
    const garden = this.getGarden();
    const plantToDelete = garden.find(p => p.id === id || p.firebaseDocId === id);
    const docId = (plantToDelete && plantToDelete.firebaseDocId) ? plantToDelete.firebaseDocId : (plantToDelete && plantToDelete.id ? plantToDelete.id : id);
    const updated = garden.filter(p => p.id !== id && p.firebaseDocId !== id && p.id !== docId);
    this.saveGarden(updated);

    try {
      const auth = await getFirebaseAuth();
      const user = auth.currentUser;
      if (user && docId && !String(docId).startsWith('plant-')) {
        const db = await getFirebaseDb();
        const { doc, deleteDoc } = await import('firebase/firestore');
        await deleteDoc(doc(db, "users", user.uid, "plants", docId));
      } else if (user && plantToDelete && plantToDelete.firebaseDocId) {
        const db = await getFirebaseDb();
        const { doc, deleteDoc } = await import('firebase/firestore');
        await deleteDoc(doc(db, "users", user.uid, "plants", plantToDelete.firebaseDocId));
      }
    } catch (err) {
      console.warn('Firestore plant delete failed:', err);
    }
  },

  async removePlant(id) {
    return this.deletePlant(id);
  },

  updatePlantCare(id, action) {
    const garden = this.getGarden();
    const plant = garden.find(p => p.id === id || p.firebaseDocId === id);
    if (plant) {
      if (!plant.timeline) plant.timeline = [];
      plant.timeline.unshift({
        date: new Date().toLocaleDateString(),
        event: action === 'water' ? 'Watered Plant 💧' : 'Fertilized Plant 🌿'
      });
      if (action === 'water') {
        plant.nextWaterDate = new Date(Date.now() + 86400000 * 3).toISOString().split('T')[0];
      }
      this.saveGarden(garden);
    }
  },

  exportDataJSON() {
    const data = {
      garden: this.getGarden(),
      settings: this.getSettings()
    };
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `plantlens_garden_backup_${new Date().toISOString().split('T')[0]}.json`;
    a.click();
    URL.revokeObjectURL(url);
  },

  importDataJSON(jsonString) {
    try {
      const parsed = JSON.parse(jsonString);
      if (parsed.garden && Array.isArray(parsed.garden)) {
        this.saveGarden(parsed.garden);
        if (parsed.settings) this.saveSettings(parsed.settings);
        return { success: true, count: parsed.garden.length };
      }
      return { success: false, error: 'Invalid JSON format. Missing garden array.' };
    } catch (e) {
      return { success: false, error: e.message };
    }
  },

  getSettings() {
    try {
      const data = localStorage.getItem(STORAGE_KEY_SETTINGS);
      return data ? JSON.parse(data) : {
        theme: 'system',
        highContrast: false,
        reducedMotion: false,
        language: 'en',
        notifications: {
          waterReminder: true,
          diseaseAlerts: true,
          weeklyTips: true
        }
      };
    } catch (e) {
      return { theme: 'system', highContrast: false, reducedMotion: false, language: 'en' };
    }
  },

  saveSettings(settingsObject) {
    try {
      localStorage.setItem(STORAGE_KEY_SETTINGS, JSON.stringify(settingsObject));
    } catch (e) {
      console.error('Failed to write settings storage:', e);
    }
  }
};
