"""
LSTM Prediction Service
Stock price prediction using LSTM neural network
"""
import logging
from typing import Dict, List, Optional

import numpy as np
import pandas as pd
from sklearn.preprocessing import MinMaxScaler

from app.core.config import settings

logger = logging.getLogger(__name__)


class LSTMService:
    """LSTM-based stock prediction service"""

    def __init__(self):
        self.sequence_length = settings.LSTM_SEQUENCE_LENGTH
        self.feature_dim = settings.LSTM_FEATURE_DIM
        self._model = None
        self._scaler = MinMaxScaler(feature_range=(0, 1))
        self._is_trained = False
        logger.info("LSTMService initialized")

    @property
    def model(self):
        """Lazy load model"""
        if self._model is None:
            self._load_model()
        return self._model

    def _load_model(self):
        """Load trained LSTM model"""
        try:
            import tensorflow as tf
            from tensorflow.keras.models import load_model

            model_path = settings.MODEL_CACHE_DIR / settings.LSTM_MODEL_PATH
            if model_path.exists():
                self._model = load_model(str(model_path))
                self._is_trained = True
                logger.info(f"LSTM model loaded from {model_path}")
            else:
                logger.warning(f"Model not found at {model_path}, using fallback")
                self._model = self._create_fallback_model()
                self._is_trained = False
        except Exception as e:
            logger.error(f"Error loading LSTM model: {e}")
            self._model = self._create_fallback_model()
            self._is_trained = False

    def _create_fallback_model(self):
        """Create a simple fallback model for when no trained model exists"""
        try:
            import tensorflow as tf
            from tensorflow.keras.models import Sequential
            from tensorflow.keras.layers import LSTM, Dense, Dropout

            model = Sequential([
                LSTM(50, return_sequences=True, input_shape=(self.sequence_length, self.feature_dim)),
                Dropout(0.2),
                LSTM(50, return_sequences=False),
                Dropout(0.2),
                Dense(25),
                Dense(1)
            ])
            model.compile(optimizer='adam', loss='mean_squared_error')
            return model
        except Exception as e:
            logger.error(f"Error creating fallback model: {e}")
            return None

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

        # Normalize
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
                    "prediction": None,
                    "confidence": 0.0,
                    "error": f"Need at least {self.sequence_length} data points, got {len(data)}"
                }

            # Prepare data
            scaled_data = self._prepare_data(data)
            sequences = self._create_sequences(scaled_data)

            if len(sequences) == 0:
                return {
                    "stock_code": stock_code,
                    "prediction": None,
                    "confidence": 0.0,
                    "error": "Failed to create sequences"
                }

            # Predict
            predictions = self.model.predict(sequences, verbose=0)

            # Inverse transform to get actual price
            # Create dummy array for inverse transform
            dummy = np.zeros((len(predictions), self.feature_dim))
            dummy[:, 3] = predictions.flatten()  # Close price is index 3
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
                "prediction": None,
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

                # Predict next day
                pred = self.model.predict(sequences[-1:], verbose=0)

                # Inverse transform
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
        return {
            "model_type": "LSTM",
            "sequence_length": self.sequence_length,
            "feature_dim": self.feature_dim,
            "is_trained": self._is_trained,
            "model_path": str(settings.MODEL_CACHE_DIR / settings.LSTM_MODEL_PATH)
        }

    def unload(self):
        """Unload model from memory"""
        if self._model is not None:
            try:
                import tensorflow as tf
                tf.keras.backend.clear_session()
            except Exception as e:
                logger.error(f"Error clearing TensorFlow session: {e}")
            self._model = None
            self._is_trained = False
            logger.info("LSTM model unloaded")


# Singleton instance
lstm_service = LSTMService()
