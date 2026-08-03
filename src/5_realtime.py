import torch
import numpy as np
import librosa
import audioop

SAMPLE_RATE = 8000
SAMPLES = 8000

def preprocess_live_chunk(audio_chunk, sr=8000):
    """
    Preprocess real-time streaming audio chunk:
    1. Resample to 8kHz if required
    2. G.711 mu-law compression simulation
    3. Truncate/Pad to 1 sec (8000 samples)
    """
    if sr != SAMPLE_RATE:
        audio_chunk = librosa.resample(audio_chunk, orig_sr=sr, target_sr=SAMPLE_RATE)

    if len(audio_chunk) < SAMPLES:
        audio_chunk = np.pad(audio_chunk, (0, SAMPLES - len(audio_chunk)))
    else:
        audio_chunk = audio_chunk[:SAMPLES]

    # G.711 mu-law compression
    clipped = np.clip(audio_chunk, -1.0, 1.0)
    as_int16 = (clipped * 32767).astype(np.int16).tobytes()
    encoded = audioop.lin2ulaw(as_int16, 2)
    decoded = audioop.ulaw2lin(encoded, 2)
    result = np.frombuffer(decoded, dtype=np.int16).astype(np.float32) / 32767.0

    return torch.tensor(result, dtype=torch.float32).unsqueeze(0).unsqueeze(0)

def detect_live_stream(audio_chunk, model):
    """Predict if the live audio chunk is REAL (0) or DEEPFAKE (1)"""
    tensor_input = preprocess_live_chunk(audio_chunk)
    with torch.no_grad():
        outputs = model(tensor_input)
        prob_fake = torch.softmax(outputs, dim=1)[0, 1].item()
    
    label = "DEEPFAKE (WARNING!)" if prob_fake > 0.5 else "REAL VOICE"
    return label, prob_fake

if __name__ == "__main__":
    print("Real-time audio deepfake interception ready.")
