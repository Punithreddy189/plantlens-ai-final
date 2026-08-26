/**
 * Scanner Page Object Model
 */
const BasePage = require('./BasePage');

class ScannerPage extends BasePage {
  get cameraPreview() { return this.find.byId('com.plantlens.ai:id/view_finder'); }
  get captureButton() { return this.find.byId('com.plantlens.ai:id/btn_capture'); }
  get galleryButton() { return this.find.byId('com.plantlens.ai:id/btn_gallery'); }
  get flashToggleButton() { return this.find.byId('com.plantlens.ai:id/btn_flash'); }
  get cropConfirmDialog() { return this.find.byId('com.plantlens.ai:id/dialog_crop'); }
  get confirmCropButton() { return this.find.byId('com.plantlens.ai:id/btn_confirm_crop'); }
  get analyzingProgress() { return this.find.byId('com.plantlens.ai:id/progress_analyzing'); }

  async capturePlant() {
    await this.click(this.captureButton);
  }

  async toggleFlash() {
    await this.click(this.flashToggleButton);
  }

  async confirmCrop() {
    if (await this.isDisplayed(this.cropConfirmDialog)) {
      await this.click(this.confirmCropButton);
    }
  }
}

module.exports = ScannerPage;
