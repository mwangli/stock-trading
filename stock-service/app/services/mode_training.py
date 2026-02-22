"""
Model Training Service
Train LSTM model for stock prediction
Using PyTorch (unified with sentiment analysis)
"""
import logging
import time
from pathlib import Path
from typing import Dict, List, Optional

import numpy as np
import pandas as pd
import torch
import torch.nn as nn
from sklearn.preprocessing import MinMaxScaler
from sklearn.model_selection import train_test_split

from app.core.config import settings

logger = logging.getLogger(__name__)


class LSTMM(nn.Module):
    """LSTM Model in PyTorch"""
    
    def __init__(self, input_size: int, hidden_size: int = 100, num_layers: int = 2, dropout: float = 0.2):
        super(LSTMM, self).__init__()
        
        self.hidden_size = hidden_size
        self.num_layers = num_layers
        
        # LSTM layer
        self.lstm = nn.LSTM(
            input_size=input_size,
            hidden_size=hidden_size,
            num_layers=num_layers,
            batch_first=True,
            dropout=dropout if num_layers > 1 else 0
        )
        
        # Dropout
        self.dropout = nn.Dropout(dropout)
        
        # Fully connected layers
        self.fc1 = nn.Linear(hidden_size, 50)
        self.fc2 = nn.Linear(50, 25)
        self.fc3 = nn.Linear(25, 1)
        
        self.relu = nn.ReLU()
    
    def forward(self, x):
        # LSTM forward
        lstm_out, (h_n, c_n) = self.lstm(x)
        
        # Take the last time step output
        out = lstm_out[:, -1, :]
        
        # Fully connected layers
        out = self.dropout(out)
        out = self.relu(self.fc1(out))
        out = self.relu(self.fc2(out))
        out = self.fc3(out)
        
        return out


