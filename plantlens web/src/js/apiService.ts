export interface DiagnosisResult {
  plant_name: string;
  scientific_name: string;
  is_diseased: boolean;
  disease_name: string;
  health_score: number;
  confidence: number;
  symptoms: string[];
  organic_remedies: string[];
  chemical_treatments: string[];
  model_tier_used: string;
  escalation_triggered: boolean;
}

export async function scanPlantLeaf(imageFile: File | Blob): Promise<DiagnosisResult> {
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
