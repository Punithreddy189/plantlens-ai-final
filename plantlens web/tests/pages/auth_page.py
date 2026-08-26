import time
from selenium.webdriver.common.by import By
from tests.pages.base_page import BasePage

class AuthPage(BasePage):
    """Authentication Modal & Flow Page Object."""

    USER_NAV_BTN = (By.ID, "nav-user-btn")
    AUTH_MODAL = (By.ID, "auth-modal")
    TAB_LOGIN = (By.ID, "auth-tab-login")
    TAB_SIGNUP = (By.ID, "auth-tab-signup")
    INPUT_NAME = (By.ID, "auth-name-input")
    INPUT_EMAIL = (By.ID, "auth-email-input")
    INPUT_PASSWORD = (By.ID, "auth-password-input")
    SUBMIT_BTN = (By.ID, "btn-auth-submit")
    ERROR_MSG = (By.ID, "auth-error-msg")
    CLOSE_MODAL_BTN = (By.CSS_SELECTOR, "#auth-modal .btn-close-modal")

    def open_auth_modal(self):
        self.click(self.USER_NAV_BTN)
        time.sleep(0.3)

    def is_auth_modal_open(self):
        modal = self.find(self.AUTH_MODAL)
        return "active" in (modal.get_attribute("class") or "")

    def switch_to_signup(self):
        self.click(self.TAB_SIGNUP)
        time.sleep(0.2)

    def switch_to_login(self):
        self.click(self.TAB_LOGIN)
        time.sleep(0.2)

    def login(self, email, password):
        self.switch_to_login()
        self.type_text(self.INPUT_EMAIL, email)
        self.type_text(self.INPUT_PASSWORD, password)
        self.click(self.SUBMIT_BTN)

    def register(self, name, email, password):
        self.switch_to_signup()
        self.type_text(self.INPUT_NAME, name)
        self.type_text(self.INPUT_EMAIL, email)
        self.type_text(self.INPUT_PASSWORD, password)
        self.click(self.SUBMIT_BTN)

    def get_error_message(self):
        try:
            return self.wait.until(lambda d: d.find_element(*self.ERROR_MSG).text.strip())
        except Exception:
            return self.get_text(self.ERROR_MSG)

    def is_error_displayed(self):
        try:
            element = self.find(self.ERROR_MSG)
            display = element.value_of_css_property("display")
            return display != "none" and len(element.text.strip()) > 0
        except Exception:
            return False

    def close_modal(self):
        self.click(self.CLOSE_MODAL_BTN)
