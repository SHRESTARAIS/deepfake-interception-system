@echo off
title Real-Time Telephony Deepfake USB Cable Live Monitor
echo ============================================================
echo      REAL-TIME TELEPHONY DEEPFAKE USB CABLE LIVE MONITOR
echo ============================================================
echo.
set PATH=C:\Users\shres\AppData\Local\Android\Sdk\platform-tools;%PATH%
cd /d "d:\deepfake-interception-system"
python src\usb_live_monitor.py
pause
