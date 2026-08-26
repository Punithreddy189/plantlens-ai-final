// Utility Helper Functions

// XSS Sanitizer
export function escapeHTML(str) {
  if (typeof str !== 'string') return '';
  return str.replace(/[&<>"']/g, (match) => {
    const map = {
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#039;'
    };
    return map[match];
  });
}

// File Input Validator
export function validateImage(file) {
  const allowedTypes = ['image/png', 'image/jpeg', 'image/jpg', 'image/webp'];
  const maxSize = 10 * 1024 * 1024; // 10 MB

  if (!file) {
    return { valid: false, message: 'No file selected.' };
  }

  if (!allowedTypes.includes(file.type)) {
    return { valid: false, message: 'Invalid file format. Please upload PNG, JPG, JPEG, or WEBP.' };
  }

  if (file.size > maxSize) {
    return { valid: false, message: 'File size exceeds 10MB limit.' };
  }

  return { valid: true };
}

// Client-side Image Compressor & Resizer
export function compressImage(fileOrDataUrl, maxWidth = 1280, quality = 0.8) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      let width = img.width;
      let height = img.height;

      if (width > maxWidth || height > maxWidth) {
        if (width > height) {
          height = Math.round((height * maxWidth) / width);
          width = maxWidth;
        } else {
          width = Math.round((width * maxWidth) / height);
          height = maxWidth;
        }
      }

      const canvas = document.createElement('canvas');
      canvas.width = width;
      canvas.height = height;
      const ctx = canvas.getContext('2d');
      ctx.drawImage(img, 0, 0, width, height);

      const compressedDataUrl = canvas.toDataURL('image/jpeg', quality);
      resolve(compressedDataUrl);
    };
    img.onerror = (err) => reject(err);

    if (typeof fileOrDataUrl === 'string') {
      img.src = fileOrDataUrl;
    } else {
      const reader = new FileReader();
      reader.onload = (e) => { img.src = e.target.result; };
      reader.onerror = (err) => reject(err);
      reader.readAsDataURL(fileOrDataUrl);
    }
  });
}

// Global Screen Reader Live Region Announcer
export function announceToScreenReader(message, priority = 'polite') {
  if (!message) return;
  let announcer = document.getElementById('scan-results-announcer');
  if (!announcer) {
    announcer = document.createElement('div');
    announcer.id = 'scan-results-announcer';
    announcer.className = 'visually-hidden';
    announcer.setAttribute('aria-live', priority);
    announcer.setAttribute('aria-atomic', 'true');
    document.body.appendChild(announcer);
  }
  announcer.textContent = '';
  setTimeout(() => {
    announcer.textContent = message;
  }, 50);
}

// Global Toast Notifier
export function showToast(message, type = 'info', duration = 3500) {
  let container = document.getElementById('toast-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'toast-container';
    container.className = 'toast-container';
    container.setAttribute('role', 'status');
    container.setAttribute('aria-live', 'polite');
    document.body.appendChild(container);
  }

  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  toast.setAttribute('role', 'alert');
  
  let icon = '🔔';
  if (type === 'success') icon = '✅';
  if (type === 'danger') icon = '⚠️';
  if (type === 'warning') icon = '💡';

  toast.innerHTML = `
    <span aria-hidden="true">${icon}</span>
    <div>${escapeHTML(message)}</div>
  `;

  container.appendChild(toast);

  // Also announce to screen reader
  announceToScreenReader(message, type === 'danger' ? 'assertive' : 'polite');

  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(100%)';
    toast.style.transition = 'all 0.3s ease';
    setTimeout(() => toast.remove(), 300);
  }, duration);
}

// Debounce Function
export function debounce(func, delay = 300) {
  let timeout;
  return (...args) => {
    clearTimeout(timeout);
    timeout = setTimeout(() => func(...args), delay);
  };
}

