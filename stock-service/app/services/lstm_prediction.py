"""
LSTM Prediction Service
Stock price prediction using LSTM neural network
Using PyTorch (unified with sentiment analysis)
"""
import logging
import os
import sys
from typing import Dict, List, Optional

import numpy as np
import pandas as pd
from sklearn.preprocessing import MinMaxScaler

from app.core.config import settings

logger = logging.getLogger(__name__)

# PyTorch import with error handling for Windows compatibility
TORCH_AVAILABLE = False
torch = None
nn = None

try:
    import torch
    import torch.nn as nn
    TORCH_AVAILABLE = True
    logger.info("PyTorch loaded successfully")
except Exception as e:
    logger.warning(f"PyTorch not available: {e}, using fallback mode")
    # Create mock classes
    class MockModule:
        pass
    class MockNN:
        LSTM = None
        Module = MockModule
        Dropout = object
        Linear = object
        ReLU = object
        FloatTensor = lambda x: x
        no_grad = lambda: None
        cuda = lambda: None
        device = lambda x: None
    torch = MockNN()
    nn = MockNN()


class LSTMM(nn.Module if TORCH_AVAILABLE else object):
    """LSTM Model in PyTorch"""

    def __init__(self, input_size: int, hidden_size: int = 100, num_layers: int = 2, dropout: float = 0.2):
        if not TORCH_AVAILABLE:
            self.hidden_size = hidden_size
            self.num_layers = num_layers
            return

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
        if not TORCH_AVAILABLE:
            # Return random prediction when PyTorch not available
            return np.random.randn(x.shape[0], 1).astype(np.float32)

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