class TrainingService:
    """LSTM model training service using PyTorch"""

    def __init__(self):
        self.sequence_length = settings.LSTM_SEQUENCE_LENGTH
        self.feature_dim = settings.LSTM_FEATURE_DIM
        self.batch_size = settings.TRAINING_BATCH_SIZE
        self.epochs = settings.TRAINING_EPOCHS
        self.learning_rate = settings.TRAINING_LEARNING_RATE
        self._is_training = False
        self._device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        logger.info(f"TrainingService initialized. Device: {self._device}")

    def _create_model(self) -> LSTMM:
        """Create LSTM model architecture"""
        model = LSTMM(
            input_size=self.feature_dim,
            hidden_size=100,
            num_layers=2,
            dropout=0.2
        )
        return model.to(self._device)

    def _prepare_training_data(self, data: pd.DataFrame) -> tuple:
        """
        Prepare data for training

        Args:
            data: DataFrame with OHLCV columns

        Returns:
            X_train, X_test, y_train, y_test, scaler
        """
        # Extract features
        features = data[['open', 'high', 'low', 'close', 'volume']].values

        # Normalize
        scaler = MinMaxScaler(feature_range=(0, 1))
        scaled_data = scaler.fit_transform(features)

        # Create sequences
        X, y = [], []
        for i in range(len(scaled_data) - self.sequence_length):
            X.append(scaled_data[i:i + self.sequence_length])
            # Target: next day's close price (index 3 is close)
            y.append(scaled_data[i + self.sequence_length, 3])

        X = np.array(X)
        y = np.array(y)

        # Split data
        X_train, X_test, y_train, y_test = train_test_split(
            X, y, test_size=0.2, random_state=42, shuffle=False
        )

        return X_train, X_test, y_train, y_test, scaler

    def _train_epoch(self, model: nn.Module, train_loader, criterion, optimizer) -> float:
        """Train for one epoch"""
        model.train()
        total_loss = 0
        
        for X_batch, y_batch in train_loader:
            X_batch = X_batch.to(self._device)
            y_batch = y_batch.to(self._device)
            
            optimizer.zero_grad()
            outputs = model(X_batch)
            loss = criterion(outputs.squeeze(), y_batch)
            loss.backward()
            optimizer.step()
            
            total_loss += loss.item()
        
        return total_loss / len(train_loader)

    def _evaluate(self, model: nn.Module, val_loader, criterion) -> tuple:
        """Evaluate model"""
        model.eval()
        total_loss = 0
        total_mae = 0
        count = 0
        
        with torch.no_grad():
            for X_batch, y_batch in val_loader:
                X_batch = X_batch.to(self._device)
                y_batch = y_batch.to(self._device)
                
                outputs = model(X_batch)
                loss = criterion(outputs.squeeze(), y_batch)
                total_loss += loss.item()
                
                # Calculate MAE
                mae = torch.abs(outputs.squeeze() - y_batch).mean().item()
                total_mae += mae
                count += 1
        
        return total_loss / count, total_mae / count

    async def train(
        self,
        data: List[Dict],
        stock_code: str = "default",
        epochs: Optional[int] = None,
        batch_size: Optional[int] = None,
        validation_split: float = 0.2
    ) -> Dict:
        """
        Train LSTM model

        Args:
            data: List of stock records
            stock_code: Stock symbol for model naming
            epochs: Number of training epochs
            batch_size: Batch size
            validation_split: Validation data ratio

        Returns:
            Training results
        """
        if self._is_training:
            return {
                "status": "error",
                "message": "Training already in progress"
            }

        try:
            self._is_training = True
            start_time = time.time()

            # Convert to DataFrame
            df = pd.DataFrame(data)

            # Validate data
            required_cols = ['open', 'high', 'low', 'close', 'volume']
            if not all(col in df.columns for col in required_cols):
                return {
                    "status": "error",
                    "message": f"Missing required columns. Need: {required_cols}"
                }

            if len(df) < self.sequence_length * 2:
                return {
                    "status": "error",
                    "message": f"Insufficient data. Need at least {self.sequence_length * 2} records"
                }

            # Prepare data
            X_train, X_test, y_train, y_test, scaler = self._prepare_training_data(df)

            logger.info(f"Training data prepared: {X_train.shape[0]} train, {X_test.shape[0]} test samples")

            # Convert to PyTorch tensors
            X_train_t = torch.FloatTensor(X_train)
            y_train_t = torch.FloatTensor(y_train)
            X_test_t = torch.FloatTensor(X_test)
            y_test_t = torch.FloatTensor(y_test)

            # Create data loaders
            train_dataset = torch.utils.data.TensorDataset(X_train_t, y_train_t)
            val_dataset = torch.utils.data.TensorDataset(X_test_t, y_test_t)
            
            train_loader = torch.utils.data.DataLoader(
                train_dataset, 
                batch_size=batch_size or self.batch_size, 
                shuffle=True
            )
            val_loader = torch.utils.data.DataLoader(
                val_dataset, 
                batch_size=batch_size or self.batch_size, 
                shuffle=False
            )

            # Create model
            model = self._create_model()
            
            # Loss and optimizer
            criterion = nn.MSELoss()
            optimizer = torch.optim.Adam(model.parameters(), lr=self.learning_rate)
            
            # Learning rate scheduler
            scheduler = torch.optim.lr_scheduler.ReduceLROnPlateau(
                optimizer, mode='min', factor=0.5, patience=5, verbose=True
            )

            # Training loop
            best_val_loss = float('inf')
            best_model_state = None
            history = {"loss": [], "val_loss": []}
            patience_counter = 0
            max_patience = 10

            num_epochs = epochs or self.epochs
            
            for epoch in range(num_epochs):
                # Train
                train_loss = self._train_epoch(model, train_loader, criterion, optimizer)
                
                # Evaluate
                val_loss, val_mae = self._evaluate(model, val_loader, criterion)
                
                # Learning rate scheduler step
                scheduler.step(val_loss)
                
                # Record history
                history["loss"].append(train_loss)
                history["val_loss"].append(val_loss)
                
                logger.info(f"Epoch {epoch+1}/{num_epochs} - Train Loss: {train_loss:.6f}, Val Loss: {val_loss:.6f}, Val MAE: {val_mae:.4f}")
                
                # Save best model
                if val_loss < best_val_loss:
                    best_val_loss = val_loss
                    best_model_state = model.state_dict().copy()
                    patience_counter = 0
                else:
                    patience_counter += 1
                
                # Early stopping
                if patience_counter >= max_patience:
                    logger.info(f"Early stopping at epoch {epoch+1}")
                    break

            # Load best model
            if best_model_state:
                model.load_state_dict(best_model_state)

            # Final evaluation
            train_loss, train_mae = self._evaluate(model, train_loader, criterion)
            test_loss, test_mae = self._evaluate(model, val_loader, criterion)

            # Save model
            model_path = settings.MODEL_CACHE_DIR / f"lstm_{stock_code}_model.pt"
            torch.save({
                'model_state_dict': model.state_dict(),
                'scaler_min': scaler.data_min_,
                'scaler_max': scaler.data_max_,
                'stock_code': stock_code,
                'sequence_length': self.sequence_length,
                'feature_dim': self.feature_dim,
            }, str(model_path))

            elapsed_time = time.time() - start_time

            result = {
                "status": "completed",
                "stock_code": stock_code,
                "model_path": str(model_path),
                "training_time": round(elapsed_time, 2),
                "epochs_completed": len(history["loss"]),
                "metrics": {
                    "train_loss": round(float(train_loss), 6),
                    "train_mae": round(float(train_mae), 4),
                    "test_loss": round(float(test_loss), 6),
                    "test_mae": round(float(test_mae), 4),
                    "val_loss": round(float(best_val_loss), 6)
                },
                "history": {
                    "loss": [round(float(x), 6) for x in history["loss"][-10:]],
                    "val_loss": [round(float(x), 6) for x in history["val_loss"][-10:]]
                }
            }

            logger.info(f"Training completed: {result}")
            return result

        except Exception as e:
            logger.error(f"Training error: {e}")
            return {
                "status": "error",
                "message": str(e)
            }
        finally:
            self._is_training = False

    def get_training_status(self) -> Dict:
        """Get current training status"""
        return {
            "is_training": self._is_training,
            "device": str(self._device),
            "config": {
                "sequence_length": self.sequence_length,
                "feature_dim": self.feature_dim,
                "batch_size": self.batch_size,
                "epochs": self.epochs,
                "learning_rate": self.learning_rate
            }
        }

    def list_models(self) -> Dict:
        """List available trained models"""
        models = []
        model_dir = settings.MODEL_CACHE_DIR

        if model_dir.exists():
            for f in model_dir.glob("*.pt"):
                models.append({
                    "name": f.name,
                    "path": str(f),
                    "size_mb": round(f.stat().st_size / (1024 * 1024), 2),
                    "modified": pd.Timestamp.fromtimestamp(f.stat().st_mtime).isoformat()
                })

        return {
            "models": models,
            "count": len(models)
        }


# Singleton instance
training_service = TrainingService()
