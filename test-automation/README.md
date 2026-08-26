# 🌱 PlantLens AI - Enterprise QA Test Automation Framework

An enterprise-grade test automation and security audit framework for **PlantLens AI** providing **1,200+ structured test cases** across **Appium 2.x Mobile E2E**, **Web/API Testing**, **Load Testing**, and **Vulnerability Scanning**, featuring unified **multi-tab Excel reporting** (`APM_E2E_Report.xlsx`), **interactive HTML dashboard** (`index.html`), and **GitHub Actions CI/CD workflows**.

---

## 🏗️ Architecture Overview

```
test-automation/
├── .github/workflows/
│   ├── APM-appium.yml             # Appium 2.x Android & Master QA Workflow
│   └── testing-suite.yml          # Parallel Matrix Testing Workflow
├── appium.config.js               # Appium 2.x Server, APM Driver & UiAutomator2 Capabilities
├── package.json                   # Dependencies: Appium, WebdriverIO, Mocha, Chai, ExcelJS, Winston
├── src/
│   ├── ai/
│   │   └── smartAITester.js       # Smart AI screen analyzer & widget scenario generator
│   ├── config/
│   │   ├── driverFactory.js       # Appium 2.x driver lifecycle with auto-device detection
│   │   └── logger.js              # Winston structured logging
│   ├── pages/                     # Page Object Model (POM)
│   │   ├── BasePage.js            # Core base abstractions & assertions
│   │   ├── SplashPage.js          # Splash screen & animations
│   │   ├── LoginPage.js           # Authentication, Google OAuth, validation
│   │   ├── HomePage.js            # Weather widget, quick scan, tab navigation
│   │   ├── ScannerPage.js         # CameraX viewfinder, flash, crop overlay
│   │   ├── PlantResultPage.js     # Health score, species ID, disease diagnosis
│   │   └── ProfilePage.js         # Language switcher (10 languages), theme toggle, PDF/CSV export
│   ├── utils/
│   │   ├── apmFinder.js           # find.byValueKey, byText, bySemanticsLabel, byAccessibilityId
│   │   ├── gestureUtils.js        # Tap, DoubleTap, LongPress, Scroll, Swipe, DragDrop, Pinch, Zoom
│   │   ├── failureHandler.js      # Screenshot capture, Logcat dump, Widget tree XML
│   │   ├── excelReportGenerator.js # 7-Tab Styled Excel Report Generator (ExcelJS)
│   │   └── htmlReportGenerator.js  # Interactive Standalone HTML Report Dashboard
│   ├── suites/
│   │   ├── appium-mobile/         # 300 Appium Mobile E2E Test Cases (10 Modules)
│   │   ├── web-api/               # 300 Web & API Test Cases (Pl@ntNet, Gemini AI, Firestore, Weather)
│   │   ├── load-testing/          # 300 Concurrency & Performance Test Scenarios (10 to 5000 VUs)
│   │   └── vulnerability-testing/ # 300 OWASP Top 10 & CWE Security Test Vectors
│   └── runners/
│       └── runAllTests.js         # Master runner executing 1200 tests & writing GitHub Step Summary
└── reports/
    ├── APM_E2E_Report.xlsx        # 7-Tab Unified Excel Report
    ├── index.html                 # Beautiful interactive HTML Dashboard
    ├── failures/                  # Captured failure screenshots and logs
    └── logs/                      # Winston execution & error logs
```

---

## 📊 Test Suite Breakdown (1,200 Total Test Cases)

| Category | Count | Scope & Focus Areas |
| :--- | :---: | :--- |
| **📱 Appium Mobile E2E** | **300** | Auth & Session (30), Form Validation (30), UI Components (30), Gestures & W3C Actions (30), CameraX & ML Vision (30), Plant Species & Disease Diagnosis (30), Garden Management & Room DB (30), Multi-language (10 Indian Languages) & Themes (30), Profile & PDF/CSV Export (30), Admin Analytics (30). |
| **🌐 Web & API Testing** | **300** | Web Frontend Features (50), Pl@ntNet API Proxy (50), Gemini Vision AI Route (50), Firebase Auth & Cloud Firestore (50), Weather & Geocoding Caching (50), Error Handling & Resilience Matrix (50). |
| **⚡ Load & Stress Testing** | **300** | Concurrency from 10 to 5,000 Virtual Users (VUs), Spike testing (0 to 1500 VUs in 5s), Soak/Endurance tests (12-hour sustained load), Latency percentiles (p50, p95, p99), Throughput (RPS), and SLA verification. |
| **🛡️ Vulnerability & Security** | **300** | Full OWASP Top 10 coverage (A01 to A10), CWE Vectors (SQLi, XSS, CSRF, SSRF, IDOR, Broken Auth, Cryptographic Failures, Insecure Deserialization, Misconfigurations, Logging Failures), CVSS 3.1 scoring, and actionable remediation guidance. |

---

## 📑 Excel Report Structure (`APM_E2E_Report.xlsx`)

The generated Excel workbook contains **7 dedicated sheets**:
1. **Summary**: Execution metadata, device info, KPI cards, and category breakdown.
2. **Appium Mobile E2E**: 300 test cases with Test ID, Module, Scenario, Steps, Device, Duration, and Status.
3. **Web & API Testing**: 300 test cases with Endpoint, Method, Scenario, Expected Status, Latency, and Status.
4. **Load & Performance**: 300 test cases with Target Service, Concurrency (VUs), Throughput, Latency p50/p95/p99, Error Rate, and SLA Compliance.
5. **Vulnerability & Security**: 300 test cases with OWASP Category, CWE ID, Target Component, Severity, CVSS 3.1 Score, Status, and Remediation.
6. **Failed Tests**: Detailed failure diagnostics, assertion errors, screenshot paths, device info, and stack traces.
7. **Execution Logs**: Detailed timestamped audit log of all test runner operations.

---

## 🚀 Local Quickstart & Execution

### 1. Install Dependencies
```bash
cd test-automation
npm install
```

### 2. Run All 1,200 Test Cases
```bash
npm run test:all
```

### 3. Run Individual Test Categories
```bash
npm run test:appium   # Run 300 Appium Mobile E2E Tests
npm run test:api      # Run 300 Web & API Tests
npm run test:load     # Run 300 Load & Performance Tests
npm run test:vuln     # Run 300 Vulnerability & Security Tests
```

---

## 🤖 Smart AI Testing Capability

The built-in `SmartAITester` module (`src/ai/smartAITester.js`):
- Analyzes Android and APM screen hierarchies at runtime.
- Automatically discovers interactive widgets (ElevatedButtons, TextFields, Switches, Radios).
- Generates dynamic boundary and validation test scenarios on the fly.
- Maps screen navigation graphs automatically.

---

## ⚙️ GitHub Actions CI/CD Integration

The workflows in `.github/workflows/`:
1. Check out code and set up Node.js (v20), Java JDK (v17), and Android SDK.
2. Build Android debug APK (`PlantLensAI-main`).
3. Install Appium 2.x and initialize drivers (`appium-APM-driver` / `UiAutomator2`).
4. Execute the 1,200 test case master suite.
5. Generate `APM_E2E_Report.xlsx` and `reports/index.html`.
6. Publish rich markdown test summary to **GitHub Step Summary**.
7. Upload all reports, failure screenshots, and logs to **GitHub Artifacts** (30-day retention).
