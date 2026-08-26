import time
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import Select
from tests.pages.base_page import BasePage

class SettingsPage(BasePage):
    """Settings & Preferences Page Object."""

    SETTINGS_VIEW = (By.ID, "settings-view")
    THEME_SELECT = (By.ID, "theme-select")
    HIGH_CONTRAST_TOGGLE = (By.ID, "toggle-high-contrast")
    LANG_SELECT = (By.ID, "lang-select")
    WATER_REMINDER_TOGGLE = (By.ID, "toggle-waterReminder")
    DISEASE_ALERTS_TOGGLE = (By.ID, "toggle-diseaseAlerts")
    WEEKLY_TIPS_TOGGLE = (By.ID, "toggle-weeklyTips")
    EXPORT_DATA_BTN = (By.ID, "btn-export-data")
    IMPORT_DATA_BTN = (By.ID, "btn-import-data")
    IMPORT_FILE_INPUT = (By.ID, "import-file-input")
    LOGOUT_BTN = (By.ID, "btn-logout")
    PROFILE_USER_NAME = (By.CSS_SELECTOR, ".profile-row h3")

    def open_settings(self):
        self.navigate_via_route("settings")
        time.sleep(0.3)

    def select_theme(self, theme_value):
        select_elem = Select(self.find(self.THEME_SELECT))
        select_elem.select_by_value(theme_value)
        time.sleep(0.2)

    def toggle_high_contrast(self):
        toggle = self.find(self.HIGH_CONTRAST_TOGGLE)
        self.driver.execute_script("arguments[0].click();", toggle)
        time.sleep(0.2)

    def is_high_contrast_active(self):
        val = self.driver.execute_script("return document.documentElement.getAttribute('data-high-contrast');")
        return val == "true"

    def get_profile_name(self):
        return self.get_text(self.PROFILE_USER_NAME)

    def click_logout(self):
        btn = self.find(self.LOGOUT_BTN)
        self.driver.execute_script("arguments[0].click();", btn)
        try:
            alert = self.driver.switch_to.alert
            alert.accept()
        except Exception:
            pass
        time.sleep(0.4)
