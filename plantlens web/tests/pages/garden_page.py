import time
from selenium.webdriver.common.by import By
from tests.pages.base_page import BasePage

class GardenPage(BasePage):
    """Garden Dashboard Page Object."""

    GARDEN_VIEW = (By.ID, "garden-view")
    SEARCH_INPUT = (By.ID, "garden-search")
    FILTER_PILLS = (By.CSS_SELECTOR, ".filter-pill")
    PLANT_CARDS = (By.CSS_SELECTOR, ".plant-card")
    ANALYTICS_RIBBON = (By.ID, "garden-analytics-ribbon")
    EMPTY_STATE = (By.CSS_SELECTOR, ".empty-state-card")
    PLANT_DETAIL_MODAL = (By.ID, "plant-detail-modal")
    PLANT_DETAIL_CONTENT = (By.ID, "plant-detail-content")
    CLOSE_DETAIL_MODAL_BTN = (By.ID, "btn-close-detail-modal")

    def open_garden(self):
        self.navigate_via_route("garden")
        time.sleep(0.4)
        # Ensure garden grid is populated
        self.wait.until(lambda d: len(d.find_elements(*self.PLANT_CARDS)) > 0 or d.find_element(*self.EMPTY_STATE).is_displayed())

    def search_plants(self, query):
        self.type_text(self.SEARCH_INPUT, query)
        time.sleep(0.4)

    def filter_by_category(self, category_name):
        pill_locator = (By.CSS_SELECTOR, f'.filter-pill[data-filter="{category_name}"]')
        self.click(pill_locator)
        time.sleep(0.3)

    def get_plant_cards(self):
        return self.find_all(self.PLANT_CARDS)

    def get_plant_card_names(self):
        cards = self.get_plant_cards()
        names = []
        for card in cards:
            title_elem = card.find_element(By.CLASS_NAME, "plant-card-title")
            names.append(title_elem.text.strip())
        return names

    def open_first_plant_details(self):
        cards = self.get_plant_cards()
        if cards:
            btn = cards[0].find_element(By.CLASS_NAME, "btn-view-details")
            self.driver.execute_script("arguments[0].scrollIntoView({block: 'center'});", btn)
            time.sleep(0.2)
            self.driver.execute_script("arguments[0].click();", btn)
            time.sleep(0.3)

    def is_detail_modal_open(self):
        modal = self.find(self.PLANT_DETAIL_MODAL)
        return "active" in (modal.get_attribute("class") or "")

    def close_detail_modal(self):
        close_btn = self.find(self.CLOSE_DETAIL_MODAL_BTN)
        self.driver.execute_script("arguments[0].click();", close_btn)
        time.sleep(0.2)

    def delete_first_plant(self):
        cards = self.get_plant_cards()
        if cards:
            btn = cards[0].find_element(By.CLASS_NAME, "btn-remove-plant")
            self.driver.execute_script("arguments[0].scrollIntoView({block: 'center'});", btn)
            time.sleep(0.2)
            self.driver.execute_script("arguments[0].click();", btn)
            # Handle native confirmation alert
            try:
                alert = self.driver.switch_to.alert
                alert.accept()
            except Exception:
                pass
            time.sleep(0.4)
