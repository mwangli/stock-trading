"""
Model Training Service
Train LSTM model for stock prediction
"""
import logging
import time
from pathlib import Path
from typing import Dict, List, Optional

import numpy as np
import pandas as pd
from sklearn.preprocessing import MinMaxScaler
from sklearn.model_selection import train_test_split

from app.core.config import settings

logger = logging.getLogger(__name__)


class TrainingService:
    """LSTM model training service"""

    def __init__(self):
        self.sequence_length = settings.LSTM_SEQUENCE_LENGTH
        self.feature_dim = settings.LSTM_FEATURE_DIM
        self.batch_size = settings.TRAINING_BATCH_SIZE
        self.epochs = settings.TRAINING_EPOCHS
        self.learning_rate = settings.TRAINING_LEARNING_RATE
        self._is_training = False
        logger.info("TrainingService initialized")

    def _create_model(self):
        """Create LSTM model architecture"""
        try:
            import tensorflow as tf
            from tensorflow.keras.models import Sequential
            from tensorflow.keras.layers import LSTM, Dense, Dropout
            from tensorflow.keras.optimizers import Adam
            from tensorflow.keras.callbacks import EarlyStopping, ModelCheckpoint

            model = Sequential([
                LSTM(100, return_sequences=True, input_shape=(self.sequence_length, self.feature_dim)),
                Dropout(0.2),
                LSTM(100, return_sequences=False),
                Dropout(0.2),
                Dense(50, activation='relu'),
                Dense(25, activation='relu'),
                Dense(1)
            ])

            optimizer = Adam(learning_rate=self.learning_rate)
            model.compile(optimizer=optimizer, loss='mse', metrics=['mae'])

            return model
        except Exception as e:
            logger.error(f"Error creating model: {e}")
            raise

    def _prepare_training_data(self, data: pd.DataFrame) -> tuple:
        """
        Prepare data for training

        Args:
            data: DataFrame with OHLCV columns

        Returns:
            X_train, X_test, y_train, y_test
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
            # Target: next day's close price
            y.append(scaled_data[i + self.sequence_length, 3])

        X = np.array(X)
        y = np.array(y)

        # Split data
        X_train, X_test, y_train, y_test = train_test_split(
            X, y, test_size=0.2, random_state=42, shuffle=False
        )

        return X_train, X_test, y_train, y_test, scaler

    def train(
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

            # Create model
            model = self._create_model()

            # Callbacks
            import tensorflow as tf
            from tensorflow.keras.callbacks import EarlyStopping, ModelCheckpoint

            model_path = settings.MODEL_CACHE_DIR / f"lstm_{stock_code}_model.h5"
            callbacks = [
                EarlyStopping(
                    monitor='val_loss',
                    patience=10,
                    restore_best_weights=True,
                    verbose=1
                ),
                ModelCheckpoint(
                    str(model_path),
                    monitor='val_loss',
                    save_best_only=True,
                    verbose=1
                )
            ]

            # Train
            history = model.fit(
                X_train, y_train,
                epochs=epochs or self.epochs,
                batch_size=batch_size or self.batch_size,
                validation_split=validation_split,
                callbacks=callbacks,
                verbose=1
            )

            # Evaluate
            train_loss, train_mae = model.evaluate(X_train, y_train, verbose=0)
            test_loss, test_mae = model.evaluate(X_test, y_test, verbose=0)

            elapsed_time = time.time() - start_time

            result = {
                "status": "completed",
                "stock_code": stock_code,
                "model_path": str(model_path),
                "training_time": round(elapsed_time, 2),
                "epochs_completed": len(history.history['loss']),
                "metrics": {
                    "train_loss": round(float(train_loss), 6),
                    "train_mae": round(float(train_mae), 4),
                    "test_loss": round(float(test_loss), 6),
                    "test_mae": round(float(test_mae), 4),
                    "val_loss": round(float(history.history['val_loss'][-1]), 6)
                },
                "history": {
                    "loss": [round(float(x), 6) for x in history.history['loss'][-10:]],
                    "val_loss": [round(float(x), 6) for x in history.history['val_loss'][-10:]]
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
            try:
                import tensorflow as tf
                tf.keras.backend.clear_session()
            except:
                pass

    def get_training_status(self) -> Dict:
        """Get current training status"""
        return {
            "is_training": self._is_training,
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
            for f in model_dir.glob("*.h5"):
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
