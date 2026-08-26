import httpx
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("OllamaService")

OLLAMA_URL = "http://localhost:11434/api/generate"
MODEL_NAME = "llama3"  # Change to "gemma" or preferred model

async def ask_ollama(question: str, plant_name: str) -> str:
    # Custom specialized system prompt to give a premium assistant experience
    system_prompt = (
        f"You are PlantLens AI, a professional botanist and master gardener assistant.\n"
        f"The user is asking a question about a plant named '{plant_name}'.\n"
        f"Provide comprehensive, encouraging, and detailed answers covering care tips, watering, advantages, "
        f"disadvantages, and botanical properties where relevant. Keep the tone friendly, expert, and highly practical.\n"
        f"User Question: {question}"
    )

    payload = {
        "model": MODEL_NAME,
        "prompt": system_prompt,
        "stream": False
    }

    try:
        async with httpx.AsyncClient(timeout=35.0) as client:
            response = await client.post(OLLAMA_URL, json=payload)
            if response.status_code == 200:
                data = response.json()
                return data.get("response", "I could not retrieve an answer at this time.")
            else:
                logger.warning(f"Ollama returned status code {response.status_code}. Using local botanist fallback.")
                return get_botanist_fallback_response(question, plant_name)
    except Exception as e:
        logger.error(f"Error connecting to Ollama: {str(e)}. Using fallback mock botanist.")
        return get_botanist_fallback_response(question, plant_name)

def get_botanist_fallback_response(question: str, plant_name: str) -> str:
    """
    Highly structured and botanically accurate mock responses to keep the application 100% usable
    even without a local Ollama server running.
    """
    q_lower = question.lower()
    
    if "water" in q_lower or "how often" in q_lower:
        return (
            f"💧 **Watering Guide for {plant_name}:**\n\n"
            f"Proper watering is crucial for your {plant_name}. Most plants struggle from overwatering rather than underwatering.\n"
            f"- **Rule of Thumb:** Always feel the top 1-2 inches of soil. If it's dry, water thoroughly until it drains out of the pot base.\n"
            f"- **Winter Cycle:** Reduce watering by half during colder months as the growth rate slows.\n\n"
            f"*Tip: Ensure your pot has drainage holes to prevent root rot!*"
        )
    elif "light" in q_lower or "sun" in q_lower:
        return (
            f"☀️ **Sunlight Requirements for {plant_name}:**\n\n"
            f"Light is the food source for your {plant_name}. Finding the perfect spot makes a massive difference:\n"
            f"- **Indoor Plants:** Most prefer bright, indirect light (near an east or north-facing window).\n"
            f"- **Direct Sunlight:** Be careful with intense afternoon sun, as it can scorch leaves and leave unsightly brown spots.\n"
            f"- **Low Light:** If placed in a dark corner, consider supplemental grow lights to keep the foliage vibrant."
        )
    elif "tip" in q_lower or "care" in q_lower or "help" in q_lower:
        return (
            f"🌱 **Expert Care Tips for your {plant_name}:**\n\n"
            f"Here are three primary habits to keep your {plant_name} thriving:\n"
            f"1. **Humidity is Key:** Tropical species love misting or a pebble tray nearby to raise ambient humidity.\n"
            f"2. **Leaf Care:** Wipe the leaves monthly with a damp cloth to remove dust. This helps the plant photosynthesize efficiently.\n"
            f"3. **Pruning:** Snip away any yellowing or dead bottom leaves to redirect energy to fresh, new shoots."
        )
    else:
        return (
            f"🌿 **PlantLens AI Specialist Advice for {plant_name}:**\n\n"
            f"Thank you for asking about {plant_name}! As a botanical assistant, here is what you should keep in mind:\n"
            f"- **Foliage Health:** Always inspect under the leaves for any tiny spider mites or scale insects.\n"
            f"- **Repotting:** If you notice roots circling the bottom or poking out of the drainage holes, it's time to upgrade to a pot 2 inches larger.\n\n"
            f"Please ensure you run Ollama locally (`ollama serve` and `ollama run {MODEL_NAME}`) to access custom contextual answers for any other deep inquiries!"
        )
