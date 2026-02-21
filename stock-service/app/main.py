"""
Stock AI Service - Main Application Entry
FastAPI application with direct database write data collection
"""
import logging
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from loguru import logger as loguru_logger

from app.core.config import settings
from app.core.database import init_databases
from app.api import sentiment, data_collection

# Import aktools router
try:
    from app.api import aktools
    has_aktools = True
except ImportError:
    has_aktools = False

# Import data_sync router conditionally
try:
    from app.api import data_sync
    has_data_sync = True
except ImportError:
    has_data_sync = False

# Configure logging
os.makedirs('logs', exist_ok=True)
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
    
    # Initialize databases
    logger.info("Initializing databases...")
    try:
        init_databases()
        logger.info("Databases initialized successfully")
    except Exception as e:
        logger.error(f"Failed to initialize databases: {e}")
    
    # Start V3.0 scheduler (includes all modules: Data Collection, Sentiment, Prediction, Ranking)
    # All results are written directly to MySQL for Java to read
    logger.info("Starting V3.0 scheduler...")
    try:
        from app.services.v3_scheduler import init_scheduler
        init_scheduler()
        logger.info("V3.0 scheduler started successfully")
    except Exception as e:
        logger.error(f"Failed to start V3.0 scheduler: {e}")
    
    yield
    
    # Shutdown
    logger.info("Shutting down Stock AI Service...")
    try:
        from app.services.v3_scheduler import shutdown_scheduler
        shutdown_scheduler()
        logger.info("V3.0 scheduler stopped")
    except Exception as e:
        logger.error(f"Failed to stop scheduler: {e}")


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
# Data collection router - provides query APIs and manual sync triggers
app.include_router(data_collection.router, prefix="/api/data", tags=["Data Collection"])

# Aktools router - provides AKShare HTTP API (public endpoints)
if has_aktools:
    app.include_router(aktools.router, prefix="/api/public", tags=["AKTools (AKShare HTTP API)"])

# Data sync router - provides manual sync APIs (if available)
if has_data_sync:
    app.include_router(data_sync.router, prefix="/api/sync", tags=["Data Sync"])


@app.get("/")
async def root():
    """Health check endpoint"""
    return {
        "status": "ok",
        "service": "Stock AI Service",
        "version": "1.0.0",
        "features": {
            "sentiment_analysis": True,
            "data_collection": True,
            "aktools": has_aktools
        }
    }


@app.get("/health")
async def health():
    """Detailed health check"""
    # Check database connections
    db_status = {"mysql": "unknown", "mongodb": "unknown", "redis": "unknown"}
    
    try:
        from app.core.database import mysql_engine
        mysql_engine.connect()
        db_status["mysql"] = "connected"
    except Exception as e:
        db_status["mysql"] = f"error: {str(e)}"
    
    try:
        from app.core.database import MongoDBClient
        mongo_client = MongoDBClient()
        mongo_client.client.admin.command('ping')
        db_status["mongodb"] = "connected"
    except Exception as e:
        db_status["mongodb"] = f"error: {str(e)}"
    
    try:
        from app.core.database import RedisClient
        redis_client = RedisClient()
        redis_client.client.ping()
        db_status["redis"] = "connected"
    except Exception as e:
        db_status["redis"] = f"error: {str(e)}"
    
    return {
        "status": "healthy",
        "services": {
            "sentiment": "ready",
            "data_collection": "ready",
            "databases": db_status
        },
        "data_collection_enabled": settings.DATA_COLLECTION_ENABLED
    }
