/**
 * Login Page Object Model
 */
const BasePage = require('./BasePage');

class LoginPage extends BasePage {
  get emailInput() { return this.find.byId('com.plantlens.ai:id/et_email'); }
  get passwordInput() { return this.find.byId('com.plantlens.ai:id/et_password'); }
  get loginButton() { return this.find.byId('com.plantlens.ai:id/btn_login'); }
  get googleSignInButton() { return this.find.byId('com.plantlens.ai:id/btn_google_signin'); }
  get guestButton() { return this.find.byId('com.plantlens.ai:id/btn_guest_continue'); }
  get errorMessage() { return this.find.byId('com.plantlens.ai:id/tv_auth_error'); }

  async login(email, password) {
    if (email !== undefined) await this.type(this.emailInput, email);
    if (password !== undefined) await this.type(this.passwordInput, password);
    await this.click(this.loginButton);
  }

  async continueAsGuest() {
    await this.click(this.guestButton);
  }

  async getValidationErrorMessage() {
    return await this.getText(this.errorMessage);
  }
}

module.exports = LoginPage;
