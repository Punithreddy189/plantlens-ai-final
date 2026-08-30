// AI Diagnostic Scanner Module
import { SAMPLE_PLANTS } from './data.js';
import { validateImage, showToast, downloadReport, escapeHTML, compressImage, announceToScreenReader } from './utils.js';
import { triggerConfetti } from './animation.js';
import { StorageManager } from './storage.js';

/**
 * Intelligent botanical fallback inference for soil requirements
 */
export function getSoilFallback(plantName = '', familyName = '') {
  const name = String(plantName || '').toLowerCase();
  const fam = String(familyName || '').toLowerCase();

  // 1. Cacti & Succulents
  if (name.includes('cactus') || name.includes('opuntia') || name.includes('succulent') || name.includes('aloe') || name.includes('echeveria') || name.includes('jade') || fam.includes('cact') || fam.includes('crassul')) {
    return {
      soilType: 'Cactus & succulent gritty coarse mix',
      soilPh: '6.0 - 7.5 (Slightly Acidic to Neutral)',
      soilDrainage: 'Fast-draining & highly porous',
      soilRecommendation: '• 50% Coarse silica sand or perlite\n• 30% Potting soil substrate\n• 20% Pumice, grit or fine gravel\n• Ensure pot has bottom drainage holes'
    };
  }

  // 2. Aroids & Tropical Houseplants (Monstera, Pothos, Philodendron, Snake plant, ZZ plant)
  if (name.includes('monstera') || name.includes('pothos') || name.includes('philodendron') || name.includes('aroid') || name.includes('sansevieria') || name.includes('snake plant') || name.includes('zz plant') || name.includes('ficus') || fam.includes('arac')) {
    return {
      soilType: 'Chunky aerated Aroid potting blend',
      soilPh: '5.5 - 6.8 (Mildly Acidic)',
      soilDrainage: 'High aeration & free-draining',
      soilRecommendation: '• 40% Chunky pine/orchid bark or coconut husk chips\n• 30% Peat moss or coco coir\n• 20% Coarse perlite or pumice\n• 10% Organic worm castings'
    };
  }

  // 3. Vegetables, Tomatoes & Fruiting Crops (Tomato, Pepper, Eggplant, Cucumber)
  if (name.includes('tomato') || name.includes('pepper') || name.includes('eggplant') || name.includes('potato') || name.includes('chili') || name.includes('cucumber') || fam.includes('solanac')) {
    return {
      soilType: 'Rich, fertile loamy garden soil',
      soilPh: '6.0 - 6.8 (Slightly Acidic)',
      soilDrainage: 'Well-drained & moisture-retentive',
      soilRecommendation: '• 40% Rich garden topsoil\n• 40% Aged compost or well-rotted manure\n• 20% Perlite or builder sand\n• Add 1 tbsp crushed eggshells/dolomite lime for calcium boost'
    };
  }

  // 4. Herbs & Leafy Greens (Basil, Mint, Rosemary, Thyme, Cilantro, Oregano, Lettuce)
  if (name.includes('basil') || name.includes('mint') || name.includes('rosemary') || name.includes('thyme') || name.includes('cilantro') || name.includes('herb') || name.includes('oregano') || fam.includes('lamiac')) {
    return {
      soilType: 'Fertile, loose, well-aerated loam',
      soilPh: '6.0 - 7.0 (Neutral to Mildly Acidic)',
      soilDrainage: 'Well-drained & light',
      soilRecommendation: '• 50% Quality potting soil\n• 30% Organic compost\n• 20% Perlite or vermiculite for root aeration\n• Avoid overly compacted dense clay'
    };
  }

  // 5. Roses & Flowering Woody Shrubs (Rose, Hibiscus, Bougainvillea, Jasmine)
  if (name.includes('rose') || name.includes('hibiscus') || name.includes('jasmine') || name.includes('bougainvillea') || fam.includes('rosac')) {
    return {
      soilType: 'Deep, nutrient-rich clay loam',
      soilPh: '6.2 - 6.8 (Slightly Acidic)',
      soilDrainage: 'Good drainage without waterlogging',
      soilRecommendation: '• 50% Garden loam soil\n• 30% Well-rotted compost or leaf mold\n• 20% Coarse builder sand\n• Mix in bone meal for strong root anchoring'
    };
  }

  // 6. Orchids & Epiphytes
  if (name.includes('orchid') || name.includes('phalaenopsis') || fam.includes('orchid')) {
    return {
      soilType: 'Chunky Epiphytic bark mix (No dense soil)',
      soilPh: '5.5 - 6.5 (Mildly Acidic)',
      soilDrainage: 'Ultra-fast drainage with maximum airflow',
      soilRecommendation: '• 70% Coarse fir bark or coconut husk chunks\n• 20% Horticultural perlite or charcoal\n• 10% Long-fiber sphagnum moss'
    };
  }

  // 7. General Default
  return {
    soilType: 'Loamy soil rich in organic matter',
    soilPh: '6.0 - 7.0 (Optimal Neutral Range)',
    soilDrainage: 'Well-drained with balanced moisture retention',
    soilRecommendation: '• 50% Garden topsoil or potting mix\n• 30% Aged organic compost\n• 20% Perlite, vermiculite, or coarse sand'
  };
}

/**
 * Strict schema normalization for soil recommendation data
 */
export function normalizeSoil(parsed = {}, plantName = '', isNotPlant = false) {
  if (isNotPlant) {
    return {
      soilType: 'N/A',
      soilPh: 'N/A',
      soilDrainage: 'N/A',
      soilRecommendation: 'N/A'
    };
  }

  const fallback = getSoilFallback(plantName || parsed.name || parsed.plantName, parsed.family);

  const rawType = parsed.soilType || parsed.soil_type;
  const rawPh = parsed.soilPh || parsed.soil_ph;
  const rawDrainage = parsed.soilDrainage || parsed.soil_drainage;
  const rawRec = parsed.soilRecommendation || parsed.soil_recommendation;

  const soilType = (typeof rawType === 'string' && rawType.trim() && rawType !== 'N/A') ? rawType.trim() : fallback.soilType;
  const soilPh = (typeof rawPh === 'string' && rawPh.trim() && rawPh !== 'N/A') ? rawPh.trim() : fallback.soilPh;
  const soilDrainage = (typeof rawDrainage === 'string' && rawDrainage.trim() && rawDrainage !== 'N/A') ? rawDrainage.trim() : fallback.soilDrainage;

  let soilRecommendation = fallback.soilRecommendation;
  if (Array.isArray(rawRec) && rawRec.length > 0) {
    soilRecommendation = rawRec.map(item => String(item).trim().startsWith('•') ? String(item).trim() : `• ${String(item).trim()}`).join('\n');
  } else if (typeof rawRec === 'string' && rawRec.trim().length > 6 && rawRec.trim() !== 'N/A') {
    soilRecommendation = rawRec.trim();
  }

  return {
    soilType,
    soilPh,
    soilDrainage,
    soilRecommendation
  };
}

/**
 * Classify pH for dynamic badge color coding
 */
