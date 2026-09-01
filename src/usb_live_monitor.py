import os
import sys
import subprocess
import time

# Automatically locate adb.exe
ADB_PATH = "adb"
sdk_adb = r"C:\Users\shres\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if os.path.exists(sdk_adb):
    ADB_PATH = sdk_adb

# Enable ANSI escape codes for Windows console colors
os.system('')

COLOR_RESET = "\033[0m"
COLOR_GREEN = "\033[1;32m"
COLOR_RED = "\033[1;31m"
COLOR_BLUE = "\033[1;34m"
COLOR_YELLOW = "\033[1;33m"
COLOR_CYAN = "\033[1;36m"
BG_GREEN = "\033[42m\033[1;30m"
BG_RED = "\033[41m\033[1;37m"

def print_header():
    print(f"{COLOR_CYAN}================================================================================")
    print(f"       📱 REAL-TIME TELEPHONY DEEPFAKE USB CABLE LIVE MONITOR")
    print(f"================================================================================")
    print(f"  Connection Mode : USB Cable (Android Debug Bridge ADB Stream)")
    print(f"  Phone App Target: Deepfake Interceptor (com.deepfake.interception)")
    print(f"  ADB Engine Path : {ADB_PATH}")
    print(f"================================================================延{COLOR_RESET}\n")

def check_adb_device():
    try:
        res = subprocess.run([ADB_PATH, "devices"], capture_output=True, text=True)
        lines = [line.strip() for line in res.stdout.splitlines() if line.strip()]
        devices = [l for l in lines[1:] if "device" in l and not "unauthorized" in l]
        return len(devices) > 0
    except Exception:
        return False

def main():
    print_header()
    print(f"{COLOR_YELLOW}[+] Checking USB cable connection to Android Phone...{COLOR_RESET}")

    if not check_adb_device():
        print(f"\n{COLOR_RED}[!] USB DEVICE NOT DETECTED YET!{COLOR_RESET}")
        print(f"{COLOR_YELLOW}    Please follow these 3 quick steps on your phone:{COLOR_RESET}")
        print("    1. Plug your Android phone into this laptop using a USB cable.")
        print("    2. Open Phone Settings -> Developer Options -> Enable 'USB Debugging'.")
        print("    3. If a pop-up appears on your phone screen, tap 'Allow USB Debugging'.\n")
        print("Waiting for USB device connection (Plug in USB cable now)...")
        
        while not check_adb_device():
            time.sleep(2)
            sys.stdout.write(".")
            sys.stdout.flush()
        print(f"\n{COLOR_GREEN}[✓] USB Cable Connection Detected Successfully!{COLOR_RESET}\n")

    print(f"{COLOR_GREEN}[✓] USB Device Connected! Starting Real-Time Telephony Stream Listener...{COLOR_RESET}")
    print(f"{COLOR_BLUE}[i] Open app on phone & tap 'Start Real-Time Interception' or make a call:{COLOR_RESET}\n")
    print("-" * 80)

    # Clear previous logcat buffer
    subprocess.run([ADB_PATH, "logcat", "-c"], capture_output=True)

    cmd = [ADB_PATH, "logcat", "-v", "time", "-s", "DeepfakeInterceptor:V"]
    process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, bufsize=1)

    try:
        for line in process.stdout:
            line_str = line.strip()
            if "[USB_CABLE_STREAM]" in line_str or "Speech Frame" in line_str:
                timestamp = line_str.split()[0] if len(line_str.split()) > 0 else ""

                if "DEEPFAKE" in line_str or "ALERT:DEEPFAKE" in line_str:
                    print(f"\n{BG_RED}  🚨 ALERT: DEEPFAKE VOICE DETECTED  {COLOR_RESET}")
                    print(f"{COLOR_RED}[{timestamp}] 🚨 WARNING: SUSPECTED DEEPFAKE VOICE DETECTED ON CALL STREAM!{COLOR_RESET}")
                    print("-" * 80)
                elif "REAL" in line_str or "ALERT:REAL" in line_str:
                    print(f"\n{BG_GREEN}  🛡️ REAL HUMAN VOICE VERIFIED  {COLOR_RESET}")
                    print(f"{COLOR_GREEN}[{timestamp}] 🛡️ REAL HUMAN VOICE VERIFIED (Authentic Harmonic Speech){COLOR_RESET}")
                    print("-" * 80)
                elif "MONITORING" in line_str:
                    print(f"{COLOR_BLUE}[{timestamp}] 🛡️ Active Audio Stream Monitoring...{COLOR_RESET}")
    except KeyboardInterrupt:
        print(f"\n{COLOR_YELLOW}[!] USB Cable Stream Listener Stopped.{COLOR_RESET}")

if __name__ == "__main__":
    main()
