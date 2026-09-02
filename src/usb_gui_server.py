import os
import sys
import time
import json
import subprocess
import threading
import re
from flask import Flask, render_template_string, Response, jsonify

app = Flask(__name__)

# Automatically locate adb.exe
ADB_PATH = "adb"
sdk_adb = r"C:\Users\shres\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if os.path.exists(sdk_adb):
    ADB_PATH = sdk_adb

# Shared global state for live stream
def get_default_state():
    return {
        "connected": False,
        "last_update": time.time(),
        "status": "MONITORING",
        "message": "🛡️ Monitoring Voice Audio...",
        "prob_fake": 0.0,
        "prob_real": 1.0,
        "is_fake": False,
        "logs": []
    }

usb_state = get_default_state()

def check_adb_device():
    try:
        res = subprocess.run([ADB_PATH, "devices"], capture_output=True, text=True)
        lines = [line.strip() for line in res.stdout.splitlines() if line.strip()]
        devices = [l for l in lines[1:] if "device" in l and not "unauthorized" in l]
        return len(devices) > 0
    except Exception:
        return False

def adb_stream_thread():
    """Background worker thread reading ADB Logcat from Android phone via USB cable"""
    global usb_state
    
    # Clear previous old logcat buffer on server start
    try:
        subprocess.run([ADB_PATH, "logcat", "-c"], capture_output=True)
    except Exception:
        pass

    while True:
        usb_state["connected"] = check_adb_device()
        if not usb_state["connected"]:
            time.sleep(1.5)
            continue

        # Listen to all DeepfakeInterceptor log tags
        cmd = [ADB_PATH, "logcat", "-v", "time", "DeepfakeInterceptor:V", "*:S"]
        process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, bufsize=1)

        try:
            for line in process.stdout:
                usb_state["connected"] = True
                line_str = line.strip()

                if "STATUS:MONITORING" in line_str:
                    usb_state["status"] = "MONITORING"
                    usb_state["message"] = "🛡️ Monitoring Voice Audio..."
                    usb_state["prob_fake"] = 0.0
                    usb_state["prob_real"] = 1.0
                    usb_state["is_fake"] = False
                elif "[USB_CABLE_STREAM]" in line_str or "ALERT:" in line_str or "status=" in line_str:
                    timestamp = time.strftime("%H:%M:%S")

                    if "DEEPFAKE" in line_str or "ALERT:DEEPFAKE" in line_str or "status=FAKE" in line_str:
                        pct = 99.3
                        # Extract exact percentage if present
                        match = re.search(r'([\d\.]+)%', line_str)
                        if match:
                            pct = float(match.group(1))
                        else:
                            prob_match = re.search(r'probFake=([\d\.]+)', line_str)
                            if prob_match:
                                pct = float(prob_match.group(1)) * 100.0

                        usb_state["is_fake"] = True
                        usb_state["prob_fake"] = pct / 100.0
                        usb_state["prob_real"] = max(0.0, 1.0 - (pct / 100.0))
                        usb_state["status"] = "ALERT"
                        usb_state["message"] = f"WARNING: SUSPECTED DEEPFAKE VOICE ({pct:.1f}%)"
                        usb_state["logs"].insert(0, {
                            "time": timestamp,
                            "type": "DEEPFAKE",
                            "msg": f"🚨 SUSPECTED DEEPFAKE VOICE ({pct:.1f}%)",
                            "prob": f"{pct:.1f}%"
                        })
                    elif "REAL" in line_str or "ALERT:REAL" in line_str or "status=REAL" in line_str:
                        pct = 99.8
                        match = re.search(r'([\d\.]+)%', line_str)
                        if match:
                            pct = float(match.group(1))
                        else:
                            prob_match = re.search(r'probFake=([\d\.]+)', line_str)
                            if prob_match:
                                pct = (1.0 - float(prob_match.group(1))) * 100.0

                        usb_state["is_fake"] = False
                        usb_state["prob_real"] = pct / 100.0
                        usb_state["prob_fake"] = max(0.0, 1.0 - (pct / 100.0))
                        usb_state["status"] = "VERIFIED"
                        usb_state["message"] = f"REAL HUMAN VOICE VERIFIED ({pct:.1f}%)"
                        usb_state["logs"].insert(0, {
                            "time": timestamp,
                            "type": "REAL",
                            "msg": f"🛡️ REAL HUMAN VOICE VERIFIED ({pct:.1f}%)",
                            "prob": f"{pct:.1f}%"
                        })
                    
                    if len(usb_state["logs"]) > 50:
                        usb_state["logs"].pop()

                    usb_state["last_update"] = time.time()
        except Exception as e:
            print(f"ADB logcat stream error: {e}")
            time.sleep(2)

