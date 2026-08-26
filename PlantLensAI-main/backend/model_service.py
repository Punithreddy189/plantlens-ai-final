import os
import io
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
model_name = os.getenv("GEMINI_MODEL", "gemini-1.5-flash")

if gemini_api_key:
    genai.configure(api_key=gemini_api_key)
    logger.info("Google Gemini API configured successfully.")
else:
    logger.error("GEMINI_API_KEY not found in env variables.")

class PlantAnalysisResponse(BaseModel):
    success: bool = Field(description="Set to true if identification/analysis was successful, false otherwise.")
    plant_name: str = Field(description="Common name of the plant.")
    scientific_name: str = Field(description="Scientific name of the plant.")
    confidence: float = Field(description="Confidence level between 0.0 and 1.0.")
    health_status: str = Field(description="State of health (e.g., 'Healthy', 'Diseased', 'Stressed').")
    disease: str = Field(description="Name of the detected disease, or 'None' if healthy.")
    description: str = Field(description="Brief botanical description of the plant.")
    treatment: str = Field(description="Treatment recommendation, or 'No treatment required.' if healthy.")
    watering: str = Field(description="Watering schedule recommendation.")
    sunlight: str = Field(description="Sunlight requirements.")
    fertilizer: str = Field(description="Fertilizer recommendation.")
    prevention: str = Field(description="Prevention tips for future health issues.")

async def analyze_plant_with_gemini(
    image_bytes: bytes,
    plant_name: str = None,
    scientific_name: str = None,
    confidence: float = 0.0
) -> dict:
    """
    Calls Gemini 1.5 Flash using the generativeai SDK to analyze the plant image.
    If plant_name/scientific_name is provided from PlantNet, Gemini behaves as a disease & care analyzer.
    If not, Gemini performs end-to-end identification, analysis, and care suggestions.
    """
    if not gemini_api_key:
        logger.error("Gemini API Key is missing. Returning failure response.")
        return {
            "success": False,
            "plant_name": plant_name or "Unknown",
            "scientific_name": scientific_name or "Unknown",
            "confidence": confidence,
            "health_status": "Unknown",
            "disease": "Error: Gemini API key missing in backend configuration.",
            "description": "",
            "treatment": "",
            "watering": "",
            "sunlight": "",
            "fertilizer": "",
            "prevention": ""
        }

    try:
        # Load image bytes into PIL Image
        img = Image.open(io.BytesIO(image_bytes))
        
        # Prepare the model
        # Using the model ID configured in env or defaulting to gemini-1.5-flash
        model = genai.GenerativeModel(model_name=model_name)
        
        # Formulate prompt based on whether PlantNet successfully identified the species
        if plant_name and scientific_name:
            prompt = (
                f"You are a professional botanist and plant disease expert.\n"
                f"PlantNet identified this plant as '{plant_name}' (scientific name: '{scientific_name}') with a confidence score of {confidence:.2f}.\n"
                f"Analyze this image and return a JSON object with the following fields:\n"
                f"- success: true\n"
                f"- plant_name: string\n"
                f"- scientific_name: string\n"
                f"- confidence: float\n"
                f"- health_status: string (e.g. 'Healthy', 'Diseased')\n"
                f"- disease: string\n"
                f"- description: string\n"
                f"- treatment: string\n"
                f"- watering: string\n"
                f"- sunlight: string\n"
                f"- fertilizer: string\n"
                f"- prevention: string\n"
            )
        else:
            prompt = (
                f"You are a professional botanist and plant disease expert. Identify this plant and its health.\n"
                f"Return a JSON object with the following fields:\n"
                f"- success: true\n"
                f"- plant_name: string\n"
                f"- scientific_name: string\n"
                f"- confidence: float (0.0 to 1.0)\n"
                f"- health_status: string\n"
                f"- disease: string\n"
                f"- description: string\n"
                f"- treatment: string\n"
                f"- watering: string\n"
                f"- sunlight: string\n"
                f"- fertilizer: string\n"
                f"- prevention: string\n"
            )

        # Temporarily removing response_schema to debug the 404 error
        # generation_config = genai.GenerationConfig(
        #    response_mime_type="application/json",
        #    response_schema=PlantAnalysisResponse,
        #    temperature=0.2
        # )

        logger.info("Sending request to Gemini 1.5 Flash...")
        response = model.generate_content([img, prompt])

        response_text = response.text.strip()
        # Clean up Markdown JSON formatting if present
        if response_text.startswith("```json"):
            response_text = response_text.replace("```json", "", 1).replace("```", "").strip()
        elif response_text.startswith("```"):
            response_text = response_text.replace("```", "").strip()

        logger.info(f"Gemini responded with text size: {len(response_text)}")
        
        # Parse the JSON response
        data = json.loads(response_text)
        
        # If PlantNet did identify the plant, we preserve PlantNet's results and confidence
        if plant_name and scientific_name:
            data["plant_name"] = plant_name
            data["scientific_name"] = scientific_name
            data["confidence"] = confidence

        return data

    except Exception as e:
        logger.exception(f"Error during Gemini analysis: {str(e)}")
        # Return graceful failure response
        return {
            "success": False,
            "plant_name": plant_name or "Unknown",
            "scientific_name": scientific_name or "Unknown",
            "confidence": confidence,
            "health_status": "Unknown",
            "disease": f"Failed to analyze plant: {str(e)}",
            "description": "An error occurred while communicating with Gemini API.",
            "treatment": "Please check backend server log connectivity.",
            "watering": "",
            "sunlight": "",
            "fertilizer": "",
            "prevention": ""
        }
