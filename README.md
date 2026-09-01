# REAL-TIME AUDIO DEEPFAKE INTERCEPTION SYSTEM
## Official Software Documentation & Deployment Readme File
**Course Code:** BCS685 | **Academic Year:** 2025–2026  
**Institution:** K.V.G. College of Engineering, Sullia (Affiliated to VTU, Belagavi)  
**Department:** Computer Science & Engineering  

---

## 📋 1. PROJECT OVERVIEW & SALIENT FEATURES

The **Real-Time Audio Deepfake Interception System** is an on-device AI security framework designed to detect synthetic voice deepfakes live during active mobile phone calls (cellular and WhatsApp VoIP) without cloud connectivity.

### **Salient Features:**
1. **100% Offline On-Device Inference:** Eliminates cloud API dependency, protecting user call privacy and eliminating network latency.
2. **Telephony-Matched AI Engine:** Uses a 1D Light Convolutional Neural Network (1D-LCNN) with Max-Feature-Map (MFM) activations, processing raw 8,000 Hz PCM audio directly ($67.9\text{ KB}$ ONNX model size, $<25\text{ ms}$ latency).
3. **Native Android Call Interceptor:** Persistent Android Foreground Service (`CallOverlayService.kt`) displaying real-time floating UI banners (**GREEN** `🛡️ REAL HUMAN VOICE VERIFIED` / **RED** `🚨 WARNING: SUSPECTED DEEPFAKE VOICE`) over active call screens.
4. **Noise Gating & Temporal Smoothing:** Active speech activity gating (`maxAbs >= 0.05f`) and 3-second sliding window average (`historyBuffer.average()`) to prevent UI flickering during speech pauses.
5. **Real-Time Laptop USB Cable Dashboard:** Python Flask server running on port 5001 (`http://127.0.0.1:5001`) reading live `[USB_CABLE_STREAM]` ADB logcat tags over a USB cable with Chart.js probability bars, animated waveform visualizers, and audit log tables.

---

## 🔑 2. SALIENT VARIABLES USED IN CODEBASE

### **A. Python Backend (`src/` & `usb_gui_server.py`):**
* `sample_rate` *(int)*: Audio sampling frequency set to `8000` Hz (G.711 telephony standard).
* `chunk_size` *(int)*: Number of audio samples in a 1-second analysis vector (`8000` samples).
* `max_abs` *(float)*: Peak absolute PCM amplitude in a 1-second waveform tensor.
* `prob_fake` / `prob_real` *(float)*: Softmax probability scores output by the 1D-LCNN model ($0.0 - 1.0$).
* `usb_state` *(dict)*: Global dictionary storing USB connection status, current verdict message, probability metrics, and log audit history.

### **B. Android Native Application (`android_app/`):**
* `audioRecord` *(AudioRecord)*: Native Android hardware recording interface capturing 16-bit PCM mono stream at 8kHz.
* `historyBuffer` *(ArrayList<Float>)*: Circular buffer holding classification predictions across the sliding window.
* `historySize` *(int)*: Rolling sliding window capacity (`3` frames = 3 seconds).
* `avgFakeInWindow` *(float)*: Arithmetic mean probability calculated via `historyBuffer.average()`.
* `ortSession` *(OrtSession)*: Microsoft ONNX Runtime C++ engine session executing local model inference.

---

## 💻 3. HARDWARE AND SOFTWARE REQUIREMENTS

### **A. Hardware Requirements:**
* **Mobile Smartphone:** Android Smartphone running Android 7.0 (API Level 24) or higher with USB Debugging enabled.
* **PC / Laptop:** Intel Core i5 / AMD Ryzen processor (8th Gen+), 8 GB RAM, USB 3.0 port.
* **Interconnect:** Standard USB 3.0 Data Cable connecting phone to laptop.

### **B. Software Requirements:**
* **Operating System:** Windows 11 (Laptop) & Android 7.0+ (Mobile).
* **Python Runtime:** Python 3.10 or 3.11.
* **Android IDE & Compiler:** Android Studio Hedgehog (2023.1.1+), Kotlin 1.9.0, Gradle 8.0+.
* **Android SDK:** Platform-Tools (`adb.exe`) API Level 24+.

---

## 🚀 4. PROCEDURE FOR COMPILING AND RUNNING SOFTWARE

### **Step 1: Compiling the Native Android Application (APK)**
1. Open PowerShell or Command Prompt.
2. Navigate to the Android project directory:
   ```cmd
   cd d:\deepfake-interception-system\android_app
   ```
