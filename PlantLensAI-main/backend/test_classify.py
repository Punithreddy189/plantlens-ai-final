from fastapi.testclient import TestClient
from main import app
import io
from PIL import Image

client = TestClient(app)

def test_health():
    res = client.get("/health")
    assert res.status_code == 200
    assert res.json() == {"status": "active"}

def test_non_image_rejection():
    # Send a text file disguised as upload
    fake_file = io.BytesIO(b"Hello world this is not an image")
    response = client.post(
        "/classify",
        files={"image": ("test.txt", fake_file, "text/plain")}
    )
    assert response.status_code == 400

def test_valid_image_request_structure():
    # Create a small valid test JPEG in memory
    img = Image.new("RGB", (64, 64), color=(34, 139, 34))
    img_byte_arr = io.BytesIO()
    img.save(img_byte_arr, format="JPEG")
    img_byte_arr.seek(0)

    # Test that /classify receives the image
    response = client.post(
        "/classify",
        files={"image": ("leaf.jpg", img_byte_arr, "image/jpeg")}
    )
    print("Response status:", response.status_code)
    print("Response body:", response.json())
    assert response.status_code in [200, 500]
    data = response.json()
    assert "is_plant" in data
    assert "success" in data
    assert "plant_name" in data

if __name__ == "__main__":
    test_health()
    print("[PASS] Health check passed")
    test_non_image_rejection()
    print("[PASS] Non-image rejection test passed")
    test_valid_image_request_structure()
    print("[PASS] Image upload structure test passed")
    print("All backend tests completed successfully!")

