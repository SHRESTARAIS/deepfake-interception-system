# Loads all audio files and their labels (real=0, fake=1)
import os
import glob
import librosa
import numpy as np

SAMPLE_RATE = 8000 # 8 kHz — same as phone call quality
SAMPLES = 8000     # 1 second of audio = 8000 samples

def find_asvspoof_paths(base_dir="data/asvspoof2019"):
    """Dynamically locates LA protocol text file and LA audio folder"""
    label_path = None
    audio_dir = None

    for root, dirs, files in os.walk(base_dir):
        for f in files:
            if f == "ASVspoof2019.LA.cm.train.trn.txt":
                label_path = os.path.join(root, f)
        for d in dirs:
            norm_root = root.replace("\\", "/").upper()
            if d == "flac" and "LA" in norm_root and "TRAIN" in norm_root:
                audio_dir = os.path.join(root, d)

    return label_path, audio_dir

def read_labels(label_file):
    labels = {}
    with open(label_file) as f:
        for line in f:
            parts = line.strip().split()
            filename = parts[1]
            tag = parts[4]
            labels[filename] = 0 if tag == "bonafide" else 1
    return labels

def load_one_audio(path):
    try:
        audio, _ = librosa.load(path, sr=SAMPLE_RATE, mono=True)
        if len(audio) < SAMPLES:
            audio = np.pad(audio, (0, SAMPLES - len(audio)))
        else:
            audio = audio[:SAMPLES]
        return audio.astype(np.float32)
    except:
        return None

def load_asvspoof(base_dir="data/asvspoof2019", max_files=5000):
    print("Loading ASVspoof 2019 LA...")
    label_path, audio_dir = find_asvspoof_paths(base_dir)
    
    if not label_path or not audio_dir:
        print(f"Error: Could not find LA label file or LA flac audio folder under {base_dir}")
        return [], []

    print(f" Label File Found: {label_path}")
    print(f" Audio Dir Found:  {audio_dir}")

    labels = read_labels(label_path)
    audios, labs = [], []
    for fname, lab in list(labels.items())[:max_files]:
        path = os.path.join(audio_dir, fname + ".flac")
        if os.path.exists(path):
            a = load_one_audio(path)
            if a is not None:
                audios.append(a)
                labs.append(lab)
    print(f" Loaded {len(audios)} ASVspoof LA files!")
    return audios, labs

def load_wavefake(wavefake_dir="data/wavefake", max_files=2000):
    print("Loading WaveFake...")
    audios, labs = [], []
    count = 0
    if not os.path.exists(wavefake_dir):
        print(f"Warning: {wavefake_dir} not found. Skipping WaveFake.")
        return [], []

    for root, dirs, files in os.walk(wavefake_dir):
        for f in files:
            if count >= max_files:
                break
            if f.endswith((".wav", ".flac")):
                a = load_one_audio(os.path.join(root, f))
                if a is not None:
                    audios.append(a)
                    labs.append(1) # WaveFake = all FAKE
                    count += 1
    print(f" Loaded {len(audios)} WaveFake files!")
    return audios, labs

if __name__ == "__main__":
    os.makedirs("data", exist_ok=True)
    a1, l1 = load_asvspoof()
    a2, l2 = load_wavefake()
    all_audio = np.array(a1 + a2)
    all_labels = np.array(l1 + l2)
    np.save("data/all_audio.npy", all_audio)
    np.save("data/all_labels.npy", all_labels)
    print(f"\n================ SUCCESS! ================")
    print(f"Total files prepared: {len(all_audio)}")
    print(f"Real voices (0): {sum(all_labels==0)}")
    print(f"Fake voices (1): {sum(all_labels==1)}")
    print(f"Saved to data/all_audio.npy and data/all_labels.npy")
    print("==========================================")