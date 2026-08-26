/**
 * Splash Page Object Model
 */
const BasePage = require('./BasePage');

class SplashPage extends BasePage {
  get logo() { return this.find.byId('com.plantlens.ai:id/iv_splash_logo'); }
  get title() { return this.find.byText('PlantLens AI'); }
  get progressIndicator() { return this.find.byId('com.plantlens.ai:id/progress_splash'); }

  async verifySplashScreen() {
    await this.waitForDisplayed(this.logo);
    return await this.isDisplayed(this.title);
  }
}

module.exports = SplashPage;
