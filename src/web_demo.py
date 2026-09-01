import os
import io
import time
import numpy as np
import librosa
import soundfile as sf
import scipy.io.wavfile
from scipy.signal import resample_poly
import onnxruntime as ort
from flask import Flask, request, jsonify, render_template_string

app = Flask(__name__)

# Load trained ONNX model
MODEL_PATH = "models/deepfake_detector.onnx"
if not os.path.exists(MODEL_PATH):
    MODEL_PATH = "../models/deepfake_detector.onnx"

session = ort.InferenceSession(MODEL_PATH)
input_name = session.get_inputs()[0].name
output_name = session.get_outputs()[0].name

SAMPLE_RATE = 8000
SAMPLES = 8000

def preprocess_audio_chunks(file_bytes):
    """Extract active spoken 1:1 PCM audio chunks, filtering out silent room noise before/after recording"""
    try:
        audio = None
        orig_sr = 8000

        # Tier 1: SoundFile Loader
        buf = io.BytesIO(file_bytes)
        try:
            audio, orig_sr = sf.read(buf)
            if audio.ndim > 1:
                audio = np.mean(audio, axis=1)
        except Exception:
            # Tier 2: Scipy WAV Loader
            try:
                buf = io.BytesIO(file_bytes)
                orig_sr, int_data = scipy.io.wavfile.read(buf)
                if int_data.ndim > 1:
                    int_data = np.mean(int_data, axis=1)
                audio = int_data.astype(np.float32) / 32768.0
            except Exception:
                # Tier 3: Librosa Loader
                try:
                    buf = io.BytesIO(file_bytes)
                    audio, orig_sr = librosa.load(buf, sr=None, mono=True)
                except Exception:
                    # Tier 4: Direct Raw PCM Parser
                    try:
                        int_data = np.frombuffer(file_bytes[44:], dtype=np.int16)
                        if len(int_data) > 100:
                            audio = int_data.astype(np.float32) / 32768.0
                            orig_sr = 8000
                    except Exception as e4:
                        print(f"All audio decoders failed: {e4}")

        if audio is None or len(audio) == 0:
            return None

        # Save last tested audio payload for instant analysis
        try:
            os.makedirs("scratch", exist_ok=True)
            sf.write("scratch/last_uploaded_audio.wav", audio, orig_sr)
        except Exception as e_save:
            pass

        # Resample to 8000 Hz if needed
        if orig_sr != SAMPLE_RATE and orig_sr > 0:
            audio = resample_poly(audio, SAMPLE_RATE, orig_sr)

        audio = audio.astype(np.float32)
        audio = audio - np.mean(audio)

        # Pad if shorter than 1 second (8000 samples)
        if len(audio) < SAMPLES:
            return [np.pad(audio, (0, SAMPLES - len(audio)))]

        # Extract 1-second chunks with 50% overlap (4000 sample stride)
        chunks = []
        stride = 4000
        for start in range(0, len(audio) - SAMPLES + 1, stride):
            segment = audio[start : start + SAMPLES]
            max_abs = np.max(np.abs(segment))
            rms = np.sqrt(np.mean(segment**2))
            # Active Spoken Chunk Trim: Filter out silent room noise before/after speech
            if max_abs >= 0.020 and rms >= 0.008:
                chunks.append(segment)

        # Fallback if quiet/soft speaking
        if len(chunks) == 0:
            for start in range(0, len(audio) - SAMPLES + 1, stride):
                segment = audio[start : start + SAMPLES]
                if np.max(np.abs(segment)) >= 0.008:
                    chunks.append(segment)

        if len(chunks) == 0:
            chunks.append(audio[:SAMPLES])

        return chunks
    except Exception as e:
        print(f"Preprocessing error: {e}")
        return None

