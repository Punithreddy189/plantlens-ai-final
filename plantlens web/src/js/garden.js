// Garden Dashboard Manager Module
import { StorageManager } from './storage.js';
import { GardenAnalytics } from './analytics.js';
import { WeatherWidget } from './weather.js';
import { showToast, escapeHTML, debounce } from './utils.js';
import { auth } from './firebase.js';
import { onAuthStateChanged } from 'firebase/auth';

export function getPlantImageUrl(plant) {
  if (plant) {
    if (plant.image && typeof plant.image === 'string' && plant.image.trim() !== '') return plant.image;
    if (plant.imageUrl && typeof plant.imageUrl === 'string' && plant.imageUrl.trim() !== '') return plant.imageUrl;
    if (plant.image_url && typeof plant.image_url === 'string' && plant.image_url.trim() !== '') return plant.image_url;
    if (plant.photoUrl && typeof plant.photoUrl === 'string' && plant.photoUrl.trim() !== '') return plant.photoUrl;
    if (plant.photo_url && typeof plant.photo_url === 'string' && plant.photo_url.trim() !== '') return plant.photo_url;
    if (plant.plantImage && typeof plant.plantImage === 'string' && plant.plantImage.trim() !== '') return plant.plantImage;
    if (plant.thumbnail && typeof plant.thumbnail === 'string' && plant.thumbnail.trim() !== '') return plant.thumbnail;
  }

  const name = (plant && (plant.name || plant.plantName)) ? (plant.name || plant.plantName) : 'Plant';
  const cleanName = escapeHTML(name).substring(0, 24);
  const isHealthy = plant && (plant.healthStatus === 'healthy' || (plant.disease && String(plant.disease).toLowerCase().includes('healthy')));
  const bgGrad = isHealthy ? '%231B5E20' : '%234E342E';
  const accent = isHealthy ? '%2381C784' : '%23FFB74D';
  const leafIcon = isHealthy ? '🌿' : '🍂';

  return `data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='400' height='260' viewBox='0 0 400 260'%3E%3Cdefs%3E%3ClinearGradient id='g' x1='0%25' y1='0%25' x2='100%25' y2='100%25'%3E%3Cstop offset='0%25' stop-color='${bgGrad}'/%3E%3Cstop offset='100%25' stop-color='%23121E14'/%3E%3C/linearGradient%3E%3C/defs%3E%3Crect fill='url(%23g)' width='400' height='260'/%3E%3Ccircle cx='200' cy='105' r='50' fill='rgba(255,255,255,0.06)'/%3E%3Ctext fill='${accent}' font-family='system-ui,sans-serif' font-size='44' text-anchor='middle' x='200' y='120'%3E${leafIcon}%3C/text%3E%3Ctext fill='%23FFFFFF' font-family='system-ui,sans-serif' font-size='16' font-weight='600' text-anchor='middle' x='200' y='180'%3E${cleanName}%3C/text%3E%3Ctext fill='%23A5D6A7' font-family='system-ui,sans-serif' font-size='12' text-anchor='middle' x='200' y='205'%3EPlantLens AI Scanned Specimen%3C/text%3E%3C/svg%3E`;
}

