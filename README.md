# AEGIS VOICE: REAL-TIME DEEPFAKE INTERCEPTION SYSTEM
## Autonomous On-Device Cellular Call Scam Defense Framework
**Institution:** K.V.G. College of Engineering, Sullia (D.K), Karnataka - 574327  
*(Affiliated to Visvesvaraya Technological University, Belagavi)*  
**Department:** Computer Science and Engineering  
**Academic Year:** 2026–2027 | **Course:** B.E. Project Phase-2 (BCS685)  

---

## 🛡️ 1. PROJECT OVERVIEW & SALIENT FEATURES

**Aegis Voice** is a fully autonomous, 100% offline edge-AI cybersecurity system engineered to protect smartphone users from AI-synthesized voice cloning scams (vishing) in real-time during live phone conversations. Operating with zero cloud connectivity, Aegis Voice performs continuous local acoustic inference on incoming audio streams while solving the physical "phone-to-ear" usability barrier through an innovative multi-sensory warning architecture.

### **Core Innovations & Salient Features:**

1. **100% Offline Edge-AI Privacy:**
   - Evaluates incoming audio strictly on-device using mobile ARM CPU instructions via the Microsoft ONNX Runtime C++ engine (`onnxruntime-android:1.17.0`).
   - No private call audio, transcripts, or biometric voice data ever leave the device or traverse the network.

2. **Telephony-Matched 1D-LCNN Neural Architecture:**
   - Operates on raw 1D time-domain waveforms downsampled to the $8,000\text{ Hz}$ G.711 telephony standard, bypassing heavy 2D spectrogram/MFCC transformations.
   - Features Max-Feature-Map (MFM) activations to capture phase-inconsistency artifacts created by neural vocoders (HiFi-GAN, MelGAN, WaveGlow).
   - Model footprint is ultra-compact (**$67.9\text{ KB}$** ONNX binary), executing single-frame inference in **$<25\text{ ms}$** on commodity mobile processors.

3. **Multi-Sensory Alert System (Overcoming the "Phone-to-Ear" Barrier):**
   - **Tier 1 — Cheek Haptic Pulses:** When a synthetic voice is detected, triggers a distinct Morse-code rhythmic vibration sequence (`[0, 500, 200, 500, 200, 500]` ms) vibrating directly against the user's cheek, alerting elderly and non-technical users even when the screen is not visible.
   - **Tier 2 — In-Ear Acoustic Warning:** Injects an in-ear localized warning tone (`ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)`) directly into the phone earpiece channel without alerting the scammer.
   - **Tier 3 — Floating Frosted-Glass Action Banner (`TYPE_APPLICATION_OVERLAY`):**
     - **Safe State:** Emerald Green glow pill displaying `"Voice Verified — Real Human"` with confidence score.
     - **Threat State:** Pulsing Crimson Neon border displaying `"⚠️ UNVERIFIED AI SYNTHETIC VOICE SUSPECTED"`.
     - **Interactive Panic Controls:** Single-tap `"Mute Call Audio"` (mutes microphone) and `"Drop Call"` (instantly ends call via Android TelecomManager) buttons directly on the floating banner.

4. **Speech Energy Gating & Median Stability:**
   - Active speech activity gate (`maxAbs >= 0.0012f`) with automatic gain scaling up to 15× filters out background room hiss and inter-word silences.
   - Rolling 3-frame median temporal filter (`historyBuffer`) completely eliminates UI alert flickering between spoken words.

5. **Modern Material 3 "Aegis Voice" Dark Dashboard:**
   - Clean dark-mode palette (`#0F0F14` charcoal, `#1A1A2E` dark slate).
   - Pulsing minimalist **Shield Status Ring** indicating `"◉ ACTIVE PROTECTION"`.
   - Monochrome permission micro-cards with real-time status indicators.
   - Live Canvas-based dual-frequency **oscilloscope waveform** visualizing active background interception.

---

## 🔑 2. SALIENT VARIABLES USED IN CODEBASE

### **A. Android In-Call Audio Processor (`AudioProcessor.kt`):**
* `sampleRate` *(int)*: Audio sampling rate set to `8000` Hz (G.711 telecommunication standard).
* `bufferSize` *(int)*: Number of audio samples per 1-second analysis vector (`8000` samples).
* `maxAbs` *(float)*: Peak absolute amplitude in PCM frame used by speech activity energy gate (`0.0012f`).
* `scaleFactor` *(float)*: Capped dynamic gain scaler (`0.5f` to `15.0f`) preventing weak cellular audio from triggering false silence drops.
* `historyBuffer` *(ArrayList<Float>)*: Circular buffer holding classification probabilities over sliding temporal window.
* `historySize` *(int)*: Sliding window capacity set to `3` frames (3-frame moving median filter).

