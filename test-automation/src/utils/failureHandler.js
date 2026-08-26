/**
 * Failure Handling Utility for Appium 2.x & APM
 * Captures screenshot, device logs, APM widget tree, and stack trace on every failure
 */
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');
const logger = require('../config/logger');

class FailureHandler {
  constructor() {
    this.failureDir = path.resolve(__dirname, '../../reports/failures');
    this.ensureDirectory();
    this.failureRecords = [];
  }

  ensureDirectory() {
    if (!fs.existsSync(this.failureDir)) {
      fs.mkdirSync(this.failureDir, { recursive: true });
    }
  }

  /**
   * Handle and document a test failure comprehensively
   */
  async handleFailure(testTitle, error, driver, extraContext = {}) {
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
    const sanitizedTitle = testTitle.replace(/[^a-zA-Z0-9_-]/g, '_').substring(0, 50);
    const failureBaseName = `${sanitizedTitle}_${timestamp}`;

    logger.error(`❌ TEST FAILURE: [${testTitle}] - ${error.message}`);

    const failureDetails = {
      testName: testTitle,
      failureReason: error.message || 'Unknown Assertion/Execution Error',
      stackTrace: error.stack || 'No stack trace available',
      timestamp: new Date().toISOString(),
      device: driver && driver.deviceName ? driver.deviceName : 'Pixel_7_API_34',
      androidVersion: driver && driver.platformVersion ? driver.platformVersion : '14.0',
      screenshotPath: 'N/A',
      widgetTreePath: 'N/A',
      deviceLogPath: 'N/A',
      ...extraContext
    };

    if (driver) {
      // 1. Capture Screenshot
      try {
        const screenshotPath = path.join(this.failureDir, `${failureBaseName}_screenshot.png`);
        if (driver.isMock) {
          fs.writeFileSync(screenshotPath, Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==', 'base64'));
        } else {
          const screenshotBase64 = await driver.takeScreenshot();
          fs.writeFileSync(screenshotPath, Buffer.from(screenshotBase64, 'base64'));
        }
        failureDetails.screenshotPath = screenshotPath;
        logger.info(`📸 Screenshot captured: ${screenshotPath}`);
      } catch (scErr) {
        logger.warn(`⚠️ Failed to capture screenshot: ${scErr.message}`);
      }

      // 2. Capture APM Widget Tree / Hierarchy
      try {
        const widgetTreePath = path.join(this.failureDir, `${failureBaseName}_widget_tree.xml`);
        const source = driver.isMock ? '<hierarchy><mock_screen id="failure_dump" /></hierarchy>' : await driver.getPageSource();
        fs.writeFileSync(widgetTreePath, source, 'utf-8');
        failureDetails.widgetTreePath = widgetTreePath;
        logger.info(`🌳 Widget tree hierarchy dumped: ${widgetTreePath}`);
      } catch (srcErr) {
        logger.warn(`⚠️ Failed to capture page source: ${srcErr.message}`);
      }

      // 3. Capture Device ADB Logs
      try {
        const logPath = path.join(this.failureDir, `${failureBaseName}_logcat.log`);
        let logs = 'Logcat capture simulation for testing session';
        try {
          logs = execSync('adb logcat -d -t 150', { encoding: 'utf-8', timeout: 3000 });
        } catch (e) {
          logs = `ADB logcat unavailable: ${e.message}`;
        }
        fs.writeFileSync(logPath, logs, 'utf-8');
        failureDetails.deviceLogPath = logPath;
      } catch (logErr) {
        logger.warn(`⚠️ Failed to capture logcat: ${logErr.message}`);
      }
    }

    this.failureRecords.push(failureDetails);
    return failureDetails;
  }

  getFailures() {
    return this.failureRecords;
  }

  clearFailures() {
    this.failureRecords = [];
  }
}

module.exports = new FailureHandler();
