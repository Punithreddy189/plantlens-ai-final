import os
import io
import re
import json
import logging
from PIL import Image
import google.generativeai as genai
from pydantic import BaseModel, Field
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("ModelService")

# Configure Gemini API
gemini_api_key = os.getenv("GEMINI_API_KEY")
configured_model = os.getenv("GEMINI_MODEL", "gemini-3.5-flash-lite")

if gemini_api_key:
    genai.configure(api_key=gemini_api_key)
    logger.info("Google Gemini API configured successfully on backend.")
else:
    logger.error("GEMINI_API_KEY not found in backend environment variables.")

class PlantAnalysisResponse(BaseModel):
    success: bool = Field(description="True if identification and diagnosis succeeded, false if not.")
    is_plant: bool = Field(description="True if the image contains a botanical plant/leaf/crop, false if non-plant.")
    error_message: str = Field(default="", description="Error or rejection message if non-plant or failed.")
    plant_name: str = Field(description="Common name of the plant.")
    scientific_name: str = Field(description="Botanical Latin name of the plant.")
    confidence: float = Field(description="Confidence level between 0.0 and 1.0.")
    health_status: str = Field(description="State of health: 'Healthy', 'Diseased', or 'Stressed'.")
    disease: str = Field(description="Name of the detected disease, or 'None (Optimal Foliage)' if healthy.")
    severity: str = Field(default="Low", description="Severity level: 'Healthy', 'Low', 'Moderate', 'High', or 'Critical'.")
    description: str = Field(description="Morphology, symptoms, and pathology description.")
    treatment: str = Field(description="Actionable step-by-step organic and chemical remedies.")
    watering: str = Field(description="Watering frequency and advice.")
    sunlight: str = Field(description="Sunlight requirements.")
    fertilizer: str = Field(description="Fertilizer recommendation.")
    prevention: str = Field(description="Foliar disease prevention tips.")
    soil_type: str = Field(default="Loamy soil", description="Optimal soil type (e.g. Loamy, Sandy Loam).")
    soil_ph: str = Field(default="6.0 - 7.0", description="Ideal soil pH range.")
    soil_drainage: str = Field(default="Well-drained", description="Soil drainage requirement.")
    soil_recommendation: str = Field(default="Mix garden soil with compost and sand.", description="Soil mix preparation tips.")
    confidence_reason: str = Field(default="Clear foliar morphology and distinct vein pattern.", description="Reason for the diagnostic confidence score.")