class LSTMService:
    """LSTM-based stock prediction service using PyTorch"""

    def __init__(self):
        self.sequence_length = settings.LSTM_SEQUENCE_LENGTH
        self.feature_dim = settings.LSTM_FEATURE_DIM
        self._model = None
        self._scaler = MinMaxScaler(feature_range=(0, 1))
        self._scaler_min = None
        self._scaler_max = None
        self._is_trained = False

        # Safe device initialization
        if TORCH_AVAILABLE:
            try:
                # Force CPU on Windows to avoid DLL issues
                if sys.platform == 'win32':
                    self._device = torch.device("cpu")
                else:
                    self._device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
            except Exception as e:
                logger.warning(f"Failed to initialize device: {e}, using CPU")
                self._device = torch.device("cpu")
        else:
            self._device = None

        logger.info(f"LSTMService initialized. Device: {self._device}, PyTorch: {TORCH_AVAILABLE}")

    @property
    def model(self):
        """Lazy load model"""
        if self._model is None:
            self._load_model()
        return self._model

    def _load_model(self):
        """Load trained LSTM model"""
        try:
            model_path = settings.MODEL_CACHE_DIR / settings.LSTM_MODEL_PATH
            # Try .pt first (PyTorch), then .h5 (TensorFlow fallback)
            pt_path = str(model_path).replace('.h5', '.pt')

            if os.path.exists(pt_path) and TORCH_AVAILABLE:
                # Load PyTorch model
                checkpoint = torch.load(pt_path, map_location=self._device)

                # Create model
                self._model = LSTMM(
                    input_size=self.feature_dim,
                    hidden_size=100,
                    num_layers=2,
                    dropout=0.2
                )
                self._model.load_state_dict(checkpoint['model_state_dict'])
                self._model.to(self._device)
                self._model.eval()

                # Load scaler
                self._scaler_min = checkpoint.get('scaler_min')
                self._scaler_max = checkpoint.get('scaler_max')
                if self._scaler_min is not None and self._scaler_max is not None:
                    self._scaler.data_min_ = self._scaler_min
                    self._scaler.data_max_ = self._scaler_max

                self._is_trained = True
                logger.info(f"LSTM model loaded from {pt_path}")
            elif model_path.exists():
                # TensorFlow fallback - create untrained model
                logger.warning(f"TensorFlow model found at {model_path}, using fallback PyTorch model")
                self._model = self._create_fallback_model()
                self._is_trained = False
            else:
                logger.warning(f"Model not found at {pt_path}, using fallback")
                self._model = self._create_fallback_model()
                self._is_trained = False

        except Exception as e:
            logger.error(f"Error loading LSTM model: {e}")
            self._model = self._create_fallback_model()
            self._is_trained = False

    def _create_fallback_model(self):
        """Create a simple fallback model for when no trained model exists"""
        if TORCH_AVAILABLE:
            model = LSTMM(
                input_size=self.feature_dim,
                hidden_size=50,
                num_layers=2,
                dropout=0.2
            )
            model.to(self._device)
            model.eval()
            return model
        else:
            # Return mock model when PyTorch not available
            return MockLSTMM(self.feature_dim)

    def _prepare_data(self, data: List[Dict]) -> np.ndarray:
        """
        Prepare stock data for prediction

        Args:
            data: List of stock records with OHLCV features

        Returns:
            Normalized feature array
        """
        if not data or len(data) < self.sequence_length:
            raise ValueError(f"Need at least {self.sequence_length} data points")

        # Extract features: Open, High, Low, Close, Volume
        features = []
        for record in data:
            features.append([
                record.get('open', 0),
                record.get('high', 0),
                record.get('low', 0),
                record.get('close', 0),
                record.get('volume', 0)
            ])

        # Convert to numpy array
        feature_array = np.array(features)

        # Normalize using stored scaler or fit new
        if self._scaler_min is not None and self._scaler_max is not None:
            # Use stored scaler parameters
            scaled_data = (feature_array - self._scaler_min) / (self._scaler_max - self._scaler_min)
        else:
            # Fit new scaler
            scaled_data = self._scaler.fit_transform(feature_array)

        return scaled_data

    def _create_sequences(self, data: np.ndarray) -> np.ndarray:
        """Create sequences for LSTM input"""
        X = []
        for i in range(len(data) - self.sequence_length + 1):
            X.append(data[i:i + self.sequence_length])
        return np.array(X)

    def predict(self, stock_code: str, data: List[Dict]) -> Dict:
        """
        Predict next day price

        Args:
            stock_code: Stock symbol
            data: Historical stock data (must have at least sequence_length records)

        Returns:
            Prediction result
        """
        try:
            if len(data) < self.sequence_length:
                return {
                    "stock_code": stock_code,
                    "predicted_price": None,
                    "confidence": 0.0,
                    "error": f"Need at least {self.sequence_length} data points, got {len(data)}"
                }

            # Prepare data
            scaled_data = self._prepare_data(data)
            sequences = self._create_sequences(scaled_data)

            if len(sequences) == 0:
                return {
                    "stock_code": stock_code,
                    "predicted_price": None,
                    "confidence": 0.0,
                    "error": "Failed to create sequences"
                }

            if TORCH_AVAILABLE:
                # Convert to PyTorch tensor
                X = torch.FloatTensor(sequences).to(self._device)

                # Predict
                with torch.no_grad():
                    predictions = self._model(X).cpu().numpy()
            else:
                # Fallback prediction
                predictions = self._model(sequences)

            # Inverse transform to get actual price
            if self._scaler_min is not None and self._scaler_max is not None:
                # Use stored scaler parameters
                dummy = np.zeros((len(predictions), self.feature_dim))
                dummy[:, 3] = predictions.flatten() if len(predictions.shape) > 1 else predictions.flatten()
                # Reverse normalization manually
                actual_predictions = dummy * (self._scaler_max - self._scaler_min) + self._scaler_min
                actual_predictions = actual_predictions[:, 3]
            else:
                # Use scaler inverse transform
                dummy = np.zeros((len(predictions), self.feature_dim))
                dummy[:, 3] = predictions.flatten() if len(predictions.shape) > 1 else predictions.flatten()
                actual_predictions = self._scaler.inverse_transform(dummy)[:, 3]

            # Get the last prediction
            predicted_price = float(actual_predictions[-1])
            last_close = data[-1].get('close', 0)

            # Calculate change
            change = predicted_price - last_close
            change_percent = (change / last_close * 100) if last_close > 0 else 0

            return {
                "stock_code": stock_code,
                "predicted_price": round(predicted_price, 2),
                "current_price": last_close,
                "change": round(change, 2),
                "change_percent": round(change_percent, 2),
                "direction": "up" if change > 0 else "down" if change < 0 else "neutral",
                "confidence": 0.75 if self._is_trained else 0.5,
                "is_trained": self._is_trained,
                "model_status": "loaded" if self._is_trained else "fallback"
            }

        except Exception as e:
            logger.error(f"Error predicting for {stock_code}: {e}")
            return {
                "stock_code": stock_code,
                "predicted_price": None,
                "confidence": 0.0,
                "error": str(e)
            }

    def predict_batch(self, stock_code: str, data: List[Dict], days: int = 5) -> Dict:
        """
        Predict multiple days ahead

        Args:
            stock_code: Stock symbol
            data: Historical stock data
            days: Number of days to predict

        Returns:
            Multi-day prediction results
        """
        try:
            if len(data) < self.sequence_length:
                return {
                    "stock_code": stock_code,
                    "predictions": [],
                    "error": f"Need at least {self.sequence_length} data points"
                }

            predictions = []
            current_data = data.copy()

            for day in range(days):
                scaled_data = self._prepare_data(current_data)
                sequences = self._create_sequences(scaled_data)

                if len(sequences) == 0:
                    break

                if TORCH_AVAILABLE:
                    # Convert to PyTorch tensor
                    X = torch.FloatTensor(sequences[-1:]).to(self._device)

                    # Predict next day
                    with torch.no_grad():
                        pred = self._model(X).cpu().numpy()
                else:
                    pred = self._model(sequences[-1:])

                # Inverse transform
                if self._scaler_min is not None and self._scaler_max is not None:
                    dummy = np.zeros((1, self.feature_dim))
                    dummy[:, 3] = pred[0, 0]
                    predicted_price = (dummy * (self._scaler_max - self._scaler_min) + self._scaler_min)[0, 3]
                else:
                    dummy = np.zeros((1, self.feature_dim))
                    dummy[:, 3] = pred[0, 0]
                    predicted_price = self._scaler.inverse_transform(dummy)[0, 3]

                predictions.append({
                    "day": day + 1,
                    "predicted_price": round(float(predicted_price), 2)
                })

                # Add prediction to data for next iteration
                last_record = current_data[-1].copy()
                last_record['close'] = predicted_price
                current_data.append(last_record)

            return {
                "stock_code": stock_code,
                "predictions": predictions,
                "is_trained": self._is_trained
            }

        except Exception as e:
            logger.error(f"Error in batch prediction for {stock_code}: {e}")
            return {
                "stock_code": stock_code,
                "predictions": [],
                "error": str(e)
            }

    def get_model_info(self) -> Dict:
        """Get model information"""
        model_path = settings.MODEL_CACHE_DIR / settings.LSTM_MODEL_PATH
        pt_path = str(model_path).replace('.h5', '.pt')

        return {
            "model_type": "LSTM",
            "framework": "PyTorch" if TORCH_AVAILABLE else "Fallback",
            "device": str(self._device) if self._device else "N/A",
            "sequence_length": self.sequence_length,
            "feature_dim": self.feature_dim,
            "is_trained": self._is_trained,
            "model_path": pt_path
        }

    def unload(self):
        """Unload model from memory"""
        if self._model is not None:
            del self._model
            self._model = None
            self._is_trained = False
            # Clear GPU cache if available
            if TORCH_AVAILABLE and torch.cuda.is_available():
                torch.cuda.empty_cache()
            logger.info("LSTM model unloaded")


# Mock LSTM for fallback
class MockLSTMM:
    """Mock LSTM model when PyTorch is not available"""

    def __init__(self, input_size: int, hidden_size: int = 50, num_layers: int = 2, dropout: float = 0.2):
        self.hidden_size = hidden_size
        self.num_layers = num_layers

    def __call__(self, x):
        # Return random prediction
        return np.random.randn(x.shape[0], 1).astype(np.float32)

    def to(self, device):
        return self

    def eval(self):
        return self


# Singleton instance
lstm_service = LSTMService()
