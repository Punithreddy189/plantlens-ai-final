import time
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By

def test_signup():
    options = Options()
    options.add_argument('--headless=new')
    options.add_argument('--no-sandbox')
    options.add_argument('--disable-dev-shm-usage')
    options.set_capability('goog:loggingPrefs', {'browser': 'ALL'})

    driver = webdriver.Chrome(options=options)
    driver.get("http://localhost:3000")
    time.sleep(2)

    try:
        # Open auth modal
        user_btn = driver.find_element(By.ID, "nav-user-btn")
        driver.execute_script("arguments[0].click();", user_btn)
        time.sleep(1)

        # Switch to signup tab
        tab_signup = driver.find_element(By.ID, "auth-tab-signup")
        driver.execute_script("arguments[0].click();", tab_signup)
        time.sleep(1)

        # Fill inputs
        name_input = driver.find_element(By.ID, "auth-name-input")
        email_input = driver.find_element(By.ID, "auth-email-input")
        pwd_input = driver.find_element(By.ID, "auth-password-input")
        submit_btn = driver.find_element(By.ID, "btn-auth-submit")

        test_email = f"punith_live_{int(time.time())}@plantlens.org"
        name_input.send_keys("Punith")
        email_input.send_keys(test_email)
        pwd_input.send_keys("Password1234!")

        driver.execute_script("arguments[0].click();", submit_btn)
        time.sleep(4)

        # Check for error or success
        err_msg = driver.find_element(By.ID, "auth-error-msg")
        print("Error Displayed:", err_msg.is_displayed(), "Text:", repr(err_msg.text))
        print("User Button Text (Initials):", repr(user_btn.text))

        print("\n--- Console Logs ---")
        for log in driver.get_log("browser"):
            safe_msg = log['message'].encode('ascii', errors='replace').decode('ascii')
            print(f"[{log['level']}] {safe_msg}")

    except Exception as e:
        print("Test Exception:", repr(e))

    driver.quit()

if __name__ == "__main__":
    test_signup()
