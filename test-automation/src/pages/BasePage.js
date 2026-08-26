/**
 * Base Page Object for PlantLens AI Test Automation
 */
const { find } = require('../utils/apmFinder');
const GestureUtils = require('../utils/gestureUtils');
const logger = require('../config/logger');

class BasePage {
  constructor(driver) {
    this.driver = driver;
    this.gestures = new GestureUtils(driver);
    this.find = find;
    find.setDriver(driver);
  }

  setDriver(driver) {
    this.driver = driver;
    this.gestures.setDriver(driver);
    this.find.setDriver(driver);
  }

  async waitForDisplayed(locator, timeoutMs = 10000) {
    logger.debug(`⏳ Waiting for element: ${locator.getSelector ? locator.getSelector() : locator}`);
    if (this.driver.isMock) return true;

    const el = locator.getElement ? await locator.getElement(this.driver) : await this.driver.$(locator);
    await el.waitForDisplayed({ timeout: timeoutMs });
    return el;
  }

  async click(locator) {
    logger.debug(`👆 Clicking: ${locator.getSelector ? locator.getSelector() : locator}`);
    if (this.driver.isMock) return true;

    const el = await this.waitForDisplayed(locator);
    await el.click();
  }

  async type(locator, text, clear = true) {
    logger.debug(`⌨️ Typing "${text}" into: ${locator.getSelector ? locator.getSelector() : locator}`);
    if (this.driver.isMock) return true;

    const el = await this.waitForDisplayed(locator);
    if (clear) await el.clearValue();
    await el.setValue(text);
  }

  async getText(locator) {
    if (this.driver.isMock) return 'Mock Text';
    const el = await this.waitForDisplayed(locator);
    return el.getText();
  }

  async isDisplayed(locator) {
    if (this.driver.isMock) return true;
    try {
      const el = locator.getElement ? await locator.getElement(this.driver) : await this.driver.$(locator);
      return await el.isDisplayed();
    } catch (e) {
      return false;
    }
  }

  async pause(ms) {
    await this.driver.pause(ms);
  }
}

module.exports = BasePage;
