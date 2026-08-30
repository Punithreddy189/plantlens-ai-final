from model_service import validate_and_fix_response

def run_tests():
    print("--- Testing Symptom-Based Diagnostic Decisions ---")

    # Test 1: Bunny Ears Cactus (clean symptoms)
    cactus = validate_and_fix_response({
        "is_plant": True,
        "plant_name": "Bunny Ears Cactus",
        "scientific_name": "Opuntia microdasys",
        "disease": "Cercospora Leaf Spot / Early Blight",
        "severity": "Moderate",
        "health_status": "Diseased",
        "observations": "• No dark spots, lesions, or rot detected\n• Vibrant green coloration across cladodes\n• Uniform areoles with glochids",
        "description": "Green succulent cladodes with evenly spaced areoles. No lesions or rot."
    }, "English")
    assert cactus["disease"] == "Healthy Foliage", f"Expected Healthy Foliage, got {cactus['disease']}"
    assert cactus["severity"] == "None", f"Expected None, got {cactus['severity']}"
    assert cactus["health_status"] == "Healthy", f"Expected Healthy, got {cactus['health_status']}"
    print("[PASS] Test 1 (Cactus Clean Symptoms -> Healthy Foliage): PASSED")

    # Test 2: Tomato with Fungal Lesions
    tomato = validate_and_fix_response({
        "is_plant": True,
        "plant_name": "Tomato",
        "scientific_name": "Solanum lycopersicum",
        "disease": "Healthy Foliage",
        "severity": "None",
        "health_status": "Healthy",
        "observations": "• Dark circular necrotic lesions\n• Concentric ring patterns with chlorotic yellow halo",
        "description": "Circular brown spots with yellow halos."
    }, "English")
    assert tomato["disease"] == "Cercospora Leaf Spot / Early Blight", f"Expected Cercospora Leaf Spot, got {tomato['disease']}"
    assert tomato["severity"] == "Moderate", f"Expected Moderate, got {tomato['severity']}"
    assert tomato["health_status"] == "Diseased", f"Expected Diseased, got {tomato['health_status']}"
    print("[PASS] Test 2 (Tomato Necrotic Lesions -> Cercospora Leaf Spot): PASSED")

    # Test 3: Cactus with Real Soft Rot
    cactus_rot = validate_and_fix_response({
        "is_plant": True,
        "plant_name": "Bunny Ears Cactus",
        "scientific_name": "Opuntia microdasys",
        "disease": "Cactus Soft Rot",
        "severity": "High",
        "health_status": "Diseased",
        "observations": "• Black soft rot spreading at the pad base\n• Mushy tissue and decay",
        "description": "Active soft rot at stem base."
    }, "English")
    assert cactus_rot["health_status"] == "Diseased", f"Expected Diseased, got {cactus_rot['health_status']}"
    assert cactus_rot["severity"] == "High", f"Expected High, got {cactus_rot['severity']}"
    print("[PASS] Test 3 (Cactus with Soft Rot -> Diseased): PASSED")

    print("\nALL 3 SYMPTOM-BASED MEDICAL DIAGNOSTIC TESTS PASSED 100%!")

if __name__ == "__main__":
    run_tests()