def clean_mixed_language(text: str, language: str) -> str:
    """
    Post-processing filter to eliminate accidental leftover English keywords in multilingual responses.
    """
    if not text or not language or language.lower() in ["english", "en"]:
        return text
    lang_lower = language.lower()

    replacements = {}
    if "telugu" in lang_lower:
        replacements = {
            "soil": "మట్టి",
            "loam": "లోమి",
            "loamy": "లోమి",
            "water": "నీరు",
            "watering": "నీటిపారుదల",
            "sunlight": "సూర్యరశ్మి",
            "drainage": "నీటి పారుదల",
            "well-drained": "మంచి నీటి పారుదల",
            "well drained": "మంచి నీటి పారుదల",
            "fertilizer": "ఎరువులు",
            "organic": "సేంద్రీయ",
            "compost": "కంపోస్ట్",
            "sand": "ఇసుక",
            "leaves": "ఆకులు",
            "leaf": "ఆకు",
            "treatment": "చికిత్స",
            "treatments": "చికిత్సా విధానాలు",
            "disease": "వ్యాధి",
            "diseases": "వ్యాధులు",
            "symptoms": "లక్షణాలు",
            "symptom": "లక్షణం",
            "prevention": "నివారణ",
            "healthy": "ఆరోగ్యకరమైనది",
            "health": "ఆరోగ్యం",
            "cure": "నివారణోపాయం"
        }
    elif "hindi" in lang_lower:
        replacements = {
            "soil": "मिट्टी",
            "loam": "दोमट",
            "loamy": "दोमट",
            "water": "पानी",
            "watering": "सिंचाई",
            "sunlight": "धूप",
            "drainage": "जल निकासी",
            "well-drained": "अच्छी जल निकासी",
            "well drained": "अच्छी जल निकासी",
            "fertilizer": "उर्वरक",
            "organic": "जैविक",
            "compost": "खाद",
            "sand": "रेत",
            "leaves": "पत्तियां",
            "leaf": "पत्ता",
            "treatment": "उपचार",
            "treatments": "उपचार के तरीके",
            "disease": "रोग",
            "diseases": "बीमारियां",
            "symptoms": "लक्षण",
            "symptom": "लक्षण",
            "prevention": "रोकथाम",
            "healthy": "स्वस्थ",
            "health": "स्वास्थ्य",
            "cure": "इलाज"
        }
    elif "tamil" in lang_lower:
        replacements = {
            "soil": "மண்",
            "loam": "வண்டல்",
            "loamy": "வண்டல்",
            "water": "தண்ணீர்",
            "watering": "நீர்ப்பாசனம்",
            "sunlight": "சூரிய ஒளி",
            "drainage": "வடிகால்",
            "well-drained": "நல்ல வடிகால்",
            "well drained": "நல்ல வடிகால்",
            "fertilizer": "உரம்",
            "organic": "இயற்கை",
            "compost": "உரம்",
            "sand": "மணல்",
            "leaves": "இலைகள்",
            "leaf": "இலை",
            "treatment": "சிகிச்சை",
            "treatments": "சிகிச்சை முறைகள்",
            "disease": "நோய்",
            "diseases": "நோய்கள்",
            "symptoms": "அறிகுறிகள்",
            "symptom": "அறிகுறி",
            "prevention": "தடுப்பு",
            "healthy": "ஆரோக்கியமானது",
            "health": "ஆரோக்கியம்",
            "cure": "குணம்"
        }
    elif "kannada" in lang_lower:
        replacements = {
            "soil": "ಮಣ್ಣು",
            "loam": "ಗೋಡು",
            "loamy": "ಗೋಡು",
            "water": "ನೀರು",
            "watering": "ನೀರುಣಿಸುವಿಕೆ",
            "sunlight": "ಸೂರ್ಯನ ಬೆಳಕು",
            "drainage": "ಒಳಚರಂಡಿ",
            "well-drained": "ಉತ್ತಮ ಒಳಚರಂಡಿ",
            "well drained": "ಉತ್ತಮ ಒಳಚರಂಡಿ",
            "fertilizer": "ಗೊಬ್ಬರ",
            "organic": "ಸಾವಯವ",
            "compost": "ಕಾಂಪೋಸ್ಟ್",
            "sand": "ಮರಳು",
            "leaves": "ಎಲೆಗಳು",
            "leaf": "ಎಲೆ",
            "treatment": "ಚಿಕಿತ್ಸೆ",
            "treatments": "ಚಿಕಿತ್ಸಾ ಕ್ರಮಗಳು",
            "disease": "ರೋಗ",
            "diseases": "ರೋಗಗಳು",
            "symptoms": "ಲಕ್ಷಣಗಳು",
            "symptom": "ಲಕ್ಷಣ",
            "prevention": "ತಡೆಗಟ್ಟುವಿಕೆ",
            "healthy": "ಆರೋಗ್ಯಕರ",
            "health": "ಆರೋಗ್ಯ",
            "cure": "ಪರಿಹಾರ"
        }

    cleaned = text
    for en_word, local_word in replacements.items():
        pattern = re.compile(rf"\b{re.escape(en_word)}\b", re.IGNORECASE)
        cleaned = pattern.sub(local_word, cleaned)
    return cleaned

def parse_clean_json(response_text: str) -> dict:
    """
    Robust JSON parser with balanced index boundary slicing for LLM outputs.
    """
    text = response_text.strip()
    if text.startswith("```json"):
        text = text.replace("```json", "", 1).replace("```", "").strip()
    elif text.startswith("```"):
        text = text.replace("```", "").strip()

    try:
        return json.loads(text)
    except Exception:
        # Fallback: slice from first '{' to last '}'
        start = text.find("{")
        end = text.rfind("}") + 1
        if start != -1 and end > start:
            try:
                return json.loads(text[start:end])
            except Exception:
                pass
        raise ValueError(f"Could not parse valid JSON from AI response: {text[:100]}")

