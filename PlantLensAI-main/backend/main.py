import os
import time
import logging
from typing import Optional
import httpx
from fastapi import FastAPI, File, UploadFile, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from dotenv import load_dotenv

# Import Ollama chat service
from ollama_service import ask_ollama

# Load environment variables
load_dotenv()

# Setup logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger("PlantLensBackend")

# Initialize FastAPI App
app = FastAPI(
    title="PlantLens AI FastAPI Hybrid Backend",
    description="Orchestrates PlantNet species identification, Gemini 1.5 Flash disease diagnostics, and Ollama chat proxy.",
    version="2.0.0"
)

# Enable CORS for standard emulator loopback addresses
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Import Gemini service
from model_service import analyze_plant_with_gemini

# Config variables
PLANTNET_API_KEY = os.getenv("PLANTNET_API_KEY")
PLANTNET_API_URL = "https://my-api.plantnet.org/v2/identify/all"

# --- Models for Chat Route ---
class ChatRequest(BaseModel):
    question: str
    plantName: str

class ChatResponse(BaseModel):
    answer: str

# --- Models for Classification Route ---
class ErrorResponse(BaseModel):
    success: bool
    error: str
    details: str

class ClassificationResponse(BaseModel):
    success: bool
    plant_name: str
    scientific_name: str
    confidence: float
    health_status: str
    disease: str
    description: str
    treatment: str
    watering: str
    sunlight: str
    fertilizer: str
    prevention: str

# --- Routes ---

@app.get("/")
def read_root():
    return {"status": "active", "service": "PlantLens AI Hybrid API"}

@app.get("/health")
def health_check():
    return {"status": "active"}

@app.post("/api/chat", response_model=ChatResponse)
async def chat_with_assistant(request: ChatRequest):
    logger.info(f"Incoming chat request for plant '{request.plantName}'. Query: '{request.question}'")
    if not request.question.strip() or not request.plantName.strip():
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Question and PlantName parameters cannot be empty."
        )
    
    answer_text = await ask_ollama(request.question, request.plantName)
    return ChatResponse(answer=answer_text)

@app.post("/classify", response_model=ClassificationResponse)
async def classify_image(images: UploadFile = File(...)):
    start_time = time.time()
    logger.info(f"Incoming classification request. File received: '{images.filename}', content-type: '{images.content_type}'")

    # 1. Request Validation
    if not images.content_type.startswith("image/"):
        logger.warning(f"Rejected non-image upload: '{images.filename}' (Type: '{images.content_type}')")
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={
                "success": False,
                "error": "Invalid request file type.",
                "details": f"File type '{images.content_type}' is not supported. Please upload a valid image file."
            }
        )

    try:
        # Read the upload file bytes
        file_bytes = await images.read()
        if len(file_bytes) == 0:
            logger.warning("Empty file uploaded.")
            return JSONResponse(
                status_code=status.HTTP_400_BAD_REQUEST,
                content={
                    "success": False,
                    "error": "Empty file content.",
                    "details": "The uploaded image file is empty."
                }
            )

        # 2. PlantNet Identification Layer
        plant_name: Optional[str] = None
        scientific_name: Optional[str] = None
        confidence: float = 0.0
        plantnet_success = False

        if not PLANTNET_API_KEY:
            logger.warning("PLANTNET_API_KEY is not configured on the backend. Skipping PlantNet identification.")
        else:
            try:
                logger.info("Sending request to PlantNet API...")
                files = {
                    "images": (images.filename, file_bytes, images.content_type)
                }
                data = {
                    "organs": "leaf"
                }
                params = {
                    "api-key": PLANTNET_API_KEY,
                    "detailed": "true"
                }

                # Call PlantNet endpoint
                async with httpx.AsyncClient(timeout=20.0) as client:
                    response = await client.post(
                        PLANTNET_API_URL,
                        files=files,
                        data=data,
                        params=params
                    )
                
                logger.info(f"PlantNet API response code: {response.status_code}")

                if response.status_code == 200:
                    res_json = response.json()
                    results = res_json.get("results", [])
                    if results:
                        top_match = results[0]
                        species = top_match.get("species", {})
                        scientific_name = species.get("scientificNameWithoutAuthor", species.get("scientificName", ""))
                        
                        common_names = species.get("commonNames", [])
                        plant_name = common_names[0] if common_names else scientific_name
                        confidence = float(top_match.get("score", 0.0))
                        
                        plantnet_success = True
                        logger.info(f"PlantNet match: '{plant_name}' ({scientific_name}) with confidence: {confidence}")
                    else:
                        logger.warning("PlantNet returned 200 but results array was empty.")
                else:
                    logger.warning(f"PlantNet returned non-200 code: {response.status_code}. Response: {response.text}")
            except Exception as ex:
                logger.error(f"PlantNet API call failed: {str(ex)}")

        # 3. Gemini Disease & Care Analysis Layer
        logger.info("Executing Gemini analysis layer...")
        gemini_result = await analyze_plant_with_gemini(
            image_bytes=file_bytes,
            plant_name=plant_name,
            scientific_name=scientific_name,
            confidence=confidence
        )

        # 4. Return combined results
        latency = time.time() - start_time
        logger.info(f"Classification pipeline complete. Latency: {latency:.2f} seconds.")
        logger.info(f"Response: Success={gemini_result.get('success', False)}, PlantName='{gemini_result.get('plant_name')}', Disease='{gemini_result.get('disease')}'")

        return ClassificationResponse(
            success=gemini_result.get("success", True),
            plant_name=gemini_result.get("plant_name", plant_name or "Unknown"),
            scientific_name=gemini_result.get("scientific_name", scientific_name or "Unknown"),
            confidence=gemini_result.get("confidence", confidence),
            health_status=gemini_result.get("health_status", "Unknown"),
            disease=gemini_result.get("disease", "None"),
            description=gemini_result.get("description", ""),
            treatment=gemini_result.get("treatment", ""),
            watering=gemini_result.get("watering", ""),
            sunlight=gemini_result.get("sunlight", ""),
            fertilizer=gemini_result.get("fertilizer", ""),
            prevention=gemini_result.get("prevention", "")
        )

    except Exception as e:
        logger.exception(f"Unhandled error in classify endpoint: {str(e)}")
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content={
                "success": False,
                "error": "Internal classification pipeline failure.",
                "details": f"An unhandled error occurred: {str(e)}"
            }
        )

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
