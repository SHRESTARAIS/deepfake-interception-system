@echo off
title USB Telephony Deepfake Interceptor Dashboard
echo ============================================================
echo      USB TELEPHONY DEEPFAKE INTERCEPTOR GUI DASHBOARD
echo ============================================================
echo.
set PATH=C:\Users\shres\AppData\Local\Android\Sdk\platform-tools;%PATH%
cd /d "d:\deepfake-interception-system"
echo Starting USB Telephony GUI Engine on http://127.0.0.1:5001 ...
start "" "http://127.0.0.1:5001"
python src\usb_gui_server.py
pause