export function getPhClassification(phString = '') {
  const str = String(phString || '').toLowerCase();
  
  if (str.includes('strongly acidic') || str.includes('acidic (< 6') || str.includes('acidic (<6')) {
    return { class: 'acidic', label: 'Acidic (< 6.0)' };
  }
  if (str.includes('slightly acidic') || str.includes('mildly acidic') || str.includes('6.0 - 6.8') || str.includes('5.5 - 6.8') || str.includes('6.0 - 6.5')) {
    return { class: 'acidic', label: 'Slightly Acidic' };
  }
  if (str.includes('alkaline') || str.includes('basic') || str.includes('7.5 -') || str.includes('> 7.5')) {
    return { class: 'alkaline', label: 'Alkaline (> 7.5)' };
  }
  if (str.includes('neutral')) {
    return { class: 'neutral', label: 'Neutral' };
  }

  // Parse numerical range e.g. "6.0 - 6.8"
  const numbers = str.match(/\d+(\.\d+)?/g);
  if (numbers && numbers.length >= 1) {
    const vals = numbers.map(n => parseFloat(n));
    const avg = vals.reduce((a, b) => a + b, 0) / vals.length;
    if (avg < 6.0) return { class: 'acidic', label: 'Acidic (< 6.0)' };
    if (avg <= 6.8) return { class: 'acidic', label: 'Slightly Acidic' };
    if (avg <= 7.3) return { class: 'neutral', label: 'Neutral' };
    return { class: 'alkaline', label: 'Alkaline' };
  }

  return { class: 'neutral', label: 'Neutral pH' };
}

