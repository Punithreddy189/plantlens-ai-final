import requests
import json

url = "http://127.0.0.1:8000/predict"
image_path = "leaf.jpg"

print(f"[*] Sending {image_path} to {url}...")

with open(image_path, "rb") as f:
    response = requests.post(url, files={"file": f})

if response.status_code == 200:
    data = response.json()
    print("\n--- PREDICTION RESULT ---")
    print(f"Disease:    {data['disease']}")
    print(f"Confidence: {data['confidence']}")
    print("-------------------------\n")
else:
    print(f"Error {response.status_code}: {response.text}")
