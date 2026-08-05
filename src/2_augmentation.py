# Makes clean audio sound like a real phone call
import os
import random
import librosa
import numpy as np

# Audioop is built-in Python standard library for G.711 mu-law compression
try:
    import audioop
except ImportError:
    # Python 3.13 fallback
    audioop = None

SAMPLE_RATE = 8000
DEMAND_DIR = "data/demand_noise"

def simulate_g711_phone(audio):
    """
    G.711 mu-law codec simulation
    Simulates Jio/Airtel/BSNL voice compression over cellular calls
    """
    audio = np.clip(audio, -1.0, 1.0)
    as_int16 = (audio * 32767).astype(np.int16).tobytes()
    if audioop:
        encoded = audioop.lin2ulaw(as_int16, 2)
        decoded = audioop.ulaw2lin(encoded, 2)
        result = np.frombuffer(decoded, dtype=np.int16).astype(np.float32)
        return result / 32767.0
    else:
        # Fallback 8-bit mu-law quantization formula
        mu = 255.0
        companded = np.sign(audio) * np.log(1.0 + mu * np.abs(audio)) / np.log(1.0 + mu)
        quantized = np.round((companded + 1.0) / 2.0 * mu)
        decompanded = (quantized / mu * 2.0) - 1.0
        expanded = np.sign(decompanded) * (1.0 / mu) * ((1.0 + mu)**np.abs(decompanded) - 1.0)
        return expanded.astype(np.float32)

def get_all_noise_files(base_dir=DEMAND_DIR):
    """Recursively collects all DEMAND noise wav files across all 18 subfolders"""
    noise_files = []
    for root, dirs, files in os.walk(base_dir):
        for f in files:
            if f.endswith(".wav"):
                noise_files.append(os.path.join(root, f))
    return noise_files

# Cache noise files list
NOISE_FILES_CACHE = None

def get_random_noise(length):
    """Pick a random noise clip from DEMAND dataset"""
    global NOISE_FILES_CACHE
    if NOISE_FILES_CACHE is None:
        NOISE_FILES_CACHE = get_all_noise_files(DEMAND_DIR)
    
    if not NOISE_FILES_CACHE:
        return np.zeros(length, dtype=np.float32)

    chosen_file = random.choice(NOISE_FILES_CACHE)
    try:
        noise, _ = librosa.load(chosen_file, sr=SAMPLE_RATE, mono=True)
        if len(noise) < length:
            noise = np.tile(noise, length // len(noise) + 1)
        start = random.randint(0, len(noise) - length)
        return noise[start:start+length].astype(np.float32)
    except:
        return np.zeros(length, dtype=np.float32)

def mix_noise(audio, snr_db):
    """Mix background noise at given volume level (Signal-to-Noise Ratio)"""
    noise = get_random_noise(len(audio))
    sig_pwr = np.mean(audio**2)
    nse_pwr = np.mean(noise**2)
    if nse_pwr == 0: 
        return audio
    scale = np.sqrt(sig_pwr / (nse_pwr * 10**(snr_db/10)))
    return np.clip(audio + scale * noise, -1.0, 1.0)

def augment_one(audio):
    """Full augmentation for one audio clip: G.711 Phone + DEMAND Noise"""
    # Always apply G.711 phone simulation
    aug = simulate_g711_phone(audio)
    # 20% chance of adding background DEMAND noise (kitchen, office, traffic)
    if random.random() < 0.20:
        snr = random.uniform(-15, -5)
        aug = mix_noise(aug, snr)
        
    return aug

if __name__ == "__main__":
    if not os.path.exists("data/all_audio.npy"):
        print("Error: data/all_audio.npy not found! Run src/1_data_loader.py first.")
    else:
        print("Loading prepared dataset...")
        audio = np.load("data/all_audio.npy")
        labels = np.load("data/all_labels.npy")
        
        os.makedirs("data/augmented", exist_ok=True)
        print(f"Applying G.711 Phone Compression + DEMAND Noise Augmentation to {len(audio)} clips...")
        
        aug_list = []
        for i, a in enumerate(audio):
            aug_list.append(augment_one(a))
            if (i+1) % 1000 == 0:
                print(f" Progress: {i+1}/{len(audio)} clips augmented...")
                
        aug_audio = np.array(aug_list)
        np.save("data/augmented/aug_audio.npy", aug_audio)
        np.save("data/augmented/aug_labels.npy", labels)
        
        print("\n================ SUCCESS! ================")
        print(f"Saved augmented dataset to data/augmented/aug_audio.npy ({aug_audio.shape})")
        print("Ready for Noise-Robust Training in Colab!")
        print("==========================================")