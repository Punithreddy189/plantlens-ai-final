# PlantLens AI - Selenium Automated Testing Suite (Phase 7)

Comprehensive end-to-end automated testing suite for **PlantLens AI Web Application** built with **Selenium WebDriver (Python)** and **Pytest**.

---

## 📋 Test Case Coverage Matrix

| Test ID | Category | Description | Target Flow / Elements |
|---|---|---|---|
| `TC_WEB_AUTH_001` | 🔐 Authentication | Valid Login | Opens auth modal, submits credentials, verifies session state |
| `TC_WEB_AUTH_002` | 🔐 Authentication | Invalid Login | Enters incorrect credentials, verifies error message banner |
| `TC_WEB_AUTH_003` | 🔐 Authentication | User Registration | Switches to Create Account tab, validates registration form |
| `TC_WEB_AUTHZ_001`| 🔑 Authorization | Unauthenticated Access | Verifies guest permissions and profile state |
| `TC_WEB_NAV_001`  | 🧭 Navigation | SPA Route Switching | Home ➔ Scanner ➔ Garden (History) ➔ Settings navigation |
| `TC_WEB_NAV_002`  | 🧭 Navigation | Browser History | Validates browser Back and Forward button popstate routing |
| `TC_WEB_UI_001`   | 🎨 UI Validation | Buttons & Actionables | Checks visibility of CTA buttons, theme toggles, and controls |
| `TC_WEB_UI_002`   | 🎨 UI Validation | Text & Branding | Validates title, brand logo ("PlantLens AI"), and typography |
| `TC_WEB_FORM_001` | 📝 Forms | Plant Diagnostic Submit | Triggers diagnostic form/chip, runs multi-stage AI pipeline |
| `TC_WEB_FORM_002` | 📝 Forms | Required Field Validation | Verifies HTML5 required attributes on input forms |
| `TC_WEB_CRUD_001` | 🔄 CRUD Operations | Add Scan Record | Saves identified plant into personal garden database |
| `TC_WEB_CRUD_002` | 🔄 CRUD Operations | View Record Details | Opens plant diagnostic card modal with care timeline |
| `TC_WEB_CRUD_003` | 🔄 CRUD Operations | Delete Plant Record | Removes plant item from garden with confirmation |
| `TC_WEB_INP_001`  | 🔍 Input Validation | Search Filter | Verifies empty state when non-matching search term is entered |
| `TC_WEB_ERR_001`  | ⚠️ Error Handling | Fallback Handling | Verifies graceful fallback to offline AI simulation without crashing |
| `TC_WEB_SESS_001` | 🔐 Session Mgmt | User Logout | Clears session and restores guest status |
| `TC_WEB_UPL_001`  | 📤 File Upload | Image File Upload | Uploads image file (`sample_plant.jpg`) to file input dropzone |
| `TC_WEB_A11Y_001` | ♿ Accessibility | ARIA & Contrast | Validates High Contrast mode toggle and `alt` attributes |
| `TC_WEB_RESP_001` | 📱 Responsive Design| Mobile Layout | Mobile viewport (iPhone emulation), tests hamburger menu drawer |
| `TC_WEB_PERF_001` | ⚡ Performance | Load Time < 3s | Measures Navigation Timing API and ensures load time < 3 sec |
| `TC_WEB_REG_001`  | 🔁 Regression | End-to-End Regression | Full workflow: Home ➔ Scan Plant ➔ AI Pipeline ➔ Save ➔ Garden Grid |

---

## 🚀 How to Run the Tests

### 1. Prerequisites
Ensure Python 3.10+ and Google Chrome are installed.
Install dependencies:
```bash
pip install selenium webdriver-manager pytest pytest-html
```

---

### 2. Running Against LIVE GitHub Pages URL
Pass the live URL via the `--base-url` or `--url` argument:
```bash
# Execute against live deployment
python tests/run_tests.py --url "https://<your-username>.github.io/<repo-name>/" --headless true
```
Or with pytest directly:
```bash
pytest tests/test_web_phase7.py --base-url="https://<your-username>.github.io/<repo-name>/" -v --html=tests/reports/report.html
```

---

### 3. Running Against Local Vite Server
Start the local server:
```bash
npm run preview
# OR
npm run dev
```

Run test suite:
```bash
python tests/run_tests.py --url "http://localhost:5173"
```

---

### 4. Selective Test Execution
Run a specific test category or individual test case using `-k`:
```bash
# Run only Authentication tests
pytest tests/test_web_phase7.py -k "AUTH" -v

# Run only Regression test
pytest tests/test_web_phase7.py -k "TC_WEB_REG_001" -v

# Run in visual (non-headless) mode
python tests/run_tests.py --headless false
```

---

## 📊 HTML Test Reports & Artifacts
After test execution:
- HTML reports are saved to: `tests/reports/report.html`
- Failure screenshots are automatically captured to: `tests/screenshots/`
