@echo off
title PlantLens AI Backend Server
echo ========================================================
echo         Starting PlantLens AI FastAPI Backend Server
echo ========================================================
echo.

cd /d "%~dp0"

REM Try to configure adb reverse if an Android device is connected
where adb >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo [INFO] Configuring ADB port reverse for USB connected device...
    adb reverse tcp:8000 tcp:8000 >nul 2>&1
) else (
    if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
        echo [INFO] Configuring ADB port reverse via Android SDK...
        "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" reverse tcp:8000 tcp:8000 >nul 2>&1
    )
)

echo [INFO] Target Directory: PlantLensAI-main\backend
echo [INFO] Server URL: http://127.0.0.1:8000
echo [INFO] Emulator URL: http://10.0.2.2:8000
echo [INFO] Swagger Docs: http://127.0.0.1:8000/docs
echo.

cd PlantLensAI-main\backend

if exist "..\..\.venv\Scripts\python.exe" (
    "..\..\.venv\Scripts\python.exe" -m uvicorn main:app --host 0.0.0.0 --port 8000 --reload
) else if exist ".venv\Scripts\python.exe" (
    ".venv\Scripts\python.exe" -m uvicorn main:app --host 0.0.0.0 --port 8000 --reload
) else (
    python -m uvicorn main:app --host 0.0.0.0 --port 8000 --reload
)

pause
