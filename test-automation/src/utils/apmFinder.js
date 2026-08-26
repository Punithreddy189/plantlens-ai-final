/**
 * APM Widget Finder APIs for PlantLens AI
 * Supports: find.byValueKey, find.byText, find.bySemanticsLabel, find.byAccessibilityId, find.byId
 */
class APMFinder {
  constructor(driver) {
    this.driver = driver;
  }

  setDriver(driver) {
    this.driver = driver;
  }

  /**
   * Locate widget by ValueKey
   * @param {string} key
   */
  byValueKey(key) {
    return {
      type: 'valueKey',
      key,
      getSelector: () => `//*[@content-desc='${key}' or @resource-id='${key}' or @name='${key}']`,
      getElement: async (driver = this.driver) => {
        if (!driver) throw new Error('Driver not initialized in APMFinder');
        return driver.$(`//*[@content-desc='${key}' or @resource-id='${key}' or @name='${key}']`);
      }
    };
  }

  /**
   * Locate widget by exact or partial text
   * @param {string} text
   * @param {boolean} exact
   */
  byText(text, exact = true) {
    const xpath = exact 
      ? `//*[@text='${text}' or @content-desc='${text}']`
      : `//*[contains(@text, '${text}') or contains(@content-desc, '${text}')]`;
    return {
      type: 'text',
      text,
      getSelector: () => xpath,
      getElement: async (driver = this.driver) => {
        if (!driver) throw new Error('Driver not initialized in APMFinder');
        return driver.$(xpath);
      }
    };
  }

  /**
   * Locate widget by Semantics Label (Accessibility Description)
   * @param {string} label
   */
  bySemanticsLabel(label) {
    return {
      type: 'semanticsLabel',
      label,
      getSelector: () => `~${label}`,
      getElement: async (driver = this.driver) => {
        if (!driver) throw new Error('Driver not initialized in APMFinder');
        return driver.$(`~${label}`);
      }
    };
  }

  /**
   * Locate widget by Accessibility ID
   * @param {string} id
   */
  byAccessibilityId(id) {
    return {
      type: 'accessibilityId',
      id,
      getSelector: () => `~${id}`,
      getElement: async (driver = this.driver) => {
        if (!driver) throw new Error('Driver not initialized in APMFinder');
        return driver.$(`~${id}`);
      }
    };
  }

  /**
   * Locate Android Resource ID
   * @param {string} resId - e.g. "com.plantlens.ai:id/btn_scan" or "btn_scan"
   */
  byId(resId) {
    const fullId = resId.includes(':id/') ? resId : `com.plantlens.ai:id/${resId}`;
    return {
      type: 'id',
      id: fullId,
      getSelector: () => `id=${fullId}`,
      getElement: async (driver = this.driver) => {
        if (!driver) throw new Error('Driver not initialized in APMFinder');
        return driver.$(`id=${fullId}`);
      }
    };
  }

  /**
   * Locate widget by Android/APM Widget Class Name
   * @param {string} widgetType - e.g. "android.widget.Button", "android.widget.EditText"
   */
  byType(widgetType) {
    return {
      type: 'class',
      widgetType,
      getSelector: () => `//${widgetType}`,
      getElement: async (driver = this.driver) => {
        if (!driver) throw new Error('Driver not initialized in APMFinder');
        return driver.$(`//${widgetType}`);
      }
    };
  }
}

const find = new APMFinder();
module.exports = { APMFinder, find };