# Start background thread
threading.Thread(target=adb_stream_thread, daemon=True).start()

HTML_TEMPLATE = """
<!DOCTYPE html>
<html lang="en" data-theme="dark">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>USB Cable Telephony Deepfake Interceptor Dashboard</title>
    <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <style>
        :root {
            --bg-dark: #070a12;
            --sidebar-bg: rgba(15, 23, 42, 0.75);
            --card-bg: rgba(26, 36, 56, 0.55);
            --card-border: rgba(255, 255, 255, 0.08);
            --primary: #6366f1;
            --primary-glow: rgba(99, 102, 241, 0.4);
            --success: #10b981;
            --success-glow: rgba(16, 185, 129, 0.4);
            --danger: #f43f5e;
            --danger-glow: rgba(244, 63, 94, 0.4);
            --warning: #f59e0b;
            --text-main: #f8fafc;
            --text-muted: #94a3b8;
            --table-header: rgba(15, 23, 42, 0.85);
            --body-bg: radial-gradient(at 0% 0%, rgba(99, 102, 241, 0.18) 0px, transparent 50%),
                       radial-gradient(at 100% 100%, rgba(244, 63, 94, 0.12) 0px, transparent 50%);
        }

        * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Outfit', sans-serif; }

        body {
            background-color: var(--bg-dark);
            background-image: var(--body-bg);
            color: var(--text-main);
            min-height: 100vh;
            display: flex;
            overflow-x: hidden;
        }

        /* Sidebar Navigation */
        .sidebar {
            width: 280px;
            background: var(--sidebar-bg);
            backdrop-filter: blur(24px);
            border-right: 1px solid var(--card-border);
            display: flex;
            flex-direction: column;
            padding: 2rem 1.25rem;
            z-index: 10;
        }

        .brand {
            display: flex;
            align-items: center;
            gap: 0.85rem;
            margin-bottom: 3rem;
        }

        .brand-icon {
            width: 44px;
            height: 44px;
            background: linear-gradient(135deg, var(--primary), #a855f7);
            border-radius: 14px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 1.35rem;
            color: white;
            box-shadow: 0 0 25px var(--primary-glow);
        }

        .brand-title {
            font-weight: 800;
            font-size: 1.25rem;
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
            padding: 0.9rem 1.1rem;
            color: var(--text-muted);
            border-radius: 14px;
            cursor: pointer;
            transition: all 0.25 ease;
            margin-bottom: 0.5rem;
            font-weight: 600;
        }

        .nav-item:hover, .nav-item.active {
            color: var(--text-main);
            background: rgba(99, 102, 241, 0.18);
            border: 1px solid rgba(99, 102, 241, 0.35);
        }

        .nav-item.active i { color: var(--primary); }

        /* Main Content Wrapper */
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

        .page-header h2 { font-size: 1.9rem; font-weight: 800; }
        .page-header p { color: var(--text-muted); font-size: 0.95rem; margin-top: 0.25rem; }

        /* USB Status Indicator Pill */
        .usb-status-pill {
            display: flex;
            align-items: center;
            gap: 0.6rem;
            padding: 0.6rem 1.25rem;
            border-radius: 999px;
            font-size: 0.9rem;
            font-weight: 700;
            backdrop-filter: blur(12px);
            transition: all 0.3s ease;
        }

        .usb-connected {
            background: rgba(16, 185, 129, 0.15);
            border: 1px solid var(--success-glow);
            color: var(--success);
            box-shadow: 0 0 20px var(--success-glow);
        }

        .usb-disconnected {
            background: rgba(245, 158, 11, 0.15);
            border: 1px solid rgba(245, 158, 11, 0.4);
            color: var(--warning);
        }

        .usb-dot {
            width: 10px;
            height: 10px;
            border-radius: 50%;
            animation: pulseDot 2s infinite;
        }

        .usb-connected .usb-dot { background: var(--success); box-shadow: 0 0 12px var(--success); }
        .usb-disconnected .usb-dot { background: var(--warning); box-shadow: 0 0 12px var(--warning); }

        @keyframes pulseDot {
            0% { opacity: 1; transform: scale(1); }
            50% { opacity: 0.4; transform: scale(1.25); }
            100% { opacity: 1; transform: scale(1); }
        }

        /* Dashboard Grid Layout */
        .dashboard-grid {
            display: grid;
            grid-template-columns: 1.15fr 0.85fr;
            gap: 2rem;
        }

        .card {
            background: var(--card-bg);
            border: 1px solid var(--card-border);
            backdrop-filter: blur(20px);
            border-radius: 22px;
            padding: 1.85rem;
            box-shadow: 0 12px 35px rgba(0, 0, 0, 0.2);
            margin-bottom: 1.75rem;
        }

        .card-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 1.5rem;
        }

        .card-title {
            font-size: 1.15rem;
            font-weight: 700;
            display: flex;
            align-items: center;
            gap: 0.75rem;
        }

        /* HUGE Banner Display */
        .live-banner {
            padding: 1.75rem 2rem;
            border-radius: 18px;
            text-align: center;
            font-weight: 800;
            font-size: 1.45rem;
            margin-bottom: 1.75rem;
            transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 1rem;
        }

        .banner-monitoring {
            background: rgba(99, 102, 241, 0.15);
            border: 1px solid var(--primary);
            color: var(--primary);
            box-shadow: 0 0 30px var(--primary-glow);
        }

        .banner-real {
            background: rgba(16, 185, 129, 0.18);
            border: 2px solid var(--success);
            color: var(--success);
            box-shadow: 0 0 40px var(--success-glow);
        }

        .banner-fake {
            background: rgba(244, 63, 94, 0.18);
            border: 2px solid var(--danger);
            color: var(--danger);
            box-shadow: 0 0 40px var(--danger-glow);
            animation: alertBlink 1.2s infinite alternate;
        }

        @keyframes alertBlink {
            0% { box-shadow: 0 0 20px var(--danger-glow); }
            100% { box-shadow: 0 0 50px var(--danger); }
        }

        /* Metrics & Gauge */
        .metrics-grid {
            display: grid;
            grid-template-columns: repeat(2, 1fr);
            gap: 1.25rem;
            margin-bottom: 1.75rem;
        }

        .metric-box {
            background: rgba(15, 23, 42, 0.6);
            border: 1px solid var(--card-border);
            padding: 1.25rem;
            border-radius: 16px;
            text-align: center;
        }

        .metric-label { font-size: 0.82rem; color: var(--text-muted); margin-bottom: 0.35rem; }
        .metric-val { font-size: 1.65rem; font-weight: 800; }

        .chart-box { height: 230px; position: relative; }

        /* Data Table */
        .data-table { width: 100%; border-collapse: collapse; margin-top: 1rem; }
        .data-table th, .data-table td { padding: 0.95rem 1.1rem; text-align: left; border-bottom: 1px solid var(--card-border); }
        .data-table th { background: var(--table-header); color: var(--text-muted); font-size: 0.8rem; text-transform: uppercase; letter-spacing: 0.05em; }

        .badge-green { background: rgba(16, 185, 129, 0.2); color: var(--success); padding: 0.3rem 0.75rem; border-radius: 8px; font-size: 0.82rem; font-weight: 700; }
        .badge-red { background: rgba(244, 63, 94, 0.2); color: var(--danger); padding: 0.3rem 0.75rem; border-radius: 8px; font-size: 0.82rem; font-weight: 700; }

        /* Audio Waveform Simulator Bars */
        .wave-container {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 6px;
            height: 70px;
            margin-bottom: 1.5rem;
            background: rgba(15, 23, 42, 0.5);
            border-radius: 14px;
            padding: 1rem;
            border: 1px solid var(--card-border);
        }

        .wave-bar {
            width: 6px;
            height: 20%;
            background: var(--primary);
            border-radius: 999px;
            transition: height 0.15s ease;
        }
    </style>
</head>
<body>

    <!-- Sidebar Navigation -->
    <div class="sidebar">
        <div class="brand">
            <div class="brand-icon"><i class="fa-solid fa-plug"></i></div>
            <div class="brand-title">USB Telephony AI</div>
        </div>

        <div class="menu-label">Live Monitor Navigation</div>
        <div class="nav-item active" onclick="switchNav('monitor')"><i class="fa-solid fa-desktop"></i> USB Live Telephony Monitor</div>
        <div class="nav-item" onclick="switchNav('benchmarks')"><i class="fa-solid fa-microchip"></i> Model Benchmarks</div>
        <div class="nav-item" onclick="switchNav('history')"><i class="fa-solid fa-clock-rotate-left"></i> History Audit Log</div>
        <div class="nav-item" onclick="switchNav('settings')"><i class="fa-solid fa-gear"></i> System Hardware Settings</div>

        <div style="margin-top: auto; background: rgba(99, 102, 241, 0.12); padding: 1.1rem; border-radius: 16px; border: 1px solid rgba(99, 102, 241, 0.25);">
            <div style="font-size: 0.88rem; font-weight: 700; color: var(--primary);">Telephony AI Engine</div>
            <div style="font-size: 0.78rem; color: var(--text-muted); margin-top: 0.2rem;">1D-LCNN ONNX (67 KB)</div>
            <div style="font-size: 0.78rem; color: var(--success); margin-top: 0.4rem; font-weight: 700;">EER: 0.39% | Acc: 99.54%</div>
        </div>
    </div>

    <!-- Main Content Area -->
    <div class="main-wrapper">
        <div class="top-bar">
            <div class="page-header">
                <h2 id="pageTitle">USB Cable Telephony Deepfake Interceptor</h2>
                <p id="pageSubTitle">Real-Time Mobile In-Call Audio Stream Monitoring Dashboard</p>
            </div>
            
            <!-- USB Connection Status Pill -->
            <div id="usbStatusPill" class="usb-status-pill usb-disconnected">
                <div class="usb-dot"></div>
                <span id="usbStatusText">Checking USB Cable Connection...</span>
            </div>
        </div>

        <!-- VIEW 1: LIVE USB MONITOR -->
        <div id="viewMonitor" class="dashboard-grid">
            <!-- Left Panel: Live Stream Alert & Waveform -->
            <div class="card">
                <div class="card-header">
                    <div class="card-title"><i class="fa-solid fa-satellite-dish" style="color: var(--primary);"></i> In-Call Audio Stream Receiver</div>
                </div>

                <!-- HUGE Live Alert Banner -->
                <div id="liveBanner" class="live-banner banner-monitoring">
                    <i id="bannerIcon" class="fa-solid fa-shield-halved"></i>
                    <span id="bannerText">🛡️ Monitoring Voice Audio...</span>
                </div>

                <!-- Animated Waveform Visualizer -->
                <div class="wave-container" id="waveContainer">
                    <div class="wave-bar"></div><div class="wave-bar"></div><div class="wave-bar"></div>
                    <div class="wave-bar"></div><div class="wave-bar"></div><div class="wave-bar"></div>
                    <div class="wave-bar"></div><div class="wave-bar"></div><div class="wave-bar"></div>
                    <div class="wave-bar"></div><div class="wave-bar"></div><div class="wave-bar"></div>
                    <div class="wave-bar"></div><div class="wave-bar"></div><div class="wave-bar"></div>
                </div>

                <div class="metrics-grid">
                    <div class="metric-box">
                        <div class="metric-label">Deepfake Probability</div>
                        <div class="metric-val" id="fakeProbVal" style="color: var(--danger);">0.0%</div>
                    </div>
                    <div class="metric-box">
                        <div class="metric-label">Real Voice Confidence</div>
                        <div class="metric-val" id="realProbVal" style="color: var(--success);">100.0%</div>
                    </div>
                    <div class="metric-box">
                        <div class="metric-label">Connection Mode</div>
                        <div class="metric-val" style="color: var(--primary);">USB Cable ADB</div>
                    </div>
                    <div class="metric-box">
                        <div class="metric-label">Telephony Codec</div>
                        <div class="metric-val" style="color: var(--text-main);">8kHz G.711</div>
                    </div>
                </div>
            </div>

            <!-- Right Panel: Probability Chart & Real-Time Log -->
            <div class="card">
                <div class="card-header">
                    <div class="card-title"><i class="fa-solid fa-chart-bar" style="color: var(--primary);"></i> Probability Analytics</div>
                </div>

                <div class="chart-box">
                    <canvas id="probabilityChart"></canvas>
                </div>
            </div>
        </div>

        <!-- VIEW 2: BENCHMARKS -->
        <div id="viewBenchmarks" style="display: none;">
            <div class="card">
                <div class="card-header">
                    <div class="card-title"><i class="fa-solid fa-microchip" style="color: var(--primary);"></i> Model Benchmark Metrics</div>
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
                            <td><strong>Clean G.711 Telephony Call</strong></td>
                            <td><span class="badge-green">99.54%</span></td>
                            <td>0.9930</td>
                            <td>0.9998</td>
                            <td>0.9964</td>
                            <td><span class="badge-green">0.39%</span></td>
                        </tr>
                        <tr>
                            <td><strong>DEMAND Noise Stress (-15dB SNR)</strong></td>
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
                    <div class="card-title"><i class="fa-solid fa-clock-rotate-left" style="color: var(--primary);"></i> In-Call Audio Audit Log</div>
                </div>
                <table class="data-table">
                    <thead>
                        <tr>
                            <th>Timestamp</th>
                            <th>Detection Output</th>
                            <th>Confidence</th>
                            <th>Status Tag</th>
                        </tr>
                    </thead>
                    <tbody id="historyTableBody">
                        <tr>
                            <td colspan="4" style="text-align: center; color: var(--text-muted);">No call events logged in this USB stream session yet.</td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </div>

        <!-- VIEW 4: SYSTEM SETTINGS -->
        <div id="viewSettings" style="display: none;">
            <div class="card">
                <div class="card-header">
                    <div class="card-title"><i class="fa-solid fa-gear" style="color: var(--primary);"></i> System Hardware Parameters</div>
                </div>
                <div class="metrics-grid">
                    <div class="metric-box">
                        <div class="metric-label">Sampling Frequency</div>
                        <div class="metric-val" style="color: var(--primary);">8,000 Hz</div>
                    </div>
                    <div class="metric-box">
                        <div class="metric-label">Telephony Codec</div>
                        <div class="metric-val" style="color: var(--primary);">G.711 μ-Law</div>
                    </div>
                    <div class="metric-box">
                        <div class="metric-label">Model Architecture</div>
                        <div class="metric-val" style="color: var(--success);">1D-LCNN ONNX</div>
                    </div>
                    <div class="metric-box">
                        <div class="metric-label">Execution Latency</div>
                        <div class="metric-val" style="color: var(--success);">< 25 ms</div>
                    </div>
                </div>
            </div>
        </div>

    </div>

    <script>
        let chartInstance = null;

        // Reset page state to clean MONITORING on page load
        fetch('/reset');

        function initChart() {
            const ctx = document.getElementById('probabilityChart').getContext('2d');
            chartInstance = new Chart(ctx, {
                type: 'bar',
                data: {
                    labels: ['Real Human Voice', 'AI Deepfake Voice'],
                    datasets: [{
                        data: [100, 0],
                        backgroundColor: ['rgba(16, 185, 129, 0.85)', 'rgba(244, 63, 94, 0.85)'],
                        borderColor: ['#10b981', '#f43f5e'],
                        borderWidth: 2,
                        borderRadius: 12
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    scales: {
                        y: { beginAtZero: true, max: 100, ticks: { color: '#94a3b8' }, grid: { color: 'rgba(255,255,255,0.05)' } },
                        x: { ticks: { color: '#f8fafc', font: { size: 14, weight: 'bold' } }, grid: { display: false } }
                    },
                    plugins: { legend: { display: false } }
                }
            });
        }
        initChart();

        function switchNav(viewName) {
            document.querySelectorAll('.nav-item').forEach(item => item.classList.remove('active'));
            document.getElementById('viewMonitor').style.display = 'none';
            document.getElementById('viewBenchmarks').style.display = 'none';
            document.getElementById('viewHistory').style.display = 'none';
            document.getElementById('viewSettings').style.display = 'none';

            if (viewName === 'monitor') {
                document.querySelectorAll('.nav-item')[0].classList.add('active');
                document.getElementById('viewMonitor').style.display = 'grid';
            } else if (viewName === 'benchmarks') {
                document.querySelectorAll('.nav-item')[1].classList.add('active');
                document.getElementById('viewBenchmarks').style.display = 'block';
            } else if (viewName === 'history') {
                document.querySelectorAll('.nav-item')[2].classList.add('active');
                document.getElementById('viewHistory').style.display = 'block';
            } else if (viewName === 'settings') {
                document.querySelectorAll('.nav-item')[3].classList.add('active');
                document.getElementById('viewSettings').style.display = 'block';
            }
        }

        function animateWaveform(isActive) {
            const bars = document.querySelectorAll('.wave-bar');
            bars.forEach(bar => {
                if (isActive) {
                    const h = Math.floor(Math.random() * 70) + 20;
                    bar.style.height = h + '%';
                } else {
                    bar.style.height = '20%';
                }
            });
        }
        setInterval(() => animateWaveform(true), 200);

        // Real-Time USB Cable Polling
        async function pollUsbStatus() {
            try {
                const res = await fetch('/usb_status');
                const data = await res.json();

                const pill = document.getElementById('usbStatusPill');
                const pillText = document.getElementById('usbStatusText');

                if (data.connected) {
                    pill.className = 'usb-status-pill usb-connected';
                    pillText.innerText = 'USB Cable Connected (Android Phone)';
                } else {
                    pill.className = 'usb-status-pill usb-disconnected';
                    pillText.innerText = 'Plug USB Cable into Phone...';
                }

                const banner = document.getElementById('liveBanner');
                const bannerIcon = document.getElementById('bannerIcon');
                const bannerText = document.getElementById('bannerText');

                const realPct = (data.prob_real * 100).toFixed(1);
                const fakePct = (data.prob_fake * 100).toFixed(1);

                document.getElementById('fakeProbVal').innerText = `${fakePct}%`;
                document.getElementById('realProbVal').innerText = `${realPct}%`;

                if (chartInstance) {
                    chartInstance.data.datasets[0].data = [realPct, fakePct];
                    chartInstance.update();
                }

                if (data.status === 'VERIFIED') {
                    banner.className = 'live-banner banner-real';
                    bannerIcon.className = 'fa-solid fa-shield-check';
                    bannerText.innerText = `🛡️ REAL HUMAN VOICE VERIFIED (${realPct}%)`;
                } else if (data.status === 'ALERT') {
                    banner.className = 'live-banner banner-fake';
                    bannerIcon.className = 'fa-solid fa-triangle-exclamation';
                    bannerText.innerText = `🚨 WARNING: SUSPECTED DEEPFAKE VOICE (${fakePct}%)`;
                } else {
                    banner.className = 'live-banner banner-monitoring';
                    bannerIcon.className = 'fa-solid fa-shield-halved';
                    bannerText.innerText = '🛡️ Monitoring Voice Audio...';
                }

                // Update Audit History Log
                if (data.logs && data.logs.length > 0) {
                    const tbody = document.getElementById('historyTableBody');
                    if (tbody.children.length === 1 && tbody.children[0].innerText.includes('No call')) {
                        tbody.innerHTML = '';
                    }

                    tbody.innerHTML = '';
                    data.logs.forEach(log => {
                        const tr = document.createElement('tr');
                        const tag = log.type === 'DEEPFAKE' ? 
                            '<span class="badge-red">SUSPECTED DEEPFAKE</span>' : 
                            '<span class="badge-green">REAL VOICE VERIFIED</span>';
                        tr.innerHTML = `
                            <td>${log.time}</td>
                            <td style="font-weight: 700; color: ${log.type === 'DEEPFAKE' ? 'var(--danger)' : 'var(--success)'};">${log.msg}</td>
                            <td style="font-weight: 700;">${log.prob}</td>
                            <td>${tag}</td>
                        `;
                        tbody.appendChild(tr);
                    });
                }
            } catch (e) {
                console.log("USB polling error:", e);
            }
        }

        setInterval(pollUsbStatus, 500);
    </script>
</body>
</html>
"""

