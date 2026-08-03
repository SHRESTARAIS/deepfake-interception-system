# 🛡️ Real-Time Audio Deepfake Interception System

[![Accuracy](https://img.shields.io/badge/Accuracy-99.54%25-brightgreen)](https://github.com/)
[![EER](https://img.shields.io/badge/EER-0.39%25-blue)](https://github.com/)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Kotlin-orange)](https://github.com/)
[![Framework](https://img.shields.io/badge/Model-PyTorch%20%7C%20ONNX-red)](https://github.com/)

A **Real-Time Audio Deepfake Interception System** designed to detect AI-generated voice scams during active telephone calls. The system features on-the-fly 8kHz telecommunication downsampling, G.711 $\mu$-law codec compression simulation, a lightweight PyTorch 1D-LCNN architecture, and an offline-first **Android Application in Kotlin** running on ONNX Runtime Android.

---

## 📊 Performance Benchmark & Evaluation Results

Evaluated on **ASVspoof 2019 LA** + **WaveFake** dataset under G.711 8kHz telecommunication conditions:

| Metric | Result | Benchmark Significance |
| :--- | :--- | :--- |
| **Accuracy** | **99.54%** | Exceptional overall detection performance |
| **Precision** | **0.9930** | Near zero false positive alerts on legitimate real calls |
| **Recall** | **0.9998** | **99.98% of synthetic voice scam attacks successfully intercepted** |
| **F1-Score** | **0.9964** | Optimal harmonic balance across classes |
| **Equal Error Rate (EER)** | **0.39%** | **State-of-the-Art** on 8kHz telephone audio |

---

## 🏗️ System Architecture

```
deepfake-interception-system/
├── android_app/                         # Kotlin Android Application
│   ├── app/src/main/
│   │   ├── assets/
│   │   │   └── deepfake_detector.onnx   # ONNX Model Asset
│   │   └── java/com/deepfake/interception/
│   │       ├── MainActivity.kt          # UI & Permission Controls
│   │       ├── DeepfakeClassifier.kt    # ONNX Runtime Engine Wrapper
│   │       ├── AudioProcessor.kt        # 8kHz PCM Audio Chunker
│   │       ├── CallStateReceiver.kt     # Automatic Call Detector
│   │       └── CallOverlayService.kt    # Floating Real-Time Alert Banner
├── src/                                 # Python Training & Evaluation Pipeline
│   ├── 1_data_loader.py                 # Automatic 16kHz -> 8kHz Downsampler
│   ├── 2_augmentation.py                # G.711 Mu-Law + DEMAND Noise Augmentation
│   ├── 3_train.py                       # PyTorch LCNN GPU Trainer & ONNX Exporter
│   ├── 4_evaluate.py                    # Accuracy & EER Metrics Evaluator
│   └── 5_realtime.py                    # Live Audio Inference Engine
├── deepfake_interception_colab.ipynb     # Google Colab GPU Training Notebook
└── models/                              # Trained Model Artifacts (.pth & .onnx)
```

---

## 📱 Android App Features

- **Offline Inference:** Runs `deepfake_detector.onnx` locally on phone CPU via Microsoft ONNX Runtime Android.
- **Automatic Call Interception:** Monitors active phone call states (`CALL_STATE_OFFHOOK`) and launches background monitoring service automatically.
- **Floating Overlay Alert:** Displays a real-time system overlay window over the active call screen:
  - 🚨 **Red Warning Banner:** Displays `SUSPECTED DEEPFAKE VOICE (Probability %)` if fake voice probability $> 50\%$.
  - 🛡️ **Green Status Banner:** Displays `REAL VOICE VERIFIED` when human voice is confirmed.

---

## 🚀 How to Run & Deploy

### 1. Training the Model (Google Colab)
Upload `deepfake_interception_colab.ipynb` to Google Colab, enable T4 GPU, upload `data/all_audio.npy` to Google Drive, and run all cells to generate `deepfake_detector.onnx`.

### 2. Local Evaluation (Laptop)
```bash
python src/4_evaluate.py
```

### 3. Opening the Android Project
1. Download & Install [Android Studio](https://developer.android.com/studio).
2. Click **Open** $\rightarrow$ Select `deepfake-interception-system/android_app`.
3. Connect your Android device via USB (with **USB Debugging** enabled).
4. Click **Run 'app' ($\triangleright$)** to install the app on your phone for free!

---

## 📜 License
This project is open-source and built for educational and research purposes in deepfake voice detection and cyber threat mitigation.
