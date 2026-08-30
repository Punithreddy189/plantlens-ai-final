// Vercel / Netlify Serverless API Function - Secure Gemini AI Proxy
// This file runs strictly on the SERVER to keep your GEMINI_API_KEY 100% private!

export default async function handler(req, res) {
  // CORS Headers
  res.setHeader('Access-Control-Allow-Credentials', true);
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET,OPTIONS,PATCH,DELETE,POST,PUT');
  res.setHeader(
    'Access-Control-Allow-Headers',
    'X-CSRF-Token, X-Requested-With, Accept, Accept-Version, Content-Length, Content-MD5, Content-Type, Date, X-Api-Version'
  );

  if (req.method === 'OPTIONS') {
    res.status(200).end();
    return;
  }

  if (req.method !== 'POST') {
    return res.status(405).json({ error: 'Method not allowed. Use POST.' });
  }

  const apiKey = process.env.GEMINI_API_KEY || process.env.VITE_GEMINI_API_KEY;
  if (!apiKey) {
    return res.status(500).json({ error: 'Server configuration error: GEMINI_API_KEY environment variable is missing.' });
  }

  try {
    const { imageBase64, mimeType } = req.body;
    if (!imageBase64) {
      return res.status(400).json({ error: 'Missing imageBase64 in request body.' });
    }

    const prompt = `Analyze this plant image for botanical identification and plant disease detection. Return ONLY valid JSON.
Do not include explanation, markdown, or extra text.
If unsure, still return best-guess values in valid JSON format.
Follow this exact JSON structure:
{
  "name": "Common plant name",
  "scientificName": "Latin botanical name",
  "family": "Botanical family name",
  "confidence": 95,
  "healthStatus": "healthy",
  "healthScore": 95,
  "diseaseName": "None (Healthy Plant)",
  "severity": "Healthy",
  "description": "Detailed diagnosis summary",
  "symptoms": ["Symptom 1", "Symptom 2"],
  "causes": ["Cause 1", "Cause 2"],
  "organicRemedies": ["Remedy 1"],
  "chemicalTreatments": ["Treatment 1"],
  "waterSchedule": "Watering frequency",
  "sunlightNeeds": "Sunlight requirement",
  "tempRange": "20°C - 30°C",
  "humidity": "50% - 70%",
  "fertilizer": "Fertilizer recommendation",
  "harvestTime": "Harvest period"
}`;

    const modelsToTry = [
      'gemini-3.5-flash-lite',
      'gemini-3.1-flash-lite',
      'gemini-3.6-flash',
      'gemini-3.5-flash',
      'gemini-flash-latest'
    ];
    let lastError = null;

    for (const model of modelsToTry) {
      for (let attempt = 1; attempt <= 2; attempt++) {
        try {
          const geminiRes = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${apiKey}`, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json'
            },
            signal: AbortSignal.timeout(30000),
            body: JSON.stringify({
              contents: [
                {
                  parts: [
                    { text: prompt },
                    {
                      inline_data: {
                        mime_type: mimeType || 'image/jpeg',
                        data: imageBase64
                      }
                    }
                  ]
                }
              ]
            })
          });

          if (geminiRes.status === 429) {
            console.warn(`[Gemini API 429 Rate Limit] Model: ${model}, attempt ${attempt}/2`);
            lastError = 'Rate limit exceeded (429). Please wait a moment and retry.';
            if (attempt < 2) {
              await new Promise((r) => setTimeout(r, 1000));
              continue;
            }
            break;
          }

          if (geminiRes.ok) {
            const data = await geminiRes.json();
            const rawText = data.candidates?.[0]?.content?.parts?.[0]?.text || '';
            
            let parsed;
            try {
              const jsonMatch = rawText.match(/\{[\s\S]*\}/);
              const cleanJSON = jsonMatch ? jsonMatch[0] : rawText.replace(/```json/g, '').replace(/```/g, '').trim();
              parsed = JSON.parse(cleanJSON);
            } catch (jsonErr) {
              console.error('JSON parse error from Gemini output:', rawText);
              if (attempt < 2) {
                await new Promise((r) => setTimeout(r, 800));
                continue;
              }
              return res.status(500).json({ error: 'Invalid JSON returned by Gemini AI.', raw: rawText });
            }

            // Schema normalization & validation
            const validated = {
              name: (typeof parsed.name === 'string' && parsed.name.trim()) ? parsed.name.trim() : 'Identified Plant',
              scientificName: (typeof parsed.scientificName === 'string' && parsed.scientificName.trim()) ? parsed.scientificName.trim() : 'Botanical Species',
              family: typeof parsed.family === 'string' ? parsed.family : 'Botanical Family',
              confidence: typeof parsed.confidence === 'number' ? Math.min(100, Math.max(10, Math.round(parsed.confidence))) : (Number(parsed.confidence) || 92),
              healthStatus: (parsed.healthStatus && String(parsed.healthStatus).toLowerCase().includes('disease')) ? 'diseased' : 'healthy',
              healthScore: typeof parsed.healthScore === 'number' ? Math.min(100, Math.max(0, Math.round(parsed.healthScore))) : (Number(parsed.healthScore) || 90),
              diseaseName: typeof parsed.diseaseName === 'string' ? parsed.diseaseName : 'None (Healthy Plant)',
              severity: typeof parsed.severity === 'string' ? parsed.severity : 'Healthy',
              description: typeof parsed.description === 'string' ? parsed.description : 'Plant diagnosis generated by Gemini Multimodal Vision AI.',
              symptoms: Array.isArray(parsed.symptoms) ? parsed.symptoms : [parsed.symptoms || 'Characteristic foliage and leaf structure'],
              causes: Array.isArray(parsed.causes) ? parsed.causes : [parsed.causes || 'Ambient lighting and soil moisture balance'],
              organicRemedies: Array.isArray(parsed.organicRemedies) ? parsed.organicRemedies : [parsed.organicRemedies || 'Maintain routine care and balanced watering'],
              chemicalTreatments: Array.isArray(parsed.chemicalTreatments) ? parsed.chemicalTreatments : [parsed.chemicalTreatments || 'None required for healthy specimen'],
              waterSchedule: typeof parsed.waterSchedule === 'string' ? parsed.waterSchedule : 'Water when topsoil is dry to the touch',
              sunlightNeeds: typeof parsed.sunlightNeeds === 'string' ? parsed.sunlightNeeds : 'Bright indirect sunlight',
              tempRange: typeof parsed.tempRange === 'string' ? parsed.tempRange : '18°C - 28°C',
              humidity: typeof parsed.humidity === 'string' ? parsed.humidity : '50% - 70%',
              fertilizer: typeof parsed.fertilizer === 'string' ? parsed.fertilizer : 'Diluted balanced organic liquid fertilizer monthly',
              harvestTime: typeof parsed.harvestTime === 'string' ? parsed.harvestTime : 'Perennial growth cycle'
            };

            return res.status(200).json(validated);
          } else {
            lastError = await geminiRes.text();
            console.error(`Gemini API error (${model}, attempt ${attempt}/2):`, lastError);
            if (attempt < 2) {
              await new Promise((r) => setTimeout(r, 1000));
            }
          }
        } catch (e) {
          lastError = e.message;
          console.error(`Gemini exception (${model}, attempt ${attempt}/2):`, e);
          if (attempt < 2) {
            await new Promise((r) => setTimeout(r, 1000));
          }
        }
      }
    }

    return res.status(500).json({ error: `Gemini API call failed across models: ${lastError}` });
  } catch (err) {
    return res.status(500).json({ error: err.message });
  }
}