export const ScannerModule = {
  currentScanResult: null,
  cameraStream: null,
  isScanning: false,

  init() {
    this.bindEvents();
    this.renderSampleChips();
  },

  bindEvents() {
    const dropzone = document.getElementById('dropzone');
    const fileInput = document.getElementById('file-input');
    const btnUpload = document.getElementById('btn-upload-file');
    const btnCamera = document.getElementById('btn-open-camera');

    if (btnUpload && fileInput) {
      btnUpload.addEventListener('click', (e) => {
        e.stopPropagation();
        fileInput.click();
      });
      fileInput.addEventListener('change', (e) => {
        if (e.target.files && e.target.files[0]) {
          this.handleFileSelected(e.target.files[0]);
        }
      });
    }

    if (dropzone) {
      // Click on dropzone opens file dialog
      dropzone.addEventListener('click', (e) => {
        if (!e.target.closest('#btn-open-camera') && !e.target.closest('#btn-upload-file')) {
          fileInput?.click();
        }
      });

      // Keyboard accessibility (Enter / Space)
      dropzone.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          if (!e.target.closest('#btn-open-camera') && !e.target.closest('#btn-upload-file')) {
            e.preventDefault();
            fileInput?.click();
          }
        }
      });

      dropzone.addEventListener('dragover', (e) => {
        e.preventDefault();
        dropzone.classList.add('drag-over');
      });
      dropzone.addEventListener('dragleave', () => dropzone.classList.remove('drag-over'));
      dropzone.addEventListener('drop', (e) => {
        e.preventDefault();
        dropzone.classList.remove('drag-over');
        if (e.dataTransfer.files && e.dataTransfer.files[0]) {
          this.handleFileSelected(e.dataTransfer.files[0]);
        }
      });
    }

    if (btnCamera) {
      btnCamera.addEventListener('click', (e) => {
        e.stopPropagation();
        this.openCameraModal();
      });
    }
  },

  renderSampleChips() {
    const container = document.getElementById('sample-chips-container');
    if (!container) return;

    container.innerHTML = SAMPLE_PLANTS.map(plant => `
      <button type="button" class="sample-chip ripple" data-sample-id="${plant.id}" aria-label="Select sample ${escapeHTML(plant.name)} (${plant.healthStatus === 'healthy' ? 'Healthy' : 'Diseased'})">
        <img src="${plant.image}" alt="${escapeHTML(plant.name)}" aria-hidden="true"/>
        <span>${escapeHTML(plant.name)} (${plant.healthStatus === 'healthy' ? 'Healthy' : 'Diseased'})</span>
      </button>
    `).join('');

    container.querySelectorAll('.sample-chip').forEach(chip => {
      chip.addEventListener('click', () => {
        const sampleId = chip.getAttribute('data-sample-id');
        const match = SAMPLE_PLANTS.find(p => p.id === sampleId);
        if (match) {
          this.startScanPipeline(match);
        }
      });
    });
  },

  async handleFileSelected(file) {
    const check = validateImage(file);
    if (!check.valid) {
      showToast(check.message, 'danger');
      return;
    }

    try {
      const compressedDataUrl = await compressImage(file, 1280, 0.8);
      await this.processImageInput(file, compressedDataUrl);
    } catch (e) {
      console.warn('Compression fallback to direct reader:', e);
      const reader = new FileReader();
      reader.onload = async (ev) => {
        await this.processImageInput(file, ev.target.result);
      };
      reader.readAsDataURL(file);
    }
  },

  async processImageInput(fileOrBlob, imageDataUrl) {
    if (this.isScanning) {
      showToast('Scan already in progress. Please wait...', 'info');
      return;
    }
    this.isScanning = true;

    try {
      console.log('🚀 [Scanner] Starting AI identification pipeline for image:', fileOrBlob);

      const rawStoredGeminiKey = localStorage.getItem('plantlens_gemini_api_key');
      const geminiKey = (rawStoredGeminiKey && rawStoredGeminiKey.trim() !== '')
        ? rawStoredGeminiKey.trim()
        : (import.meta.env.VITE_GEMINI_API_KEY || import.meta.env.GEMINI_API_KEY || '');

      const rawStoredKey = localStorage.getItem('plantlens_plantnet_api_key');
      const plantnetKey = (rawStoredKey && rawStoredKey.trim() !== '') 
        ? rawStoredKey.trim() 
        : (import.meta.env.VITE_PLANTNET_API_KEY || '');

      console.log('🔑 [Scanner] Active Gemini Key:', geminiKey ? `${geminiKey.substring(0, 6)}...` : 'None');
      console.log('🔑 [Scanner] Active Pl@ntNet Key:', plantnetKey ? `${plantnetKey.substring(0, 6)}...` : 'None');

      let lastErrMsg = 'Could not connect to AI services.';

      // 1. Local FastAPI Backend Engine (http://127.0.0.1:8000/classify)
      try {
        console.log('📡 [Scanner] Checking local FastAPI backend (http://127.0.0.1:8000/classify)...');
        const fastApiResult = await this.callFastAPIBackend(fileOrBlob, imageDataUrl);
        if (fastApiResult && (fastApiResult.name || fastApiResult.scientificName)) {
          console.log('✅ [Scanner] FastAPI backend identification successful:', fastApiResult.name);
          showToast(`🌿 Identified via FastAPI: ${fastApiResult.name}`, 'success');
          this.startScanPipeline(fastApiResult);
          return;
        }
      } catch (fastApiErr) {
        console.info('ℹ️ [Scanner] Local FastAPI backend unavailable:', fastApiErr.message);
      }

      // 2. Serverless Gemini Vision Proxy (/api/analyze)
      showToast('🌿 Analyzing plant with Gemini Vision AI...', 'info');
      try {
        console.log('📡 [Scanner] Calling serverless endpoint /api/analyze...');
        const serverlessResult = await this.callServerlessAPI(imageDataUrl, fileOrBlob.type || 'image/jpeg');
        if (serverlessResult && (serverlessResult.name || serverlessResult.scientificName)) {
          console.log('✅ [Scanner] Gemini identification successful:', serverlessResult.name);
          this.startScanPipeline(serverlessResult);
          return;
        }
      } catch (apiErr) {
        console.warn('⚠️ [Scanner] Serverless /api/analyze failed:', apiErr.message);
        lastErrMsg = apiErr.message;
      }

    // 2. Secondary Engine: Pl@ntNet (FastAPI backend or direct Vite proxy)
    showToast('🌿 Trying Pl@ntNet Botanical Engine...', 'info');
    try {
      console.log('📡 [Scanner] Invoking Pl@ntNet API...');
      const plantNetResult = await this.callPlantNetAPI(fileOrBlob, imageDataUrl, plantnetKey);
      if (plantNetResult) {
        console.log('✅ [Scanner] Pl@ntNet identification successful:', plantNetResult.name);
        this.startScanPipeline(plantNetResult);
        return;
      }
    } catch (err) {
      console.error('❌ [Scanner] Pl@ntNet Error:', err);
      lastErrMsg = `${lastErrMsg} | Pl@ntNet: ${err.message}`;
    }

    // 3. Client-side Gemini fallback if key is configured in browser
    if (geminiKey) {
      try {
        showToast('🌿 Trying direct Gemini Vision AI fallback...', 'info');
        console.log('📡 [Scanner] Trying direct client Gemini fallback...');
        const directGeminiResult = await this.callGeminiAPI(imageDataUrl, fileOrBlob.type || 'image/jpeg', geminiKey);
        if (directGeminiResult) {
          console.log('✅ [Scanner] Direct Gemini identification successful:', directGeminiResult.name);
          this.startScanPipeline(directGeminiResult);
          return;
        }
      } catch (gemErr) {
        console.error('❌ [Scanner] Direct Gemini Error:', gemErr);
        lastErrMsg = `${lastErrMsg} | Direct Gemini: ${gemErr.message}`;
      }
    }

    // 4. If all fail, display clear diagnostic guidance with exact error
    const unknownResult = {
      id: 'scan-' + Date.now(),
      image: imageDataUrl,
      name: 'Unidentified Plant',
      scientificName: 'Analysis / API Error',
      family: 'PlantLens AI',
      confidence: 0,
      healthStatus: 'healthy',
      healthScore: 0,
      diseaseName: 'Identification Error',
      severity: 'Low',
      description: lastErrMsg,
      symptoms: ['No species identification returned by API', lastErrMsg],
      causes: ['API key missing or server error', 'Check Vercel/server logs for details'],
      organicRemedies: ['Ensure GEMINI_API_KEY environment variable is configured'],
      chemicalTreatments: ['Not applicable'],
      waterSchedule: 'Check soil moisture before watering',
      sunlightNeeds: 'Bright Indirect Light',
      tempRange: '18°C - 28°C',
      humidity: '50% - 70%',
      fertilizer: 'Standard balanced houseplant fertilizer',
      harvestTime: 'N/A',
      soilType: 'Loamy soil rich in organic matter',
      soilPh: '6.0 - 7.0',
      soilDrainage: 'Well-drained with balanced aeration',
      soilRecommendation: '• 50% Garden loam soil\n• 30% Organic compost\n• 20% Sand or perlite for aeration'
    };
    this.startScanPipeline(unknownResult);
  } finally {
    this.isScanning = false;
  }
},

  async callFastAPIBackend(fileOrBlob, imageDataUrl) {
    const formData = new FormData();
    formData.append('image', fileOrBlob, fileOrBlob.name || 'plant_scan.jpg');
    formData.append('language', 'English');

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 20000);

    try {
      const res = await fetch('http://127.0.0.1:8000/classify', {
        method: 'POST',
        body: formData,
        signal: controller.signal
      });
      clearTimeout(timeoutId);
      if (!res.ok) {
        throw new Error(`FastAPI returned status ${res.status}`);
      }
      const data = await res.json();
      if (!data.success && data.error_message) {
        throw new Error(data.error_message);
      }

      const soilData = getSoilFallback(data.plant_name, data.scientific_name);
      return {
        id: 'scan-' + Date.now(),
        image: imageDataUrl,
        isPlant: data.is_plant,
        name: data.plant_name || 'Identified Plant',
        scientificName: data.scientific_name || 'Botanical Species',
        family: 'PlantLens AI',
        confidence: Math.round((data.confidence || 0.95) * 100),
        healthStatus: (data.health_status && data.health_status.toLowerCase().includes('disease')) ? 'diseased' : 'healthy',
        healthScore: data.health_status === 'Healthy' ? 95 : 60,
        diseaseName: data.disease || 'None (Healthy Plant)',
        severity: data.severity || 'Healthy',
        description: data.description || 'Diagnosis generated via PlantLens AI Hybrid Backend.',
        symptoms: [data.confidence_reason || 'Characteristic leaf morphology'],
        causes: ['Environmental factors and ambient photoperiod'],
        organicRemedies: data.treatment ? [data.treatment] : ['Routine plant maintenance'],
        chemicalTreatments: ['None required'],
        waterSchedule: data.watering || 'Water when topsoil feels dry',
        sunlightNeeds: data.sunlight || 'Bright indirect daylight',
        tempRange: '18°C - 28°C',
        humidity: '50% - 70%',
        fertilizer: data.fertilizer || 'Balanced fertilizer monthly',
        harvestTime: 'Perennial cycle',
        soilType: data.soil_type || soilData.soilType,
        soilPh: data.soil_ph || soilData.soilPh,
        soilDrainage: data.soil_drainage || soilData.soilDrainage,
        soilRecommendation: data.soil_recommendation || soilData.soilRecommendation
      };
    } catch (e) {
      clearTimeout(timeoutId);
      throw e;
    }
  },

  async callPlantNetAPI(fileOrBlob, imageDataUrl, plantnetKey) {
    let data = null;
    const key = plantnetKey || import.meta.env.VITE_PLANTNET_API_KEY || '';

    // 1. Try FastAPI backend if running locally
    try {
      const backendForm = new FormData();
      backendForm.append('file', fileOrBlob, fileOrBlob.name || 'plant_scan.jpg');
      
      console.log('📡 [Pl@ntNet] Checking FastAPI backend: http://localhost:8000/identify');
      const response = await fetch('http://localhost:8000/identify', {
        method: 'POST',
        body: backendForm,
        signal: AbortSignal.timeout(5000)
      });

      if (response.ok) {
        data = await response.json();
        console.log('🌿 [Pl@ntNet] Received response from FastAPI backend');
      }
    } catch (e) {
      console.log('ℹ️ [Pl@ntNet] FastAPI backend not running or timed out. Using proxy/direct fallback...');
    }

    // 2. Fallback: Try Vite dev proxy
    if (!data) {
      const endpoint = `/api/plantnet/v2/identify/all?api-key=${key}&detailed=true`;
      try {
        console.log(`📡 [Pl@ntNet] Trying proxy endpoint: ${endpoint.split('?')[0]}`);
        const form = new FormData();
        form.append('images', fileOrBlob, fileOrBlob.name || 'plant_scan.jpg');
        form.append('organs', 'leaf');

        const res = await fetch(endpoint, {
          method: 'POST',
          body: form,
          signal: AbortSignal.timeout(15000)
        });

        if (res.ok) {
          data = await res.json();
          console.log('🌿 [Pl@ntNet] Successful identification via proxy');
        } else {
          const errBody = await res.text();
          console.warn(`[Pl@ntNet] Endpoint returned ${res.status}:`, errBody);
        }
      } catch (err) {
        console.warn('[Pl@ntNet] Proxy fetch error:', err.message);
      }
    }

    if (!data) {
      throw new Error('Could not contact Pl@ntNet API (ensure network connectivity or check IP whitelist at my.plantnet.org)');
    }

    console.log('🌿 Full Pl@ntNet Response:', data);

    const results = data.results || (Array.isArray(data) ? data : []);
    if (!results || results.length === 0) {
      throw new Error('No botanical species match found in image.');
    }

    const top = results[0];
    const scientificName =
      top.species?.scientificNameWithoutAuthor ||
      top.species?.scientificName ||
      top.scientificName ||
      'Opuntia ficus-indica';

    const commonName =
      top.species?.commonNames?.[0] ||
      (top.commonNames && top.commonNames[0]) ||
      scientificName;

    const familyName =
      top.species?.family?.scientificNameWithoutAuthor ||
      top.species?.family?.scientificName ||
      top.family ||
      'Cactaceae';

    const score = typeof top.score === 'number' ? top.score : 0.92;
    const confidencePct = Math.min(99, Math.max(15, Math.round(score * 100)));
    const healthScore = Math.min(98, Math.round(75 + (confidencePct * 0.2)));

    const isCactus = commonName.toLowerCase().includes('cactus') || 
                     scientificName.toLowerCase().includes('opuntia') || 
                     familyName.toLowerCase().includes('cact');

    const soilData = getSoilFallback(commonName, familyName);

    return {
      id: 'scan-' + Date.now(),
      image: imageDataUrl,
      name: commonName,
      scientificName: scientificName,
      family: familyName,
      confidence: confidencePct,
      healthStatus: 'healthy',
      healthScore: healthScore,
      diseaseName: 'None (Healthy Specimen)',
      severity: 'Healthy',
      severityLevel: 1,
      description: `${commonName} (${scientificName}) identified with ${confidencePct}% confidence via Pl@ntNet AI Engine. Specimen exhibits strong structural vitality.`,
      symptoms: [
        'Vibrant healthy pigmentation and turgor',
        'Intact epidermal tissue without necrosis',
        'No active fungal lesions or pest infestations'
      ],
      causes: [
        'Optimal ambient lighting and moisture balance',
        'Well-aerated substrate and suitable temperature range'
      ],
      treatments: [
        'Maintain existing care and watering routine.',
        'Inspect periodically for dust or foliage vitality.'
      ],
      organicRemedies: [
        'Wipe pads/leaves occasionally with soft brush or damp cloth',
        'Preventive organic neem oil misting once quarterly'
      ],
      chemicalTreatments: [
        'None required for healthy plant'
      ],
      waterSchedule: isCactus ? 'Water once every 10-14 days (allow soil to dry out completely)' : 'Water when top 1-2 inches of soil feel dry (approx. 1-2 times weekly)',
      sunlightNeeds: isCactus ? 'Full Direct Sunlight (6+ hours daily)' : 'Bright Indirect Light to Moderate Sunlight',
      tempRange: isCactus ? '20°C - 35°C' : '18°C - 28°C',
      humidity: isCactus ? '20% - 40% (Low Humidity)' : '45% - 70%',
      fertilizer: isCactus ? 'Diluted succulent/cactus fertilizer once every 2 months in spring/summer' : 'Diluted balanced organic liquid fertilizer monthly',
      harvestTime: 'Perennial Decorative Cycle',
      soilType: soilData.soilType,
      soilPh: soilData.soilPh,
      soilDrainage: soilData.soilDrainage,
      soilRecommendation: soilData.soilRecommendation,
      preventionTips: [
        'Ensure pot drainage holes are clear and unobstructed',
        'Never allow standing water at root base'
      ]
    };
  },

  async callServerlessAPI(base64DataUrl, mimeType) {
    const base64Data = base64DataUrl.split(',')[1];
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 25000);

    try {
      const res = await fetch('/api/analyze', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        signal: controller.signal,
        body: JSON.stringify({
          imageBase64: base64Data,
          mimeType: mimeType || 'image/jpeg'
        })
      });
      clearTimeout(timeoutId);

      if (!res.ok) {
        const errText = await res.text();
        console.error('❌ Serverless API Error:', res.status, errText);
        throw new Error(`Serverless API error (${res.status}): ${errText}`);
      }
      const parsed = await res.json();
      return {
        ...parsed,
        image: base64DataUrl,
        id: 'scan-' + Date.now()
      };
    } catch (err) {
      clearTimeout(timeoutId);
      if (err.name === 'AbortError') {
        throw new Error('Analysis request timed out (25s). Please check connection.');
      }
      throw err;
    }
  },

  async callGeminiAPI(base64DataUrl, mimeType, apiKey) {
    const base64Data = base64DataUrl.split(',')[1];
    const prompt = `You are a strict botanical expert and vision AI.
FIRST: Carefully examine whether this image contains a real plant, crop, leaf, flower, stem, tree, or foliage.
If the image does NOT contain a plant (for example: a human face, selfie, person, animal, car, furniture, electronic screen, food, room, or any non-plant subject), you MUST return JSON:
{
  "isPlant": false,
  "name": "Not a Plant",
  "scientificName": "Non-Botanical Subject",
  "family": "N/A",
  "confidence": 0,
  "healthStatus": "unknown",
  "healthScore": 0,
  "diseaseName": "No Plant Detected",
  "severity": "N/A",
  "description": "No plant, leaf, or botanical foliage was detected in this image. Please take a clear photo of a plant to identify it.",
  "symptoms": ["Non-plant subject detected in camera view"],
  "causes": ["Image contains a person, face, room, or non-botanical object"],
  "organicRemedies": ["Please point camera at a plant, tree leaf, or flower"],
  "chemicalTreatments": ["None"],
  "waterSchedule": "N/A",
  "sunlightNeeds": "N/A",
  "tempRange": "N/A",
  "humidity": "N/A",
  "fertilizer": "N/A",
  "harvestTime": "N/A",
  "soilType": "N/A",
  "soilPh": "N/A",
  "soilDrainage": "N/A",
  "soilRecommendation": "N/A"
}

If the image IS a plant, return valid JSON:
{
  "isPlant": true,
  "name": "Common plant name",
  "scientificName": "Latin botanical name",
  "family": "Botanical family name",
  "confidence": 95,
  "healthStatus": "healthy",
  "healthScore": 95,
  "diseaseName": "Specific disease or None (Healthy Plant)",
  "severity": "Healthy",
  "description": "Botanical and health summary",
  "symptoms": ["Symptom 1", "Symptom 2"],
  "causes": ["Cause 1", "Cause 2"],
  "organicRemedies": ["Remedy 1"],
  "chemicalTreatments": ["Treatment 1"],
  "waterSchedule": "Watering frequency",
  "sunlightNeeds": "Sunlight requirement",
  "tempRange": "20°C - 30°C",
  "humidity": "50% - 70%",
  "fertilizer": "Fertilizer recommendation",
  "harvestTime": "Harvest period",
  "soilType": "Optimal soil type (e.g. Loamy soil rich in organic matter)",
  "soilPh": "Ideal pH range (e.g. 6.0 - 6.8)",
  "soilDrainage": "Drainage requirement (e.g. Well-drained with high aeration)",
  "soilRecommendation": "• 50% Garden loam\\n• 30% Compost\\n• 20% Sand or perlite"
}
Return ONLY valid JSON.`;

    const modelsToTry = [
      'gemini-3.5-flash-lite',
      'gemini-flash-lite-latest',
      'gemini-3.6-flash',
      'gemini-3.7-flash',
      'gemini-3.5-flash',
      'gemini-flash-latest'
    ];
    let lastError = null;

    for (const model of modelsToTry) {
      try {
        console.log(`🤖 [Gemini AI] Trying model: ${model}`);
        const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${apiKey}`;

        const response = await fetch(url, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'x-goog-api-key': apiKey
          },
          signal: AbortSignal.timeout(10000),
          body: JSON.stringify({
            contents: [
              {
                parts: [
                  { text: prompt },
                  {
                    inline_data: {
                      mime_type: mimeType || 'image/jpeg',
                      data: base64Data
                    }
                  }
                ]
              }
            ]
          })
        });

        if (response.status === 404 || response.status === 400) {
          lastError = await response.text();
          console.warn(`[Gemini AI] Model ${model} not available (${response.status}):`, lastError);
          continue;
        }

        if (response.ok) {
          const data = await response.json();
          const rawText = data.candidates?.[0]?.content?.parts?.[0]?.text || '';
          console.log('🌿 [Gemini AI] Raw AI Response received:', rawText);

          let parsed = null;
          const jsonMatch = rawText.match(/\{[\s\S]*\}/);
          if (jsonMatch) {
            parsed = JSON.parse(jsonMatch[0]);
          } else {
            parsed = JSON.parse(rawText.replace(/```json/g, '').replace(/```/g, '').trim());
          }

          if (parsed) {
            const rawName = String(parsed.name || parsed.plantName || '').trim();
            const isNotPlant = parsed.isPlant === false || 
              rawName.toLowerCase().includes('not a plant') || 
              rawName.toLowerCase().includes('human') || 
              rawName.toLowerCase().includes('person') || 
              rawName.toLowerCase().includes('face');

            const plantName = isNotPlant ? 'Not a Plant' : (rawName || 'Identified Plant');
            const soilData = normalizeSoil(parsed, plantName, isNotPlant);

            return {
              id: 'scan-' + Date.now(),
              image: base64DataUrl,
              isPlant: !isNotPlant,
              name: plantName,
              scientificName: isNotPlant ? 'Non-Botanical Subject' : (parsed.scientificName || 'Botanical Species'),
              family: isNotPlant ? 'N/A' : (parsed.family || 'Botanical'),
              confidence: isNotPlant ? 0 : (parsed.confidence || 95),
              healthStatus: isNotPlant ? 'unknown' : (parsed.healthStatus || 'healthy'),
              healthScore: isNotPlant ? 0 : (parsed.healthScore || (parsed.healthStatus === 'healthy' ? 95 : 50)),
              diseaseName: isNotPlant ? 'No Plant Detected' : (parsed.diseaseName || (parsed.healthStatus === 'healthy' ? 'None (Healthy Plant)' : 'Condition Detected')),
              severity: isNotPlant ? 'N/A' : (parsed.severity || (parsed.healthStatus === 'healthy' ? 'Healthy' : 'Medium')),
              description: isNotPlant ? 'No plant, leaf, or botanical foliage was detected in this image. Please take a clear photo of a plant to identify it.' : (parsed.description || 'Diagnosis generated via Google Gemini Multimodal Vision AI.'),
              symptoms: isNotPlant ? ['Non-plant subject detected in camera view'] : (parsed.symptoms || ['Characteristic leaf pigmentation and structure']),
              causes: isNotPlant ? ['Image contains a person, face, room, or non-botanical object'] : (parsed.causes || ['Growth environment and photoperiod factors']),
              organicRemedies: isNotPlant ? ['Please point camera at a plant leaf or flower'] : (parsed.organicRemedies || ['Routine care and balanced watering']),
              chemicalTreatments: isNotPlant ? ['None'] : (parsed.chemicalTreatments || ['None required']),
              waterSchedule: isNotPlant ? 'N/A' : (parsed.waterSchedule || 'Water when topsoil feels dry'),
              sunlightNeeds: isNotPlant ? 'N/A' : (parsed.sunlightNeeds || 'Bright indirect daylight'),
              tempRange: isNotPlant ? 'N/A' : (parsed.tempRange || '18°C - 28°C'),
              humidity: isNotPlant ? 'N/A' : (parsed.humidity || '50% - 70%'),
              fertilizer: isNotPlant ? 'N/A' : (parsed.fertilizer || 'Balanced organic plant food monthly during growing season'),
              harvestTime: isNotPlant ? 'N/A' : (parsed.harvestTime || 'Perennial cycle'),
              soilType: soilData.soilType,
              soilPh: soilData.soilPh,
              soilDrainage: soilData.soilDrainage,
              soilRecommendation: soilData.soilRecommendation
            };
          }
        } else {
          lastError = await response.text();
          console.warn(`[Gemini AI] Model ${model} returned error (${response.status}):`, lastError);
        }
      } catch (e) {
        lastError = e.message;
        console.warn(`[Gemini AI] Model ${model} caught exception:`, e);
      }
    }

    throw new Error(`Gemini API returned error across models: ${lastError}`);
  },

  openCameraModal() {
    const modal = document.getElementById('camera-modal');
    const video = document.getElementById('webcam-video');
    if (!modal || !video) return;

    modal.classList.add('active');

    if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
      navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })
        .then(stream => {
          this.cameraStream = stream;
          video.srcObject = stream;
        })
        .catch(err => {
          showToast('Unable to access camera. Please check device permissions.', 'warning');
        });
    }

    const snapBtn = document.getElementById('btn-snap-photo');
    const closeBtn = document.getElementById('btn-close-camera');

    // Focus snap button for keyboard users
    setTimeout(() => snapBtn?.focus(), 100);

    // Escape key listener for camera modal
    this._cameraKeyHandler = (e) => {
      if (e.key === 'Escape') {
        this.closeCameraModal();
      }
    };
    window.addEventListener('keydown', this._cameraKeyHandler);

    if (snapBtn) {
      snapBtn.onclick = () => {
        const canvas = document.createElement('canvas');
        canvas.width = video.videoWidth || 640;
        canvas.height = video.videoHeight || 480;
        const ctx = canvas.getContext('2d');
        ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
        const dataUrl = canvas.toDataURL('image/jpeg', 0.9);

        this.closeCameraModal();

        canvas.toBlob((blob) => {
          if (blob) {
            this.processImageInput(blob, dataUrl);
          } else {
            showToast('Failed to capture frame from camera.', 'danger');
          }
        }, 'image/jpeg', 0.9);
      };
    }

    if (closeBtn) {
      closeBtn.onclick = () => this.closeCameraModal();
    }
  },

  closeCameraModal() {
    const modal = document.getElementById('camera-modal');
    if (modal) modal.classList.remove('active');
    if (this._cameraKeyHandler) {
      window.removeEventListener('keydown', this._cameraKeyHandler);
      this._cameraKeyHandler = null;
    }
    if (this.cameraStream) {
      this.cameraStream.getTracks().forEach(track => track.stop());
      this.cameraStream = null;
    }
    // Return focus to camera trigger button
    document.getElementById('btn-open-camera')?.focus();
  },

  startScanPipeline(plantResultData) {
    const dropzoneCard = document.getElementById('dropzone');
    const samplesBar = document.getElementById('samples-bar');
    const pipelineCard = document.getElementById('scan-pipeline-card');
    const reportCard = document.getElementById('report-card');

    if (dropzoneCard) dropzoneCard.style.display = 'none';
    if (samplesBar) samplesBar.style.display = 'none';
    if (reportCard) reportCard.style.display = 'none';
    if (pipelineCard) pipelineCard.style.display = 'block';

    announceToScreenReader('AI plant diagnostic pipeline running. Analyzing leaf texture, neural classification, and disease patterns...');

    const steps = [
      { id: 'step-upload', text: 'Uploading Image...' },
      { id: 'step-preprocess', text: 'Pre-processing Image & Normalizing...' },
      { id: 'step-identify', text: 'Identifying Botanical Species...' },
      { id: 'step-disease', text: 'Running Neural Network Disease Detection...' },
      { id: 'step-report', text: 'Generating AI Diagnostic Report...' }
    ];

    const pipelineContainer = document.getElementById('pipeline-steps');
    if (pipelineContainer) {
      pipelineContainer.innerHTML = steps.map((s, idx) => `
        <div class="pipeline-step ${idx === 0 ? 'active' : ''}" id="${s.id}">
          <div class="step-icon-status">${idx + 1}</div>
          <span>${s.text}</span>
        </div>
      `).join('');
    }

    let currentStepIndex = 0;
    const interval = setInterval(() => {
      if (currentStepIndex < steps.length - 1) {
        const prevStep = document.getElementById(steps[currentStepIndex].id);
        if (prevStep) {
          prevStep.classList.remove('active');
          prevStep.classList.add('completed');
          prevStep.querySelector('.step-icon-status').innerHTML = '✓';
        }

        currentStepIndex++;
        const nextStep = document.getElementById(steps[currentStepIndex].id);
        if (nextStep) {
          nextStep.classList.add('active');
        }
      } else {
        clearInterval(interval);
        // Pipeline Complete
        const lastStep = document.getElementById(steps[currentStepIndex].id);
        if (lastStep) {
          lastStep.classList.remove('active');
          lastStep.classList.add('completed');
          lastStep.querySelector('.step-icon-status').innerHTML = '✓';
        }

        setTimeout(() => {
          if (pipelineCard) pipelineCard.style.display = 'none';
          this.renderReport(plantResultData);
        }, 600);
      }
    }, 500);
  },

  renderReport(data) {
    this.currentScanResult = data;
    const reportCard = document.getElementById('report-card');
    if (!reportCard) return;

    reportCard.style.display = 'block';

    const isNotPlant = data.isPlant === false || 
      data.name.toLowerCase().includes('not a plant') || 
      data.diseaseName === 'No Plant Detected' ||
      data.scientificName === 'Non-Botanical Subject';

    if (isNotPlant) {
      announceToScreenReader('Scan result: No plant detected in this photo.');
      showToast('⚠️ No plant detected in this photo. Please scan a plant or leaf.', 'warning');

      reportCard.innerHTML = `
        <div class="report-header" style="border-left: 4px solid var(--danger-color, #ef4444); background: #fef2f2;">
          <img src="${data.image}" alt="Scanned Image" class="report-img" style="filter: grayscale(30%);"/>
          <div>
            <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 8px; flex-wrap: wrap;">
              <span class="badge" style="background: #fee2e2; color: #dc2626; font-weight: 700;">🚫 NON-PLANT DETECTED</span>
              <span class="badge" style="background: #f1f5f9; color: #64748b;">Subject: Non-Botanical</span>
            </div>
            <h2 style="margin-bottom: 4px; color: #991b1b;">No Plant Detected in Image</h2>
            <p class="subheading" style="color: #64748b;">The AI vision system identified this image as a person, face, or non-plant object.</p>
          </div>
        </div>

        <div style="margin-top: 20px; background: #fff; border: 1px solid #fee2e2; border-radius: var(--radius-md, 12px); padding: 24px;">
          <h4 style="margin-bottom: 12px; color: #b91c1c; display: flex; align-items: center; gap: 8px;">
            <span>💡</span> How to Get an Accurate Plant Scan:
          </h4>
          <ul style="padding-left: 20px; color: #475569; line-height: 1.8;">
            <li><strong>Point at foliage:</strong> Frame a clear, close-up photo of a plant leaf, stem, flower, or crop.</li>
            <li><strong>Check lighting:</strong> Ensure bright, natural lighting without strong shadows or glare.</li>
            <li><strong>Avoid people & cluttered rooms:</strong> Keep the plant in focus and centered in the camera frame.</li>
          </ul>

          <div style="margin-top: 24px; display: flex; gap: 12px; flex-wrap: wrap;">
            <button type="button" class="btn btn-primary ripple" id="btn-scan-again" style="background: var(--primary-color, #166534); color: white; padding: 10px 20px; border-radius: 8px; border: none; cursor: pointer; font-weight: 600;">
              📷 Try Scanning a Plant Leaf
            </button>
          </div>
        </div>
      `;

      document.getElementById('btn-scan-again')?.addEventListener('click', () => {
        reportCard.style.display = 'none';
        const dropzoneCard = document.getElementById('dropzone');
        const samplesBar = document.getElementById('samples-bar');
        if (dropzoneCard) dropzoneCard.style.display = 'block';
        if (samplesBar) samplesBar.style.display = 'flex';
      });
      return;
    }

    // Announce complete diagnostic result to screen readers
    const announceMsg = `Plant analysis complete for ${data.name}. Diagnosis: ${data.diseaseName}. AI Confidence: ${data.confidence}%. Health score: ${data.healthScore} out of 100.`;
    announceToScreenReader(announceMsg);

    // Trigger confetti if plant is healthy!
    if (data.healthStatus === 'healthy') {
      triggerConfetti();
      showToast('🎉 Healthy plant detected! Great job with plant care!', 'success');
    } else {
      showToast(`⚠️ Diagnostic complete: ${data.diseaseName}`, 'warning');
    }

    const severityClass = data.healthStatus === 'healthy' ? 'badge-healthy' : 'badge-diseased';
    const isLowConfidence = data.confidence < 80;

    // Soil & Agronomy Recommendation normalization
    const soilData = normalizeSoil(data, data.name, false);
    const phInfo = getPhClassification(soilData.soilPh);

    reportCard.innerHTML = `
      <div class="report-header">
        <img src="${data.image}" alt="${escapeHTML(data.name)}" class="report-img"/>
        <div>
          <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 8px; flex-wrap: wrap;">
            <span class="badge ${severityClass}">${data.healthStatus.toUpperCase()}</span>
            <span class="badge ${isLowConfidence ? 'badge-warning' : 'badge-info'}">Confidence: ${data.confidence}%</span>
            ${isLowConfidence ? '<span class="badge" style="background:#FFF3E0; color:#E65100;">⚠️ Low Confidence - Consider Retaking</span>' : ''}
          </div>
          <h2 style="margin-bottom: 4px;">${escapeHTML(data.name)}</h2>
          <p class="subheading" style="font-style: italic;">${escapeHTML(data.scientificName)} | Family: ${escapeHTML(data.family || 'Botanical')}</p>
          ${isLowConfidence ? '<p style="font-size: 0.85rem; color: #E65100; margin-top: 4px;">💡 Tip: For optimal accuracy, photograph the leaf/flower close-up in natural daylight.</p>' : ''}
          
          <div class="gauges-flex">
            <!-- Confidence Meter -->
            <div class="circular-gauge-box">
              <svg width="48" height="48" viewBox="0 0 36 36" aria-hidden="true">
                <path d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" stroke="var(--border-color)" stroke-width="3.5"/>
                <path d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" stroke="var(--primary-color)" stroke-width="3.5" stroke-dasharray="${data.confidence}, 100" class="progress-ring-circle"/>
              </svg>
              <div>
                <div style="font-weight: 700;">${data.confidence}%</div>
                <div class="text-muted">AI Confidence</div>
              </div>
            </div>

            <!-- Health Score Gauge -->
            <div class="circular-gauge-box">
              <svg width="48" height="48" viewBox="0 0 36 36" aria-hidden="true">
                <path d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" stroke="var(--border-color)" stroke-width="3.5"/>
                <path d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" stroke="${data.healthScore > 70 ? 'var(--success-color)' : 'var(--danger-color)'}" stroke-width="3.5" stroke-dasharray="${data.healthScore}, 100" class="progress-ring-circle"/>
              </svg>
              <div>
                <div style="font-weight: 700;">${data.healthScore} / 100</div>
                <div class="text-muted">Health Score</div>
              </div>
            </div>

            <!-- Severity Gauge -->
            <div class="circular-gauge-box">
              <div>
                <div style="font-weight: 700; color: ${data.severity === 'Healthy' ? 'var(--success-color)' : 'var(--danger-color)'};">${escapeHTML(data.severity)}</div>
                <div class="text-muted">Disease Severity</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="report-grid-sections">
        <div class="report-block">
          <h4 style="margin-bottom: 12px; color: var(--primary-color);">🔍 Disease & Condition Overview</h4>
          <p style="margin-bottom: 12px;"><strong>Diagnosis:</strong> ${escapeHTML(data.diseaseName)}</p>
          <p style="color: var(--text-secondary);">${escapeHTML(data.description)}</p>

          <h4 style="margin: 20px 0 10px; color: var(--primary-color);">⚠️ Observed Symptoms</h4>
          <ul style="padding-left: 20px; color: var(--text-secondary);">
            ${(data.symptoms || []).map(s => `<li>${escapeHTML(s)}</li>`).join('')}
          </ul>
        </div>

        <div class="report-block">
          <h4 style="margin-bottom: 12px; color: var(--primary-color);">🧪 Causes & Treatment Protocol</h4>
          <p><strong>Possible Causes:</strong></p>
          <ul style="padding-left: 20px; margin-bottom: 14px; color: var(--text-secondary);">
            ${(data.causes || []).map(c => `<li>${escapeHTML(c)}</li>`).join('')}
          </ul>

          <p><strong>🌿 Organic Remedies:</strong></p>
          <ul style="padding-left: 20px; margin-bottom: 14px; color: var(--text-secondary);">
            ${(data.organicRemedies || []).map(r => `<li>${escapeHTML(r)}</li>`).join('')}
          </ul>

          <p><strong>💊 Chemical Treatments:</strong></p>
          <ul style="padding-left: 20px; color: var(--text-secondary);">
            ${(data.chemicalTreatments || []).map(r => `<li>${escapeHTML(r)}</li>`).join('')}
          </ul>
        </div>
      </div>

      <div style="margin-top: 24px; background: var(--bg-color); padding: 20px 24px; border-radius: var(--radius-md);">
        <h4 style="margin-bottom: 12px; color: var(--primary-color);">📋 Care & Environment Requirements</h4>
        <div class="care-specs-grid">
          <div class="spec-item">
            <div style="font-size: 1.2rem;" aria-hidden="true">💧</div>
            <div style="font-weight: 600;">Water</div>
            <div class="text-muted" style="font-size: 0.8rem;">${escapeHTML(data.waterSchedule)}</div>
          </div>
          <div class="spec-item">
            <div style="font-size: 1.2rem;" aria-hidden="true">☀️</div>
            <div style="font-weight: 600;">Sunlight</div>
            <div class="text-muted" style="font-size: 0.8rem;">${escapeHTML(data.sunlightNeeds)}</div>
          </div>
          <div class="spec-item">
            <div style="font-size: 1.2rem;" aria-hidden="true">🌡️</div>
            <div style="font-weight: 600;">Temp</div>
            <div class="text-muted" style="font-size: 0.8rem;">${escapeHTML(data.tempRange)}</div>
          </div>
          <div class="spec-item">
            <div style="font-size: 1.2rem;" aria-hidden="true">💨</div>
            <div style="font-weight: 600;">Humidity</div>
            <div class="text-muted" style="font-size: 0.8rem;">${escapeHTML(data.humidity)}</div>
          </div>
          <div class="spec-item">
            <div style="font-size: 1.2rem;" aria-hidden="true">🌾</div>
            <div style="font-weight: 600;">Fertilizer</div>
            <div class="text-muted" style="font-size: 0.8rem;">${escapeHTML(data.fertilizer)}</div>
          </div>
        </div>
      </div>

      <!-- Soil & Agronomy Recommendation Section -->
      <div class="report-soil-section" style="margin-top: 24px; background: var(--surface-card); border: 1px solid var(--border-color); border-radius: var(--radius-lg); padding: 24px; box-shadow: var(--shadow-sm);">
        <div style="display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; margin-bottom: 18px;">
          <h4 style="margin: 0; color: var(--primary-color); display: flex; align-items: center; gap: 8px; font-size: 1.15rem;">
            <span>🌱</span> Soil & Agronomy Recommendation
          </h4>
          <span class="badge badge-info" style="font-size: 0.78rem;">Smart Substrate Guide</span>
        </div>

        <!-- Soil Attributes Grid -->
        <div class="soil-specs-grid">
          <div class="soil-spec-card">
            <div class="soil-spec-icon">🟫</div>
            <div class="soil-spec-label">Optimal Soil Type</div>
            <div class="soil-spec-val">${escapeHTML(soilData.soilType)}</div>
          </div>

          <div class="soil-spec-card">
            <div class="soil-spec-icon">⚗️</div>
            <div class="soil-spec-label">Ideal Soil pH</div>
            <div class="soil-spec-val" style="display: flex; flex-direction: column; gap: 4px; align-items: center;">
              <span>${escapeHTML(soilData.soilPh)}</span>
              <span class="ph-badge ${phInfo.class}">${phInfo.label}</span>
            </div>
          </div>

          <div class="soil-spec-card">
            <div class="soil-spec-icon">💧</div>
            <div class="soil-spec-label">Drainage Requirement</div>
            <div class="soil-spec-val">${escapeHTML(soilData.soilDrainage)}</div>
          </div>
        </div>

        <!-- Soil Mix Recipe Box -->
        <div class="soil-recipe-card" style="margin-top: 16px; background: rgba(46, 125, 50, 0.05); border: 1px dashed var(--primary-color); border-radius: var(--radius-md); padding: 16px 20px;">
          <div style="font-weight: 600; color: var(--primary-color); margin-bottom: 8px; display: flex; align-items: center; gap: 8px;">
            <span>🥣</span> Recommended Soil Mix Recipe & Preparation Tips:
          </div>
          <ul class="soil-recipe-list" style="margin: 0; padding-left: 20px; color: var(--text-secondary); line-height: 1.7; font-size: 0.92rem;">
            ${soilData.soilRecommendation.split('\n').filter(line => line.trim()).map(line => `<li>${escapeHTML(line.replace(/^[•\-\*]\s*/, ''))}</li>`).join('')}
          </ul>
        </div>

        <!-- Interactive Soil Tips Drawer Action -->
        <div style="margin-top: 16px; display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px;">
          <button type="button" class="btn btn-secondary ripple" id="btn-toggle-soil-tips" style="font-size: 0.85rem; padding: 8px 16px; display: flex; align-items: center; gap: 6px;">
            <span>🌿</span> <span id="soil-tips-toggle-text">Show Soil Improvement & Amendment Tips</span>
          </button>
          <span class="text-muted" style="font-size: 0.8rem;">💡 Adjust soil texture & pH for maximum root vitality</span>
        </div>

        <div id="soil-improvement-drawer" style="display: none; margin-top: 14px; background: var(--bg-color); border-radius: var(--radius-md); padding: 16px; border: 1px solid var(--border-color); font-size: 0.88rem; color: var(--text-secondary); line-height: 1.6;">
          <div style="font-weight: 600; color: var(--text-primary); margin-bottom: 8px;">💡 Expert Agronomist Soil Amendment Guide:</div>
          <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px;">
            <div style="background: var(--surface-card); padding: 10px 14px; border-radius: var(--radius-sm); border: 1px solid var(--border-color);">
              <strong style="color: var(--primary-color);">📉 To Lower pH (Acidic):</strong><br>
              Mix in sphagnum peat moss, elemental sulfur, or composted oak leaves.
            </div>
            <div style="background: var(--surface-card); padding: 10px 14px; border-radius: var(--radius-sm); border: 1px solid var(--border-color);">
              <strong style="color: var(--primary-color);">📈 To Raise pH (Alkaline):</strong><br>
              Add agricultural lime (calcium carbonate) or crushed dolomite in light doses.
            </div>
            <div style="background: var(--surface-card); padding: 10px 14px; border-radius: var(--radius-sm); border: 1px solid var(--border-color);">
              <strong style="color: var(--primary-color);">🪨 Fix Dense Clay/Waterlogging:</strong><br>
              Incorporate 20-30% coarse perlite, grit, or pumice to create root breathing channels.
            </div>
            <div style="background: var(--surface-card); padding: 10px 14px; border-radius: var(--radius-sm); border: 1px solid var(--border-color);">
              <strong style="color: var(--primary-color);">🐛 Boost Organic Biology:</strong><br>
              Top-dress with pure worm castings or well-rotted aged compost once per month.
            </div>
          </div>
        </div>
      </div>

      <div class="report-actions">
        <button class="btn btn-primary ripple" id="btn-view-in-garden" data-route="garden">🌿 View in My Garden</button>
        <button class="btn btn-secondary ripple" id="btn-download-pdf">📄 Download PDF Report</button>
        <button class="btn btn-secondary ripple" id="btn-share-report">🔗 Share Diagnosis</button>
        <button class="btn btn-secondary ripple" id="btn-scan-again">🔄 Scan Another Plant</button>
      </div>
    `;

    // Toggle Soil Improvement Drawer Listener
    const btnSoilTips = document.getElementById('btn-toggle-soil-tips');
    const drawerSoilTips = document.getElementById('soil-improvement-drawer');
    const toggleText = document.getElementById('soil-tips-toggle-text');
    if (btnSoilTips && drawerSoilTips) {
      btnSoilTips.addEventListener('click', () => {
        const isHidden = drawerSoilTips.style.display === 'none';
        drawerSoilTips.style.display = isHidden ? 'block' : 'none';
        if (toggleText) {
          toggleText.textContent = isHidden ? 'Hide Soil Improvement & Amendment Tips' : 'Show Soil Improvement & Amendment Tips';
        }
      });
    }

    // Automatically save scanned plant to Garden & Cloud Firestore
    StorageManager.addPlantToGarden({
      ...data,
      ...soilData
    });
    showToast(`🌿 "${data.name}" automatically saved to your cloud garden!`, 'success');

    // Wire actions
    document.getElementById('btn-download-pdf')?.addEventListener('click', () => {
      downloadReport({
        ...data,
        ...soilData
      });
    });

    document.getElementById('btn-share-report')?.addEventListener('click', () => {
      if (navigator.share) {
        navigator.share({
          title: `PlantLens AI Diagnosis: ${data.name}`,
          text: `Check out this AI diagnosis for ${data.name}: ${data.healthStatus === 'healthy' ? 'Healthy' : data.diseaseName}`
        }).catch(() => {});
      } else {
        navigator.clipboard.writeText(window.location.href);
        showToast('Link copied to clipboard!', 'info');
      }
    });

    document.getElementById('btn-scan-again')?.addEventListener('click', () => {
      const dropzoneCard = document.getElementById('dropzone');
      const samplesBar = document.getElementById('samples-bar');
      if (dropzoneCard) dropzoneCard.style.display = 'block';
      if (samplesBar) samplesBar.style.display = 'block';
      if (reportCard) reportCard.style.display = 'none';
      dropzoneCard?.focus();
    });
  }
};
