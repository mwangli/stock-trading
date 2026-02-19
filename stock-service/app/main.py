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
    
    # Start data collection scheduler
    if settings.DATA_COLLECTION_ENABLED:
        logger.info("Starting data collection scheduler...")
        try:
            from app.services.scheduler import init_scheduler
            init_scheduler()
            logger.info("Data collection scheduler started")
        except Exception as e:
            logger.error(f"Failed to start scheduler: {e}")
    
    yield
    
    # Shutdown
    logger.info("Shutting down Stock AI Service...")
    if settings.DATA_COLLECTION_ENABLED:
        try:
            from app.services.scheduler import shutdown_scheduler
            shutdown_scheduler()
            logger.info("Data collection scheduler stopped")
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

# Data sync router - provides manual sync APIs (if available)
if has_data_sync:
    app.include_router(data_sync.router, prefix="/api/sync", tags=["Data Sync"])


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
