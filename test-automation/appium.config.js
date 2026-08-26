/**
 * Appium 2.x Configuration for PlantLens AI
 * Supports: appium-APM-driver (preferred) with UiAutomator2 fallback
 */
require('dotenv').config({ path: '.env.test' });
const path = require('path');

const resolvedApkPath = process.env.APK_PATH 
  ? path.resolve(__dirname, process.env.APK_PATH)
  : path.resolve(__dirname, '../PlantLensAI-main/app/build/intermediates/apk/debug/app-debug.apk');

module.exports = {
  server: {
    host: process.env.APPIUM_HOST || '127.0.0.1',
    port: parseInt(process.env.APPIUM_PORT, 10) || 4723,
    logPath: path.resolve(__dirname, 'reports/logs/appium-server.log'),
    basePath: '/'
  },
  capabilities: {
    // Primary APM Driver Configuration
    apmDriver: {
      platformName: 'Android',
      'appium:automationName': 'APM',
      'appium:deviceName': process.env.DEVICE_NAME || 'Android Emulator',
      'appium:platformVersion': process.env.PLATFORM_VERSION || '14.0',
      'appium:app': resolvedApkPath,
      'appium:appPackage': process.env.APP_PACKAGE || 'com.plantlens.ai',
      'appium:appActivity': process.env.APP_ACTIVITY || 'com.plantlens.ai.activities.SplashActivity',
      'appium:noReset': false,
      'appium:fullReset': false,
      'appium:autoGrantPermissions': true,
      'appium:newCommandTimeout': 300,
      'appium:apmEnableSemantics': true,
      'appium:apmWaitForWidgetTimeout': 15000
    },
    // Standard UiAutomator2 Fallback Configuration
    uiAutomator2: {
      platformName: 'Android',
      'appium:automationName': 'UiAutomator2',
      'appium:deviceName': process.env.DEVICE_NAME || 'Android Emulator',
      'appium:platformVersion': process.env.PLATFORM_VERSION || '14.0',
      'appium:app': resolvedApkPath,
      'appium:appPackage': process.env.APP_PACKAGE || 'com.plantlens.ai',
      'appium:appActivity': process.env.APP_ACTIVITY || 'com.plantlens.ai.activities.SplashActivity',
      'appium:noReset': false,
      'appium:fullReset': false,
      'appium:autoGrantPermissions': true,
      'appium:newCommandTimeout': 300,
      'appium:uiautomator2ServerInstallTimeout': 60000,
      'appium:adbExecTimeout': 60000
    }
  }
};