HTML_TEMPLATE = """
<!DOCTYPE html>
<html lang="en" data-theme="dark">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Deepfake Interceptor - AI Voice Security System</title>
    <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <style>
        :root[data-theme="dark"] {
            --bg-dark: #090d16;
            --sidebar-bg: rgba(15, 23, 42, 0.7);
            --card-bg: rgba(30, 41, 59, 0.5);
            --card-border: rgba(255, 255, 255, 0.08);
            --primary: #6366f1;
            --primary-glow: rgba(99, 102, 241, 0.4);
            --success: #10b981;
            --success-glow: rgba(16, 185, 129, 0.3);
            --danger: #f43f5e;
            --danger-glow: rgba(244, 63, 94, 0.4);
            --text-main: #f8fafc;
            --text-muted: #94a3b8;
            --table-header: rgba(15, 23, 42, 0.8);
            --body-bg: radial-gradient(at 0% 0%, rgba(99, 102, 241, 0.15) 0px, transparent 50%),
                       radial-gradient(at 100% 100%, rgba(244, 63, 94, 0.1) 0px, transparent 50%);
        }

        :root[data-theme="light"] {
            --bg-dark: #f8fafc;
            --sidebar-bg: #ffffff;
            --card-bg: #ffffff;
            --card-border: #e2e8f0;
            --primary: #4f46e5;
            --primary-glow: rgba(79, 70, 229, 0.2);
            --success: #059669;
            --success-glow: rgba(5, 150, 105, 0.2);
            --danger: #e11d48;
            --danger-glow: rgba(225, 29, 72, 0.2);
            --text-main: #0f172a;
            --text-muted: #64748b;
            --table-header: #f1f5f9;
            --body-bg: none;
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
            font-family: 'Outfit', sans-serif;
        }

        body {
            background-color: var(--bg-dark);
            background-image: var(--body-bg);
            color: var(--text-main);
            min-height: 100vh;
            display: flex;
            overflow-x: hidden;
            transition: background-color 0.3s ease, color 0.3s ease;
        }

        /* Sidebar Menu */
        .sidebar {
            width: 260px;
            background: var(--sidebar-bg);
            backdrop-filter: blur(20px);
            border-right: 1px solid var(--card-border);
            display: flex;
            flex-direction: column;
            padding: 2rem 1.25rem;
            z-index: 10;
        }

        .brand {
            display: flex;
            align-items: center;
            gap: 0.75rem;
            margin-bottom: 3rem;
        }

        .brand-icon {
            width: 42px;
            height: 42px;
            background: linear-gradient(135deg, var(--primary), #a855f7);
            border-radius: 12px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 1.25rem;
            color: white;
            box-shadow: 0 0 20px var(--primary-glow);
        }

        .brand-title {
            font-weight: 700;
            font-size: 1.2rem;
            letter-spacing: -0.02em;
        }

        .menu-label {
            font-size: 0.75rem;
            text-transform: uppercase;
            letter-spacing: 0.1em;
            color: var(--text-muted);
            margin-bottom: 0.75rem;
            font-weight: 600;
        }

        .nav-item {
            display: flex;
            align-items: center;
            gap: 1rem;
            padding: 0.85rem 1rem;
            color: var(--text-muted);
            border-radius: 12px;
            cursor: pointer;
            transition: all 0.2s ease;
            margin-bottom: 0.5rem;
            font-weight: 500;
        }

        .nav-item:hover, .nav-item.active {
            color: var(--text-main);
            background: rgba(99, 102, 241, 0.15);
            border: 1px solid rgba(99, 102, 241, 0.3);
        }

        .nav-item.active i {
            color: var(--primary);
        }

        /* Main Content */
        .main-wrapper {
            flex: 1;
            padding: 2.5rem 3rem;
            overflow-y: auto;
        }

        .top-bar {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 2.5rem;
        }

        .page-header h2 {
            font-size: 1.8rem;
            font-weight: 700;
        }

        .page-header p {
            color: var(--text-muted);
            font-size: 0.95rem;
            margin-top: 0.25rem;
        }

        .right-controls {
            display: flex;
            align-items: center;
            gap: 1rem;
        }

        /* Theme Switcher Toggle */
        .theme-switcher {
            display: flex;
            background: var(--card-bg);
            border: 1px solid var(--card-border);
            padding: 0.25rem;
            border-radius: 999px;
            gap: 0.2rem;
        }

        .theme-btn {
            border: none;
            background: transparent;
            color: var(--text-muted);
            padding: 0.4rem 0.75rem;
            border-radius: 999px;
            cursor: pointer;
            font-size: 0.85rem;
            font-weight: 600;
            display: flex;
            align-items: center;
            gap: 0.4rem;
            transition: all 0.2s ease;
        }

        .theme-btn.active {
            background: var(--primary);
            color: white;
            box-shadow: 0 2px 8px var(--primary-glow);
        }

        .status-pill {
            display: flex;
            align-items: center;
            gap: 0.5rem;
            background: rgba(16, 185, 129, 0.1);
            border: 1px solid var(--success-glow);
            color: var(--success);
            padding: 0.5rem 1rem;
            border-radius: 999px;
            font-size: 0.85rem;
            font-weight: 600;
        }

        .status-dot {
            width: 8px;
            height: 8px;
            background: var(--success);
            border-radius: 50%;
            box-shadow: 0 0 10px var(--success);
            animation: pulse 2s infinite;
        }

        @keyframes pulse {
            0% { opacity: 1; transform: scale(1); }
            50% { opacity: 0.5; transform: scale(1.2); }
            100% { opacity: 1; transform: scale(1); }
        }

        /* Dashboard Grid Layout */
        .dashboard-grid {
            display: grid;
            grid-template-columns: 1.2fr 0.8fr;
            gap: 2rem;
        }

        .card {
            background: var(--card-bg);
            border: 1px solid var(--card-border);
            backdrop-filter: blur(16px);
            border-radius: 20px;
            padding: 1.75rem;
            box-shadow: 0 10px 30px rgba(0, 0, 0, 0.05);
            margin-bottom: 1.5rem;
        }

        .card-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 1.5rem;
        }

        .card-title {
            font-size: 1.1rem;
            font-weight: 600;
            display: flex;
            align-items: center;
            gap: 0.6rem;
        }

        .clear-btn {
            background: rgba(244, 63, 94, 0.1);
            border: 1px solid var(--danger-glow);
            color: var(--danger);
            padding: 0.4rem 0.8rem;
            border-radius: 8px;
            font-size: 0.8rem;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s ease;
        }

        .clear-btn:hover {
            background: var(--danger);
            color: white;
        }

        /* Audio Recorder & Upload Controls */
        .control-tabs {
            display: flex;
            gap: 0.5rem;
            background: var(--card-border);
            padding: 0.35rem;
            border-radius: 12px;
            margin-bottom: 1.5rem;
        }

        .tab-btn {
            flex: 1;
            padding: 0.65rem;
            text-align: center;
            border: none;
            background: transparent;
            color: var(--text-muted);
            font-weight: 600;
            border-radius: 8px;
            cursor: pointer;
            transition: all 0.2s ease;
        }

        .tab-btn.active {
            background: var(--primary);
            color: white;
            box-shadow: 0 4px 12px var(--primary-glow);
        }

        .drop-zone {
            border: 2px dashed rgba(99, 102, 241, 0.4);
            border-radius: 16px;
            padding: 3rem 2rem;
            text-align: center;
            cursor: pointer;
            transition: all 0.3s ease;
            background: rgba(99, 102, 241, 0.03);
        }

        .drop-zone:hover {
            border-color: var(--primary);
            background: rgba(99, 102, 241, 0.08);
            transform: translateY(-2px);
        }

        .drop-icon {
            font-size: 2.5rem;
            color: var(--primary);
            margin-bottom: 1rem;
        }

        /* Live Canvas Waveform */
        .visualizer-box {
            background: var(--card-border);
            border-radius: 14px;
            height: 120px;
            margin-bottom: 1.5rem;
            overflow: hidden;
            position: relative;
        }

        canvas#waveformCanvas {
            width: 100%;
            height: 100%;
        }

        .btn-group {
            display: flex;
            gap: 1rem;
        }

        .action-btn {
            flex: 1;
            padding: 0.9rem 1.5rem;
            border-radius: 12px;
            border: none;
            font-weight: 600;
            font-size: 1rem;
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 0.6rem;
            transition: all 0.2s ease;
        }

        .btn-record {
            background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
            color: white;
            box-shadow: 0 10px 25px rgba(239, 68, 68, 0.3);
        }

        .btn-record:hover {
            transform: translateY(-2px);
            box-shadow: 0 15px 30px rgba(239, 68, 68, 0.4);
        }

        .btn-stop {
            background: linear-gradient(135deg, #64748b 0%, #475569 100%);
            color: white;
        }

        /* Detection Result Banner */
        .result-banner {
            padding: 1.25rem;
            border-radius: 14px;
            text-align: center;
            font-weight: 700;
            font-size: 1.25rem;
            margin-bottom: 1.5rem;
            display: none;
            animation: slideDown 0.4s cubic-bezier(0.16, 1, 0.3, 1);
        }

        @keyframes slideDown {
            from { opacity: 0; transform: translateY(-10px); }
            to { opacity: 1; transform: translateY(0); }
        }

        .banner-real {
            background: rgba(16, 185, 129, 0.15);
            border: 1px solid var(--success);
            color: var(--success);
            box-shadow: 0 0 30px var(--success-glow);
        }

        .banner-fake {
            background: rgba(244, 63, 94, 0.15);
            border: 1px solid var(--danger);
            color: var(--danger);
            box-shadow: 0 0 30px var(--danger-glow);
        }

        /* Metrics & Gauge */
        .metrics-grid {
            display: grid;
            grid-template-columns: repeat(2, 1fr);
            gap: 1rem;
            margin-bottom: 1.5rem;
        }

        .metric-box {
            background: var(--card-border);
            padding: 1rem;
            border-radius: 12px;
            text-align: center;
        }

        .metric-label {
            font-size: 0.8rem;
            color: var(--text-muted);
            margin-bottom: 0.25rem;
        }

        .metric-val {
            font-size: 1.4rem;
            font-weight: 700;
        }

        .chart-box {
            height: 220px;
            position: relative;
        }

        /* Table Design for Benchmarks & History */
        .data-table {
            width: 100%;
            border-collapse: collapse;
            margin-top: 1rem;
        }

        .data-table th, .data-table td {
            padding: 0.85rem 1rem;
            text-align: left;
            border-bottom: 1px solid var(--card-border);
        }

        .data-table th {
            background: var(--table-header);
            color: var(--text-muted);
            font-size: 0.8rem;
            text-transform: uppercase;
            letter-spacing: 0.05em;
        }

        .badge-green {
            background: rgba(16, 185, 129, 0.2);
            color: var(--success);
            padding: 0.25rem 0.6rem;
            border-radius: 6px;
            font-size: 0.8rem;
            font-weight: 600;
        }

        .badge-red {
            background: rgba(244, 63, 94, 0.2);
            color: var(--danger);
            padding: 0.25rem 0.6rem;
            border-radius: 6px;
            font-size: 0.8rem;
            font-weight: 600;
        }
    </style>
</head>
<body>

    <!-- Sidebar Navigation -->
    <div class="sidebar">
        <div class="brand">
            <div class="brand-icon"><i class="fa-solid fa-shield-halved"></i></div>
            <div class="brand-title">Deepfake AI</div>
        </div>

        <div class="menu-label">Menu</div>
        <div class="nav-item active" onclick="switchNav('detection')"><i class="fa-solid fa-chart-line"></i> Live Detection</div>
        <div class="nav-item" onclick="switchNav('benchmarks')"><i class="fa-solid fa-microchip"></i> Model Benchmarks</div>
        <div class="nav-item" onclick="switchNav('history')"><i class="fa-solid fa-clock-rotate-left"></i> History Log</div>
        <div class="nav-item" onclick="switchNav('settings')"><i class="fa-solid fa-gear"></i> System Settings</div>

        <div style="margin-top: auto; background: rgba(99, 102, 241, 0.1); padding: 1rem; border-radius: 14px; border: 1px solid rgba(99, 102, 241, 0.2);">
            <div style="font-size: 0.85rem; font-weight: 600; color: var(--primary);">Phase 1 Model</div>
            <div style="font-size: 0.75rem; color: var(--text-muted); margin-top: 0.2rem;">1D-LCNN ONNX (67 KB)</div>
            <div style="font-size: 0.75rem; color: var(--success); margin-top: 0.4rem; font-weight: 600;">EER: 0.39% | Acc: 99.54%</div>
        </div>
    </div>

    <!-- Main Content Area -->
    <div class="main-wrapper">
        <div class="top-bar">
            <div class="page-header">
                <h2 id="pageTitle">Real-Time Audio Deepfake Interception System</h2>
                <p id="pageSubTitle">Offline Telecommunication-Aware Voice Scam Security Dashboard</p>
            </div>
            
            <div class="right-controls">
                <!-- Theme Switcher -->
                <div class="theme-switcher">
                    <button class="theme-btn" id="themeLightBtn" onclick="setTheme('light')"><i class="fa-solid fa-sun"></i> Light</button>
                    <button class="theme-btn active" id="themeDarkBtn" onclick="setTheme('dark')"><i class="fa-solid fa-moon"></i> Dark</button>
                    <button class="theme-btn" id="themeSystemBtn" onclick="setTheme('system')"><i class="fa-solid fa-desktop"></i> System</button>
                </div>

                <div class="status-pill">
                    <div class="status-dot"></div>
                    Engine Online (ONNX Native)
                </div>
            </div>
        </div>

        <!-- VIEW 1: LIVE DETECTION -->
        <div id="viewDetection" class="dashboard-grid">
            <!-- Left Panel: Input & Live Audio Controls -->
            <div class="card">
                <div class="card-header">
                    <div class="card-title"><i class="fa-solid fa-microphone-lines" style="color: var(--primary);"></i> Audio Source Input</div>
                </div>

                <div class="control-tabs">
                    <button class="tab-btn active" onclick="setMode('mic')"><i class="fa-solid fa-microphone"></i> Live Laptop Mic</button>
                    <button class="tab-btn" onclick="setMode('file')"><i class="fa-solid fa-file-audio"></i> Upload Audio File</button>
                </div>

                <!-- Live Mic Section -->
                <div id="micSection">
                    <div class="visualizer-box">
                        <canvas id="waveformCanvas"></canvas>
                    </div>
                    <div class="btn-group">
                        <button class="action-btn btn-record" id="recBtn" onclick="toggleRecording()">
                            <i class="fa-solid fa-circle"></i> Start Live Mic Detection
                        </button>
                    </div>
                </div>

                <!-- File Upload Section -->
                <div id="fileSection" style="display: none;">
                    <div class="drop-zone" onclick="document.getElementById('audioInputFile').click()">
                        <i class="fa-solid fa-cloud-arrow-up drop-icon"></i>
                        <h3>Drop Audio File Here or Click to Browse</h3>
                        <p style="color: var(--text-muted); font-size: 0.85rem; margin-top: 0.5rem;">Supports .wav, .flac, .mp3, .m4a, .aac, .ogg (Auto-decoded in browser)</p>
                        <input type="file" id="audioInputFile" accept="audio/*" style="display: none;" onchange="uploadAudioFile()">
                    </div>
                </div>
            </div>

            <!-- Right Panel: Inspection Results & Graph -->
            <div class="card">
                <div class="card-header">
                    <div class="card-title"><i class="fa-solid fa-chart-pie" style="color: var(--primary);"></i> Detection Output</div>
                </div>

                <div class="result-banner" id="resultBanner"></div>

                <div class="metrics-grid">
                    <div class="metric-box">
                        <div class="metric-label">Deepfake Probability</div>
                        <div class="metric-val" id="fakeProbVal" style="color: var(--danger);">-</div>
                    </div>
                    <div class="metric-box">
                        <div class="metric-label">Real Voice Confidence</div>
                        <div class="metric-val" id="realProbVal" style="color: var(--success);">-</div>
                    </div>
                    <div class="metric-box">
                        <div class="metric-label">Inference Latency</div>
                        <div class="metric-val" id="latencyVal" style="color: var(--text-main);">-</div>
                    </div>
                    <div class="metric-box">
                        <div class="metric-label">Telephony Codec</div>
                        <div class="metric-val" style="color: var(--text-main);">8kHz G.711</div>
                    </div>
                </div>

                <div class="chart-box">
                    <canvas id="probabilityChart"></canvas>
                </div>
            </div>
        </div>

        <!-- VIEW 2: MODEL BENCHMARKS -->
        <div id="viewBenchmarks" style="display: none;">
            <div class="card">
                <div class="card-header">
                    <div class="card-title"><i class="fa-solid fa-microchip" style="color: var(--primary);"></i> Experimental Benchmark Evaluation Results</div>
                </div>
                <table class="data-table">
                    <thead>
                        <tr>
                            <th>Evaluation Scenario</th>
                            <th>Accuracy</th>
                            <th>Precision</th>
                            <th>Recall</th>
                            <th>F1-Score</th>
                            <th>Equal Error Rate (EER)</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td><strong>Standard Telephony Call (Clean G.711)</strong></td>
                            <td><span class="badge-green">99.54%</span></td>
                            <td>0.9930</td>
                            <td>0.9998</td>
                            <td>0.9964</td>
                            <td><span class="badge-green">0.39%</span></td>
                        </tr>
                        <tr>
                            <td><strong>DEMAND Noise Stress Test (-15dB SNR)</strong></td>
                            <td><span class="badge-red">87.07%</span></td>
                            <td>0.9822</td>
                            <td>0.8100</td>
                            <td>0.8878</td>
                            <td><span class="badge-red">16.24%</span></td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </div>

        <!-- VIEW 3: HISTORY LOG -->
        <div id="viewHistory" style="display: none;">
            <div class="card">
                <div class="card-header">
                    <div class="card-title"><i class="fa-solid fa-clock-rotate-left" style="color: var(--primary);"></i> Audio Inspection Audit History Log</div>
                    <button class="clear-btn" onclick="clearHistory()"><i class="fa-solid fa-trash"></i> Clear History</button>
                </div>
                <table class="data-table">
                    <thead>
                        <tr>
                            <th>Timestamp</th>
                            <th>Audio Source</th>
                            <th>Deepfake Prob</th>
                            <th>Real Confidence</th>
                            <th>Status Tag</th>
                        </tr>
                    </thead>
                    <tbody id="historyTableBody">
                        <tr>
                            <td colspan="5" style="text-align: center; color: var(--text-muted);">No recent audio inspections recorded in this session.</td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </div>

        <!-- VIEW 4: SYSTEM SETTINGS -->
        <div id="viewSettings" style="display: none;">
            <div class="card">
                <div class="card-header">
                    <div class="card-title"><i class="fa-solid fa-gear" style="color: var(--primary);"></i> Telecommunication AI Security Parameters</div>
                </div>
                <div class="metrics-grid">
                    <div class="metric-box">
                        <div class="metric-label">Sampling Rate</div>
                        <div class="metric-val" style="color: var(--primary);">8,000 Hz</div>
                    </div>
                    <div class="metric-box">
                        <div class="metric-label">Telephony Codec</div>
                        <div class="metric-val" style="color: var(--primary);">G.711 μ-Law</div>
                    </div>
                    <div class="metric-box">
                        <div class="metric-label">ONNX Engine Footprint</div>
                        <div class="metric-val" style="color: var(--success);">67 KB</div>
                    </div>
                    <div class="metric-box">
                        <div class="metric-label">Inference Execution</div>
                        <div class="metric-val" style="color: var(--success);">100% Offline CPU</div>
                    </div>
                </div>
            </div>
        </div>

    </div>

    <script>
        let currentMode = 'mic';
        let mediaStream = null;
        let scriptProcessor = null;
        let gainNode = null;
        let pcmSamples = [];
        let isRecording = false;
        let audioContext = null;
        let analyser = null;
        let animFrameId = null;
        let chartInstance = null;

        // Pure JS PCM 16-bit WAV Encoder Function
        function encodeWAV(samples, sampleRate) {
            const buffer = new ArrayBuffer(44 + samples.length * 2);
            const view = new DataView(buffer);

            const writeString = (offset, str) => {
                for (let i = 0; i < str.length; i++) {
                    view.setUint8(offset + i, str.charCodeAt(i));
                }
            };

            writeString(0, 'RIFF');
            view.setUint32(4, 36 + samples.length * 2, true);
            writeString(8, 'WAVE');
            writeString(12, 'fmt ');
            view.setUint32(16, 16, true); // Subchunk1Size (16 for PCM)
            view.setUint16(20, 1, true);  // AudioFormat (1 = PCM)
            view.setUint16(22, 1, true);  // NumChannels (1 = Mono)
            view.setUint32(24, sampleRate, true);
            view.setUint32(28, sampleRate * 2, true); // ByteRate
            view.setUint16(32, 2, true);  // BlockAlign
            view.setUint16(34, 16, true); // BitsPerSample
            writeString(36, 'data');
            view.setUint32(40, samples.length * 2, true);

            let offset = 44;
            for (let i = 0; i < samples.length; i++, offset += 2) {
                const s = Math.max(-1, Math.min(1, samples[i]));
                view.setInt16(offset, s < 0 ? s * 0x8000 : s * 0x7FFF, true);
            }

            return new Blob([view], { type: 'audio/wav' });
        }

        // Theme Switcher Functions
        function setTheme(theme) {
            document.querySelectorAll('.theme-btn').forEach(btn => btn.classList.remove('active'));
            if (theme === 'light') {
                document.documentElement.setAttribute('data-theme', 'light');
                document.getElementById('themeLightBtn').classList.add('active');
            } else if (theme === 'dark') {
                document.documentElement.setAttribute('data-theme', 'dark');
                document.getElementById('themeDarkBtn').classList.add('active');
            } else if (theme === 'system') {
                const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
                document.documentElement.setAttribute('data-theme', prefersDark ? 'dark' : 'light');
                document.getElementById('themeSystemBtn').classList.add('active');
            }
        }

        function switchNav(viewName) {
            document.querySelectorAll('.nav-item').forEach(item => item.classList.remove('active'));
            document.getElementById('viewDetection').style.display = 'none';
            document.getElementById('viewBenchmarks').style.display = 'none';
            document.getElementById('viewHistory').style.display = 'none';
            document.getElementById('viewSettings').style.display = 'none';

            if (viewName === 'detection') {
                document.querySelectorAll('.nav-item')[0].classList.add('active');
                document.getElementById('viewDetection').style.display = 'grid';
                document.getElementById('pageTitle').innerText = 'Real-Time Audio Deepfake Interception System';
            } else if (viewName === 'benchmarks') {
                document.querySelectorAll('.nav-item')[1].classList.add('active');
                document.getElementById('viewBenchmarks').style.display = 'block';
                document.getElementById('pageTitle').innerText = 'Model Benchmark Metrics & Evaluation';
            } else if (viewName === 'history') {
                document.querySelectorAll('.nav-item')[2].classList.add('active');
                document.getElementById('viewHistory').style.display = 'block';
                document.getElementById('pageTitle').innerText = 'Audio Inspection History Log';
            } else if (viewName === 'settings') {
                document.querySelectorAll('.nav-item')[3].classList.add('active');
                document.getElementById('viewSettings').style.display = 'block';
                document.getElementById('pageTitle').innerText = 'System Configuration Parameters';
            }
        }

        function setMode(mode) {
            currentMode = mode;
            document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
            if (mode === 'mic') {
                document.querySelectorAll('.tab-btn')[0].classList.add('active');
                document.getElementById('micSection').style.display = 'block';
                document.getElementById('fileSection').style.display = 'none';
            } else {
                document.querySelectorAll('.tab-btn')[1].classList.add('active');
                document.getElementById('micSection').style.display = 'none';
                document.getElementById('fileSection').style.display = 'block';
            }
        }

        async function toggleRecording() {
            const btn = document.getElementById('recBtn');
            if (!isRecording) {
                try {
                    // Disable Chrome noise suppression / AGC to capture raw natural human vocal harmonics
                    mediaStream = await navigator.mediaDevices.getUserMedia({
                        audio: {
                            echoCancellation: false,
                            noiseSuppression: false,
                            autoGainControl: false,
                            channelCount: 1
                        }
                    });
                    audioContext = new (window.AudioContext || window.webkitAudioContext)();
                    analyser = audioContext.createAnalyser();
                    const source = audioContext.createMediaStreamSource(mediaStream);
                    source.connect(analyser);

                    pcmSamples = [];
                    scriptProcessor = audioContext.createScriptProcessor(4096, 1, 1);
                    scriptProcessor.onaudioprocess = e => {
                        if (isRecording) {
                            const inputData = e.inputBuffer.getChannelData(0);
                            for (let i = 0; i < inputData.length; i++) {
                                pcmSamples.push(inputData[i]);
                            }
                        }
                    };

                    // Silent GainNode keeps Web Audio processing graph active without speaker echo
                    gainNode = audioContext.createGain();
                    gainNode.gain.value = 0.0;
                    source.connect(scriptProcessor);
                    scriptProcessor.connect(gainNode);
                    gainNode.connect(audioContext.destination);

                    drawWaveform();

                    isRecording = true;
                    btn.className = 'action-btn btn-stop';
                    btn.innerHTML = '<i class="fa-solid fa-square"></i> Stop & Analyze Audio';
                } catch (err) {
                    alert("Microphone access denied: " + err.message);
                }
            } else {
                isRecording = false;
                if (scriptProcessor) scriptProcessor.disconnect();
                if (gainNode) gainNode.disconnect();
                if (mediaStream) mediaStream.getTracks().forEach(track => track.stop());
                
                cancelAnimationFrame(animFrameId);
                btn.className = 'action-btn btn-record';
                btn.innerHTML = '<i class="fa-solid fa-circle"></i> Start Live Mic Detection';

                // Encode raw PCM float samples into a clean standard 16-bit WAV Blob
                const wavBlob = encodeWAV(pcmSamples, audioContext.sampleRate);
                if (audioContext) audioContext.close();
                sendAudioToBackend(wavBlob, 'Live Laptop Mic');
            }
        }

        function drawWaveform() {
            const canvas = document.getElementById('waveformCanvas');
            const ctx = canvas.getContext('2d');
            canvas.width = canvas.offsetWidth;
            canvas.height = canvas.offsetHeight;

            const bufferLength = analyser.frequencyBinCount;
            const dataArray = new Uint8Array(bufferLength);

            function render() {
                animFrameId = requestAnimationFrame(render);
                analyser.getByteFrequencyData(dataArray);

                ctx.fillStyle = '#0f172a';
                ctx.fillRect(0, 0, canvas.width, canvas.height);

                const barWidth = (canvas.width / bufferLength) * 2.5;
                let x = 0;

                for (let i = 0; i < bufferLength; i++) {
                    const barHeight = (dataArray[i] / 255) * canvas.height;
                    ctx.fillStyle = `rgb(99, 102, 241)`;
                    ctx.fillRect(x, canvas.height - barHeight, barWidth, barHeight);
                    x += barWidth + 1;
                }
            }
            render();
        }

        async function uploadAudioFile() {
            const fileInput = document.getElementById('audioInputFile');
            if (fileInput.files && fileInput.files[0]) {
                const file = fileInput.files[0];
                const banner = document.getElementById('resultBanner');
                banner.style.display = 'block';
                banner.className = 'result-banner';
                banner.style.background = 'rgba(99, 102, 241, 0.2)';
                banner.style.color = 'var(--primary)';
                banner.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Decoding Audio File in Browser...';

                try {
                    const arrayBuffer = await file.arrayBuffer();
                    const tempAudioContext = new (window.AudioContext || window.webkitAudioContext)();
                    const decodedData = await tempAudioContext.decodeAudioData(arrayBuffer);
                    const pcmSamples = decodedData.getChannelData(0);
                    const wavBlob = encodeWAV(pcmSamples, decodedData.sampleRate);
                    tempAudioContext.close();
                    sendAudioToBackend(wavBlob, file.name);
                } catch (err) {
                    console.log("Fallback to direct file payload:", err);
                    sendAudioToBackend(file, file.name);
                }
            }
        }

        async function sendAudioToBackend(audioBlob, sourceName) {
            const formData = new FormData();
            formData.append('audio', audioBlob, 'recording.wav');

            const banner = document.getElementById('resultBanner');
            banner.style.display = 'block';
            banner.className = 'result-banner';
            banner.style.background = 'rgba(99, 102, 241, 0.2)';
            banner.style.color = 'var(--primary)';
            banner.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> Running 1D-LCNN ONNX Detection...';

            try {
                const response = await fetch('/detect', { method: 'POST', body: formData });
                const data = await response.json();

                if (data.error) {
                    alert("Audio Processing Notice: " + data.error);
                    banner.style.display = 'none';
                    return;
                }

                const probFakePct = (data.prob_fake * 100).toFixed(1);
                const probRealPct = (data.prob_real * 100).toFixed(1);

                if (data.is_fake) {
                    banner.className = 'result-banner banner-fake';
                    banner.innerHTML = `<i class="fa-solid fa-triangle-exclamation"></i> WARNING: SUSPECTED DEEPFAKE VOICE (${probFakePct}%)`;
                } else {
                    banner.className = 'result-banner banner-real';
                    banner.innerHTML = `<i class="fa-solid fa-shield-check"></i> REAL HUMAN VOICE VERIFIED (${probRealPct}%)`;
                }

                document.getElementById('fakeProbVal').innerText = `${probFakePct}%`;
                document.getElementById('realProbVal').innerText = `${probRealPct}%`;
                document.getElementById('latencyVal').innerText = `${data.latency_ms} ms`;

                updateChart(data.prob_real * 100, data.prob_fake * 100);
                addHistoryRecord(sourceName, probFakePct, probRealPct, data.is_fake);
            } catch (err) {
                console.error(err);
                alert("Error connecting to backend server.");
            }
        }

        function addHistoryRecord(source, fakePct, realPct, isFake) {
            const tbody = document.getElementById('historyTableBody');
            if (tbody.children.length === 1 && tbody.children[0].innerText.includes('No recent')) {
                tbody.innerHTML = '';
            }

            const timeStr = new Date().toLocaleTimeString();
            const tag = isFake ? '<span class="badge-red">SUSPECTED DEEPFAKE</span>' : '<span class="badge-green">REAL VOICE VERIFIED</span>';
            
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>${timeStr}</td>
                <td>${source}</td>
                <td style="color: var(--danger); font-weight: 700;">${fakePct}%</td>
                <td style="color: var(--success); font-weight: 700;">${realPct}%</td>
                <td>${tag}</td>
            `;
            tbody.insertBefore(tr, tbody.firstChild);
        }

        function clearHistory() {
            const tbody = document.getElementById('historyTableBody');
            tbody.innerHTML = `
                <tr>
                    <td colspan="5" style="text-align: center; color: var(--text-muted);">No recent audio inspections recorded in this session.</td>
                </tr>
            `;
        }

        function updateChart(realPct, fakePct) {
            const ctx = document.getElementById('probabilityChart').getContext('2d');
            if (chartInstance) chartInstance.destroy();

            chartInstance = new Chart(ctx, {
                type: 'bar',
                data: {
                    labels: ['Real Voice', 'Deepfake Voice'],
                    datasets: [{
                        data: [realPct, fakePct],
                        backgroundColor: ['rgba(16, 185, 129, 0.8)', 'rgba(244, 63, 94, 0.8)'],
                        borderColor: ['#10b981', '#f43f5e'],
                        borderWidth: 2,
                        borderRadius: 10
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    scales: {
                        y: {
                            beginAtZero: true,
                            max: 100,
                            ticks: { color: 'var(--text-muted)' },
                            grid: { color: 'rgba(150,150,150,0.1)' }
                        },
                        x: {
                            ticks: { color: 'var(--text-main)', font: { size: 14, weight: 'bold' } },
                            grid: { display: false }
                        }
                    },
                    plugins: { legend: { display: false } }
                }
            });
        }
    </script>
</body>
</html>
"""

