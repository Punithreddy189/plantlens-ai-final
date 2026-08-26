import os
import httpx
from fastapi import FastAPI, File, UploadFile, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from dotenv import load_dotenv
from typing import Optional

# Load environment variables from parent .env or current directory
load_dotenv()
load_dotenv("../.env")

PLANTNET_API_KEY = os.getenv("PLANTNET_API_KEY", os.getenv("VITE_PLANTNET_API_KEY", ""))
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", os.getenv("VITE_GEMINI_API_KEY", ""))

app = FastAPI(
    title="PlantLens AI Backend API",
    description="Backend proxy for Pl@ntNet botanical classification and Gemini vision diagnostics",
    version="1.0.0"
)

# Enable CORS for all frontend origins (Vite localhost:3000, Android, production)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
    expose_headers=["*"],
)

@app.get("/")
def read_root():
    return {
        "status": "online",
        "service": "PlantLens AI Backend Engine",
        "endpoints": ["/identify", "/docs"]
    }

@app.post("/identify")
async def identify_plant(
    file: UploadFile = File(...),
    api_key: Optional[str] = Query(None, description="Optional custom Pl@ntNet API key")
):
    """
    Identify botanical plant species via Pl@ntNet API without CORS restrictions.
    """
    key_to_use = api_key or PLANTNET_API_KEY
    if not key_to_use:
        raise HTTPException(status_code=400, detail="Missing Pl@ntNet API key. Please configure in .env or provide as query parameter.")

    image_bytes = await file.read()
    url = "https://my-api.plantnet.org/v2/identify/all"
    params = {
        "api-key": key_to_use,
        "detailed": "true"
    }

    # Pl@ntNet strictly requires image/jpeg or image/png
    filename = file.filename or "plant_scan.jpg"
    if not (filename.lower().endswith(".jpg") or filename.lower().endswith(".jpeg") or filename.lower().endswith(".png")):
        filename = "plant_scan.jpg"

    content_type = "image/png" if filename.lower().endswith(".png") else "image/jpeg"

    files = {
        "images": (filename, image_bytes, content_type)
    }
    data = {
        "organs": "leaf"
    }

    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.post(url, params=params, files=files, data=data)
            
            if response.status_code == 200:
                return response.json()
            
            if response.status_code == 404:
                return {"results": [], "message": "No botanical species recognized in image. Please ensure leaf/flower is clearly visible."}
            
            error_data = response.text
            print(f"[Pl@ntNet Error] Status {response.status_code}: {error_data}")
            
            if "remote IP not allowed" in error_data:
                raise HTTPException(
                    status_code=403,
                    detail="Pl@ntNet Error: Remote IP not allowed. Please add your IP '180.235.121.242' to Authorized IPs at my.plantnet.org."
                )
                
            raise HTTPException(status_code=response.status_code, detail=f"Pl@ntNet API error: {error_data}")
    except httpx.RequestError as exc:
        raise HTTPException(status_code=500, detail=f"Network error contacting Pl@ntNet: {str(exc)}")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