def normalize_disease(disease: str, is_healthy: bool) -> str:
    if not disease or str(disease).strip().lower() in ["unspecified", "undefined", "null", "none", "unknown"]:
        return "Healthy Foliage" if is_healthy else "Abiotic Foliar Stress / Leaf Blight"
    d_lower = disease.strip().lower()
    weak_terms = ["unknown", "unsure", "leaf issue", "problem", "damage", "general issue", "none", "n/a", "plant issue", "unclear issue", "unspecified", "undefined"]
    if any(d_lower == term or d_lower.startswith(term) for term in weak_terms):
        if is_healthy or "none" in d_lower:
            return "Healthy Foliage"
        return "Abiotic Foliar Stress / Leaf Blight"
    return disease

def normalize_treatment(treatment: str, is_healthy: bool) -> str:
    if is_healthy:
        return "• Maintain standard scheduled watering\n• Provide adequate indirect sunlight\n• Inspect foliage weekly for early signs of pests"
    
    if not treatment or len(treatment.strip()) < 25 or len(treatment.split("\n")) < 2:
        return (
            "• Step 1: Prune and safely dispose of infected or discolored foliage\n"
            "• Step 2: Apply organic cold-pressed neem oil or appropriate copper-based fungicide\n"
            "• Step 3: Improve air circulation, ensure well-drained soil, and avoid wetting leaves"
        )
    return treatment

DISEASE_PATTERNS = [
    "necrotic", "lesion", "lesions", "spots", "spot",
    "blight", "halo", "halos", "chlorosis", "chlorotic",
    "yellow ring", "yellowing", "brown patch", "brown patches", "brown spot",
    "fungal", "rot", "mold", "mildew", "pustule", "pustules",
    "wilting", "curling", "scab", "canker", "cercospora", "alternaria"
]

def is_succulent_or_cactus(data: dict) -> bool:
    """
    Checks if the plant is a cactus, succulent, or thick-leaved arid botanical species.
    """
    text = f"{data.get('plant_name', '')} {data.get('scientific_name', '')} {data.get('description', '')} {data.get('observations', '')}".lower()
    succulent_keywords = [
        "cactus", "cacti", "opuntia", "succulent", "bunny ear", "aloe", "sansevieria",
        "haworthia", "crassula", "mammillaria", "echinocactus", "cereus", "euphorbia", "cladode"
    ]
    return any(kw in text for kw in succulent_keywords)

def has_active_damage_words(text: str) -> bool:
    """
    Checks if active, non-negated disease tokens are present in text.
    """
    if not text:
        return False
    t_lower = text.lower()
    damage_tokens = [
        "necrotic", "blight", "pustule", "chlorotic halo", "yellow halo",
        "cercospora", "alternaria", "sunken lesion", "mushy", "black rot", "soft rot", "oozing", "canker", "rot"
    ]
    for token in damage_tokens:
        if token in t_lower:
            idx = t_lower.find(token)
            prefix = t_lower[max(0, idx - 25):idx]
            if any(neg in prefix for neg in ["no ", "without ", "not ", "free of ", "free from ", "absence of "]):
                continue
            return True
    return False

def is_explicitly_healthy(text: str) -> bool:
    """
    Checks if the combined text explicitly describes healthy, disease-free foliage.
    """
    if not text:
        return False
    healthy_phrases = [
        "no visible disease",
        "no spots",
        "no visible lesions",
        "no lesions",
        "healthy foliage",
        "clean leaf",
        "clean green",
        "no infection",
        "free from disease",
        "free of disease",
        "absence of disease",
        "no signs of disease",
        "vibrant green leaf without",
        "uniform green",
        "without visible disease",
        "healthy cactus",
        "firm green pad",
        "healthy succulent"
    ]
    t_lower = text.lower()
    has_healthy_phrase = any(p in t_lower for p in healthy_phrases)
    if not has_healthy_phrase:
        return False
    return not has_active_damage_words(text)

def detect_visual_disease_clues(data: dict) -> bool:
    """
    Combines description, confidence_reason, and observations to detect active visual pathology symptoms.
    Excludes natural botanical structures like cactus areoles, glochids, or variegation.
    """
    combined = (
        f"{data.get('description', '')} {data.get('confidence_reason', '')} {data.get('observations', '')}"
    ).lower()
    if not combined.strip():
        return False

    if is_explicitly_healthy(combined):
        return False

    # For cacti and succulents, require active, non-negated rot/necrosis rather than natural areole dots
    if is_succulent_or_cactus(data):
        return has_active_damage_words(combined)

    return any(p in combined for p in DISEASE_PATTERNS) and has_active_damage_words(combined)

