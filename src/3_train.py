import os
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import Dataset, DataLoader
import numpy as np

# Check GPU availability
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
print(f"Using device: {device}")

# 1. Lightweight Audio Deepfake Classifier Model (1D-LCNN Architecture)
class AudioDeepfakeClassifier(nn.Module):
    def __init__(self):
        super(AudioDeepfakeClassifier, self).__init__()
        # Conv block 1
        self.conv1 = nn.Conv1d(1, 16, kernel_size=15, stride=2, padding=7)
        self.bn1 = nn.BatchNorm1d(16)
        self.relu = nn.ReLU()
        self.pool = nn.MaxPool1d(2)
        
        # Conv block 2
        self.conv2 = nn.Conv1d(16, 32, kernel_size=7, stride=2, padding=3)
        self.bn2 = nn.BatchNorm1d(32)
        
        # Conv block 3
        self.conv3 = nn.Conv1d(32, 64, kernel_size=5, stride=2, padding=2)
        self.bn3 = nn.BatchNorm1d(64)
        
        # Global Avg Pooling & FC
        self.global_pool = nn.AdaptiveAvgPool1d(1)
        self.fc = nn.Sequential(
            nn.Linear(64, 32),
            nn.ReLU(),
            nn.Dropout(0.3),
            nn.Linear(32, 2) # 0: Real, 1: Fake
        )

    def forward(self, x):
        # x shape: (batch_size, 1, samples)
        if x.dim() == 2:
            x = x.unsqueeze(1)
        x = self.pool(self.relu(self.bn1(self.conv1(x))))
        x = self.pool(self.relu(self.bn2(self.conv2(x))))
        x = self.pool(self.relu(self.bn3(self.conv3(x))))
        x = self.global_pool(x).squeeze(-1)
        out = self.fc(x)
        return out

# 2. PyTorch Dataset Loader
class DeepfakeDataset(Dataset):
    def __init__(self, audio_data, labels):
        self.audio = torch.tensor(audio_data, dtype=torch.float32)
        self.labels = torch.tensor(labels, dtype=torch.long)

    def __len__(self):
        return len(self.labels)

    def __getitem__(self, idx):
        return self.audio[idx], self.labels[idx]

def train_model(data_path="data/augmented/aug_audio.npy", label_path="data/augmented/aug_labels.npy", epochs=10, batch_size=32, lr=0.001):
    if not os.path.exists(data_path):
        print(f"Data file {data_path} not found. Running with fallback data/all_audio.npy...")
        data_path = "data/all_audio.npy"
        label_path = "data/all_labels.npy"

    print("Loading preprocessed dataset...")
    audio_data = np.load(data_path)
    labels = np.load(label_path)
    
    dataset = DeepfakeDataset(audio_data, labels)
    train_size = int(0.8 * len(dataset))
    val_size = len(dataset) - train_size
    train_dataset, val_dataset = torch.utils.data.random_split(dataset, [train_size, val_size])

    train_loader = DataLoader(train_dataset, batch_size=batch_size, shuffle=True)
    val_loader = DataLoader(val_dataset, batch_size=batch_size, shuffle=False)

    model = AudioDeepfakeClassifier().to(device)
    criterion = nn.CrossEntropyLoss()
    optimizer = optim.Adam(model.parameters(), lr=lr)

    print("Starting training...")
    for epoch in range(epochs):
        model.train()
        total_loss, correct = 0, 0
        for batch_audio, batch_labels in train_loader:
            batch_audio, batch_labels = batch_audio.to(device), batch_labels.to(device)

            optimizer.zero_grad()
            outputs = model(batch_audio)
            loss = criterion(outputs, batch_labels)
            loss.backward()
            optimizer.step()

            total_loss += loss.item()
            preds = outputs.argmax(dim=1)
            correct += (preds == batch_labels).sum().item()

        acc = correct / train_size
        print(f"Epoch [{epoch+1}/{epochs}] - Loss: {total_loss/len(train_loader):.4f} - Train Acc: {acc*100:.2f}%")

    os.makedirs("models", exist_ok=True)
    torch.save(model.state_dict(), "models/deepfake_detector.pth")
    print("Model saved to models/deepfake_detector.pth!")

    # Export to ONNX for CPU real-time inference
    try:
        dummy_input = torch.randn(1, 1, 8000).to(device)
        torch.onnx.export(
            model, 
            dummy_input, 
            "models/deepfake_detector.onnx", 
            input_names=["input"], 
            output_names=["output"],
            opset_version=14,
            do_constant_folding=True
        )
        print("Exported ONNX model to models/deepfake_detector.onnx!")
    except Exception as e:
        print(f"ONNX Export Warning: {e}")
        print("Existing model models/deepfake_detector.onnx remains active.")

if __name__ == "__main__":
    train_model()
