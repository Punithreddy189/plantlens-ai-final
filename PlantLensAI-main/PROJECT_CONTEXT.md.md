# PlantLens AI Current Architecture

## Species Identification
- Pl@ntNet API
- Direct Retrofit integration
- Global Firestore cache

## Disease Detection
Planned v2.1 architecture:

PlantNet API
↓
Species Name
↓
Supported crop?
↓
TensorFlow Lite disease model
↓
Disease diagnosis

Unsupported plants:
↓
Rule-based symptom analyzer

## Multi-language
- English
- Telugu
- Tamil
- Hindi
- Kannada
- Malayalam
- Bengali
- Marathi
- Gujarati
- Punjabi

## Themes
- Light
- Dark
- Follow System

## Profile Features
- Edit username
- Avatar
- Achievements
- Export PDF/CSV

## Weather
- Open-Meteo
- 1-hour cache
- 5 km location cache

## Admin Analytics
- Total users
- Total scans
- Top plants
- Feedback reports

## Current Version
PlantLens AI v2.0 Stable

## Next Version
PlantLens AI v2.1

### Disease Detection Architecture

PlantNet API
↓
TensorFlow Lite Disease Model
↓
Health Score
↓
Treatment Recommendations

Unsupported plants:
↓
Symptom Analyzer
↓
General Diagnosis
