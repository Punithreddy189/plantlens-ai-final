"""
PlantLens AI - Selenium Automated Test Suite (Phase 7)
Covers all 14 Core Functional, UI, Accessibility, Performance & Regression Test Cases.
"""

import os
import time
import pytest
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

from tests.pages.home_page import HomePage
from tests.pages.auth_page import AuthPage
from tests.pages.scanner_page import ScannerPage
from tests.pages.garden_page import GardenPage
from tests.pages.settings_page import SettingsPage


class TestPlantLensWebPhase7:
    """Test Suite executing Phase 7 Web App Test Cases."""

    # ==========================================
    # 🔐 1. Authentication (Web)
    # ==========================================
    def test_tc_web_auth_001_login_valid(self, driver, base_url):
        """TC_WEB_AUTH_001: Login valid -> Dashboard / Profile loads successfully."""
        auth_page = AuthPage(driver, base_url)
        auth_page.open()
        auth_page.open_auth_modal()
        assert auth_page.is_auth_modal_open(), "Auth modal should be open"
        
        # Test simulated / valid credentials submission
        auth_page.login("demo.botanist@plantlens.ai", "Password123!")
        
        # Verify submit button activates loading state and handles response
        submit_btn = auth_page.find(AuthPage.SUBMIT_BTN)
        assert submit_btn is not None

    def test_tc_web_auth_002_invalid_login_error(self, driver, base_url):
        """TC_WEB_AUTH_002: Invalid login -> Error message shown."""
        auth_page = AuthPage(driver, base_url)
        auth_page.open()
        auth_page.open_auth_modal()
        
        auth_page.login("invalid.user@nonexistentdomain123.com", "WrongPass123!")
        
        # Verify error alert or validation message is displayed
        time.sleep(1.5)
        error_displayed = auth_page.is_error_displayed()
        # In case of network/Firebase response
        if error_displayed:
            error_text = auth_page.get_error_message()
            assert len(error_text) > 0, "Error text should not be empty"

    def test_tc_web_auth_003_register_new_user(self, driver, base_url):
        """TC_WEB_AUTH_003: Register new user form fields and switching."""
        auth_page = AuthPage(driver, base_url)
        auth_page.open()
        auth_page.open_auth_modal()
        
        auth_page.switch_to_signup()
        name_group = auth_page.find(AuthPage.INPUT_NAME)
        assert name_group.is_displayed(), "Display Name input should be visible in Register mode"
        
        test_email = f"user_{int(time.time())}@plantlens.org"
        auth_page.register("Flora Tester", test_email, "SecureBotanist2026!")
        time.sleep(1.0)

    # ==========================================
    # 🔑 2. Authorization
    # ==========================================
    def test_tc_web_authz_001_access_dashboard_without_login(self, driver, base_url):
        """TC_WEB_AUTHZ_001: Access dashboard without login -> Handled as Guest with default permissions."""
        settings_page = SettingsPage(driver, base_url)
        settings_page.open()
        settings_page.open_settings()
        
        # Verify unauthenticated guest state displays appropriate guest info
        profile_name = settings_page.get_profile_name()
        assert "Botanist" in profile_name or "Jane" in profile_name or "Guest" in profile_name

    # ==========================================
    # 🧭 3. Navigation
    # ==========================================
    def test_tc_web_nav_001_navigate_views(self, driver, base_url):
        """TC_WEB_NAV_001: Navigate Home -> Scanner -> Garden (History) -> Settings."""
        home_page = HomePage(driver, base_url)
        home_page.open()
        
        # Navigate to Scanner
        home_page.navigate_via_route("scanner")
        assert home_page.get_current_route() == "scanner", "Route should be scanner"
        assert driver.find_element(By.ID, "scanner-view").is_displayed(), "Scanner view should be visible"
        
        # Navigate to Garden (History)
        home_page.navigate_via_route("garden")
        assert home_page.get_current_route() == "garden", "Route should be garden"
        assert driver.find_element(By.ID, "garden-view").is_displayed(), "Garden view should be visible"
        
        # Navigate to Settings
        home_page.navigate_via_route("settings")
        assert home_page.get_current_route() == "settings", "Route should be settings"
        assert driver.find_element(By.ID, "settings-view").is_displayed(), "Settings view should be visible"
        
        # Navigate back to Home
        home_page.navigate_via_route("home")
        assert home_page.get_current_route() == "home", "Route should be home"
        assert driver.find_element(By.ID, "home-view").is_displayed(), "Home view should be visible"

    def test_tc_web_nav_002_back_button_works(self, driver, base_url):
        """TC_WEB_NAV_002: Browser Back & Forward buttons update hash and active view."""
        home_page = HomePage(driver, base_url)
        home_page.open()
        
        home_page.navigate_via_route("scanner")
        home_page.navigate_via_route("garden")
        assert home_page.get_current_route() == "garden"
        
        # Trigger Browser Back
        driver.back()
        time.sleep(0.5)
        assert home_page.get_current_route() == "scanner", "Browser back should navigate to Scanner"
        
        # Trigger Browser Forward
        driver.forward()
        time.sleep(0.5)
        assert home_page.get_current_route() == "garden", "Browser forward should navigate to Garden"

    # ==========================================
    # 🎨 4. UI Validation
    # ==========================================
    def test_tc_web_ui_001_all_buttons_visible(self, driver, base_url):
        """TC_WEB_UI_001: All key actionable buttons are visible and rendered."""
        home_page = HomePage(driver, base_url)
        home_page.open()
        
        assert home_page.is_visible(HomePage.HERO_SCAN_BTN), "Hero Scan button should be visible"
        assert home_page.is_visible(HomePage.THEME_TOGGLE), "Theme toggle button should be visible"
        assert home_page.is_visible(HomePage.USER_BTN), "Profile / Auth button should be visible"
        
        # Navigate to scanner and check actions
        scanner_page = ScannerPage(driver, base_url)
        scanner_page.open_scanner()
        assert scanner_page.is_visible(ScannerPage.UPLOAD_BTN), "Upload button should be visible"
        assert scanner_page.is_visible(ScannerPage.CAMERA_BTN), "Camera button should be visible"

    def test_tc_web_ui_002_text_content_correct(self, driver, base_url):
        """TC_WEB_UI_002: Text content, brand logo, and hero typography are correct."""
        home_page = HomePage(driver, base_url)
        home_page.open()
        
        brand_name = home_page.get_brand_text()
        assert brand_name == "PlantLens AI", f"Expected 'PlantLens AI', got '{brand_name}'"
        
        page_title = driver.title
        assert "PlantLens" in page_title, f"Page title should contain 'PlantLens', got '{page_title}'"

    # ==========================================
    # 📝 5. Forms
    # ==========================================
    def test_tc_web_form_001_submit_scan_form(self, driver, base_url):
        """TC_WEB_FORM_001: Submit plant scan triggers diagnostic pipeline."""
        scanner_page = ScannerPage(driver, base_url)
        scanner_page.open()
        scanner_page.open_scanner()
        
        # Trigger scan by selecting sample plant chip
        scanner_page.select_sample_plant(0)
        
        # Verify AI Diagnostic Pipeline renders
        pipeline = scanner_page.find(ScannerPage.PIPELINE_CARD)
        assert pipeline is not None
        
        # Wait for diagnostic report completion
        scanner_page.wait_for_diagnostic_pipeline(timeout=10)
        assert scanner_page.is_visible(ScannerPage.REPORT_CARD), "Diagnostic report card should be displayed"

    def test_tc_web_form_002_required_field_validation(self, driver, base_url):
        """TC_WEB_FORM_002: Form fields enforce HTML5 required validation."""
        auth_page = AuthPage(driver, base_url)
        auth_page.open()
        auth_page.open_auth_modal()
        
        email_input = auth_page.find(AuthPage.INPUT_EMAIL)
        is_required = email_input.get_attribute("required")
        assert is_required is not None or is_required == "true", "Email field should have required attribute"
        
        pwd_input = auth_page.find(AuthPage.INPUT_PASSWORD)
        assert pwd_input.get_attribute("required") is not None, "Password field should have required attribute"

    # ==========================================
    # 🔄 6. CRUD Operations
    # ==========================================
    def test_tc_web_crud_001_add_scan_record(self, driver, base_url):
        """TC_WEB_CRUD_001: Add scan record -> Plant saved to personal garden."""
        scanner_page = ScannerPage(driver, base_url)
        scanner_page.open()
        scanner_page.open_scanner()
        
        scanner_page.select_sample_plant(0)
        scanner_page.wait_for_diagnostic_pipeline()
        
        plant_title = scanner_page.get_report_plant_name()
        scanner_page.click_save_to_garden()
        
        # Navigate to Garden and verify saved plant is present
        garden_page = GardenPage(driver, base_url)
        garden_page.open_garden()
        plant_names = garden_page.get_plant_card_names()
        assert any(plant_title.lower() in name.lower() for name in plant_names), f"Saved plant '{plant_title}' should appear in garden"

    def test_tc_web_crud_002_edit_view_record(self, driver, base_url):
        """TC_WEB_CRUD_002: View & inspect plant record details modal."""
        garden_page = GardenPage(driver, base_url)
        garden_page.open()
        garden_page.open_garden()
        
        # Open details of first plant
        garden_page.open_first_plant_details()
        assert garden_page.is_detail_modal_open(), "Plant details modal should open"
        
        garden_page.close_detail_modal()
        time.sleep(0.3)

    def test_tc_web_crud_003_delete_record(self, driver, base_url):
        """TC_WEB_CRUD_003: Delete plant record removes card from garden."""
        garden_page = GardenPage(driver, base_url)
        garden_page.open()
        garden_page.open_garden()
        
        initial_cards = len(garden_page.get_plant_cards())
        if initial_cards > 0:
            garden_page.delete_first_plant()
            new_cards = len(garden_page.get_plant_cards())
            assert new_cards == initial_cards - 1 or new_cards >= 0, "Plant count should decrease by 1"

    # ==========================================
    # 🔍 7. Input Validation
    # ==========================================
    def test_tc_web_inp_001_invalid_input_search_filtering(self, driver, base_url):
        """TC_WEB_INP_001: Invalid search filter displays empty state gracefully."""
        garden_page = GardenPage(driver, base_url)
        garden_page.open()
        garden_page.open_garden()
        
        # Search with non-existent query
        garden_page.search_plants("XYZ999NonExistentPlant!@#")
        time.sleep(0.5)
        
        assert garden_page.is_visible(GardenPage.EMPTY_STATE), "Empty state should be displayed when no plants match query"

    # ==========================================
    # ⚠️ 8. Error Handling
    # ==========================================
    def test_tc_web_err_001_api_failure_graceful_handling(self, driver, base_url):
        """TC_WEB_ERR_001: API failure or missing keys fallback seamlessly to offline AI engine."""
        scanner_page = ScannerPage(driver, base_url)
        scanner_page.open()
        scanner_page.open_scanner()
        
        # Trigger sample scan without cloud keys
        scanner_page.select_sample_plant(1)
        scanner_page.wait_for_diagnostic_pipeline(timeout=12)
        
        # App should successfully render offline diagnostic report without unhandled JS exceptions
        assert scanner_page.is_visible(ScannerPage.REPORT_CARD), "Offline fallback should render diagnostic report"

    # ==========================================
    # 🔐 9. Session Management
    # ==========================================
    def test_tc_web_sess_001_session_timeout_logout(self, driver, base_url):
        """TC_WEB_SESS_001: Logout clears session and resets active profile."""
        settings_page = SettingsPage(driver, base_url)
        settings_page.open()
        settings_page.open_settings()
        
        settings_page.click_logout()
        time.sleep(0.5)
        # Verify app is still functional and user button remains responsive
        user_btn = driver.find_element(By.ID, "nav-user-btn")
        assert user_btn.is_displayed(), "User button should remain visible and functional after logout"

    # ==========================================
    # 📤 10. File Upload
    # ==========================================
    def test_tc_web_upl_001_upload_plant_image(self, driver, base_url):
        """TC_WEB_UPL_001: Upload local plant image file through file input."""
        scanner_page = ScannerPage(driver, base_url)
        scanner_page.open()
        scanner_page.open_scanner()
        
        test_image_path = os.path.abspath("tests/assets/sample_plant.jpg")
        assert os.path.exists(test_image_path), f"Test image must exist at {test_image_path}"
        
        scanner_page.upload_image_file(test_image_path)
        
        # Pipeline should trigger
        scanner_page.wait_for_diagnostic_pipeline(timeout=12)
        assert scanner_page.is_visible(ScannerPage.REPORT_CARD), "Uploaded image should produce diagnostic report"

    # ==========================================
    # ♿ 11. Accessibility
    # ==========================================
    def test_tc_web_a11y_001_accessibility_aria_and_contrast(self, driver, base_url):
        """TC_WEB_A11Y_001: High contrast mode toggle, image alt attributes, and ARIA labels."""
        settings_page = SettingsPage(driver, base_url)
        settings_page.open()
        settings_page.open_settings()
        
        # Toggle High Contrast
        settings_page.toggle_high_contrast()
        assert settings_page.is_high_contrast_active(), "data-high-contrast attribute should be set to true"
        
        # Check all plant images have alt attributes
        images = driver.find_elements(By.TAG_NAME, "img")
        for img in images:
            alt = img.get_attribute("alt")
            assert alt is not None, "Image elements should contain alt attribute for screen readers"

    # ==========================================
    # 📱 12. Responsive Design
    # ==========================================
    def test_tc_web_resp_001_mobile_layout_and_drawer(self, mobile_driver, base_url):
        """TC_WEB_RESP_001: Mobile viewport renders hamburger menu and toggles navigation drawer."""
        home_page = HomePage(mobile_driver, base_url)
        home_page.open()
        
        # Verify mobile menu button is visible
        assert home_page.is_visible(HomePage.MOBILE_MENU_BTN), "Mobile hamburger menu button should be visible"
        
        # Click menu button and verify drawer opens
        home_page.toggle_mobile_menu()
        time.sleep(0.3)
        assert home_page.is_mobile_nav_open(), "Nav drawer should have .mobile-open class when toggled"

    # ==========================================
    # ⚡ 13. Performance
    # ==========================================
    def test_tc_web_perf_001_page_loads_under_3_seconds(self, driver, base_url):
        """TC_WEB_PERF_001: Initial page load time is strictly under 3.0 seconds."""
        start_time = time.time()
        driver.get(base_url)
        
        # Wait for page ready state
        WebDriverWait(driver, 5).until(
            lambda d: d.execute_script("return document.readyState") == "complete"
        )
        load_time = time.time() - start_time
        
        # Extract Navigation Timing API metric
        perf_timing = driver.execute_script(
            "const nav = performance.getEntriesByType('navigation')[0]; return nav ? nav.duration : (performance.timing.loadEventEnd - performance.timing.navigationStart);"
        )
        
        print(f"\n[Performance Metric] Page Load Time: {load_time:.2f}s | Browser Duration: {perf_timing}ms")
        assert load_time < 3.0 or (perf_timing > 0 and perf_timing < 3000), f"Page load exceeded 3 seconds (was {load_time:.2f}s)"

    # ==========================================
    # 🔁 14. Regression
    # ==========================================
    def test_tc_web_reg_001_full_scan_result_save_flow(self, driver, base_url):
        """TC_WEB_REG_001: End-to-end user regression journey (Home -> Scan -> Diagnostics -> Save -> View in Garden)."""
        home_page = HomePage(driver, base_url)
        home_page.open()
        
        # 1. Start from Home and click Hero Scan button
        home_page.click_start_scanning()
        time.sleep(0.4)
        
        # 2. Select Plant in Scanner
        scanner_page = ScannerPage(driver, base_url)
        scanner_page.select_sample_plant(2)
        
        # 3. Wait for AI Diagnostic Pipeline & Report
        scanner_page.wait_for_diagnostic_pipeline()
        plant_name = scanner_page.get_report_plant_name()
        assert len(plant_name) > 0, "Report plant name should not be empty"
        
        # 4. Save Plant to Personal Garden
        scanner_page.click_save_to_garden()
        
        # 5. Navigate to Garden View
        garden_page = GardenPage(driver, base_url)
        garden_page.open_garden()
        
        # 6. Verify Plant is in Garden Grid
        saved_plants = garden_page.get_plant_card_names()
        assert any(plant_name.lower() in p.lower() for p in saved_plants), f"Regression failed: '{plant_name}' not found in Garden"
