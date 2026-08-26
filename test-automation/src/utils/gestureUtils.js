/**
 * Gesture Testing Utilities for Appium 2.x & APM
 * Implements: Tap, Double Tap, Long Press, Scroll, Swipe, Drag and Drop, Pinch, Zoom
 */
const logger = require('../config/logger');

class GestureUtils {
  constructor(driver) {
    this.driver = driver;
  }

  setDriver(driver) {
    this.driver = driver;
  }

  /**
   * Tap on element or coordinates
   */
  async tap(elementOrCoords) {
    logger.debug('👆 Executing Tap Gesture');
    if (this.driver.isMock) return true;

    if (elementOrCoords.click) {
      await elementOrCoords.click();
    } else {
      const { x, y } = elementOrCoords;
      await this.driver.performActions([{
        type: 'pointer',
        id: 'finger1',
        parameters: { pointerType: 'touch' },
        actions: [
          { type: 'pointerMove', duration: 0, x, y },
          { type: 'pointerDown', button: 0 },
          { type: 'pause', duration: 100 },
          { type: 'pointerUp', button: 0 }
        ]
      }]);
      await this.driver.releaseActions();
    }
  }

  /**
   * Double Tap on element or coordinates
   */
  async doubleTap(elementOrCoords) {
    logger.debug('👆👆 Executing Double Tap Gesture');
    if (this.driver.isMock) return true;

    let x = 500, y = 500;
    if (elementOrCoords.getLocation && elementOrCoords.getSize) {
      const loc = await elementOrCoords.getLocation();
      const size = await elementOrCoords.getSize();
      x = Math.round(loc.x + size.width / 2);
      y = Math.round(loc.y + size.height / 2);
    } else if (elementOrCoords.x !== undefined) {
      x = elementOrCoords.x;
      y = elementOrCoords.y;
    }

    await this.driver.performActions([{
      type: 'pointer',
      id: 'finger1',
      parameters: { pointerType: 'touch' },
      actions: [
        { type: 'pointerMove', duration: 0, x, y },
        { type: 'pointerDown', button: 0 },
        { type: 'pause', duration: 50 },
        { type: 'pointerUp', button: 0 },
        { type: 'pause', duration: 80 },
        { type: 'pointerDown', button: 0 },
        { type: 'pause', duration: 50 },
        { type: 'pointerUp', button: 0 }
      ]
    }]);
    await this.driver.releaseActions();
  }

  /**
   * Long Press on element or coordinates
   */
  async longPress(elementOrCoords, durationMs = 1500) {
    logger.debug(`⏱️ Executing Long Press Gesture (${durationMs}ms)`);
    if (this.driver.isMock) return true;

    let x = 500, y = 500;
    if (elementOrCoords.getLocation && elementOrCoords.getSize) {
      const loc = await elementOrCoords.getLocation();
      const size = await elementOrCoords.getSize();
      x = Math.round(loc.x + size.width / 2);
      y = Math.round(loc.y + size.height / 2);
    } else if (elementOrCoords.x !== undefined) {
      x = elementOrCoords.x;
      y = elementOrCoords.y;
    }

    await this.driver.performActions([{
      type: 'pointer',
      id: 'finger1',
      parameters: { pointerType: 'touch' },
      actions: [
        { type: 'pointerMove', duration: 0, x, y },
        { type: 'pointerDown', button: 0 },
        { type: 'pause', duration: durationMs },
        { type: 'pointerUp', button: 0 }
      ]
    }]);
    await this.driver.releaseActions();
  }

  /**
   * Swipe across coordinates or direction
   */
  async swipe(startX, startY, endX, endY, duration = 600) {
    logger.debug(`👉 Executing Swipe from (${startX}, ${startY}) to (${endX}, ${endY})`);
    if (this.driver.isMock) return true;

    await this.driver.performActions([{
      type: 'pointer',
      id: 'finger1',
      parameters: { pointerType: 'touch' },
      actions: [
        { type: 'pointerMove', duration: 0, x: startX, y: startY },
        { type: 'pointerDown', button: 0 },
        { type: 'pause', duration: 100 },
        { type: 'pointerMove', duration, x: endX, y: endY },
        { type: 'pointerUp', button: 0 }
      ]
    }]);
    await this.driver.releaseActions();
  }