3. Compile the debug APK using Gradle:
   ```powershell
   $env:JAVA_HOME = "C:\Users\shres\.jdks\jbr-17.0.14"
   .\gradlew.bat assembleDebug
   ```
4. Output APK location:  
   `d:\deepfake-interception-system\app-debug.apk`

---

### **Step 2: Launching the Laptop USB Mirroring Web Dashboard**
1. Connect your Android phone to your laptop via USB cable.
2. Double-click the 1-click batch launcher:  
   `d:\deepfake-interception-system\run_usb_gui_dashboard.bat`
3. The script automatically sets the Android SDK `adb.exe` path, starts `usb_gui_server.py` on port 5001, and opens Chrome to **`http://127.0.0.1:5001`**.

---

### **Step 3: Operating the Real-Time Interceptor**
1. Install `app-debug.apk` on your Android phone.
2. Open the app $\rightarrow$ Grant **Microphone**, **Phone State**, and **Display Over Other Apps** permissions.
3. Tap **`▶ Start Real-Time Interception`** once.
4. Make or receive a phone call or WhatsApp voice call.
5. The floating overlay on the phone and the laptop web dashboard will display live real-time verdicts (**GREEN** Real / **RED** Fake).

---

## 🌐 5. PUBLIC DOMAIN & OPEN-SOURCE SOFTWARE ACKNOWLEDGMENTS

In accordance with academic regulations, the open-source libraries, datasets, and public domain tools utilized in this project are formally acknowledged below:

| Module / Library Name | Official Website / Repository | Usage & Purpose in Project |
| :--- | :--- | :--- |
| **PyTorch (v2.0)** | `https://pytorch.org/` | Open-source machine learning framework used for model training, loss computation, and backpropagation. |
| **ONNX & ONNX Runtime Android** | `https://onnxruntime.ai/` | Microsoft C++ inference engine (`onnxruntime-android:1.17.0`) used for local mobile ONNX model execution. |
| **ASVspoof 2019 LA Dataset** | `https://www.asvspoof.org/` | Academic benchmark dataset providing genuine speech and logical access synthetic voice conversion samples. |
| **DEMAND Noise Database** | `https://zenodo.org/record/1227121` | Multi-channel environmental background noise database used for stress testing model robustness. |
| **Flask Framework (v3.0)** | `https://flask.palletsprojects.com/` | Python micro web framework used to build the laptop USB streaming server on port 5001. |
| **Chart.js (v4.4)** | `https://www.chartjs.org/` | Open-source JavaScript charting library used for rendering real-time probability bar graphs on the web dashboard. |
| **Android SDK Platform-Tools (ADB)** | `https://developer.android.com/tools/releases/platform-tools` | Android Debug Bridge used to stream USB logcat telemetry lines (`adb logcat -T 1`) from phone to laptop. |

---

## 💿 6. CONTENTS OF SUBMITTED SOFTWARE DISC / PEN DRIVE

The submitted software disc / Pen Drive contains the following complete directory structure:

```text
d:\deepfake-interception-system\
├── README.md                           <- Official Software Readme & Manual
├── run_usb_gui_dashboard.bat           <- 1-Click Laptop Dashboard Launcher
├── app-debug.apk                       <- Compiled Android Application Package
├── models/
│   ├── deepfake_detector.onnx          <- 67.9 KB Trained ONNX Engine
│   └── deepfake_detector.pt            <- PyTorch Checkpoint Model
├── src/
│   ├── model.py                        <- 1D-LCNN & MFM Architecture Code
│   ├── preprocess.py                   <- 8kHz Audio Preprocessing Script
│   ├── train.py                        <- PyTorch Training Pipeline
│   ├── evaluate.py                     <- EER & Accuracy Evaluation Script
│   ├── export_onnx.py                  <- PyTorch to ONNX Exporter
│   └── usb_gui_server.py               <- Flask Web Server & ADB Telemetry Reader
└── android_app/                        <- Native Kotlin Android Studio Project
    └── app/src/main/java/com/deepfake/interception/
        ├── CallOverlayService.kt       <- Floating UI Overlay Foreground Service
        ├── AudioProcessor.kt           <- AudioRecord, Noise Gate & Sliding Window
        ├── DeepfakeClassifier.kt       <- ONNX Runtime Android Engine
        ├── CallStateReceiver.kt        <- System Call State Receiver
        └── MainActivity.kt             <- Permissions & Main Activity
```
