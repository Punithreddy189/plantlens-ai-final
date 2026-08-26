/**
 * Plant Result & Disease Diagnosis Page Object Model
 */
const BasePage = require('./BasePage');

class PlantResultPage extends BasePage {
  get plantCommonName() { return this.find.byId('com.plantlens.ai:id/tv_plant_name'); }
  get plantScientificName() { return this.find.byId('com.plantlens.ai:id/tv_scientific_name'); }
  get confidenceScore() { return this.find.byId('com.plantlens.ai:id/tv_confidence_score'); }
  get healthScoreBadge() { return this.find.byId('com.plantlens.ai:id/badge_health_score'); }
  get diseaseName() { return this.find.byId('com.plantlens.ai:id/tv_disease_title'); }
  get treatmentText() { return this.find.byId('com.plantlens.ai:id/tv_treatment_details'); }
  get saveToGardenButton() { return this.find.byId('com.plantlens.ai:id/btn_save_garden'); }
  get shareButton() { return this.find.byId('com.plantlens.ai:id/btn_share_result'); }

  async getPlantDiagnosis() {
    return {
      name: await this.getText(this.plantCommonName),
      scientificName: await this.getText(this.plantScientificName),
      confidence: await this.getText(this.confidenceScore),
      healthScore: await this.getText(this.healthScoreBadge),
      disease: await this.getText(this.diseaseName)
    };
  }

  async saveToGarden() {
    await this.click(this.saveToGardenButton);
  }
}

module.exports = PlantResultPage;
