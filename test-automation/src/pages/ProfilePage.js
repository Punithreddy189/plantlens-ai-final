/**
 * Profile & Settings Page Object Model
 */
const BasePage = require('./BasePage');

class ProfilePage extends BasePage {
  get username() { return this.find.byId('com.plantlens.ai:id/tv_profile_username'); }
  get editProfileButton() { return this.find.byId('com.plantlens.ai:id/btn_edit_profile'); }
  get languageSpinner() { return this.find.byId('com.plantlens.ai:id/spinner_language'); }
  get themeSwitch() { return this.find.byId('com.plantlens.ai:id/switch_dark_theme'); }
  get exportPdfButton() { return this.find.byId('com.plantlens.ai:id/btn_export_pdf'); }
  get exportCsvButton() { return this.find.byId('com.plantlens.ai:id/btn_export_csv'); }
  get logoutButton() { return this.find.byId('com.plantlens.ai:id/btn_logout'); }

  async toggleTheme() {
    await this.click(this.themeSwitch);
  }

  async exportReport(format = 'pdf') {
    if (format.toLowerCase() === 'pdf') {
      await this.click(this.exportPdfButton);
    } else {
      await this.click(this.exportCsvButton);
    }
  }

  async logout() {
    await this.click(this.logoutButton);
  }
}

module.exports = ProfilePage;
