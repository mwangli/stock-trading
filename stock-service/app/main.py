"""
Stock AI Service - Main Application Entry
Python后端服务，直接读写数据库，无需提供HTTP API给Java端

环境变量控制:
- MINIMAL_MODE=true    # 最小模式，不加载PyTorch/调度器
- ENABLE_SCHEDULER=true # 启用定时任务调度器
"""
import logging
import os
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from loguru import logger as loguru_logger

from app.core.config import settings

# 环境变量控制
MINIMAL_MODE = os.getenv("MINIMAL_MODE", "false").lower() == "true"
ENABLE_SCHEDULER = os.getenv("ENABLE_SCHEDULER", "true").lower() == "true"

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
    if MINIMAL_MODE:
        logger.info("Starting in MINIMAL MODE (scheduler only)...")
        yield
        logger.info("Shutting down MINIMAL MODE...")
        return

    # 完整模式
    logger.info("Starting Stock AI Service (Full Mode)...")
    logger.info(f"Model cache directory: {settings.MODEL_CACHE_DIR}")
    logger.info(f"Debug mode: {settings.DEBUG}")
    
    # Initialize databases
    logger.info("Initializing databases...")
    try:
        from app.core.database import init_databases
        init_databases()
        logger.info("Databases initialized successfully")
    except Exception as e:
        logger.error(f"Failed to initialize databases: {e}")
    
    # Start scheduler (optional)
    if ENABLE_SCHEDULER:
        logger.info("Starting Unified scheduler...")
        try:
            from app.core.scheduler import init_scheduler
            init_scheduler()
            logger.info("Unified scheduler started successfully")
        except Exception as e:
            logger.error(f"Failed to start Unified scheduler: {e}")
    
    yield
    
    # Shutdown
    logger.info("Shutting down Stock AI Service...")
    if ENABLE_SCHEDULER:
        try:
            from app.core.scheduler import shutdown_scheduler
            shutdown_scheduler()
            logger.info("Unified scheduler stopped")
        except Exception as e:
            logger.error(f"Failed to stop scheduler: {e}")


# Create FastAPI app (基础服务，仅用于健康检查)
app = FastAPI(
    title="Stock AI Service",
    description="Python后端服务 - 直接读写数据库，定时执行数据采集/情感分析/LSTM预测/股票排名",
    version="1.0.0",
    lifespan=lifespan
)

# CORS configuration
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS if not MINIMAL_MODE else ["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/")
async def root():
    """Health check endpoint"""
    return {
        "status": "ok",
        "service": "Stock AI Service",
        "version": "1.0.0",
        "mode": "minimal" if MINIMAL_MODE else "full",
        "description": "Python后端服务，直接读写数据库"
    }


@app.get("/health")
async def health():
    """Detailed health check"""
    if MINIMAL_MODE:
        return {"status": "healthy", "mode": "minimal"}
    
    # 完整模式：检查数据库连接
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
        "mode": "full",
        "databases": db_status,
        "data_collection_enabled": settings.DATA_COLLECTION_ENABLED
    }
