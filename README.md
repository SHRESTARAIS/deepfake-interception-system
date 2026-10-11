<div align="center">

# 🛡️ Aegis Voice
### Autonomous On-Device Cellular Deepfake Speech Interception & Multi-Sensory Mitigation Framework

[![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#)
[![Language](https://img.shields.io/badge/Language-Kotlin%20%7C%20Python-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](#)
[![AI Runtime](https://img.shields.io/badge/AI%20Engine-ONNX%20Runtime%20C%2B%2B-005CED?style=for-the-badge&logo=onnx&logoColor=white)](#)
[![Model Footprint](https://img.shields.io/badge/Model%20Size-67.9%20KB-success?style=for-the-badge)](#)
[![Latency](https://img.shields.io/badge/Latency-%3C%2025ms-orange?style=for-the-badge)](#)
[![Accuracy](https://img.shields.io/badge/Accuracy-99.54%25-brightgreen?style=for-the-badge)](#)
[![EER](https://img.shields.io/badge/EER-0.39%25-blue?style=for-the-badge)](#)
[![Edge Privacy](https://img.shields.io/badge/Privacy-100%25%20Offline%20Edge--AI-red?style=for-the-badge)](#)

<p align="center">
  <b>A production-ready Android security system that intercepts telecommunication voice cloning scams (vishing) in real-time during live cellular calls — featuring ultra-lightweight 1D-LCNN inference, tactile cheek Morse-code haptics, in-ear acoustic warnings, and single-tap call termination.</b>
</p>

<p align="center">
  <img src="assets/screenshots/linkedin_featured_banner.jpg" width="880" alt="Aegis Voice System Showcase" />
</p>

[Key Innovations](#-key-innovations--salient-features) • [Production Screenshots](#-production-screenshots--in-call-walkthrough) • [System Architecture](#-system-architecture--pipeline) • [Empirical Benchmarks](#-empirical-benchmarks) • [Quickstart](#-quickstart--installation)

</div>

---

## 📱 Production Screenshots & In-Call Walkthrough

<div align="center">

| 1. Permissions Setup | 2. Protection Armed | 3. In-Call: Human Verified | 4. In-Call: Deepfake Intercepted |
| :---: | :---: | :---: | :---: |
| <img src="assets/screenshots/01_permissions_setup.jpg" width="220" alt="Permissions Setup" /> | <img src="assets/screenshots/02_active_protection_armed.jpg" width="220" alt="Active Protection Armed" /> | <img src="assets/screenshots/03_incall_verified_safe.jpg" width="220" alt="Live Call Verified Safe" /> | <img src="assets/screenshots/04_incall_threat_interception.jpg" width="220" alt="Live Call Threat Intercepted" /> |
| **Pre-Flight Configuration**<br>Status micro-cards verify Microphone, Phone State, and Overlay permissions. | **Active Protection Armed**<br>1D-LCNN engine online in RAM (`< 15 MB`), ready for incoming phone calls. | **Authentic Voice (Safe)**<br>Frosted-glass emerald pill displays `Voice Verified — Real Human (89%)`. | **Threat Interception (Mitigate)**<br>Crimson banner (`99% Fake`), cheek haptics + **Mute Call** & **Drop Call** actions. |

</div>

---

## 🛡️ Key Innovations & Salient Features

### 1. 100% Offline Edge-AI Privacy (Zero Cloud Dependency)
* Standard forensic detectors upload call audio to cloud servers, introducing severe latency ($500\text{ ms} - 2000\text{ ms}$) and exposing private conversations to wiretapping risks.
* **Aegis Voice** executes inference completely on-device via mobile ARM CPU vector instructions using the embedded Microsoft ONNX Runtime C++ engine (`onnxruntime-android:1.17.0`).
* **Zero bytes** of audio, transcripts, or telemetry ever leave the handset, ensuring absolute compliance with global data privacy regulations (including India's Digital Personal Data Protection Act).

### 2. Telephony-Matched 1D-LCNN Architecture
* Commercial voice models degrade drastically on cellular calls because telephony transcoding (G.711 $\mu$-law, AMR-NB) strips frequencies above $3,400\text{ Hz}$.
* Aegis Voice trains natively on $8,000\text{ Hz}$ raw time-domain waveforms, completely bypassing expensive 2D spectrogram or Mel-filterbank transformations.
* Utilizes **Max-Feature-Map (MFM)** non-linearities ($\text{MFM}(x) = \max(x_1, x_2)$) to isolate vocoder phase artifacts produced by state-of-the-art zero-shot speech synthesizers.
* **Model Size:** Ultra-compressed **$67.9\text{ KB}$** ONNX binary.
* **Execution Latency:** **$< 25\text{ ms}$** wall-clock time per 1-second audio frame.

### 3. Three-Tier Multi-Sensory Alert Pipeline (Solving the "Phone-to-Ear" Barrier)
During a live telephone call, the user holds the device against their cheek, making traditional visual screen notifications completely invisible. Aegis Voice solves this fundamental usability flaw with a coordinated three-tier warning mechanism:
* **Tier 1 — Cheek Morse-Code Haptics:** Pulses a distinct tactile warning pattern (`[0, 500, 200, 500, 200, 500]` ms) vibrating directly against the cheekbone, warning the user without requiring them to look at the display.
* **Tier 2 — In-Ear Warning Tone Injection:** Injects an audible acoustic double-beep (`TONE_PROP_BEEP2`) directly into the active earpiece audio channel (`AudioManager.STREAM_VOICE_CALL`) for 400 ms.
* **Tier 3 — Floating Frosted-Glass Overlay (`TYPE_APPLICATION_OVERLAY`):**
  * **Safe State:** Emerald Green glow pill displaying `Voice Verified — Real Human`.
  * **Threat State:** Flashing Crimson Neon border displaying `UNVERIFIED AI SYNTHETIC VOICE SUSPECTED (99%)`.

### 4. Interactive In-Call Fraud Countermeasures
Rather than acting solely as a passive warning banner, Aegis Voice empowers users with immediate, single-tap physical mitigations directly on the floating banner:
* 🔇 **Panic Mute (`MUTE CALL`):** Instantly cuts user microphone transmission (`audioManager.isMicrophoneMute = true`), preventing fraudsters from eliciting confidential financial data or recording user voice samples for cloning.
* 🛑 **Instant Call Drop (`DROP CALL`):** Immediately terminates the fraudulent cellular connection via native Android Telecom APIs (`TelecomManager.endCall()`).

### 5. Conversational Hysteresis & Energy Gating
* **Active Speech Energy Noise Gate:** Evaluates peak absolute amplitude (`maxAbs >= 0.0012f`) with dynamic amplitude gain scaling (up to $15\times$) to bypass inter-word silence and ambient background hiss, preserving CPU cycles and battery.
* **3-Frame Temporal Median Filter:** Smooths raw inference probabilities across a sliding circular window (`historyBuffer`), completely eliminating false-positive alert fluttering during normal conversational pauses.

---

## 🏗️ System Architecture & Pipeline

```
  ┌────────────────────────────────────────────────────────────────────────┐
  │                        1. Audio Capture Stage                          │
  │     In-Call Telephony Audio Stream (8,000 Hz, 16-bit PCM Mono)         │
  │          Hardware Speakerphone Auto-Routing for Microphone Capture     │
  └───────────────────────────────────┬────────────────────────────────────┘
                                      │
                                      ▼
  ┌────────────────────────────────────────────────────────────────────────┐
  │                        2. Pre-Processing Stage                         │
  │           DC-Offset Mean Subtraction & Hardware Bias Centering          │
  │       Active Speech Energy Gating (maxAbs >= 0.0012f, Gain Capped 15x) │
  └───────────────────────────────────┬────────────────────────────────────┘
                                      │
                                      ▼
  ┌────────────────────────────────────────────────────────────────────────┐
  │                   3. 1D-LCNN AI Classification Engine                  │
  │       Raw 1D Waveform Input ([1, 1, 8000] Tensor, No Spectrogram)      │
  │        3-Layer Conv1D + Max-Feature-Map (MFM) + Global Pooling         │
  │              Local ONNX Runtime C++ Inference (< 25 ms)                │
  └───────────────────────────────────┬────────────────────────────────────┘
                                      │
                                      ▼
  ┌────────────────────────────────────────────────────────────────────────┐
  │                      4. Temporal Median Smoothing                      │
  │             3-Frame Circular Window Buffer (historyBuffer)             │
  │            Moving Median Probability Calculation (Hysteresis)          │
  └───────────────────────────────────┬────────────────────────────────────┘
                                      │
                                      ▼
  ┌────────────────────────────────────────────────────────────────────────┐
  │                 5. Multi-Sensory Warning & Action Layer                │
  │   • Tier 1: Morse-Code Haptic Pulses (Cheek Physical Warning)          │
  │   • Tier 2: In-Ear Tone Injection (STREAM_VOICE_CALL Dual Beep)        │
  │   • Tier 3: Frosted-Glass Pill Banner with [MUTE] & [DROP CALL]        │
  └────────────────────────────────────────────────────────────────────────┘
```

---

## 📊 Empirical Benchmarks

The system was evaluated on telephony-degraded speech combined with the ASVspoof 2019 Logical Access (LA) and DEMAND environmental noise corpora downsampled to $8\text{ kHz}$ G.711 format:

| Performance Metric | Measured Value | Academic Benchmark Target | Evaluation Status |
| :--- | :---: | :---: | :---: |
| **Classification Accuracy** | **99.54%** | $\ge 99.0\%$ | **PASSED** |
| **Equal Error Rate (EER)** | **0.39%** | $\le 1.0\%$ | **PASSED** |
| **Precision** | **0.9930** | $\ge 0.9900$ | **PASSED** |
| **Recall / Sensitivity** | **0.9998** | $\ge 0.9900$ | **PASSED** |
| **F1-Score** | **0.9964** | $\ge 0.9900$ | **PASSED** |
| **On-Device Inference Latency** | **$< 25\text{ ms}$** | $< 30\text{ ms}$ | **PASSED** |
| **Compiled Model Footprint** | **$67.9\text{ KB}$** | $< 100\text{ KB}$ | **PASSED** |
| **Application RAM Overhead** | **$< 15\text{ MB}$** | $< 50\text{ MB}$ | **PASSED** |
| **Active CPU Utilization** | **$< 5.0\%$** | $< 10.0\%$ | **PASSED** |
| **Sensory Alert Dispatch Delay** | **$< 40\text{ ms}$** | $< 100\text{ ms}$ | **PASSED** |

---

## 📂 Repository Structure

```text
deepfake-interception-system/
├── assets/
│   └── screenshots/
│       ├── 01_permissions_setup.jpg          <- Setup screen with permission indicators
│       ├── 02_active_protection_armed.jpg    <- Armed screen with 1D-LCNN online
│       ├── 03_incall_verified_safe.jpg       <- In-call verified authentic voice banner
│       └── 04_incall_threat_interception.jpg <- In-call deepfake intercepted with controls
├── models/
│   ├── deepfake_detector.onnx                <- 67.9 KB Telephony-Trained 1D-LCNN Binary
│   └── deepfake_detector.pth                 <- PyTorch Checkpoint
├── src/
│   ├── 1_data_loader.py                      <- 8kHz Audio Pipeline & G.711 Transcoding
│   ├── 2_augmentation.py                     <- Environmental Noise Superimposition
│   ├── 3_train.py                            <- 1D-LCNN Model Architecture & PyTorch Training
│   └── 4_evaluate.py                         <- EER, ROC-AUC, and Benchmark Profiling
├── android_app/                              <- Production Kotlin Android Studio Project
│   └── app/src/main/
│       ├── AndroidManifest.xml               <- AudioRecord, Call State, Vibrate, Overlay
│       ├── assets/deepfake_detector.onnx     <- Embedded Mobile ONNX Model
│       └── java/com/deepfake/interception/
│           ├── MainActivity.kt               <- Material 3 Dark UI, Oscilloscope & Setup
│           ├── CallOverlayService.kt         <- Multi-Sensory Alerts, Cheek Haptics & Pill Banner
│           ├── AudioProcessor.kt             <- 8kHz AudioRecord, Energy Gating & Median Filter
│           ├── DeepfakeClassifier.kt         <- Microsoft ONNX Runtime Mobile Classifier
│           └── CallStateReceiver.kt          <- Telephony Broadcast Receiver for Auto-Trigger
└── app-debug.apk                             <- Pre-Compiled Standalone Android Package
```

---

## 🚀 Quickstart & Installation

### Option 1: Install Pre-Compiled APK Directly
1. Connect your Android smartphone running Android 7.0 or higher.
2. Install the standalone APK via ADB:
   ```bash
   adb install -r app-debug.apk
   ```
3. Open **Aegis Voice** on your phone, grant the three core permissions (Microphone, Phone State, Display Overlay), and tap **Start Real-Time Interception**.

### Option 2: Build from Source via Android Studio
1. Clone this repository:
   ```bash
   git clone https://github.com/SHRESTARAIS/deepfake-interception-system.git
   cd deepfake-interception-system/android_app
   ```
2. Build the project using Gradle:
   ```bash
   ./gradlew assembleDebug
   ```
3. Find the compiled APK at `app/build/outputs/apk/debug/app-debug.apk`.

---

## 🛠️ Tech Stack & Dependencies

* **Mobile OS:** Android 7.0+ (API 24 to 34+)
* **Programming Languages:** Kotlin 1.9.0, Python 3.10+, C++ (JNI / ONNX Runtime)
* **Edge Inference Engine:** `com.microsoft.onnxruntime:onnxruntime-android:1.17.0`
* **Deep Learning Frameworks:** PyTorch 2.0, ONNX 1.15.0, Torchaudio
* **Benchmark Datasets:** ASVspoof 2019 Logical Access (LA), DEMAND Noise Corpus, WaveFake Multi-Vocoder

---

## 👤 Author & Lead Engineer

**Shresta Rai S**  
*System Architecture, 1D-LCNN Neural Network Design, & Android Edge Implementation*  
[![GitHub](https://img.shields.io/badge/GitHub-SHRESTARAIS-181717?style=flat&logo=github)](https://github.com/SHRESTARAIS)  
[![LinkedIn](https://img.shields.io/badge/LinkedIn-Connect-0A66C2?style=flat&logo=linkedin)](https://www.linkedin.com/in/shresta-rai-s-)

> *Developed as a B.E. Capstone Research Project under the academic guidance of Prof. Naseema C. A., Department of Computer Science & Engineering, K.V.G. College of Engineering (Affiliated to Visvesvaraya Technological University, Belagavi).*

---

## 📄 License
This project is developed for academic research and edge-AI mobile cybersecurity advancement under the MIT License.
