import os
import torch
import numpy as np
import importlib
from sklearn.metrics import roc_curve, accuracy_score, precision_score, recall_score, f1_score

train_module = importlib.import_module("3_train")
AudioDeepfakeClassifier = train_module.AudioDeepfakeClassifier

def compute_eer(label, score):
    """Compute Equal Error Rate (EER)"""
    fpr, tpr, thresholds = roc_curve(label, score, pos_label=1)
    fnr = 1 - tpr
    eer_threshold = thresholds[np.nanargmin(np.absolute((fnr - fpr)))]
    eer = fpr[np.nanargmin(np.absolute((fnr - fpr)))]
    return eer, eer_threshold

def evaluate(model_path="models/deepfake_detector.pth", test_audio_path="data/augmented/aug_audio.npy", test_labels_path="data/augmented/aug_labels.npy"):
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    
    if not os.path.exists(test_audio_path):
        test_audio_path = "data/all_audio.npy"
        test_labels_path = "data/all_labels.npy"

    if not os.path.exists(model_path):
        print(f"Model file {model_path} not found. Please train the model first.")
        return

    model = AudioDeepfakeClassifier().to(device)
    model.load_state_dict(torch.load(model_path, map_location=device))
    model.eval()

    print("Loading test data...")
    audio = np.load(test_audio_path)
    labels = np.load(test_labels_path)

    audio_tensor = torch.tensor(audio, dtype=torch.float32).to(device)
    
    with torch.no_grad():
        outputs = model(audio_tensor)
        probs = torch.softmax(outputs, dim=1)[:, 1].cpu().numpy()
        preds = (probs > 0.5).astype(int)

    acc = accuracy_score(labels, preds)
    prec = precision_score(labels, preds, zero_division=0)
    rec = recall_score(labels, preds, zero_division=0)
    f1 = f1_score(labels, preds, zero_division=0)
    eer, threshold = compute_eer(labels, probs)

    print("\n================ EVALUATION RESULTS ================")
    print(f"Accuracy : {acc*100:.2f}%")
    print(f"Precision: {prec:.4f}")
    print(f"Recall   : {rec:.4f}")
    print(f"F1-Score : {f1:.4f}")
    print(f"EER      : {eer*100:.2f}% (Threshold: {threshold:.4f})")
    print("====================================================")

if __name__ == "__main__":
    evaluate()
