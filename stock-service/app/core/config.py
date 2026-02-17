"""
Application Configuration
"""
import os
from pathlib import Path
from typing import List

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """Application settings"""

    # Application
    APP_NAME: str = "Stock AI Service"
    DEBUG: bool = True
    API_PREFIX: str = "/api"

    # Server
    HOST: str = "0.0.0.0"
    PORT: int = 8001

    # CORS
    CORS_ORIGINS: List[str] = [
        "http://localhost:8000",
        "http://localhost:8080",
        "http://localhost:3000"
    ]

    # Model paths
    BASE_DIR: Path = Path(__file__).resolve().parent.parent
    MODEL_CACHE_DIR: Path = BASE_DIR / "models"
    DATA_DIR: Path = BASE_DIR / "data"

    # FinBERT settings
    FINBERT_MODEL_NAME: str = "ProsusAI/finbert"
    FINBERT_MAX_LENGTH: int = 512

    # LSTM settings
    LSTM_MODEL_PATH: str = "models/lstm_stock_model.h5"
    LSTM_SEQUENCE_LENGTH: int = 60
    LSTM_FEATURE_DIM: int = 5

    # Training settings
    TRAINING_BATCH_SIZE: int = 32
    TRAINING_EPOCHS: int = 50
    TRAINING_LEARNING_RATE: float = 0.001

    # Cache settings
    SENTIMENT_CACHE_TTL: int = 3600  # seconds
    PREDICTION_CACHE_TTL: int = 300  # seconds

    class Config:
        env_file = ".env"
        case_sensitive = True


settings = Settings()

# Create directories if not exist
settings.MODEL_CACHE_DIR.mkdir(exist_ok=True)
settings.DATA_DIR.mkdir(exist_ok=True)
