import sys
import os
from datetime import datetime

# Ensure project root is in python path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

import pytest
from selenium import webdriver
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.chrome.options import Options
from webdriver_manager.chrome import ChromeDriverManager

def pytest_addoption(parser):
    parser.addoption(
        "--base-url",
        action="store",
        default=os.environ.get("BASE_URL", "http://localhost:5173"),
        help="Base URL of the web application (e.g., Live GitHub Pages URL or local server)"
    )
    parser.addoption(
        "--headless",
        action="store",
        default="true",
        help="Run browser in headless mode ('true' or 'false')"
    )

@pytest.fixture(scope="session")
def base_url(request):
    url = request.config.getoption("--base-url")
    return url.rstrip("/")

@pytest.fixture(scope="function")
def driver(request):
    headless_opt = request.config.getoption("--headless").lower() == "true"
    chrome_options = Options()
    
    if headless_opt:
        chrome_options.add_argument("--headless=new")
    
    chrome_options.add_argument("--no-sandbox")
    chrome_options.add_argument("--disable-dev-shm-usage")
    chrome_options.add_argument("--disable-gpu")
    chrome_options.add_argument("--window-size=1440,900")
    chrome_options.add_argument("--ignore-certificate-errors")
    chrome_options.add_argument("--allow-running-insecure-content")
    chrome_options.add_argument("--use-fake-ui-for-media-stream")
    chrome_options.add_argument("--use-fake-device-for-media-stream")
    
    # Enable performance and console logging
    chrome_options.set_capability("goog:loggingPrefs", {"performance": "ALL", "browser": "ALL"})
    
    try:
        service = Service(ChromeDriverManager().install())
        driver_instance = webdriver.Chrome(service=service, options=chrome_options)
    except Exception:
        driver_instance = webdriver.Chrome(options=chrome_options)
        
    driver_instance.implicitly_wait(4)
    
    yield driver_instance
    
    # Teardown: screenshot on failure
    if hasattr(request.node, "rep_call") and request.node.rep_call.failed:
        os.makedirs("tests/screenshots", exist_ok=True)
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        screenshot_path = f"tests/screenshots/FAIL_{request.node.name}_{timestamp}.png"
        try:
            driver_instance.save_screenshot(screenshot_path)
            print(f"\n[Screenshot saved] {screenshot_path}")
        except Exception as e:
            print(f"\n[Screenshot error] {e}")
            
    driver_instance.quit()

@pytest.fixture(scope="function")
def mobile_driver(request):
    headless_opt = request.config.getoption("--headless").lower() == "true"
    chrome_options = Options()
    
    if headless_opt:
        chrome_options.add_argument("--headless=new")
        
    chrome_options.add_argument("--no-sandbox")
    chrome_options.add_argument("--disable-dev-shm-usage")
    chrome_options.add_argument("--disable-gpu")
    
    # Emulate iPhone 14 / mobile viewport
    mobile_emulation = {
        "deviceMetrics": {"width": 390, "height": 844, "pixelRatio": 3.0},
        "userAgent": "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1"
    }
    chrome_options.add_experimental_option("mobileEmulation", mobile_emulation)
    
    try:
        service = Service(ChromeDriverManager().install())
        driver_instance = webdriver.Chrome(service=service, options=chrome_options)
    except Exception:
        driver_instance = webdriver.Chrome(options=chrome_options)
        
    driver_instance.implicitly_wait(4)
    yield driver_instance
    driver_instance.quit()

@pytest.hookimpl(tryfirst=True, hookwrapper=True)
def pytest_runtest_makereport(item, call):
    outcome = yield
    rep = outcome.get_result()
    setattr(item, "rep_" + rep.when, rep)
