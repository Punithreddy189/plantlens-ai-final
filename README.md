# 🌱 PlantLens AI

**PlantLens AI** is an intelligent plant identification and disease diagnosis platform designed to assist farmers, gardeners, and plant enthusiasts. It combines computer vision, machine learning (TensorFlow Lite & Gemini AI Vision), and the Pl@ntNet API to deliver instant species classification, health scoring, and actionable treatment recommendations.

---

## 📱 Features

- **Plant Identification**: High-accuracy species recognition powered by Pl@ntNet API.
- **Disease Diagnosis & Health Scoring**: Instant disease detection using TensorFlow Lite and Gemini Vision AI.
- **Multi-language Support**: English, Telugu, Tamil, Hindi, Kannada, Malayalam, Bengali, Marathi, Gujarati, and Punjabi.
- **Weather Integration**: Hyperlocal weather forecasting via Open-Meteo with caching.
- **Profile & History**: Scan history, PDF/CSV export, achievements, and customizable profiles.
- **Dual Platforms**:
  - **Android Application**: Native Kotlin app with CameraX, offline TFLite support, and Material Design 3.
  - **Web Application**: Fast modern responsive web app built with Vite and React.

---

## 📂 Project Structure

```
PlantLensAI/
├── PlantLensAI-main/     # Native Android Application (Kotlin) & Python Backend
│   ├── app/              # Android app source code (CameraX, TFLite, UI)
│   ├── backend/          # Python FastAPI/Flask backend service (Gemini / Pl@ntNet)
│   └── build.gradle.kts  # Gradle configuration
├── plantlens web/        # Web Application (Vite + React)
│   ├── src/              # React components, pages, and hooks
│   ├── api/              # Serverless API routes
│   └── backend/          # Python backend for web
├── .firebaserc           # Firebase project configuration
├── firebase.json         # Firebase hosting configuration
└── README.md
```

---

## 🚀 Getting Started

### Android App
1. Open the `PlantLensAI-main` directory in **Android Studio**.
2. Sync Gradle dependencies.
3. Set up your API keys in `backend/.env` (see `backend/.env.example`).
4. Build and run on an Android device or emulator (Android 8.0+ / API 26+).

### Web App
1. Navigate to `plantlens web`:
   ```bash
   cd "plantlens web"
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Copy `.env.example` to `.env` and fill in your keys:
   ```bash
   cp .env.example .env
   ```
4. Start development server:
   ```bash
   npm run dev
   ```

---

## 🔑 Environment Configuration

Create `.env` files in `plantlens web/` and `PlantLensAI-main/backend/` using the provided `.env.example` templates with your respective API keys:
- **Pl@ntNet API Key**
- **Google Gemini API Key**
- **Firebase Configuration**

---

## 📄 License

This project is licensed under the MIT License.
