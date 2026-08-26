import time
import sys
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By

def test_firestore():
    options = Options()
    options.add_argument('--headless=new')
    options.add_argument('--no-sandbox')
    options.add_argument('--disable-dev-shm-usage')
    options.set_capability('goog:loggingPrefs', {'browser': 'ALL'})

    driver = webdriver.Chrome(options=options)

    # Check connection
    driver.get("http://localhost:3000")
    time.sleep(2)

    # Trigger Save Data
    try:
        driver.execute_script("""
            window.lastAlertMessage = null;
            window.alert = function(msg) {
                window.lastAlertMessage = msg;
            };
        """)
        
        btn = driver.find_element(By.XPATH, "//button[contains(., 'Save Data')]")
        driver.execute_script("arguments[0].click();", btn)
        time.sleep(4)

        alert_msg = driver.execute_script("return window.lastAlertMessage;")
        if alert_msg:
            print("ALERT:", alert_msg.encode('ascii', errors='replace').decode('ascii'))

        print("\n--- Console Logs ---")
        logs = driver.get_log("browser")
        for entry in logs:
            safe_msg = entry['message'].encode('ascii', errors='replace').decode('ascii')
            print(f"[{entry['level']}] {safe_msg}")

    except Exception as e:
        print("Error:", repr(e))

    driver.quit()

if __name__ == "__main__":
    test_firestore()