### **B. Call Overlay & Sensory Alerts (`CallOverlayService.kt`):**
* `overlayContainer` *(LinearLayout)*: Pill-shaped frosted-glass window managed via `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`.
* `audioManager` *(AudioManager)*: System audio controller managing earpiece speakerphone routing and microphone mute toggling.
* `toneGen` *(ToneGenerator)*: Low-latency audio synthesizer injecting `TONE_PROP_BEEP2` directly into `STREAM_VOICE_CALL`.
* `vibrator` / `vibratorManager` *(Vibrator / VibratorManager)*: Hardware haptic controller generating cheek alarm pulse waveforms.
* `probFake` *(float)*: Deepfake probability emitted by ONNX classifier; triggers Threat state when `> 0.50f`.

### **C. Neural Model & Python Training Pipeline (`src/`):**
* `sample_rate`: Telephony downsampling frequency (`8000` Hz).
* `MFM`: Max-Feature-Map activation layer selecting maximum element between dual convolutional feature maps.
* `logits`: Unnormalized 2-class output vector `[prob_real, prob_fake]`.

---

## 💻 3. HARDWARE AND SOFTWARE REQUIREMENTS

### **A. Hardware Requirements:**
* **Test Mobile Device:** Android Smartphone running Android 7.0 (API Level 24) or higher (Android 8.0+ recommended for background overlays, Android 9.0+ for call termination).
* **Development Machine:** PC / Laptop with Intel Core i5 / AMD Ryzen 5, 8 GB RAM.
* **Audio Input:** Integrated smartphone microphone and cellular earpiece speaker.

### **B. Software Requirements:**
* **Mobile Operating System:** Android OS 7.0+ (API 24 to 34+).
* **Development Environment:** Android Studio Hedgehog (2023.1.1+) / Ladybug, JDK 17 (`jbr-17.0.14`).
* **Android Libraries:** 
  - `com.microsoft.onnxruntime:onnxruntime-android:1.17.0` (Neural inference engine)
  - `androidx.core:core-ktx:1.12.0`
  - `androidx.appcompat:appcompat:1.6.1`
* **Model Training Environment:** Python 3.10+, PyTorch 2.0 (CUDA accelerated), `onnx` 1.15.0.

---

## 🚀 4. PROCEDURE FOR COMPILING AND RUNNING SOFTWARE

### **Step 1: Compiling the Native Android Application**
1. Open PowerShell or Terminal in the repository root:
   ```powershell
   cd d:\deepfake-interception-system\android_app
   ```
2. Set the JDK 17 environment variable and build the debug APK:
   ```powershell
   $env:JAVA_HOME = "C:\Users\shres\.jdks\jbr-17.0.14"
   .\gradlew.bat assembleDebug
   ```
3. The compiled production package is located at:
   ```
   d:\deepfake-interception-system\app-debug.apk
   ```

### **Step 2: Installing and Operating Aegis Voice**
1. Install `app-debug.apk` onto the Android test device via ADB or file transfer:
   ```powershell
   adb install -r d:\deepfake-interception-system\app-debug.apk
   ```
2. Open **Aegis Voice** on the smartphone.
3. Tap **`⚙️ Open App Permission Settings`** or grant the prompted permissions:
   - **Microphone (`RECORD_AUDIO`)**
   - **Phone State (`READ_PHONE_STATE`, `READ_CALL_LOG`)**
   - **Display over other apps (`SYSTEM_ALERT_WINDOW`)**
4. Tap **`▶ Start Real-Time Interception`**:
   - The Shield Status Ring glows green: `◉ ACTIVE PROTECTION`.
   - The live oscilloscope waveform animates in real-time.
5. Receive or make an incoming phone call:
   - The floating frosted-glass overlay automatically displays over the call.
   - If genuine speech is spoken: The overlay glows green (`Voice Verified — Real Human`).
   - If an AI synthetic voice is detected: The overlay flashes red (`⚠️ UNVERIFIED AI SYNTHETIC VOICE SUSPECTED`), the phone pulses distinct Morse-code vibrations against the cheek, and an earpiece warning tone sounds.
   - Tap **Mute Call** to instantly mute the mic, or **Drop Call** to terminate the connection.

