from selenium.webdriver.common.by import By
from tests.pages.base_page import BasePage

class HomePage(BasePage):
    """Home View Page Object."""

    HERO_TITLE = (By.ID, "hero-typewriter-text")
    HERO_SCAN_BTN = (By.CSS_SELECTOR, "#home-view .hero-actions button[data-route='scanner']")
    BRAND_LOGO = (By.CSS_SELECTOR, ".brand-logo span")
    NAV_HOME = (By.CSS_SELECTOR, "nav a[data-route='home']")
    NAV_SCANNER = (By.CSS_SELECTOR, "nav a[data-route='scanner']")
    NAV_GARDEN = (By.CSS_SELECTOR, "nav a[data-route='garden']")
    NAV_SETTINGS = (By.CSS_SELECTOR, "nav a[data-route='settings']")
    THEME_TOGGLE = (By.ID, "quick-theme-toggle")
    USER_BTN = (By.ID, "nav-user-btn")
    MOBILE_MENU_BTN = (By.ID, "mobile-menu-btn")
    NAV_LINKS_CONTAINER = (By.ID, "nav-links")
    CARE_TIPS_CONTAINER = (By.ID, "care-tips-container")
    TIP_PREV_BTN = (By.ID, "tip-prev")
    TIP_NEXT_BTN = (By.ID, "tip-next")
    FAQ_QUESTIONS = (By.CSS_SELECTOR, ".faq-question")
    STAT_PLANTS = (By.ID, "stat-plants")

    def get_brand_text(self):
        return self.get_text(self.BRAND_LOGO)

    def get_hero_title_text(self):
        return self.get_text(self.HERO_TITLE)

    def click_start_scanning(self):
        self.click(self.HERO_SCAN_BTN)

    def toggle_theme(self):
        self.click(self.THEME_TOGGLE)

    def get_theme_attribute(self):
        return self.driver.execute_script("return document.documentElement.getAttribute('data-theme');")

    def toggle_mobile_menu(self):
        self.click(self.MOBILE_MENU_BTN)

    def is_mobile_nav_open(self):
        element = self.find(self.NAV_LINKS_CONTAINER)
        return "mobile-open" in (element.get_attribute("class") or "")
