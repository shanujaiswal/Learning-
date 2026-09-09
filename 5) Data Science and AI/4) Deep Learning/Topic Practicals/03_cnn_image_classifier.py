"""
03 - CNN Image Classifier (PyTorch)
=====================================

Companion to: Theory/02 Convolutional Neural Networks CNNs.md

A small convolutional neural network trained on FashionMNIST
(28x28 grayscale images, 10 clothing categories). Architecture:

    Conv(1->16, 3x3) -> ReLU -> MaxPool(2)
    Conv(16->32, 3x3) -> ReLU -> MaxPool(2)
    Conv(32->64, 3x3) -> ReLU
    Flatten -> Linear -> ReLU -> Linear(-> 10 classes)

NOTE: the first time you run this script, torchvision will DOWNLOAD the
FashionMNIST dataset (~30 MB) into a local ./data folder. Subsequent runs
reuse the cached copy and won't re-download.

Run:
    python 03_cnn_image_classifier.py
"""

import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import DataLoader
from torchvision import datasets, transforms

torch.manual_seed(42)

DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")
print(f"Using device: {DEVICE}")


# ---------------------------------------------------------------------------
# 1. Data: FashionMNIST, downloaded on first run into ./data
# ---------------------------------------------------------------------------
transform = transforms.Compose([
    transforms.ToTensor(),  # scales pixel values from [0, 255] to [0.0, 1.0]
])

train_dataset = datasets.FashionMNIST(
    root="./data", train=True, download=True, transform=transform
)
test_dataset = datasets.FashionMNIST(
    root="./data", train=False, download=True, transform=transform
)

BATCH_SIZE = 64
train_loader = DataLoader(train_dataset, batch_size=BATCH_SIZE, shuffle=True)
test_loader = DataLoader(test_dataset, batch_size=BATCH_SIZE, shuffle=False)

CLASS_NAMES = [
    "T-shirt/top", "Trouser", "Pullover", "Dress", "Coat",
    "Sandal", "Shirt", "Sneaker", "Bag", "Ankle boot",
]


# ---------------------------------------------------------------------------
# 2. Model: a small 3-conv-layer CNN
# ---------------------------------------------------------------------------
class SmallCNN(nn.Module):
    def __init__(self, n_classes=10):
        super().__init__()
        # Input: (batch, 1, 28, 28)
        self.conv1 = nn.Conv2d(1, 16, kernel_size=3, padding=1)   # -> (16, 28, 28)
        self.conv2 = nn.Conv2d(16, 32, kernel_size=3, padding=1)  # -> (32, 14, 14) after pool1
        self.conv3 = nn.Conv2d(32, 64, kernel_size=3, padding=1)  # -> (64, 7, 7) after pool2
        self.pool = nn.MaxPool2d(2)  # halves height and width

        self.fc1 = nn.Linear(64 * 7 * 7, 128)
        self.fc2 = nn.Linear(128, n_classes)

    def forward(self, x):
        x = F.relu(self.conv1(x))
        x = self.pool(x)                # 28x28 -> 14x14

        x = F.relu(self.conv2(x))
        x = self.pool(x)                # 14x14 -> 7x7

        x = F.relu(self.conv3(x))       # stays 7x7 (padding=1, no pooling)

        x = x.flatten(start_dim=1)      # (batch, 64*7*7)
        x = F.relu(self.fc1(x))
        x = self.fc2(x)                  # raw logits, softmax happens inside the loss
        return x


model = SmallCNN().to(DEVICE)
criterion = nn.CrossEntropyLoss()
optimizer = torch.optim.Adam(model.parameters(), lr=1e-3)


# ---------------------------------------------------------------------------
# 3. Train / evaluate loops
# ---------------------------------------------------------------------------
def train_one_epoch():
    model.train()
    total_loss, correct, total = 0.0, 0, 0
    for images, labels in train_loader:
        images, labels = images.to(DEVICE), labels.to(DEVICE)

        optimizer.zero_grad()
        logits = model(images)
        loss = criterion(logits, labels)
        loss.backward()
        optimizer.step()

        total_loss += loss.item() * images.size(0)
        correct += (logits.argmax(dim=1) == labels).sum().item()
        total += labels.size(0)

    return total_loss / total, correct / total


@torch.no_grad()
def evaluate():
    model.eval()
    correct, total = 0, 0
    for images, labels in test_loader:
        images, labels = images.to(DEVICE), labels.to(DEVICE)
        logits = model(images)
        correct += (logits.argmax(dim=1) == labels).sum().item()
        total += labels.size(0)
    return correct / total


if __name__ == "__main__":
    N_EPOCHS = 5
    print(f"\nTraining SmallCNN on FashionMNIST for {N_EPOCHS} epochs...\n")

    for epoch in range(1, N_EPOCHS + 1):
        train_loss, train_acc = train_one_epoch()
        test_acc = evaluate()
        print(f"epoch {epoch}/{N_EPOCHS}  "
              f"train_loss={train_loss:.4f}  train_acc={train_acc:.2%}  "
              f"test_acc={test_acc:.2%}")

    # A few example predictions on the first test batch.
    print("\nSample predictions from the first test batch:")
    model.eval()
    images, labels = next(iter(test_loader))
    images, labels = images.to(DEVICE), labels.to(DEVICE)
    with torch.no_grad():
        preds = model(images).argmax(dim=1)
    for i in range(8):
        print(f"  predicted={CLASS_NAMES[preds[i]]:<12}  actual={CLASS_NAMES[labels[i]]}")
