// Settings Manager & Preferences Module
import { StorageManager } from './storage.js';
import { showToast } from './utils.js';

export const SettingsModule = {
  init() {
    this.settings = StorageManager.getSettings();
    this.applyCurrentSettings();
    this.bindEvents();
  },

  applyCurrentSettings() {
    // Theme application
    if (this.settings.theme === 'dark' || (this.settings.theme === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
      document.documentElement.setAttribute('data-theme', 'dark');
    } else {
      document.documentElement.setAttribute('data-theme', 'light');
    }

    // High Contrast
    if (this.settings.highContrast) {
      document.documentElement.setAttribute('data-high-contrast', 'true');
    } else {
      document.documentElement.removeAttribute('data-high-contrast');
    }
  },

  bindEvents() {
    // Theme switchers
    const themeSelect = document.getElementById('theme-select');
    if (themeSelect) {
      themeSelect.value = this.settings.theme || 'system';
      themeSelect.addEventListener('change', (e) => {
        this.settings.theme = e.target.value;
        StorageManager.saveSettings(this.settings);
        this.applyCurrentSettings();
        showToast(`Theme updated to ${e.target.value}`, 'success');
      });
    }

    // High Contrast toggle
    const toggleContrast = document.getElementById('toggle-high-contrast');
    if (toggleContrast) {
      toggleContrast.checked = !!this.settings.highContrast;
      toggleContrast.addEventListener('change', (e) => {
        this.settings.highContrast = e.target.checked;
        StorageManager.saveSettings(this.settings);
        this.applyCurrentSettings();
        showToast(`High Contrast Mode ${e.target.checked ? 'Enabled' : 'Disabled'}`, 'info');
      });
    }

    // API Key settings
    const inputPlantnetKey = document.getElementById('input-plantnet-key');
    const btnSavePlantnet = document.getElementById('btn-save-plantnet-key');
    const inputGeminiKey = document.getElementById('input-gemini-key');
    const btnSaveGemini = document.getElementById('btn-save-gemini-key');

    if (inputPlantnetKey) {
      inputPlantnetKey.value = localStorage.getItem('plantlens_plantnet_api_key') || import.meta.env.VITE_PLANTNET_API_KEY || '';
    }
    if (btnSavePlantnet && inputPlantnetKey) {
      btnSavePlantnet.addEventListener('click', () => {
        const val = inputPlantnetKey.value.trim();
        if (val) {
          localStorage.setItem('plantlens_plantnet_api_key', val);
          showToast('🌿 Pl@ntNet API Key saved successfully!', 'success');
        } else {
          localStorage.removeItem('plantlens_plantnet_api_key');
          showToast('Pl@ntNet API Key cleared.', 'info');
        }
      });
    }

    if (inputGeminiKey) {
      inputGeminiKey.value = localStorage.getItem('plantlens_gemini_api_key') || import.meta.env.VITE_GEMINI_API_KEY || import.meta.env.GEMINI_API_KEY || '';
    }
    if (btnSaveGemini && inputGeminiKey) {
      btnSaveGemini.addEventListener('click', () => {
        const val = inputGeminiKey.value.trim();
        if (val) {
          localStorage.setItem('plantlens_gemini_api_key', val);
          showToast('🤖 Gemini Vision API Key saved successfully!', 'success');
        } else {
          localStorage.removeItem('plantlens_gemini_api_key');
          showToast('Gemini API Key cleared.', 'info');
        }
      });
    }

    // Language select
    const langSelect = document.getElementById('lang-select');
    if (langSelect) {
      langSelect.value = this.settings.language || 'en';
      langSelect.addEventListener('change', (e) => {
        this.settings.language = e.target.value;
        StorageManager.saveSettings(this.settings);
        showToast('Language preference updated.', 'success');
      });
    }

    // Notification toggles
    ['waterReminder', 'diseaseAlerts', 'weeklyTips'].forEach(key => {
      const toggle = document.getElementById(`toggle-${key}`);
      if (toggle) {
        toggle.checked = this.settings.notifications ? !!this.settings.notifications[key] : true;
        toggle.addEventListener('change', (e) => {
          if (!this.settings.notifications) this.settings.notifications = {};
          this.settings.notifications[key] = e.target.checked;
          StorageManager.saveSettings(this.settings);
          showToast('Notification preference saved.', 'info');
        });
      }
    });

    // Data Export
    document.getElementById('btn-export-data')?.addEventListener('click', () => {
      StorageManager.exportDataJSON();
      showToast('Garden JSON Data exported successfully!', 'success');
    });

    // Data Import
    const importInput = document.getElementById('import-file-input');
    document.getElementById('btn-import-data')?.addEventListener('click', () => importInput?.click());
    if (importInput) {
      importInput.addEventListener('change', (e) => {
        if (e.target.files && e.target.files[0]) {
          const reader = new FileReader();
          reader.onload = (evt) => {
            const res = StorageManager.importDataJSON(evt.target.result);
            if (res.success) {
              showToast(`Imported ${res.count} garden records successfully!`, 'success');
              window.location.reload();
            } else {
              showToast(`Import Failed: ${res.error}`, 'danger');
            }
          };
          reader.readAsText(e.target.files[0]);
        }
      });
    }

    // Modals
    this.wireModal('btn-change-password', 'password-modal');
    this.wireModal('btn-help-center', 'help-modal');
    this.wireModal('btn-privacy-terms', 'privacy-modal');
    this.wireModal('btn-about-app', 'about-modal');
  },

  wireModal(triggerBtnId, modalId) {
    const btn = document.getElementById(triggerBtnId);
    const modal = document.getElementById(modalId);
    if (!btn || !modal) return;

    const closeModal = () => {
      modal.classList.remove('active');
      btn.focus();
    };

    btn.addEventListener('click', () => {
      modal.classList.add('active');
      const firstFocusable = modal.querySelector('button, input, [tabindex="0"]');
      setTimeout(() => firstFocusable?.focus(), 100);
    });

    modal.querySelectorAll('.btn-close-modal').forEach(c => {
      c.addEventListener('click', closeModal);
    });

    window.addEventListener('keydown', (e) => {
      if (e.key === 'Escape' && modal.classList.contains('active')) {
        closeModal();
      }
    });
  }
};
