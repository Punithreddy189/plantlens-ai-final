import hashlib
import io
import json
import logging
import re
import threading
import time
from collections import OrderedDict
from fastapi import FastAPI, File, HTTPException, Request, UploadFile, status
from fastapi.middleware.cors import CORSMiddleware
from google import genai
from google.genai import types
from PIL import Image, ImageFilter, ImageOps
from pydantic import BaseModel, Field
from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded
from slowapi.util import get_remote_address
from dotenv import load_dotenv

# Load environment variables
load_dotenv()
load_dotenv("../.env")
load_dotenv("../../.env")

# Pipeline configuration
PIPELINE_VERSION = "v2.3.0"
MODEL_FAST = "gemini-2.0-flash"
MODEL_DEEP = "gemini-1.5-pro"
MAX_FILE_SIZE = 5 * 1024 * 1024  # 5 MB
MAX_DIMENSION = 1920
INFERENCE_TIMEOUT_MS = 15000  # 15 seconds

logging.basicConfig(
    level=logging.INFO,
    format='{"time":"%(asctime)s", "level":"%(levelname)s", "service":"plantlens-gateway", "message":%(message)s}',
)
logger = logging.getLogger("plantlens.gateway")

limiter = Limiter(key_func=get_remote_address)
app = FastAPI(title="PlantLens AI Diagnostic Gateway", version=PIPELINE_VERSION)
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

client = genai.Client()

SYSTEM_INSTRUCTION = """
You are an expert plant pathologist and agronomist AI.

TASK:
Analyze the plant leaf image and return COMPLETE foliar pathology and diagnosis.

CRITICAL PATHOLOGY RULES:
1. You MUST ALWAYS return ALL fields with complete accuracy.
2. You MUST distinguish between natural botanical structures (such as cactus areoles, trichomes, variegation) vs genuine fungal/bacterial lesions.
3. Specific Disease Signatures:
   • Dark necrotic spots with yellow chlorotic halos → "Cercospora Leaf Spot / Early Blight"
   • Powdery white/gray foliar coating → "Powdery Mildew"
   • Rust pustules or orange-brown sporulation → "Foliar Rust Disease"
   • Burned/dark water-soaked margins → "Leaf Blight"
   • Soft black rot or mushy tissue → "Stem / Root Rot"
4. Healthy Foliage:
   • Return "None (Healthy Plant)" with health_score >= 95 if leaf is free of necrotic infection, rot, or lesions.
5. Strict Calibration:
   - health_score: Integer 0 to 100 (0 = terminal/defoliated, 40 = moderate necrosis/spots, 100 = flawless foliage).
   - confidence: Float 0.0 to 1.0 (e.g. 0.95).
   - symptoms: Detailed list of visual markers (e.g. "Dark necrotic spots on upper leaf surface", "Chlorotic yellow halos surrounding lesions").
   - organic_remedies & chemical_treatments: Comprehensive actionable treatments.
"""


class DiagnosisResponse(BaseModel):
    plant_name: str
    scientific_name: str
    is_diseased: bool
    disease_name: str
    health_score: int = Field(ge=0, le=100)
    confidence: float = Field(ge=0.0, le=1.0)
    symptoms: list[str]
    organic_remedies: list[str]
    chemical_treatments: list[str]
    model_tier_used: str = MODEL_FAST
    escalation_triggered: bool = False
    initial_fast_confidence: float | None = None


class ThreadSafeLRUCache:
    def __init__(self, capacity: int = 1000, ttl_seconds: int = 86400):
        self.capacity = capacity
        self.ttl = ttl_seconds
        self.cache: OrderedDict[str, tuple[float, DiagnosisResponse]] = OrderedDict()
        self.lock = threading.Lock()

    def get(self, key: str) -> DiagnosisResponse | None:
        with self.lock:
            if key not in self.cache:
                return None
            timestamp, value = self.cache[key]
            if time.time() - timestamp > self.ttl:
                del self.cache[key]
                return None
            self.cache.move_to_end(key)
            return value

    def set(self, key: str, value: DiagnosisResponse):
        with self.lock:
            if key in self.cache:
                self.cache.move_to_end(key)
            self.cache[key] = (time.time(), value)
            if len(self.cache) > self.capacity:
                self.cache.popitem(last=False)


diagnostic_cache = ThreadSafeLRUCache()


def extract_json_payload(raw_text: str) -> dict:
    match = re.search(r"\{.*\}", raw_text, re.DOTALL)
    if not match:
        raise ValueError(f"No JSON block found in response: {raw_text[:80]}...")
    return json.loads(match.group(0))


