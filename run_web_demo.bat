@echo off
title Launch Deepfake Interceptor Web Dashboard
echo ============================================================
echo      LAUNCHING DEEPFAKE VOICE INTERCEPTION SYSTEM
echo ============================================================
echo.
cd /d "d:\deepfake-interception-system"
echo Starting Python Flask Server on http://127.0.0.1:5000 ...
start "" "http://127.0.0.1:5000"
python src/web_demo.py
pause