def validate_and_fix_response(data: dict, language: str) -> dict:
    """
    Guarantees complete and robust pathology, treatment, soil, and confidence fields.
    Includes multi-signal visual symptom override while protecting natural botanical patterns on cacti/succulents.
    """
    if not data.get("is_plant", True):
        return data

    # Enforce calibrated confidence (eliminates 0% bug)
    try:
        conf_val = float(data.get("confidence") or 0.92)
        if conf_val > 1.0:
            conf_val = conf_val / 100.0
        elif conf_val < 0.30:
            conf_val = 0.92
        data["confidence"] = max(0.75, min(0.98, round(conf_val, 2)))
    except Exception:
        data["confidence"] = 0.92

    disease_val = (data.get("disease") or "").strip()
    health_val = (data.get("health_status") or "").strip().lower()
    is_healthy = health_val == "healthy" or disease_val.lower() in ["none", "none (healthy foliage)", "none (optimal foliage)", "healthy", "healthy foliage"]

    # Protect healthy cactus/succulent from false disease classification
    if is_succulent_or_cactus(data):
        combined = f"{data.get('description', '')} {data.get('confidence_reason', '')} {data.get('observations', '')}".lower()
        if not has_active_damage_words(combined):
            data["disease"] = "Healthy Foliage"
            data["severity"] = "None"
            data["health_status"] = "Healthy"
            data["treatment"] = (
                "• Provide 6+ hours of bright direct sunlight\n"
                "• Allow soil to dry completely between waterings\n"
                "• Use gritty, well-draining cactus soil mix"
            )
            data["soil_type"] = "Cactus and succulent gritty mix"
            data["soil_ph"] = "6.0 - 7.5"
            data["soil_drainage"] = "Fast-draining coarse gritty soil"
            data["soil_recommendation"] = "• 50% Perlite or coarse sand\n• 30% Potting soil\n• 20% Pumice or gravel"
            return data

    # 1. Base Normalization
    data["disease"] = normalize_disease(disease_val, is_healthy)

    # 2. BOTANICAL CONSISTENCY CHECK (Contradiction Override):
    # If observations explicitly state absence of damage (e.g. 'no dark spots', 'no lesions', 'vibrant green', 'no infection'),
    # override any contradictory AI disease classification back to Healthy Foliage.
    obs_all = f"{data.get('observations', '')} {data.get('description', '')} {data.get('confidence_reason', '')}".lower()
    no_damage_signals = [
        "no dark spots", "no spots", "no lesions", "no visible lesions", "no infection",
        "healthy foliage", "vibrant green", "no fungal", "no visible disease", "free of disease",
        "free from disease", "absence of disease", "no signs of disease", "absence of rot", "no rot",
        "clean leaf", "firm green pad", "no evidence of disease"
    ]
    has_explicit_healthy_cues = any(signal in obs_all for signal in no_damage_signals)
    has_active_disease = has_active_damage_words(obs_all)

    if has_explicit_healthy_cues and not has_active_disease:
        logger.info("BOTANICAL CONSISTENCY OVERRIDE: Symptoms describe absence of disease. Forcing Healthy Foliage.")
        data["disease"] = "Healthy Foliage"
        data["severity"] = "None"
        data["health_status"] = "Healthy"
        data["confidence_reason"] = "No pathological symptoms detected; natural morphology and tissue integrity confirmed."
        if is_succulent_or_cactus(data):
            data["treatment"] = (
                "• Provide 6+ hours of bright direct sunlight\n"
                "• Allow soil to dry completely between waterings\n"
                "• Use gritty, well-draining cactus soil mix"
            )
            data["soil_type"] = "Cactus and succulent gritty mix"
            data["soil_ph"] = "6.0 - 7.5"
            data["soil_drainage"] = "Fast-draining coarse gritty soil"
            data["soil_recommendation"] = "• 50% Perlite or coarse sand\n• 30% Potting soil\n• 20% Pumice or gravel"
        else:
            data["treatment"] = (
                "• Maintain standard scheduled watering\n"
                "• Provide adequate indirect sunlight\n"
                "• Inspect foliage weekly for early signs of pests"
            )
        return data

    # 3. FINAL DEFENSE & HARD OVERRIDE RULE:
    combined_text = f"{data.get('description', '')} {data.get('confidence_reason', '')} {data.get('observations', '')}"
    has_visual_symptoms = detect_visual_disease_clues(data)
    is_safe_healthy = is_explicitly_healthy(combined_text)

    if has_visual_symptoms and not is_safe_healthy and (
        data["disease"].lower() in ["healthy foliage", "none", "no plant detected", "healthy", "none (optimal)"]
        or data.get("severity") in ["None", "none", "Healthy", "Optimal", "0"]
        or is_healthy
    ):
        logger.warning("HARD OVERRIDE TRIGGERED: Symptoms detected in visual cues while disease was marked healthy. Correcting to Cercospora Leaf Spot / Early Blight.")
        t_low = combined_text.lower()
        if "rust" in t_low or "pustule" in t_low:
            data["disease"] = "Foliar Rust Disease"
        elif "mildew" in t_low or "mold" in t_low:
            data["disease"] = "Powdery / Downy Mildew"
        elif "blight" in t_low:
            data["disease"] = "Foliar Blight Disease"
        else:
            data["disease"] = "Cercospora Leaf Spot / Early Blight"

        data["health_status"] = "Diseased"
        data["severity"] = "Moderate"
        data["treatment"] = (
            "• Remove infected leaves immediately\n"
            "• Spray neem oil or copper fungicide every 5–7 days\n"
            "• Avoid overhead watering\n"
            "• Improve airflow around plant"
        )
    elif data["disease"] == "Healthy Foliage":
        data["severity"] = "None"
        data["health_status"] = "Healthy"
    elif is_healthy:
        data["severity"] = "None"
        data["health_status"] = "Healthy"
    else:
        if not data.get("health_status") or data["health_status"] == "Healthy":
            data["health_status"] = "Diseased"
        if not data.get("health_status") or data["health_status"] == "Healthy":
            data["health_status"] = "Diseased"

    # 3. Standardize severity enum
    sev_raw = (data.get("severity") or "Low").strip().lower()
    if sev_raw in ["none", "healthy", "optimal", "0"]:
        data["severity"] = "None"
    elif "crit" in sev_raw:
        data["severity"] = "Critical"
    elif "high" in sev_raw or "severe" in sev_raw:
        data["severity"] = "High"
    elif "mod" in sev_raw or "med" in sev_raw:
        data["severity"] = "Moderate"
    else:
        data["severity"] = "Low"

    # 4. Enforce multi-line actionable treatment (minimum 3 steps)
    treatment_val = data.get("treatment")
    if isinstance(treatment_val, list):
        treatment_val = "\n".join(f"• {item}" if not item.startswith("•") else item for item in treatment_val)
    data["treatment"] = normalize_treatment(str(treatment_val or ""), data["severity"] == "None")

    # 4. Enforce prevention
    prev_val = data.get("prevention")
    if isinstance(prev_val, list):
        data["prevention"] = "\n".join(f"• {item}" if not item.startswith("•") else item for item in prev_val)
    elif not prev_val or len(str(prev_val).strip()) < 15:
        data["prevention"] = "• Inspect leaves regularly for spots\n• Water at root base to keep foliage dry\n• Ensure adequate plant spacing for airflow"

    # 5. Enforce soil requirements
    if not data.get("soil_type") or data["soil_type"] == "N/A":
        data["soil_type"] = "Loamy soil"
    if not data.get("soil_ph") or data["soil_ph"] == "N/A":
        data["soil_ph"] = "6.0 - 6.8"
    if not data.get("soil_drainage") or data["soil_drainage"] == "N/A":
        data["soil_drainage"] = "Well-drained with good aeration"
    
    soil_rec = data.get("soil_recommendation")
    if isinstance(soil_rec, list):
        data["soil_recommendation"] = "\n".join(f"• {item}" if not item.startswith("•") else item for item in soil_rec)
    elif not soil_rec or len(str(soil_rec).strip()) < 15 or soil_rec == "N/A":
        data["soil_recommendation"] = "• 50% Garden soil\n• 30% Organic compost\n• 20% Sand or perlite for drainage"

    # 6. Enforce calibrated confidence (eliminates 0% bug)
    try:
        conf_val = float(data.get("confidence") or 0.92)
        if conf_val > 1.0:
            conf_val = conf_val / 100.0
        data["confidence"] = max(0.75, min(0.98, round(conf_val, 2)))
    except Exception:
        data["confidence"] = 0.92

    # 7. Enforce confidence reason
    if not data.get("confidence_reason") or len(str(data["confidence_reason"]).strip()) < 10:
        data["confidence_reason"] = "Clear foliar morphology, distinct venation pattern, and observable leaf texture."

    return data