def preprocess_image_pipeline(raw_bytes: bytes) -> tuple[bytes, str, dict]:
    try:
        with Image.open(io.BytesIO(raw_bytes)) as verify_img:
            verify_img.verify()
    except Exception:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid or corrupt image stream.",
        )

    image = Image.open(io.BytesIO(raw_bytes))
    image = ImageOps.exif_transpose(image)

    # Flatten transparency to clean RGB
    if image.mode in ("RGBA", "LA", "P"):
        canvas = Image.new("RGB", image.size, (255, 255, 255))
        if image.mode == "P":
            image = image.convert("RGBA")
        canvas.paste(image, mask=image.split()[-1] if image.mode in ("RGBA", "LA") else None)
        image = canvas
    elif image.mode != "RGB":
        image = image.convert("RGB")

    # Preserve details with proportional resize
    if max(image.size) > MAX_DIMENSION:
        image.thumbnail((MAX_DIMENSION, MAX_DIMENSION), Image.Resampling.LANCZOS)

    # Accentuate micro-lesion margins
    image = image.filter(ImageFilter.UnsharpMask(radius=1.2, percent=60, threshold=2))

    output_stream = io.BytesIO()
    image.save(output_stream, format="JPEG", quality=90, optimize=True, progressive=True)
    processed_bytes = output_stream.getvalue()
    image_hash = hashlib.sha256(processed_bytes).hexdigest()

    metadata = {
        "dimensions": f"{image.size[0]}x{image.size[1]}",
        "size_kb": len(processed_bytes) // 1024,
        "hash": image_hash,
    }
    return processed_bytes, image_hash, metadata


def execute_gemini_inference(processed_bytes: bytes, target_model: str) -> DiagnosisResponse:
    candidate_models = [target_model]
    if target_model == MODEL_FAST:
        candidate_models += ["gemini-3.6-flash", "gemini-3.5-flash", "gemini-flash-latest"]
    elif target_model == MODEL_DEEP:
        candidate_models += ["gemini-3.5-flash", "gemini-3.6-flash", "gemini-3.1-pro-preview", "gemini-pro-latest"]

    last_err = None
    for model_name in candidate_models:
        try:
            response = client.models.generate_content(
                model=model_name,
                contents=[
                    types.Part.from_bytes(data=processed_bytes, mime_type="image/jpeg"),
                    "Analyze and diagnose foliage health metrics in strict accordance with the system pathology instructions.",
                ],
                config=types.GenerateContentConfig(
                    system_instruction=SYSTEM_INSTRUCTION,
                    temperature=0.1,
                    top_p=0.9,
                    top_k=40,
                    response_mime_type="application/json",
                    response_schema=DiagnosisResponse,
                    http_options=types.HttpOptions(timeout=INFERENCE_TIMEOUT_MS),
                ),
            )
            parsed_json = extract_json_payload(response.text)
            result = DiagnosisResponse.model_validate(parsed_json)
            result.model_tier_used = target_model
            return result
        except Exception as err:
            last_err = err
            logger.warning(f"Inference attempt with model {model_name} failed: {err}")
            continue

    raise last_err or RuntimeError(f"All inference attempts failed for {target_model}")


@app.post("/api/v1/diagnose", response_model=DiagnosisResponse)
@limiter.limit("30/minute")
async def diagnose_leaf(request: Request, file: UploadFile = File(...)):
    raw_bytes = await file.read()
    if len(raw_bytes) > MAX_FILE_SIZE:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail=f"Payload exceeds {MAX_FILE_SIZE // (1024 * 1024)}MB limit.",
        )

    processed_bytes, image_hash, meta = preprocess_image_pipeline(raw_bytes)
    cache_key = f"{image_hash}:{PIPELINE_VERSION}"

    cached_result = diagnostic_cache.get(cache_key)
    if cached_result:
        logger.info(json.dumps({"event": "cache_hit", "key": cache_key, "model": cached_result.model_tier_used}))
        return cached_result

    logger.info(
        json.dumps(
            {
                "event": "cache_miss_inferencing",
                "hash": image_hash,
                "dimensions": meta["dimensions"],
                "size_kb": meta["size_kb"],
            }
        )
    )

    # Tier 1: Fast Flash Diagnostic
    diagnosis = None
    try:
        diagnosis = execute_gemini_inference(processed_bytes, MODEL_FAST)
    except Exception as err:
        logger.warning(json.dumps({"event": "fast_tier_failed", "error": str(err), "action": "escalating_to_deep_model"}))

    # Tier 2: Automatic Escalation on Ambiguity or Failure
    initial_fast_conf = diagnosis.confidence if diagnosis else None
    needs_escalation = (
        diagnosis is None
        or diagnosis.confidence < 0.60
        or diagnosis.disease_name.startswith("Uncertain")
    )

    if needs_escalation:
        logger.info(
            json.dumps(
                {
                    "event": "tier_escalation_triggered",
                    "reason": "confidence_below_threshold" if diagnosis else "fast_tier_exception",
                    "fast_confidence": initial_fast_conf,
                    "target_model": MODEL_DEEP,
                }
            )
        )
        try:
            deep_diagnosis = execute_gemini_inference(processed_bytes, MODEL_DEEP)
            deep_diagnosis.escalation_triggered = True
            deep_diagnosis.initial_fast_confidence = initial_fast_conf
            diagnosis = deep_diagnosis
        except Exception as deep_err:
            logger.error(json.dumps({"event": "deep_tier_failed", "error": str(deep_err)}))
            if diagnosis is None:
                raise HTTPException(
                    status_code=status.HTTP_502_BAD_GATEWAY,
                    detail=f"Inference failure across all tiers: {str(deep_err)}",
                )

    diagnostic_cache.set(cache_key, diagnosis)
    return diagnosis