  /**
   * Scroll down or up
   */
  async scroll(direction = 'down', distanceRatio = 0.5) {
    logger.debug(`📜 Executing Scroll ${direction.toUpperCase()}`);
    if (this.driver.isMock) return true;

    const startX = 540;
    const startY = direction === 'down' ? 1400 : 600;
    const endY = direction === 'down' ? Math.round(1400 - 800 * distanceRatio) : Math.round(600 + 800 * distanceRatio);

    await this.swipe(startX, startY, startX, endY, 700);
  }

  /**
   * Drag and drop from source to target element
   */
  async dragAndDrop(sourceElement, targetElement) {
    logger.debug('📦 Executing Drag and Drop');
    if (this.driver.isMock) return true;

    const srcLoc = await sourceElement.getLocation();
    const srcSize = await sourceElement.getSize();
    const tgtLoc = await targetElement.getLocation();
    const tgtSize = await targetElement.getSize();

    const startX = Math.round(srcLoc.x + srcSize.width / 2);
    const startY = Math.round(srcLoc.y + srcSize.height / 2);
    const endX = Math.round(tgtLoc.x + tgtSize.width / 2);
    const endY = Math.round(tgtLoc.y + tgtSize.height / 2);

    await this.driver.performActions([{
      type: 'pointer',
      id: 'finger1',
      parameters: { pointerType: 'touch' },
      actions: [
        { type: 'pointerMove', duration: 0, x: startX, y: startY },
        { type: 'pointerDown', button: 0 },
        { type: 'pause', duration: 1000 },
        { type: 'pointerMove', duration: 1000, x: endX, y: endY },
        { type: 'pause', duration: 200 },
        { type: 'pointerUp', button: 0 }
      ]
    }]);
    await this.driver.releaseActions();
  }

  /**
   * Pinch gesture (Zoom Out)
   */
  async pinch(centerX = 540, centerY = 960, distance = 300) {
    logger.debug('🤏 Executing Pinch (Zoom Out) Gesture');
    if (this.driver.isMock) return true;

    await this.driver.performActions([
      {
        type: 'pointer',
        id: 'finger1',
        parameters: { pointerType: 'touch' },
        actions: [
          { type: 'pointerMove', duration: 0, x: centerX - distance, y: centerY },
          { type: 'pointerDown', button: 0 },
          { type: 'pointerMove', duration: 600, x: centerX - 50, y: centerY },
          { type: 'pointerUp', button: 0 }
        ]
      },
      {
        type: 'pointer',
        id: 'finger2',
        parameters: { pointerType: 'touch' },
        actions: [
          { type: 'pointerMove', duration: 0, x: centerX + distance, y: centerY },
          { type: 'pointerDown', button: 0 },
          { type: 'pointerMove', duration: 600, x: centerX + 50, y: centerY },
          { type: 'pointerUp', button: 0 }
        ]
      }
    ]);
    await this.driver.releaseActions();
  }

  /**
   * Zoom gesture (Pinch Open / Zoom In)
   */
  async zoom(centerX = 540, centerY = 960, distance = 300) {
    logger.debug('🔍 Executing Zoom (Zoom In) Gesture');
    if (this.driver.isMock) return true;

    await this.driver.performActions([
      {
        type: 'pointer',
        id: 'finger1',
        parameters: { pointerType: 'touch' },
        actions: [
          { type: 'pointerMove', duration: 0, x: centerX - 50, y: centerY },
          { type: 'pointerDown', button: 0 },
          { type: 'pointerMove', duration: 600, x: centerX - distance, y: centerY },
          { type: 'pointerUp', button: 0 }
        ]
      },
      {
        type: 'pointer',
        id: 'finger2',
        parameters: { pointerType: 'touch' },
        actions: [
          { type: 'pointerMove', duration: 0, x: centerX + 50, y: centerY },
          { type: 'pointerDown', button: 0 },
          { type: 'pointerMove', duration: 600, x: centerX + distance, y: centerY },
          { type: 'pointerUp', button: 0 }
        ]
      }
    ]);
    await this.driver.releaseActions();
  }
}

module.exports = GestureUtils;