export const GardenModule = {
  currentCategory: 'All',
  searchQuery: '',

  init() {
    this.bindEvents();
    this.render();

    // Listen to Firebase Auth state changes
    onAuthStateChanged(auth, (user) => {
      this.render();
    });

    // Listen for background Firestore sync completion
    window.addEventListener('garden-sync-completed', () => {
      this.render();
    });
  },

  bindEvents() {
    const searchInput = document.getElementById('garden-search');
    if (searchInput) {
      searchInput.addEventListener('input', debounce((e) => {
        this.searchQuery = e.target.value.toLowerCase().trim();
        this.renderGrid();
      }, 250));
    }

    const filterContainer = document.getElementById('garden-filter-pills');
    if (filterContainer) {
      filterContainer.addEventListener('click', (e) => {
        const pill = e.target.closest('.filter-pill');
        if (pill) {
          filterContainer.querySelectorAll('.filter-pill').forEach(p => p.classList.remove('active'));
          pill.classList.add('active');
          this.currentCategory = pill.getAttribute('data-filter') || 'All';
          this.renderGrid();
        }
      });
    }
  },

  render() {
    const user = auth.currentUser;
    const analyticsContainer = document.getElementById('garden-analytics-ribbon');
    const weatherContainer = document.getElementById('garden-weather-container');
    const toolbar = document.querySelector('.garden-toolbar');
    const grid = document.getElementById('garden-grid');

    if (!grid) return;

    // 1. Unauthenticated / Logged Out Guest Mode
    if (!user) {
      if (analyticsContainer) analyticsContainer.style.display = 'none';
      if (weatherContainer) weatherContainer.style.display = 'none';
      if (toolbar) toolbar.style.display = 'none';

      grid.innerHTML = `
        <div class="empty-state-card" style="grid-column: 1 / -1; padding: 56px 24px; text-align: center; max-width: 640px; margin: 40px auto; background: var(--surface-card); border-radius: var(--radius-lg); border: 1px solid var(--border-color); box-shadow: var(--shadow-sm);">
          <div class="empty-state-icon" style="font-size: 3.5rem; margin-bottom: 16px;">🔒</div>
          <h3 style="font-size: 1.4rem; margin-bottom: 10px; color: var(--text-primary);">Please Sign In to Access Your Cloud Garden</h3>
          <p class="subheading" style="margin-bottom: 28px; color: var(--text-secondary); line-height: 1.6;">
            Sign in or register to sync your plant diagnoses, track automated watering reminders, and access your cloud health analytics in real time across Web & Android.
          </p>
          <button class="btn btn-primary ripple" id="btn-garden-auth-prompt" style="padding: 12px 32px; font-size: 1rem; border-radius: var(--radius-full);">🔑 Sign In / Register</button>
        </div>
      `;

      document.getElementById('btn-garden-auth-prompt')?.addEventListener('click', () => {
        const authModal = document.getElementById('auth-modal');
        if (authModal) authModal.classList.add('active');
      });
      return;
    }

    // 2. Authenticated Mode - Reveal Dashboard Components
    if (analyticsContainer) analyticsContainer.style.display = 'grid';
    if (weatherContainer) weatherContainer.style.display = 'block';
    if (toolbar) toolbar.style.display = 'flex';

    this.renderAnalyticsRibbon();
    this.renderWeatherWidget();
    this.renderGrid();
  },

  renderAnalyticsRibbon() {
    const container = document.getElementById('garden-analytics-ribbon');
    if (!container) return;

    const metrics = GardenAnalytics.calculateMetrics();
    const healthyPctNum = parseInt(metrics.healthyPercent) || 0;
    const diseasedPctNum = parseInt(metrics.diseasedPercent) || 0;

    container.innerHTML = `
      <div class="metric-card">
        <div class="metric-icon">🌱</div>
        <div>
          <div class="metric-val">${metrics.total}</div>
          <div class="metric-lbl">Total Plants</div>
        </div>
      </div>

      <div class="metric-card">
        <div class="metric-icon" style="background: rgba(76, 175, 80, 0.15); color: #2E7D32;">💚</div>
        <div>
          <div class="metric-val" style="color: var(--success-color);">${metrics.healthyPercent}</div>
          <div class="metric-lbl">Healthy Ratio</div>
        </div>
      </div>

      <div class="metric-card">
        <div class="metric-icon" style="background: rgba(244, 67, 54, 0.15); color: #C62828;">⚠️</div>
        <div>
          <div class="metric-val" style="color: var(--danger-color);">${metrics.diseasedPercent}</div>
          <div class="metric-lbl">Diseased Ratio</div>
        </div>
      </div>

      <div class="metric-card">
        <div class="metric-icon" style="background: rgba(33, 150, 243, 0.15); color: #1565C0;">💧</div>
        <div>
          <div class="metric-val">${metrics.todaysWaterCount}</div>
          <div class="metric-lbl">Today's Water</div>
        </div>
      </div>

      <div class="metric-card">
        <div class="metric-icon" style="background: rgba(255, 193, 7, 0.18); color: #F57F17;">📈</div>
        <div>
          <div class="metric-val">${metrics.weeklyGrowth}</div>
          <div class="metric-lbl">Weekly Growth</div>
        </div>
      </div>

      <div class="metric-card">
        <div class="metric-icon" style="background: rgba(156, 39, 176, 0.15); color: #7B1FA2;">🩺</div>
        <div>
          <div class="metric-val">${metrics.averageHealth}</div>
          <div class="metric-lbl">Avg Health Score</div>
        </div>
      </div>

      <!-- Real-Time Health Distribution Bar -->
      <div style="grid-column: 1 / -1; background: var(--surface-card); padding: 14px 18px; border-radius: var(--radius-md); border: 1px solid var(--border-color); margin-top: 4px;">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; font-size: 0.85rem; font-weight: 600;">
          <span>📊 Garden Health Distribution</span>
          <span style="color: var(--text-secondary); font-weight: 500;">${metrics.healthyCount || 0} Healthy &bull; ${metrics.diseasedCount || 0} In Treatment</span>
        </div>
        <div style="height: 10px; border-radius: var(--radius-full); background: rgba(244, 67, 54, 0.25); overflow: hidden; display: flex;">
          <div style="width: ${healthyPctNum}%; background: var(--success-color); transition: width 0.6s ease;" title="Healthy Plants: ${metrics.healthyPercent}"></div>
          <div style="width: ${diseasedPctNum}%; background: var(--danger-color); transition: width 0.6s ease;" title="Diseased Plants: ${metrics.diseasedPercent}"></div>
        </div>
      </div>
    `;
  },

  renderWeatherWidget() {
    WeatherWidget.renderWidget('garden-weather-container');
  },

  renderGrid() {
    const grid = document.getElementById('garden-grid');
    if (!grid) return;

    let garden = StorageManager.getGarden();

    // Category Filter
    if (this.currentCategory !== 'All') {
      if (this.currentCategory === 'Healthy') {
        garden = garden.filter(p => p.healthStatus === 'healthy' || (p.disease && p.disease.toLowerCase().includes('healthy')));
      } else if (this.currentCategory === 'Diseased') {
        garden = garden.filter(p => p.healthStatus === 'diseased' || (p.disease && !p.disease.toLowerCase().includes('healthy')));
      } else {
        garden = garden.filter(p => (p.category || 'Indoor') === this.currentCategory);
      }
    }

    // Search Query Filter
    if (this.searchQuery) {
      garden = garden.filter(p => 
        (p.name && p.name.toLowerCase().includes(this.searchQuery)) ||
        (p.plantName && p.plantName.toLowerCase().includes(this.searchQuery)) ||
        (p.scientificName && p.scientificName.toLowerCase().includes(this.searchQuery)) ||
        (p.disease && p.disease.toLowerCase().includes(this.searchQuery)) ||
        (p.diseaseName && p.diseaseName.toLowerCase().includes(this.searchQuery))
      );
    }

    if (garden.length === 0) {
      grid.innerHTML = `
        <div class="empty-state-card" style="grid-column: 1 / -1; padding: 48px 24px; text-align: center;">
          <div class="empty-state-icon">🌱</div>
          <h3>Your Personal Garden is Empty</h3>
          <p class="subheading" style="margin-bottom: 24px;">Scan a plant with the AI Scanner or scan from your Android app to add plants here automatically.</p>
          <button class="btn btn-primary ripple" data-route="scanner">📷 Start Scanning</button>
        </div>
      `;
      return;
    }

    grid.innerHTML = garden.map(plant => {
      const plantName = plant.name || plant.plantName || "Plant";
      const plantScientific = plant.scientificName || "";
      const isHealthy = plant.healthStatus === 'healthy' || (plant.disease && plant.disease.toLowerCase().includes('healthy'));
      const conditionName = plant.diseaseName || plant.disease || (isHealthy ? "Healthy Plant" : "Diseased");
      const plantImg = getPlantImageUrl(plant);

      return `
        <div class="plant-card" id="card-${plant.id || plant.firebaseDocId}">
          <div class="plant-card-img-wrap">
            <img src="${plantImg}" onerror="this.onerror=null; this.src='${getPlantImageUrl({ name: plantName, healthStatus: isHealthy ? 'healthy' : 'diseased' })}';" alt="${escapeHTML(plantName)}"/>
            <span class="badge ${isHealthy ? 'badge-healthy' : 'badge-diseased'} plant-card-status-badge">
              ${isHealthy ? 'HEALTHY' : 'DISEASED'}
            </span>
          </div>
          <div class="plant-card-content">
            <div class="plant-card-title">${escapeHTML(plantName)}</div>
            ${plantScientific ? `<div class="plant-card-latin">${escapeHTML(plantScientific)}</div>` : ''}
            
            <div class="plant-card-meta">
              <div class="meta-row">
                <span>🦠 Condition:</span>
                <strong style="color: var(--text-primary);">${escapeHTML(conditionName)}</strong>
              </div>
              <div class="meta-row">
                <span>💧 Watering:</span>
                <strong style="color: var(--primary-color);">${escapeHTML(plant.waterSchedule || plant.nextWaterDate || 'Every 2-3 days')}</strong>
              </div>
              <div class="meta-row">
                <span>📅 Added:</span>
                <span>${escapeHTML(plant.addedDate || (plant.createdAt ? new Date(plant.createdAt).toLocaleDateString() : 'Recently'))}</span>
              </div>
            </div>

            <div class="plant-card-actions">
              <button class="btn btn-primary btn-view-details" data-id="${plant.id || plant.firebaseDocId}" style="flex: 1; padding: 8px 14px; font-size: 0.85rem;">Details</button>
              <button class="btn btn-secondary btn-remove-plant" data-id="${plant.id || plant.firebaseDocId}" style="padding: 8px 14px; font-size: 0.85rem; border-color: var(--danger-color); color: var(--danger-color);">Delete</button>
            </div>
          </div>
        </div>
      `;
    }).join('');

    // Wire Card Events
    grid.querySelectorAll('.btn-view-details').forEach(btn => {
      btn.addEventListener('click', () => {
        const id = btn.getAttribute('data-id');
        this.openPlantDetailsModal(id);
      });
    });

    grid.querySelectorAll('.btn-remove-plant').forEach(btn => {
      btn.addEventListener('click', async (e) => {
        e.stopPropagation();
        const id = btn.getAttribute('data-id');
        if (confirm('Are you sure you want to remove this plant from your cloud garden?')) {
          try {
            if (StorageManager.deletePlant) {
              await StorageManager.deletePlant(id);
            } else if (StorageManager.removePlant) {
              await StorageManager.removePlant(id);
            }
            showToast('Plant removed from cloud garden 🗑️', 'info');
            this.render();
          } catch (err) {
            console.error('Delete plant error:', err);
            showToast('Failed to remove plant: ' + (err.message || err), 'danger');
          }
        }
      });
    });
  },

  openPlantDetailsModal(plantId) {
    const garden = StorageManager.getGarden();
    const plant = garden.find(p => (p.id === plantId || p.firebaseDocId === plantId));
    if (!plant) return;

    const modal = document.getElementById('plant-detail-modal');
    const content = document.getElementById('plant-detail-content');
    if (!modal || !content) return;

    const plantName = plant.name || plant.plantName || "Plant";
    const isHealthy = plant.healthStatus === 'healthy' || (plant.disease && plant.disease.toLowerCase().includes('healthy'));
    const plantImg = getPlantImageUrl(plant);

    content.innerHTML = `
      <div style="display: flex; gap: 20px; align-items: center; margin-bottom: 20px;">
        <img src="${plantImg}" onerror="this.onerror=null; this.src='${getPlantImageUrl({ name: plantName, healthStatus: isHealthy ? 'healthy' : 'diseased' })}';" alt="${escapeHTML(plantName)}" style="width: 100px; height: 100px; border-radius: var(--radius-md); object-fit: cover;"/>
        <div>
          <h3>${escapeHTML(plantName)}</h3>
          ${plant.scientificName ? `<p class="subheading" style="font-style: italic;">${escapeHTML(plant.scientificName)}</p>` : ''}
          <span class="badge ${isHealthy ? 'badge-healthy' : 'badge-diseased'}" style="margin-top: 6px;">
            ${isHealthy ? 'HEALTHY' : 'DISEASED'} - Health Score: ${plant.healthScore || 85}/100
          </span>
        </div>
      </div>

      <div style="margin-bottom: 20px; background: var(--surface-card); padding: 16px; border-radius: var(--radius-md); border: 1px solid var(--border-color);">
        <h4 style="color: var(--primary-color); margin-bottom: 8px;">Diagnosis & Health Condition</h4>
        <p><strong>Condition:</strong> ${escapeHTML(plant.diseaseName || plant.disease || 'Healthy Plant')}</p>
        <p style="color: var(--text-secondary); font-size: 0.9rem; margin-top: 4px;">${escapeHTML(plant.description || (isHealthy ? 'Optimal leaf health. No fungal or bacterial infections detected.' : 'Identified symptoms requiring treatment and watering adjustments.'))}</p>
      </div>

      <div style="margin-bottom: 20px;">
        <h4 style="color: var(--primary-color); margin-bottom: 8px;">📜 Care History Timeline</h4>
        <div class="timeline-list">
          ${(plant.timeline || [
            { date: 'Today', event: 'Synchronized with Cloud Database' },
            { date: 'Scan Date', event: 'AI Diagnostic Scan Completed' },
            { date: 'Added', event: 'Saved to Personal Garden' }
          ]).map(t => `
            <div class="timeline-item">
              <strong>${escapeHTML(t.date)}:</strong> ${escapeHTML(t.event)}
            </div>
          `).join('')}
        </div>
      </div>

      <div style="display: flex; gap: 12px; justify-content: flex-end;">
        <button class="btn btn-secondary btn-close-modal">Close</button>
      </div>
    `;

    content.querySelectorAll('.btn-close-modal').forEach(btn => {
      btn.addEventListener('click', () => modal.classList.remove('active'));
    });

    modal.classList.add('active');
  }
};
