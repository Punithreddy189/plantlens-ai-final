/**
 * @typedef {Object} DiagnosisResult
 * @property {string} plant_name
 * @property {string} scientific_name
 * @property {boolean} is_diseased
 * @property {string} disease_name
 * @property {number} health_score
 * @property {number} confidence
 * @property {string[]} symptoms
 * @property {string[]} organic_remedies
 * @property {string[]} chemical_treatments
 * @property {string} model_tier_used
 * @property {boolean} escalation_triggered
 */

/**
 * Scan plant leaf via the unified FastAPI diagnostic gateway.
 * @param {File | Blob} imageFile
 * @returns {Promise<DiagnosisResult>}
 */
export async function scanPlantLeaf(imageFile) {
  const formData = new FormData();
  formData.append("file", imageFile, "leaf_scan.jpg");

  const response = await fetch("http://localhost:8000/api/v1/diagnose", {
    method: "POST",
    body: formData,
  });

  if (!response.ok) {
    const errorDetail = await response.text();
    throw new Error(`Diagnostic failed (${response.status}): ${errorDetail}`);
  }

  return response.json();
}