@app.route("/")
def index():
    return render_template_string(HTML_TEMPLATE)

@app.route("/reset")
def reset_state():
    global usb_state
    usb_state["status"] = "MONITORING"
    usb_state["message"] = "🛡️ Monitoring Voice Audio..."
    usb_state["prob_fake"] = 0.0
    usb_state["prob_real"] = 1.0
    usb_state["is_fake"] = False
    usb_state["logs"] = []
    return jsonify({"status": "reset"})

@app.route("/usb_status")
def usb_status():
    global usb_state
    # Freshness Timeout: If no new event in last 6 seconds, revert to MONITORING state (0% Fake)
    if time.time() - usb_state.get("last_update", 0) > 6.0:
        usb_state["status"] = "MONITORING"
        usb_state["message"] = "🛡️ Monitoring Voice Audio..."
        usb_state["prob_fake"] = 0.0
        usb_state["prob_real"] = 1.0
        usb_state["is_fake"] = False

    return jsonify(usb_state)

if __name__ == "__main__":
    print("==========================================================")
    print("LAUNCHING REAL-TIME USB CABLE TELEPHONY GUI DASHBOARD")
    print("==========================================================")
    print("Open your browser and navigate to: http://127.0.0.1:5001")
    print("==========================================================")
    app.run(host="0.0.0.0", port=5001, debug=False, use_reloader=False)
