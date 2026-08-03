# Makes clean audio sound like a real phone call
import numpy as np
import audioop # built-in Python — no install needed!
import random
import librosa
import os
SAMPLE_RATE = 8000
DEMAND_DIR = "data/demand_noise"
def simulate_g711_phone(audio):
"""
G.711 mu-law codec simulation
This is exactly what Jio/Airtel/BSNL do to your voice on a phone call
audioop is already installed with Python — no pip install needed
"""
# Step 1: Convert float audio to 16-bit integers
audio = np.clip(audio, -1.0, 1.0)
as_int16 = (audio * 32767).astype(np.int16).tobytes()
# Step 2: Apply G.711 mu-law encoding (phone compression)
encoded = audioop.lin2ulaw(as_int16, 2)
# Step 3: Decode back (simulates receiving end)
decoded = audioop.ulaw2lin(encoded, 2)
# Step 4: Convert back to float
result = np.frombuffer(decoded, dtype=np.int16).astype(np.float32)
return result / 32767.0
def get_random_noise(length):
"""Pick a random noise clip from DEMAND dataset"""
all_files = []
for folder in os.listdir(DEMAND_DIR):
fpath = os.path.join(DEMAND_DIR, folder)
if os.path.isdir(fpath):
for f in os.listdir(fpath):
if f.endswith(".wav"):
all_files.append(os.path.join(fpath, f))
if not all_files:
return np.zeros(length)
noise, _ = librosa.load(random.choice(all_files), sr=8000, mono=True)
if len(noise) < length:
noise = np.tile(noise, length // len(noise) + 1)
start = random.randint(0, len(noise) - length)
return noise[start:start+length]
def mix_noise(audio, snr_db):
"""Mix background noise at given volume level"""
noise = get_random_noise(len(audio))
sig_pwr = np.mean(audio**2)
nse_pwr = np.mean(noise**2)
if nse_pwr == 0: return audio
scale = np.sqrt(sig_pwr / (nse_pwr * 10**(snr_db/10)))
return np.clip(audio + scale * noise, -1.0, 1.0)
def augment_one(audio):
"""Full augmentation for one audio clip"""
# Always apply G.711 phone simulation
aug = simulate_g711_phone(audio)
# 20% chance of adding background noise
if random.random() < 0.20:
snr = random.uniform(-15, -5)
aug = mix_noise(aug, snr)
return aug
if __name__ == "__main__":
print("Loading Sadhana's data...")
audio = np.load("data/all_audio.npy")
labels = np.load("data/all_labels.npy")
print(f"Augmenting {len(audio)} files... (this may take a few minutes)")
aug_list = []
for i, a in enumerate(audio):
aug_list.append(augment_one(a))
if (i+1) % 200 == 0:
print(f" Progress: {i+1}/{len(audio)}")
aug_audio = np.array(aug_list)
np.save("data/augmented/aug_audio.npy", aug_audio)
np.save("data/augmented/aug_labels.npy", labels)
print("DONE! Tell Sharanya she can start training now.")