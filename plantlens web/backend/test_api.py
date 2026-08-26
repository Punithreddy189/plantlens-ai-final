import os
import httpx
import asyncio

async def test():
    url = 'https://my-api.plantnet.org/v2/identify/all'
    api_key = os.getenv('PLANTNET_API_KEY', '')
    params = {'api-key': api_key, 'detailed': 'true'}
    # Standard 1x1 jpeg byte
    dummy_jpg = b'\xff\xd8\xff\xe0\x00\x10JFIF\x00\x01\x01\x01\x00`\x00`\x00\x00\xff\xdb\x00C\x00\x08\x06\x06\x07\x06\x05\x08\x07\x07\x07\t\t\x08\n\x0c\x14\r\x0c\x0b\x0b\x0c\x19\x12\x13\x0f\x14\x1d\x1a\x1f\x1e\x1d\x1a\x1c\x1c $.\' \",#\x1c\x1c(7),01444\x1f\'9=82<.342\xff\xc0\x00\x0b\x08\x00\x01\x00\x01\x01\x01\x11\x00\xff\xc4\x00\x1f\x00\x00\x01\x05\x01\x01\x01\x01\x01\x01\x00\x00\x00\x00\x00\x00\x00\x00\x01\x02\x03\x04\x05\x06\x07\x08\t\n\x0b\xff\xda\x00\x08\x01\x01\x00\x00?\x00\xbf\x00\xff\xd9'
    files = {'images': ('plant.jpg', dummy_jpg, 'image/jpeg')}
    data = {'organs': 'leaf'}
    async with httpx.AsyncClient(timeout=15.0) as client:
        r = await client.post(url, params=params, files=files, data=data)
        print('STATUS:', r.status_code)
        print('TEXT:', r.text[:300])

if __name__ == '__main__':
    asyncio.run(test())
