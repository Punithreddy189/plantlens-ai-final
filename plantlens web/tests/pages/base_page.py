import time
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.common.by import By
from selenium.common.exceptions import TimeoutException, NoSuchElementException

class BasePage:
    """Base Page Object with common Selenium utilities & explicit waits."""
    
    def __init__(self, driver, base_url="http://localhost:5173"):
        self.driver = driver
        self.base_url = base_url
        self.wait = WebDriverWait(driver, 10)
        self.short_wait = WebDriverWait(driver, 4)

    def open(self, path=""):
        target_url = f"{self.base_url}/{path}".replace("//", "/").replace(":/", "://")
        self.driver.get(target_url)
        self.wait_for_boot_screen_to_hide()

    def wait_for_boot_screen_to_hide(self):
        """Wait for the initial AI boot/splash animation screen to fade away."""
        try:
            WebDriverWait(self.driver, 6).until(
                EC.invisibility_of_element_located((By.ID, "boot-screen"))
            )
        except Exception:
            # Fallback: force remove boot-screen class if animation is delayed
            try:
                self.driver.execute_script(
                    "const b = document.getElementById('boot-screen'); if(b) b.classList.add('hidden');"
                )
            except Exception:
                pass

    def find(self, locator):
        return self.wait.until(EC.presence_of_element_located(locator))

    def find_visible(self, locator):
        return self.wait.until(EC.visibility_of_element_located(locator))

    def find_all(self, locator):
        return self.driver.find_elements(*locator)

    def click(self, locator):
        element = self.wait.until(EC.element_to_be_clickable(locator))
        self.driver.execute_script("arguments[0].scrollIntoView({block: 'center'});", element)
        time.sleep(0.1)
        try:
            element.click()
        except Exception:
            # JavaScript click fallback if overlay intercepts
            self.driver.execute_script("arguments[0].click();", element)

    def type_text(self, locator, text, clear_first=True):
        element = self.find_visible(locator)
        if clear_first:
            element.clear()
        element.send_keys(text)

    def get_text(self, locator):
        element = self.find(locator)
        return element.text.strip()

    def is_visible(self, locator):
        try:
            return self.short_wait.until(EC.visibility_of_element_located(locator)).is_displayed()
        except (TimeoutException, NoSuchElementException):
            return False

    def is_present(self, locator):
        try:
            self.driver.find_element(*locator)
            return True
        except NoSuchElementException:
            return False

    def execute_script(self, script, *args):
        return self.driver.execute_script(script, *args)

    def get_current_route(self):
        """Extract current active route hash or section."""
        hash_val = self.driver.execute_script("return window.location.hash;").replace("#", "")
        if hash_val:
            return hash_val
        active_view = self.driver.execute_script(
            "const el = document.querySelector('.page-view.active'); return el ? el.id.replace('-view', '') : 'home';"
        )
        return active_view

    def navigate_via_route(self, route_name):
        locator = (By.CSS_SELECTOR, f'[data-route="{route_name}"]')
        self.click(locator)
        time.sleep(0.3)
