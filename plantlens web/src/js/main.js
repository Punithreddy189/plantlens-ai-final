import { db, auth } from "./firebase.js";
import { collection, addDoc, doc, setDoc, deleteDoc, onSnapshot, increment } from "firebase/firestore";
import { onAuthStateChanged } from "firebase/auth";

// 1. Dynamic Save Plant Function (Accepts object or name/disease strings)
window.savePlant = async function (plantData, optionalDisease = "Healthy") {
  const user = auth.currentUser;

  if (!user) {
    alert("Please login first ❗");
    return null;
  }

  try {
    let payload = {};

    if (typeof plantData === "object" && plantData !== null) {
      payload = {
        plantName: plantData.name || plantData.plantName || "Unknown Plant",
        scientificName: plantData.scientificName || "",
        disease: plantData.diseaseName || plantData.disease || "Healthy Plant",
        confidence: plantData.confidence || 95,
        healthStatus: plantData.healthStatus || "healthy",
        healthScore: plantData.healthScore || 85,
        image: plantData.image || "",
        waterSchedule: plantData.waterSchedule || "Every 2-3 days",
        createdAt: new Date().toISOString()
      };
    } else {
      payload = {
        plantName: plantData || "Tomato",
        disease: optionalDisease || "Healthy Plant",
        confidence: 95,
        healthStatus: optionalDisease && optionalDisease.toLowerCase().includes("healthy") ? "healthy" : "diseased",
        createdAt: new Date().toISOString()
      };
    }

    const docRef = await addDoc(collection(db, "users", user.uid, "plants"), payload);

    // Increment user's total scans count
    await setDoc(doc(db, "users", user.uid), {
      totalScans: increment(1)
    }, { merge: true });

    alert(`Plant saved: ${payload.plantName} 🌿`);
    return docRef.id;
  } catch (error) {
    console.error("Save Plant Error:", error);
    alert("Error ❌: " + (error.message || error));
    return null;
  }
};

// Global legacy alias
window.saveData = function () {
  return window.savePlant("Tomato", "Leaf Spot");
};

// 2. Delete Plant Function
window.deletePlant = async function (docId) {
  const user = auth.currentUser;
  if (!user) {
    alert("Please login first ❗");
    return;
  }

  if (confirm("Are you sure you want to remove this plant from your cloud garden?")) {
    try {
      await deleteDoc(doc(db, "users", user.uid, "plants", docId));
      alert("Plant deleted from Cloud 🗑️");
    } catch (error) {
      console.error("Delete Plant Error:", error);
      alert("Error deleting plant ❌: " + (error.message || error));
    }
  }
};

// 3. Real-time onSnapshot Plant Loader with Cards, Images & Delete Actions
export function loadPlants(user) {
  const plantList = document.getElementById("plant-list");
  if (!plantList) return;

  const plantsRef = collection(db, "users", user.uid, "plants");

  onSnapshot(plantsRef, (snapshot) => {
    plantList.innerHTML = "";

    if (snapshot.empty) {
      plantList.innerHTML = `
        <div style="grid-column: 1 / -1; padding: 20px; text-align: center; color: var(--text-secondary);">
          <p>🌱 Your cloud garden is empty. Scan a plant or click <b>💾 Save Data</b> to add your first real plant!</p>
        </div>
      `;
      return;
    }

    snapshot.forEach((docSnap) => {
      const data = docSnap.data();
      const docId = docSnap.id;

      const isHealthy = data.healthStatus === "healthy" || (data.disease && data.disease.toLowerCase().includes("healthy"));
      const imageHTML = data.image ? 
        `<img src="${data.image}" alt="${data.plantName}" style="width: 100%; height: 130px; object-fit: cover; border-radius: var(--radius-sm); margin-bottom: 8px;"/>` : "";

      const card = document.createElement("div");
      card.className = "card";
      card.style.padding = "14px";
      card.style.borderRadius = "var(--radius-md)";
      card.style.background = "var(--surface-card)";
      card.style.border = "1px solid var(--border-color)";
      card.style.boxShadow = "var(--shadow-sm)";
      card.style.display = "flex";
      card.style.flexDirection = "column";

      card.innerHTML = `
        ${imageHTML}
        <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 4px;">
          <h4 style="margin: 0; color: var(--text-primary); font-size: 1.05rem;">🌱 ${data.plantName || "Plant"}</h4>
          <span class="badge ${isHealthy ? 'badge-healthy' : 'badge-diseased'}" style="font-size: 0.75rem;">
            ${data.confidence ? `${data.confidence}%` : (isHealthy ? 'Healthy' : 'Diseased')}
          </span>
        </div>
        ${data.scientificName ? `<p style="font-style: italic; font-size: 0.8rem; color: var(--text-secondary); margin-bottom: 4px;">${data.scientificName}</p>` : ""}
        <p style="color: var(--text-secondary); font-size: 0.85rem; margin: 4px 0;">🦠 <b>Condition:</b> ${data.disease || "Healthy"}</p>
        <small style="color: var(--text-muted); font-size: 0.75rem; margin-bottom: 10px;">📅 ${data.createdAt ? new Date(data.createdAt).toLocaleDateString() : "Recently"}</small>
        
        <button onclick="deletePlant('${docId}')" class="btn btn-secondary" style="margin-top: auto; padding: 6px 12px; font-size: 0.8rem; border-color: var(--danger-color); color: var(--danger-color);">
          🗑️ Delete
        </button>
      `;

      plantList.appendChild(card);
    });
  }, (error) => {
    console.warn("Firestore onSnapshot error:", error);
    if (plantList) {
      plantList.innerHTML = `<p style="color: var(--danger-color);">Error connecting to cloud garden: ${error.message}</p>`;
    }
  });
}

// 4. Auto-attach to Auth State
onAuthStateChanged(auth, (user) => {
  const plantList = document.getElementById("plant-list");
  if (!plantList) return;

  if (user) {
    console.log("Real-time sync active for user:", user.uid);
    loadPlants(user);
  } else {
    plantList.innerHTML = `
      <div style="grid-column: 1 / -1; padding: 20px; text-align: center; color: var(--text-secondary);">
        <p>🔒 Please <a href="#settings" data-route="settings" style="color: var(--primary-color); font-weight: 600; text-decoration: underline;">Sign In</a> to view and sync your cloud garden.</p>
      </div>
    `;
  }
});
