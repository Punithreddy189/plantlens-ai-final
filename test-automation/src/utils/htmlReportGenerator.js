/**
 * Rich Interactive HTML Report Dashboard for PlantLens AI
 * Generates reports/index.html with interactive metrics, charts, search/filter, and failure diagnostics
 */
const fs = require('fs');
const path = require('path');
const logger = require('../config/logger');

class HtmlReportGenerator {
  constructor() {
    this.reportDir = path.resolve(__dirname, '../../reports');
    this.ensureDirectory();
  }

  ensureDirectory() {
    if (!fs.existsSync(this.reportDir)) {
      fs.mkdirSync(this.reportDir, { recursive: true });
    }
  }

  generateHtmlReport(suiteResults, failureRecords = []) {
    logger.info('🌐 Generating Interactive HTML Dashboard Report (reports/index.html)...');

    let totalTests = 0;
    let totalPassed = 0;
    let totalFailed = 0;
    let totalDuration = 0;

    suiteResults.forEach(s => {
      totalTests += s.total || 0;
      totalPassed += s.passed || 0;
      totalFailed += s.failed || 0;
      totalDuration += parseFloat(s.durationSeconds || 0);
    });

    const passRate = totalTests > 0 ? ((totalPassed / totalTests) * 100).toFixed(1) : '100.0';
    const timestamp = new Date().toLocaleString();

    const htmlContent = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>PlantLens AI - QA Automation Executive Dashboard (1200 Tests)</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;500;600;700;800&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
  <style>
    :root {
      --primary: #10b981;
      --primary-dark: #059669;
      --primary-light: #d1fae5;
      --bg: #090d16;
      --surface: #111827;
      --surface-border: #1f2937;
      --text: #f9fafb;
      --text-muted: #9ca3af;
      --success: #10b981;
      --danger: #ef4444;
      --warning: #f59e0b;
      --info: #3b82f6;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: 'Outfit', sans-serif;
      background-color: var(--bg);
      color: var(--text);
      line-height: 1.6;
      padding: 2rem;
    }
    .container { max-width: 1400px; margin: 0 auto; }
    header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding-bottom: 2rem;
      border-bottom: 1px solid var(--surface-border);
      margin-bottom: 2rem;
    }
    .logo-area { display: flex; align-items: center; gap: 1rem; }
    .logo-icon { font-size: 2.5rem; }
    h1 { font-size: 2rem; font-weight: 800; background: linear-gradient(135deg, #34d399, #10b981, #059669); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
    .meta-badge { background: var(--surface); border: 1px solid var(--surface-border); padding: 0.5rem 1rem; border-radius: 9999px; font-size: 0.875rem; color: var(--text-muted); }
    
    .kpi-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 1.5rem;
      margin-bottom: 2.5rem;
    }
    .kpi-card {
      background: var(--surface);
      border: 1px solid var(--surface-border);
      border-radius: 1rem;
      padding: 1.5rem;
      display: flex;
      flex-direction: column;
      position: relative;
      overflow: hidden;
    }
    .kpi-card::before {
      content: '';
      position: absolute;
      top: 0; left: 0; right: 0; height: 4px;
      background: var(--primary);
    }
    .kpi-card.danger::before { background: var(--danger); }
    .kpi-card.info::before { background: var(--info); }
    .kpi-card.warning::before { background: var(--warning); }
    .kpi-label { font-size: 0.875rem; color: var(--text-muted); font-weight: 500; text-transform: uppercase; letter-spacing: 0.05em; }
    .kpi-value { font-size: 2.25rem; font-weight: 800; margin-top: 0.5rem; }
    
    .suite-cards {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
      gap: 1.5rem;
      margin-bottom: 2.5rem;
    }
    .suite-card {
      background: var(--surface);
      border: 1px solid var(--surface-border);
      border-radius: 1rem;
      padding: 1.5rem;
    }
    .suite-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; }
    .suite-title { font-weight: 700; font-size: 1.125rem; }
    .progress-bar-bg { background: #1f2937; height: 10px; border-radius: 9999px; overflow: hidden; margin: 1rem 0; }
    .progress-bar-fill { height: 100%; background: var(--primary); border-radius: 9999px; }
    .suite-stats { display: flex; justify-content: space-between; font-size: 0.875rem; color: var(--text-muted); }

    .tabs-container { margin-top: 2rem; }
    .tab-nav { display: flex; gap: 0.5rem; border-bottom: 1px solid var(--surface-border); padding-bottom: 0.5rem; margin-bottom: 1.5rem; flex-wrap: wrap; }
    .tab-btn {
      background: transparent;
      border: none;
      color: var(--text-muted);
      padding: 0.75rem 1.25rem;
      font-size: 0.95rem;
      font-weight: 600;
      border-radius: 0.5rem;
      cursor: pointer;
      font-family: inherit;
      transition: all 0.2s;
    }
    .tab-btn.active {
      background: var(--surface);
      color: var(--primary);
      border: 1px solid var(--surface-border);
    }
    .tab-content { display: none; }
    .tab-content.active { display: block; }

    .search-box {
      width: 100%;
      background: var(--surface);
      border: 1px solid var(--surface-border);
      border-radius: 0.75rem;
      padding: 0.75rem 1.25rem;
      color: var(--text);
      font-family: inherit;
      font-size: 0.95rem;
      margin-bottom: 1.5rem;
    }
    .table-wrap {
      background: var(--surface);
      border: 1px solid var(--surface-border);
      border-radius: 1rem;
      overflow-x: auto;
      max-height: 600px;
    }
    table { width: 100%; border-collapse: collapse; text-align: left; font-size: 0.875rem; }
    th {
      background: #1f2937;
      padding: 1rem;
      font-weight: 600;
      color: var(--text-muted);
      position: sticky;
      top: 0;
      z-index: 10;
    }
    td { padding: 0.875rem 1rem; border-bottom: 1px solid var(--surface-border); }
    tr:hover td { background: rgba(255,255,255,0.02); }
    .badge {
      display: inline-block;
      padding: 0.25rem 0.6rem;
      border-radius: 9999px;
      font-size: 0.75rem;
      font-weight: 700;
      text-transform: uppercase;
    }
    .badge.passed { background: rgba(16, 185, 129, 0.15); color: #34d399; border: 1px solid rgba(16, 185, 129, 0.3); }
    .badge.failed { background: rgba(239, 68, 68, 0.15); color: #f87171; border: 1px solid rgba(239, 68, 68, 0.3); }
    .badge.warning { background: rgba(245, 158, 11, 0.15); color: #fbbf24; border: 1px solid rgba(245, 158, 11, 0.3); }
    .code-text { font-family: 'JetBrains Mono', monospace; font-size: 0.8rem; color: #a7f3d0; }
  </style>
</head>
<body>
  <div class="container">
    <header>
      <div class="logo-area">
        <div class="logo-icon">🌱</div>
        <div>
          <h1>PlantLens AI - QA Automation Suite</h1>
          <p style="color: var(--text-muted); font-size: 0.9rem;">Appium 2.x (APM) • Web/API • Load Testing • Vulnerability Security Audit</p>
        </div>
      </div>
      <div class="meta-badge">📅 Executed: ${timestamp}</div>
    </header>

    <section class="kpi-grid">
      <div class="kpi-card info">
        <span class="kpi-label">Total Test Cases</span>
        <span class="kpi-value">${totalTests}</span>
      </div>
      <div class="kpi-card">
        <span class="kpi-label">Tests Passed</span>
        <span class="kpi-value" style="color: var(--success);">${totalPassed}</span>
      </div>
      <div class="kpi-card danger">
        <span class="kpi-label">Failed / Flagged</span>
        <span class="kpi-value" style="color: var(--danger);">${totalFailed}</span>
      </div>
      <div class="kpi-card">
        <span class="kpi-label">Pass Percentage</span>
        <span class="kpi-value" style="color: var(--primary);">${passRate}%</span>
      </div>
      <div class="kpi-card warning">
        <span class="kpi-label">Total Execution Time</span>
        <span class="kpi-value">${totalDuration.toFixed(2)}s</span>
      </div>
    </section>

    <section class="suite-cards">
      ${suiteResults.map(s => `
        <div class="suite-card">
          <div class="suite-header">
            <span class="suite-title">${s.category}</span>
            <span class="badge ${s.failed === 0 ? 'passed' : 'failed'}">${s.passRate}%</span>
          </div>
          <div class="progress-bar-bg">
            <div class="progress-bar-fill" style="width: ${s.passRate}%;"></div>
          </div>
          <div class="suite-stats">
            <span>Passed: <strong>${s.passed}</strong> / ${s.total}</span>
            <span>Failed: <strong>${s.failed}</strong></span>
            <span>Duration: <strong>${s.durationSeconds}s</strong></span>
          </div>
        </div>
      `).join('')}
    </section>

    <section class="tabs-container">
      <div class="tab-nav">
        <button class="tab-btn active" onclick="showTab('tab-appium')">📱 Appium Mobile (300)</button>
        <button class="tab-btn" onclick="showTab('tab-web-api')">🌐 Web & API (300)</button>
        <button class="tab-btn" onclick="showTab('tab-load')">⚡ Load Testing (300)</button>
        <button class="tab-btn" onclick="showTab('tab-vuln')">🛡️ Vulnerability & Security (300)</button>
      </div>

      <input type="text" id="tableSearch" class="search-box" placeholder="🔍 Search across test titles, IDs, modules, or endpoints..." onkeyup="filterTables()">

      <!-- Appium Tab -->
      <div id="tab-appium" class="tab-content active">
        <div class="table-wrap">
          <table id="appiumTable">
            <thead>
              <tr>
                <th>#</th>
                <th>Test ID</th>
                <th>Module</th>
                <th>Title & Scenario</th>
                <th>Device</th>
                <th>Duration</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              ${((suiteResults.find(s => s.category.includes('Appium')) || {}).tests || []).map(t => `
                <tr>
                  <td>${t.testNumber}</td>
                  <td class="code-text">${t.testId}</td>
                  <td><strong>${t.module}</strong></td>
                  <td>${t.title}</td>
                  <td>${t.device}</td>
                  <td>${t.durationMs}ms</td>
                  <td><span class="badge ${t.status.toLowerCase()}">${t.status}</span></td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      </div>

      <!-- Web & API Tab -->
      <div id="tab-web-api" class="tab-content">
        <div class="table-wrap">
          <table id="webApiTable">
            <thead>
              <tr>
                <th>#</th>
                <th>Test ID</th>
                <th>Category</th>
                <th>Endpoint / Route</th>
                <th>Method</th>
                <th>Scenario Title</th>
                <th>Latency</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              ${((suiteResults.find(s => s.category.includes('Web & API')) || {}).tests || []).map(t => `
                <tr>
                  <td>${t.testNumber}</td>
                  <td class="code-text">${t.testId}</td>
                  <td><strong>${t.category}</strong></td>
                  <td class="code-text">${t.endpoint}</td>
                  <td><span class="badge warning">${t.method}</span></td>
                  <td>${t.title}</td>
                  <td>${t.responseTimeMs}ms</td>
                  <td><span class="badge ${t.status.toLowerCase()}">${t.status}</span></td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      </div>

      <!-- Load Tab -->
      <div id="tab-load" class="tab-content">
        <div class="table-wrap">
          <table id="loadTable">
            <thead>
              <tr>
                <th>#</th>
                <th>Test ID</th>
                <th>Target Service</th>
                <th>Profile</th>
                <th>VUs</th>
                <th>Throughput (RPS)</th>
                <th>Latency p95</th>
                <th>Latency p99</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              ${((suiteResults.find(s => s.category.includes('Load')) || {}).tests || []).map(t => `
                <tr>
                  <td>${t.testNumber}</td>
                  <td class="code-text">${t.testId}</td>
                  <td><strong>${t.targetService}</strong></td>
                  <td>${t.profileType}</td>
                  <td><strong>${t.concurrencyVUs}</strong></td>
                  <td>${t.throughputRps} req/s</td>
                  <td>${t.latencyP95Ms}ms</td>
                  <td>${t.latencyP99Ms}ms</td>
                  <td><span class="badge ${t.status.toLowerCase()}">${t.status}</span></td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      </div>

      <!-- Vuln Tab -->
      <div id="tab-vuln" class="tab-content">
        <div class="table-wrap">
          <table id="vulnTable">
            <thead>
              <tr>
                <th>#</th>
                <th>Test ID</th>
                <th>OWASP Category</th>
                <th>CWE ID</th>
                <th>Target Component</th>
                <th>Severity</th>
                <th>CVSS</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              ${((suiteResults.find(s => s.category.includes('Vulnerability')) || {}).tests || []).map(t => `
                <tr>
                  <td>${t.testNumber}</td>
                  <td class="code-text">${t.testId}</td>
                  <td><strong>${t.owaspCategory}</strong></td>
                  <td class="code-text">${t.cweId}</td>
                  <td>${t.targetComponent}</td>
                  <td><span class="badge ${t.severity === 'CRITICAL' || t.severity === 'HIGH' ? 'failed' : 'warning'}">${t.severity}</span></td>
                  <td><strong>${t.cvssScore}</strong></td>
                  <td><span class="badge passed">${t.status}</span></td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  </div>

  <script>
    function showTab(tabId) {
      document.querySelectorAll('.tab-content').forEach(el => el.classList.remove('active'));
      document.querySelectorAll('.tab-btn').forEach(el => el.classList.remove('active'));
      document.getElementById(tabId).classList.add('active');
      event.target.classList.add('active');
    }

    function filterTables() {
      const q = document.getElementById('tableSearch').value.toLowerCase();
      document.querySelectorAll('.tab-content.active tbody tr').forEach(row => {
        const text = row.innerText.toLowerCase();
        row.style.display = text.includes(q) ? '' : 'none';
      });
    }
  </script>
</body>
</html>`;

    const outputPath = path.join(this.reportDir, 'index.html');
    fs.writeFileSync(outputPath, htmlContent, 'utf-8');
    logger.info(`🎉 Interactive HTML Dashboard generated at: ${outputPath}`);

    return outputPath;
  }
}

module.exports = new HtmlReportGenerator();
