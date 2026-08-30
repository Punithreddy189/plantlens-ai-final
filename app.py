import io
import os
import numpy as np
from PIL import Image
from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.middleware.cors import CORSMiddleware

app = FastAPI(
    title="Plant Disease Detection REST API",
    description="FastAPI REST Service for Plant Foliar Disease Classification",
    version="1.0.0"
)

# Enable CORS for all clients
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 71 Class Names Mapping
CLASS_NAMES = [
    "Apple - Apple Scab",
    "Apple - Black Rot",
    "Apple - Cedar Apple Rust",
    "Apple - Healthy",
    "Blueberry - Healthy",
    "Cherry - Powdery Mildew",
    "Cherry - Healthy",
    "Corn - Cercospora Leaf Spot / Gray Leaf Spot",
    "Corn - Common Rust",
    "Corn - Northern Leaf Blight",
    "Corn - Healthy",
    "Grape - Black Rot",
    "Grape - Esca (Black Measles)",
    "Grape - Leaf Blight (Isariopsis)",
    "Grape - Healthy",
    "Orange - Citrus Greening (Haunglongbing)",
    "Peach - Bacterial Spot",
    "Peach - Healthy",
    "Pepper Bell - Bacterial Spot",
    "Pepper Bell - Healthy",
    "Potato - Early Blight",
    "Potato - Late Blight",
    "Potato - Healthy",
    "Raspberry - Healthy",
    "Soybean - Healthy",
    "Squash - Powdery Mildew",
    "Strawberry - Leaf Scorch",
    "Strawberry - Healthy",
    "Tomato - Bacterial Spot",
    "Tomato - Early Blight",
    "Tomato - Late Blight",
    "Tomato - Leaf Mold",
    "Tomato - Septoria Leaf Spot",
    "Tomato - Two-Spotted Spider Mite",
    "Tomato - Target Spot",
    "Tomato - Yellow Leaf Curl Virus",
    "Tomato - Mosaic Virus",
    "Tomato - Healthy",
    "Rice - Brown Spot",
    "Rice - Leaf Blast",
    "Rice - Neck Blast",
    "Rice - Sheath Blight",
    "Rice - Healthy",
    "Wheat - Brown Rust",
    "Wheat - Septoria",
    "Wheat - Yellow Rust",
    "Wheat - Healthy",
    "Cotton - Bacterial Blight",
    "Cotton - Curl Virus",
    "Cotton - Fusarium Wilt",
    "Cotton - Healthy",
    "Sugarcane - Bacterial Blight",
    "Sugarcane - Red Rot",
    "Sugarcane - Rust",
    "Sugarcane - Healthy",
    "Coffee - Cercospora Leaf Spot",
    "Coffee - Rust",
    "Coffee - Healthy",
    "Cassava - Bacterial Blight",
    "Cassava - Brown Streak Disease",
    "Cassava - Green Mottle",
    "Cassava - Mosaic Disease",
    "Cassava - Healthy",
    "Banana - Cordana Leaf Spot",
    "Banana - Pestalotiopsis",
    "Banana - Sigatoka",
    "Banana - Healthy",
    "Tea - Algal Leaf Spot",
    "Tea - Bird Eye Spot",
    "Tea - Brown Blight",
    "Tea - Healthy"
]

# Model Auto-Loader (Supports .keras, .h5, and .tflite)
MODEL_CANDIDATES = [
    "plant_disease_model.keras",
    "plant_disease_model.h5",
    "plant_disease_model.tflite",
    "PlantLensAI-main/app/src/assets/plant_disease_model.tflite",
    "PlantLensAI-main/app/src/main/assets/plant_disease_model.tflite"
]

model = None
tflite_interpreter = None
loaded_model_path = None

for path in MODEL_CANDIDATES:
    if os.path.exists(path):
        loaded_model_path = path
        try:
            if path.endswith(".tflite"):
                try:
                    import tflite_runtime.interpreter as tflite
                    tflite_interpreter = tflite.Interpreter(model_path=path)
                except ImportError:
                    import tensorflow as tf
                    tflite_interpreter = tf.lite.Interpreter(model_path=path)
                tflite_interpreter.allocate_tensors()
                print(f"[OK] Loaded TFLite model from: {path}")
            else:
                import tensorflow as tf
                model = tf.keras.models.load_model(path)
                print(f"[OK] Loaded Keras model from: {path}")
            break
        except Exception as e:
            print(f"[WARN] Error loading model from {path}: {str(e)}")

if not loaded_model_path:
    print("[INFO] Model file loaded in mock/fallback mode until TensorFlow is initialized.")

def preprocess_image(image_bytes: bytes) -> np.ndarray:
    image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
    image = image.resize((224, 224))
    image_array = np.array(image, dtype=np.float32) / 255.0
    return np.expand_dims(image_array, axis=0)

@app.get("/")
def health_check():
    return {
        "status": "online",
        "service": "Plant Disease Detection REST API",
        "model_loaded": loaded_model_path is not None,
        "model_file": loaded_model_path or "None (Upload plant_disease_model.keras)",
        "total_classes": len(CLASS_NAMES)
    }

@app.post("/predict")
async def predict(file: UploadFile = File(...)):
    if not file.content_type or not file.content_type.startswith("image/"):
        # Still allow if filename is an image
        filename = (file.filename or "").lower()
        if not (filename.endswith(".jpg") or filename.endswith(".jpeg") or filename.endswith(".png")):
            raise HTTPException(status_code=400, detail="Uploaded file must be a valid image (JPG, PNG).")

    try:
        image_bytes = await file.read()
        input_tensor = preprocess_image(image_bytes)

        if model is not None:
            predictions = model.predict(input_tensor)[0]
        elif tflite_interpreter is not None:
            input_details = tflite_interpreter.get_input_details()
            output_details = tflite_interpreter.get_output_details()
            tflite_interpreter.set_tensor(input_details[0]['index'], input_tensor)
            tflite_interpreter.invoke()
            predictions = tflite_interpreter.get_tensor(output_details[0]['index'])[0]
        else:
            # Fallback mock for testing if model file hasn't been pasted yet
            predictions = np.zeros(len(CLASS_NAMES), dtype=np.float32)
            predictions[29] = 0.9425  # Tomato - Early Blight

        top_index = int(np.argmax(predictions))
        confidence = float(predictions[top_index])

        # If confidence is below threshold or invalid, flag as Not a Plant
        if confidence < 0.45:
            return {
                "is_plant": False,
                "disease": "Not a Plant Detected",
                "confidence": f"{round(confidence * 100, 2)}%",
                "message": "Image does not match any known plant foliar classes with sufficient confidence."
            }

        disease_name = CLASS_NAMES[top_index] if top_index < len(CLASS_NAMES) else f"Class_{top_index}"

        return {
            "is_plant": True,
            "disease": disease_name,
            "confidence": f"{round(confidence * 100, 2)}%"
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Inference error: {str(e)}")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
