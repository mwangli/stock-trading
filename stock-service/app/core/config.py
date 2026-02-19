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

    # Database Configuration
    # MySQL
    MYSQL_HOST: str = "localhost"
    MYSQL_PORT: int = 3306
    MYSQL_USER: str = "root"
    MYSQL_PASSWORD: str = "password"
    MYSQL_DATABASE: str = "stock_trading"

    # MongoDB
    MONGODB_URL: str = "mongodb://localhost:27017"
    MONGODB_DB: str = "stock_trading"

    # Redis
    REDIS_HOST: str = "localhost"
    REDIS_PORT: int = 6379
    REDIS_DB: int = 0
    REDIS_PASSWORD: str = ""

    # Data Collection Configuration
    DATA_COLLECTION_ENABLED: bool = True
    DATA_COLLECTION_CRON_STOCK_LIST: str = "0 0 9 * * *"  # 每天9:00
    DATA_COLLECTION_CRON_HISTORICAL: str = "0 30 15 * * *"  # 每天15:30
    DATA_COLLECTION_CRON_REALTIME: str = "0 0/1 9-15 * * *"  # 交易时段每分钟
    DATA_COLLECTION_HISTORY_DAYS: int = 60
    DATA_COLLECTION_BATCH_SIZE: int = 100
    DATA_COLLECTION_THREAD_POOL_SIZE: int = 10
    DATA_COLLECTION_MAX_RETRIES: int = 3
    DATA_COLLECTION_RETRY_DELAY: int = 1  # seconds

    class Config:
        env_file = ".env"
        case_sensitive = True


settings = Settings()

# Create directories if not exist
settings.MODEL_CACHE_DIR.mkdir(exist_ok=True)
settings.DATA_DIR.mkdir(exist_ok=True)
