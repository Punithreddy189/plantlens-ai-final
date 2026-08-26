/**
 * Home Page Object Model
 */
const BasePage = require('./BasePage');

class HomePage extends BasePage {
  get greetingText() { return this.find.byId('com.plantlens.ai:id/tv_greeting'); }
  get weatherCard() { return this.find.byId('com.plantlens.ai:id/card_weather'); }
  get weatherTemp() { return this.find.byId('com.plantlens.ai:id/tv_temperature'); }
  get scanFab() { return this.find.byId('com.plantlens.ai:id/fab_scan'); }
  get recentScansList() { return this.find.byId('com.plantlens.ai:id/rv_recent_scans'); }
  get bottomNavScanner() { return this.find.byId('com.plantlens.ai:id/nav_scanner'); }
  get bottomNavSaved() { return this.find.byId('com.plantlens.ai:id/nav_saved'); }
  get bottomNavLibrary() { return this.find.byId('com.plantlens.ai:id/nav_library'); }
  get bottomNavProfile() { return this.find.byId('com.plantlens.ai:id/nav_profile'); }

  async openScanner() {
    await this.click(this.scanFab);
  }

  async navigateToTab(tabName) {
    switch (tabName.toLowerCase()) {
      case 'saved': await this.click(this.bottomNavSaved); break;
      case 'library': await this.click(this.bottomNavLibrary); break;
      case 'profile': await this.click(this.bottomNavProfile); break;
      default: await this.click(this.bottomNavScanner); break;
    }
  }
}

module.exports = HomePage;
