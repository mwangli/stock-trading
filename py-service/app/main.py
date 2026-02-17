"""
Stock AI Service - Main Application Entry
FastAPI application for sentiment analysis and LSTM prediction
"""
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.core.config import settings
from app.api import sentiment, prediction, training

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('logs/app.log'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan handler"""
    logger.info("Starting Stock AI Service...")
    logger.info(f"Model cache directory: {settings.MODEL_CACHE_DIR}")
    logger.info(f"Debug mode: {settings.DEBUG}")
    yield
    logger.info("Shutting down Stock AI Service...")


app = FastAPI(
    title="Stock AI Service",
    description="AI-powered stock analysis service with FinBERT sentiment and LSTM prediction",
    version="1.0.0",
    lifespan=lifespan
)

# CORS configuration
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Register routers
app.include_router(sentiment.router, prefix="/api/sentiment", tags=["Sentiment Analysis"])
app.include_router(prediction.router, prefix="/api/lstm", tags=["LSTM Prediction"])
app.include_router(training.router, prefix="/api/training", tags=["Model Training"])


@app.get("/")
async def root():
    """Health check endpoint"""
    return {
        "status": "ok",
        "service": "Stock AI Service",
        "version": "1.0.0"
    }


@app.get("/health")
async def health():
    """Detailed health check"""
    return {
        "status": "healthy",
        "models_loaded": True,
        "services": {
            "sentiment": "ready",
            "prediction": "ready"
        }
    }