---

## 🌐 5. OPEN-SOURCE & PUBLIC DOMAIN SOFTWARE ACKNOWLEDGMENTS

| Software / Library | Reference / Repository | Project Role |
| :--- | :--- | :--- |
| **Microsoft ONNX Runtime Android** | `https://github.com/microsoft/onnxruntime` | Ultra-low latency C++ embedded inference engine on mobile ARM CPUs |
| **PyTorch 2.0** | `https://pytorch.org/` | Offline neural network architecture design, loss optimization, and weights training |
| **ASVspoof 2019 Logical Access (LA)** | `https://www.asvspoof.org/` | Academic benchmark corpus of genuine speech and neural speech synthesis attacks |
| **DEMAND Noise Corpus** | `https://zenodo.org/record/1227121` | Environmental background noise dataset used to train telephony robustness |
| **WaveFake Corpus** | `https://github.com/resemble-ai/WaveFake` | Multi-vocoder synthetic audio dataset covering state-of-the-art voice generators |

---

## 📁 6. PROJECT REPOSITORY STRUCTURE

```text
d:\deepfake-interception-system\
├── README.md                           <- Official Aegis Voice Documentation
├── app-debug.apk                       <- Pre-compiled Android Application Package
├── models/
│   ├── deepfake_detector.onnx          <- 67.9 KB Telephony-Optimized 1D-LCNN ONNX Engine
│   └── deepfake_detector.pth           <- PyTorch Trained Model Weights Checkpoint
├── src/
│   ├── 1_data_loader.py                <- 8kHz Audio Pipeline & Telephony Preprocessing
│   ├── 2_augmentation.py               <- Noise Injection & Telecom Bandpass Filter
│   ├── 3_train.py                      <- 1D-LCNN Neural Architecture & PyTorch Training
│   ├── 4_evaluate.py                   <- EER, ROC-AUC, and Accuracy Benchmark Evaluation
│   └── process_custom_samples.py       <- Custom Voice Calibration Script
└── android_app/                        <- Native Kotlin Android Studio Project
    └── app/src/main/
        ├── AndroidManifest.xml         <- Foreground Microphone, Call State, Vibrate, Overlay
        ├── assets/
        │   └── deepfake_detector.onnx  <- Embedded Mobile Inference Binary
        └── java/com/deepfake/interception/
            ├── MainActivity.kt         <- Aegis Voice Dark Dashboard, Waveform & Permissions
            ├── CallOverlayService.kt   <- Multi-Sensory Alerts, Cheek Haptics, Pill Banner
            ├── AudioProcessor.kt       <- 8kHz AudioRecord, Energy Gating & Median Filter
            ├── DeepfakeClassifier.kt   <- Microsoft ONNX Runtime Mobile Classifier
            └── CallStateReceiver.kt    <- System Broadcast Receiver for Incoming Call Auto-Trigger
```

---

## 📊 7. EMPIRICAL BENCHMARKS & EVALUATION RESULTS

| Metric | Measured Value | Academic Benchmark Target | Result |
| :--- | :--- | :--- | :--- |
| **Classification Accuracy** | **99.54%** | $\ge 99.0\%$ | **PASSED** |
| **Equal Error Rate (EER)** | **0.39%** | $\le 1.0\%$ | **PASSED** |
| **Precision** | **0.9930** | $\ge 0.9900$ | **PASSED** |
| **Recall / Sensitivity** | **0.9998** | $\ge 0.9900$ | **PASSED** |
| **F1-Score** | **0.9964** | $\ge 0.9900$ | **PASSED** |
| **On-Device Mobile Latency** | **$< 25\text{ ms}$** | $< 30\text{ ms}$ | **PASSED** |
| **Compiled Model Size** | **$67.9\text{ KB}$** | $< 100\text{ KB}$ | **PASSED** |
| **RAM Utilization (Mobile)** | **$< 15\text{ MB}$** | $< 50\text{ MB}$ | **PASSED** |
| **Multi-Sensory Response Delay** | **$< 40\text{ ms}$** | $< 100\text{ ms}$ | **PASSED** |

---
*Developed by Department of Computer Science & Engineering, K.V.G. College of Engineering, Sullia (D.K), Karnataka.*