def clean_full_response(data: dict, language: str) -> dict:
    """
    Applies post-processing multilingual cleaning across all string fields.
    """
    if not language or language.lower() in ["english", "en"]:
        return data

    for key, value in list(data.items()):
        if key == "scientific_name":
            continue
        if isinstance(value, str):
            data[key] = clean_mixed_language(value, language)

    return data

async def analyze_plant_with_gemini(
    image_bytes: bytes,
    plant_name: str = None,
    scientific_name: str = None,
    confidence: float = 0.0,
    language: str = "English"
) -> dict:
    """
    Executes Gemini Vision AI inference on the backend to detect plant species and diagnose diseases in the requested language.
    """
    if not gemini_api_key:
        logger.error("Gemini API Key is missing on backend. Returning failure response.")
        return {
            "success": False,
            "is_plant": False,
            "error_message": "Backend server configuration error: GEMINI_API_KEY is not set.",
            "plant_name": plant_name or "Unknown",
            "scientific_name": scientific_name or "Unknown",
            "confidence": confidence,
            "health_status": "Unknown",
            "disease": "Error: Gemini API key missing in backend configuration.",
            "severity": "N/A",
            "description": "Please check backend .env file.",
            "treatment": "Configure GEMINI_API_KEY in backend/.env",
            "watering": "N/A",
            "sunlight": "N/A",
            "fertilizer": "N/A",
            "prevention": "N/A",
            "soil_type": "N/A",
            "soil_ph": "N/A",
            "soil_drainage": "N/A",
            "soil_recommendation": "N/A"
        }

    try:
        # Load image bytes into PIL Image
        img = Image.open(io.BytesIO(image_bytes))
        
        # Formulate thorough prompt with mixed-image handling, multilingual directive, and pathology rules
        context_str = ""
        prompt = f"""
You are an expert plant pathologist AI.

TASK:
Analyze the plant leaf image and return COMPLETE diagnosis.
{context_str}
CRITICAL RULES (MUST FOLLOW STRICTLY):
1. You MUST ALWAYS return ALL fields populated.
2. You MUST NOT stop at plant identification.
3. You MUST analyze leaf condition for disease.

DISEASE DETECTION (VERY IMPORTANT):
- CAREFULLY distinguish between natural botanical structures (such as cactus areoles, glochids, white/yellow trichomes, natural spine dots, variegation) vs genuine fungal/bacterial lesions.
- Symmetrical, evenly distributed dots/areoles on cacti (e.g. Opuntia / Bunny Ears cactus) and succulents are NATURAL BOTANICAL MORPHOLOGY, NOT A DISEASE!
- Firm green cactus pads with standard areoles MUST be diagnosed as "Healthy Foliage", severity "None".
- If and only if you see genuine, irregular foliar damage:
  • dark necrotic spots with yellow chlorotic halos → "Cercospora Leaf Spot / Early Blight"
  • powdery coating → "Powdery Mildew"
  • rust pustules → "Foliar Rust Disease"
  • burned/dark water-soaked edges → "Leaf Blight"
  • soft black rot or mushy cactus tissue → "Cactus Stem / Root Rot"

HEALTHY CONDITION:
- Return "Healthy Foliage" if leaf/pad is free of necrotic infection, rot, or lesions
- Natural patterns (cactus areoles, variegated stripes) are 100% HEALTHY

OBJECT VALIDATION:
- If this image does NOT contain any plant, leaf, crop, or flower (e.g. human face, room, animal, vehicle, electronic screen), return:
  "success": false, "is_plant": false, "error_message": "No plant detected. Please aim camera at a plant leaf.",
  "plant_name": "Not a plant", "scientific_name": "Non-Botanical Subject", "confidence": 0.0, "health_status": "Unknown",
  "disease": "No Plant Detected", "severity": "None", "confidence_reason": "Non-botanical subject.", "description": "Non-plant subject.", "treatment": "Please scan a plant leaf.",
  "watering": "N/A", "sunlight": "N/A", "fertilizer": "N/A", "prevention": "N/A", "soil_type": "N/A", "soil_ph": "N/A", "soil_drainage": "N/A", "soil_recommendation": "N/A"

TREATMENT RULES:
- ALWAYS give minimum 3 steps:
  1. Remove infected leaves
  2. Apply neem oil or fungicide
  3. Improve watering & airflow

LANGUAGE RULES:
- Output MUST be in {language}
- DO NOT mix English (except Latin scientific_name)

OUTPUT FORMAT (STRICT JSON ONLY, NO CODE FENCES):
{{
  "success": true,
  "is_plant": true,
  "error_message": "",
  "plant_name": "Common Plant Name",
  "scientific_name": "Latin Botanical Name",
  "disease": "Specific Disease Name or Healthy Foliage",
  "health_status": "Healthy or Diseased",
  "severity": "None | Low | Moderate | High | Critical",
  "confidence": 0.95,
  "observations": "• Detailed bullet symptom 1\\n• Detailed bullet symptom 2",
  "description": "• Detailed bullet symptom 1\\n• Detailed bullet symptom 2",
  "confidence_reason": "Rationale based on leaf morphology and pathology.",
  "treatment": "• Step 1: Remove infected leaves\\n• Step 2: Apply neem oil or fungicide\\n• Step 3: Improve watering & airflow",
  "watering": "Watering requirements",
  "sunlight": "Sunlight requirements",
  "fertilizer": "Fertilizer recommendations",
  "prevention": "• Prevention tip 1\\n• Prevention tip 2",
  "soil_type": "Loamy soil",
  "soil_ph": "6.0 - 6.8",
  "soil_drainage": "Well-drained",
  "soil_recommendation": "• 50% Garden soil\\n• 30% Compost\\n• 20% Sand"
}}

FINAL INSTRUCTION:
Even if unsure → NEVER return empty or generic answer.
Always give best possible disease + treatment.
"""

        models_to_try = [
            configured_model,
            "gemini-3.5-flash-lite",
            "gemini-flash-lite-latest",
            "gemini-3.6-flash",
            "gemini-3.7-flash",
            "gemini-3.5-flash",
            "gemini-flash-latest"
        ]
        
        # Deduplicate while preserving order
        unique_models = []
        for m in models_to_try:
            if m and m not in unique_models:
                unique_models.append(m)

        last_error = None
        for model_id in unique_models:
            try:
                logger.info(f"Attempting Gemini inference using model '{model_id}'...")
                model = genai.GenerativeModel(model_name=model_id)
                response = model.generate_content(
                    [img, prompt],
                    generation_config=genai.GenerationConfig(temperature=0.2)
                )
                
                data = parse_clean_json(response.text)

                # Preserve PlantNet botanical names & calibrate confidence score
                if plant_name and scientific_name and data.get("is_plant", True):
                    if not data.get("plant_name") or data.get("plant_name") == "Common Plant Name":
                        data["plant_name"] = plant_name
                    if not data.get("scientific_name") or data.get("scientific_name") == "Latin Botanical Name":
                        data["scientific_name"] = scientific_name
                    
                    gemini_conf = float(data.get("confidence", 0.90))
                    if confidence > 0.10:
                        calibrated_conf = round((confidence * 0.45) + (gemini_conf * 0.55), 2)
                        data["confidence"] = max(0.30, min(0.98, calibrated_conf))

                # Comprehensive Fallback Normalization & Output Validation Layer
                data = validate_and_fix_response(data, language)

                # Post-processing Language Cleaner for Multilingual Consistency
                data = clean_full_response(data, language)

                logger.info(f"Gemini analysis successful with model '{model_id}': is_plant={data.get('is_plant')}, disease={data.get('disease')}, severity={data.get('severity')}")
                return data
            except Exception as ex:
                logger.warning(f"Model '{model_id}' failed: {str(ex)}")
                last_error = ex
                continue

        raise last_error or Exception("All Gemini model endpoints failed.")

    except Exception as e:
        logger.exception(f"Error during Gemini analysis: {str(e)}")
        return {
            "success": False,
            "is_plant": False,
            "error_message": f"AI analysis failed: {str(e)}",
            "plant_name": plant_name or "Unknown",
            "scientific_name": scientific_name or "Unknown",
            "confidence": confidence,
            "health_status": "Unknown",
            "disease": f"Diagnosis failed: {str(e)}",
            "severity": "N/A",
            "description": "An error occurred while communicating with the Gemini AI service.",
            "treatment": "Please verify server network connection and API key quotas.",
            "watering": "N/A",
            "sunlight": "N/A",
            "fertilizer": "N/A",
            "prevention": "N/A"
        }