// Print / PDF Report Generator
export function downloadReport(reportData) {
  const printWindow = window.open('', '_blank');
  if (!printWindow) {
    showToast('Popup blocker prevented report window opening.', 'warning');
    return;
  }

  const reportHTML = `
    <!DOCTYPE html>
    <html>
    <head>
      <title>PlantLens AI Diagnostic Report - ${escapeHTML(reportData.name)}</title>
      <style>
        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; padding: 40px; color: #1B5E20; line-height: 1.6; }
        .header { display: flex; align-items: center; justify-content: space-between; border-bottom: 3px solid #2E7D32; padding-bottom: 20px; }
        .logo { font-size: 24px; font-weight: bold; color: #2E7D32; }
        .badge { display: inline-block; padding: 6px 14px; border-radius: 20px; font-weight: bold; }
        .healthy { background: #E8F5E9; color: #2E7D32; }
        .diseased { background: #FFEBEE; color: #C62828; }
        .section { margin-top: 24px; padding: 16px; background: #F5FFF5; border-radius: 12px; border: 1px solid #E0E0E0; }
        h1 { margin: 0 0 5px 0; }
        h3 { color: #2E7D32; border-bottom: 1px solid #C8E6C9; padding-bottom: 4px; }
        ul { padding-left: 20px; }
        .meta-table { width: 100%; border-collapse: collapse; margin-top: 10px; }
        .meta-table td { padding: 8px; border: 1px solid #E0E0E0; }
      </style>
    </head>
    <body>
      <div class="header">
        <div class="logo">🌿 PlantLens AI Diagnostic Report</div>
        <div>Date: ${new Date().toLocaleDateString()}</div>
      </div>

      <div style="margin-top: 20px;">
        <h1>${escapeHTML(reportData.name)}</h1>
        <p><i>${escapeHTML(reportData.scientificName)}</i> | Family: ${escapeHTML(reportData.family || 'N/A')}</p>
        <span class="badge ${reportData.healthStatus === 'healthy' ? 'healthy' : 'diseased'}">
          ${reportData.healthStatus.toUpperCase()} - Confidence ${reportData.confidence}%
        </span>
      </div>

      <div class="section">
        <h3>Diagnostic Overview</h3>
        <p><strong>Diagnosis:</strong> ${escapeHTML(reportData.diseaseName)}</p>
        <p><strong>Severity:</strong> ${escapeHTML(reportData.severity)}</p>
        <p><strong>Health Score:</strong> ${reportData.healthScore} / 100</p>
        <p>${escapeHTML(reportData.description)}</p>
      </div>

      <div class="section">
        <h3>Symptoms & Causes</h3>
        <p><strong>Symptoms:</strong></p>
        <ul>${(reportData.symptoms || []).map(s => `<li>${escapeHTML(s)}</li>`).join('')}</ul>
        <p><strong>Causes:</strong></p>
        <ul>${(reportData.causes || []).map(c => `<li>${escapeHTML(c)}</li>`).join('')}</ul>
      </div>

      <div class="section">
        <h3>Actionable Treatments</h3>
        <p><strong>Organic Remedies:</strong></p>
        <ul>${(reportData.organicRemedies || []).map(r => `<li>${escapeHTML(r)}</li>`).join('')}</ul>
        <p><strong>Chemical Options:</strong></p>
        <ul>${(reportData.chemicalTreatments || []).map(r => `<li>${escapeHTML(r)}</li>`).join('')}</ul>
      </div>

      <div class="section">
        <h3>Care Specifications</h3>
        <table class="meta-table">
          <tr><td><strong>Water Schedule:</strong></td><td>${escapeHTML(reportData.waterSchedule)}</td></tr>
          <tr><td><strong>Sunlight Needs:</strong></td><td>${escapeHTML(reportData.sunlightNeeds)}</td></tr>
          <tr><td><strong>Temperature Range:</strong></td><td>${escapeHTML(reportData.tempRange)}</td></tr>
          <tr><td><strong>Humidity:</strong></td><td>${escapeHTML(reportData.humidity)}</td></tr>
          <tr><td><strong>Fertilizer:</strong></td><td>${escapeHTML(reportData.fertilizer)}</td></tr>
        </table>
      </div>

      <script>
        window.onload = function() { window.print(); }
      </script>
    </body>
    </html>
  `;

  printWindow.document.write(reportHTML);
  printWindow.document.close();
}
