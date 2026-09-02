@echo off
title Real-Time Audio Deepfake Interception Dashboard
color 0B
echo ============================================================
echo   REAL-TIME AUDIO DEEPFAKE INTERCEPTION DASHBOARD
echo ============================================================
echo.
set PATH=C:\Users\shres\AppData\Local\Android\Sdk\platform-tools;%PATH%
cd /d "d:\deepfake-interception-system"
echo Starting Real-Time Security Engine on http://127.0.0.1:5001 ...
start "" "http://127.0.0.1:5001"
python src\usb_gui_server.py
pause
