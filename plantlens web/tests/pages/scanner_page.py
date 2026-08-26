import time
from selenium.webdriver.common.by import By
from tests.pages.base_page import BasePage

class ScannerPage(BasePage):
    """Scanner & AI Diagnostic Pipeline Page Object."""

    SCANNER_VIEW = (By.ID, "scanner-view")
    DROPZONE = (By.ID, "dropzone")
    FILE_INPUT = (By.ID, "file-input")
    UPLOAD_BTN = (By.ID, "btn-upload-file")
    CAMERA_BTN = (By.ID, "btn-open-camera")
    SAMPLE_CHIPS = (By.CSS_SELECTOR, ".sample-chip")
    PIPELINE_CARD = (By.ID, "scan-pipeline-card")
    PIPELINE_STEPS = (By.CSS_SELECTOR, ".pipeline-step")
    REPORT_CARD = (By.ID, "report-card")
    REPORT_TITLE = (By.CSS_SELECTOR, "#report-card h2")
    SAVE_TO_GARDEN_BTN = (By.ID, "btn-save-to-garden")
    DOWNLOAD_PDF_BTN = (By.ID, "btn-download-pdf")
    SHARE_REPORT_BTN = (By.ID, "btn-share-report")
    SCAN_AGAIN_BTN = (By.ID, "btn-scan-again")
    CAMERA_MODAL = (By.ID, "camera-modal")
    SNAP_PHOTO_BTN = (By.ID, "btn-snap-photo")
    CLOSE_CAMERA_BTN = (By.ID, "btn-close-camera")

    def open_scanner(self):
        self.navigate_via_route("scanner")

    def upload_image_file(self, absolute_file_path):
        file_input = self.find(self.FILE_INPUT)
        file_input.send_keys(absolute_file_path)

    def select_sample_plant(self, index=0):
        chips = self.find_all(self.SAMPLE_CHIPS)
        if chips and len(chips) > index:
            self.click((By.CSS_SELECTOR, f".sample-chip:nth-child({index + 1})"))

    def wait_for_diagnostic_pipeline(self, timeout=12):
        """Wait until pipeline finishes and report card is displayed."""
        self.wait.until(lambda d: d.find_element(*self.REPORT_CARD).is_displayed())

    def get_report_plant_name(self):
        return self.get_text(self.REPORT_TITLE)

    def click_save_to_garden(self):
        self.click(self.SAVE_TO_GARDEN_BTN)
        time.sleep(0.5)

    def click_scan_again(self):
        self.click(self.SCAN_AGAIN_BTN)
        time.sleep(0.3)

    def open_camera(self):
        self.click(self.CAMERA_BTN)

    def close_camera(self):
        self.click(self.CLOSE_CAMERA_BTN)
