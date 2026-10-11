"""
Verify Aegis Voice metrics from the command line using the SAME ONNX model
that ships inside the Android app.

Usage (from repo root):
    python src/verify_metrics.py
    python src/verify_metrics.py --audio data/augmented/aug_audio.npy --labels data/augmented/aug_labels.npy
"""
import argparse
import time

import numpy as np
import onnxruntime as ort
from sklearn.metrics import (accuracy_score, confusion_matrix, f1_score,
                             precision_score, recall_score, roc_curve)

parser = argparse.ArgumentParser()
parser.add_argument("--model", default="android_app/app/src/main/assets/deepfake_detector.onnx")
parser.add_argument("--audio", default="data/augmented/aug_audio.npy")
parser.add_argument("--labels", default="data/augmented/aug_labels.npy")
args = parser.parse_args()

audio = np.load(args.audio, mmap_mode="r")
labels = np.load(args.labels)
session = ort.InferenceSession(args.model, providers=["CPUExecutionProvider"])

probs, times = [], []
for i in range(len(labels)):
    x = np.asarray(audio[i], dtype=np.float32).reshape(1, 1, 8000)
    t0 = time.perf_counter()
    logits = session.run(None, {"input": x})[0][0]
    times.append((time.perf_counter() - t0) * 1000)
    e = np.exp(logits - logits.max())
    probs.append(e[1] / e.sum())          # probability of FAKE

probs = np.array(probs)
preds = (probs > 0.5).astype(int)

fpr, tpr, _ = roc_curve(labels, probs)
k = np.nanargmin(np.abs((1 - tpr) - fpr))  # point where FNR == FPR

print("Clips evaluated :", len(labels), f"(real={int((labels==0).sum())}, fake={int((labels==1).sum())})")
print(f"Accuracy        : {accuracy_score(labels, preds)*100:.2f}%")
print(f"Precision       : {precision_score(labels, preds):.4f}")
print(f"Recall          : {recall_score(labels, preds):.4f}")
print(f"F1-Score        : {f1_score(labels, preds):.4f}")
print(f"EER             : {fpr[k]*100:.2f}%")
print("Confusion matrix [[TN FP],[FN TP]]:\n", confusion_matrix(labels, preds))
print(f"Latency (PC CPU): mean {np.mean(times):.2f} ms, p95 {np.percentile(times, 95):.2f} ms per 1-second clip")