@app.route("/")
def index():
    return render_template_string(HTML_TEMPLATE)

@app.route("/detect", methods=["POST", "OPTIONS"])
def detect():
    if request.method == "OPTIONS":
        response = jsonify({"status": "ok"})
        response.headers.add("Access-Control-Allow-Origin", "*")
        response.headers.add("Access-Control-Allow-Headers", "Content-Type,Authorization")
        response.headers.add("Access-Control-Allow-Methods", "GET,PUT,POST,DELETE,OPTIONS")
        return response

    if "audio" not in request.files:
        res = jsonify({"error": "No audio provided"})
        res.headers.add("Access-Control-Allow-Origin", "*")
        return res, 400

    file = request.files["audio"]
    file_bytes = file.read()

    start_time = time.time()
    chunks = preprocess_audio_chunks(file_bytes)
    
    if chunks is None or len(chunks) == 0:
        res = jsonify({"error": "Failed to load audio file format. Please try WAV, MP3, FLAC, or record live mic."})
        res.headers.add("Access-Control-Allow-Origin", "*")
        return res, 400

    real_probs = []
    fake_probs = []

    for idx, chunk in enumerate(chunks):
        inp = chunk.reshape(1, 1, 8000)
        out = session.run([output_name], {input_name: inp})[0][0]

        e = np.exp(out - np.max(out))
        probs = e / np.sum(e)
        raw_fake = float(probs[1])

        fake_probs.append(raw_fake)
        real_probs.append(1.0 - raw_fake)

    avg_fake = float(np.mean(fake_probs)) if len(fake_probs) > 0 else 0.0
    avg_real = float(1.0 - avg_fake)
    latency_ms = round((time.time() - start_time) * 1000, 2)

    is_fake = avg_fake > 0.50

    print(f"ACTIVE CHUNK TRIM TEST -> Chunks: {len(chunks)} | Final Real Prob: {avg_real*100:.1f}%, Fake Prob: {avg_fake*100:.1f}% => Result: {'DEEPFAKE' if is_fake else 'REAL'}")

    res = jsonify({
        "is_fake": is_fake,
        "prob_real": avg_real,
        "prob_fake": avg_fake,
        "latency_ms": latency_ms
    })
    res.headers.add("Access-Control-Allow-Origin", "*")
    return res

if __name__ == "__main__":
    print("==========================================================")
    print("LAUNCHING DEEPFAKE INTERCEPTION LAPTOP WEB DASHBOARD")
    print("==========================================================")
    print("Open your web browser and navigate to: http://127.0.0.1:5000")
    print("==========================================================")
    app.run(host="0.0.0.0", port=5000, debug=False, use_reloader=False)
