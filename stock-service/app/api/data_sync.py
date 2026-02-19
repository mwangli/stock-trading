"""
Data Sync API Routes
Provides manual trigger endpoints for data collection and sync operations
"""
from typing import Dict, Any, Optional, List
from fastapi import APIRouter, Query, HTTPException, BackgroundTasks
from pydantic import BaseModel
from loguru import logger

from app.services.data_collection_service import data_collection_service


router = APIRouter()


class SyncResponse(BaseModel):
    """Sync operation response"""
    code: int
    message: str
    data: Optional[Dict[str, Any]] = None


class StockListResponse(BaseModel):
    """Stock list response"""
    code: int
    message: str
    data: Optional[Dict[str, int]] = None


class HistoricalDataResponse(BaseModel):
    """Historical data sync response"""
    code: int
    message: str
    data: Optional[Dict[str, Any]] = None


# ===============================
# Manual Sync Endpoints
# ===============================

@router.post("/stocks", response_model=StockListResponse)
async def sync_stock_list(background_tasks: BackgroundTasks):
    """
    Manually trigger stock list sync from AKShare to MySQL
    """
    try:
        logger.info("Manual trigger: Stock list sync")
        
        # Run in background to avoid timeout
        async def do_sync():
            return await data_collection_service.fetch_and_save_stock_list()
        
        background_tasks.add_task(do_sync)
        
        return StockListResponse(
            code=200,
            message="Stock list sync started in background",
            data={"status": "started"}
        )
    except Exception as e:
        logger.error(f"Failed to start stock list sync: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/stocks/{stock_code}/history", response_model=HistoricalDataResponse)
async def sync_stock_history(
    stock_code: str,
    days: int = Query(60, description="Number of days to sync"),
    background_tasks: BackgroundTasks = None
):
    """
    Manually trigger historical data sync for a specific stock from AKShare to MongoDB
    """
    try:
        logger.info(f"Manual trigger: Historical data sync for {stock_code}, days={days}")
        
        count = await data_collection_service.fetch_and_save_historical_data(
            stock_code=stock_code,
            days=days
        )
        
        return HistoricalDataResponse(
            code=200,
            message=f"Historical data sync completed for {stock_code}",
            data={
                "stock_code": stock_code,
                "days": days,
                "records_saved": count
            }
        )
    except Exception as e:
        logger.error(f"Failed to sync historical data for {stock_code}: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/quotes", response_model=SyncResponse)
async def sync_realtime_quotes():
    """
    Manually trigger real-time quotes sync from AKShare to Redis and MySQL
    """
    try:
        logger.info("Manual trigger: Real-time quotes sync")
        
        count = await data_collection_service.fetch_and_save_realtime_quotes()
        
        return SyncResponse(
            code=200,
            message="Real-time quotes sync completed",
            data={"quotes_updated": count}
        )
    except Exception as e:
        logger.error(f"Failed to sync real-time quotes: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/all", response_model=SyncResponse)
async def sync_all_stocks(background_tasks: BackgroundTasks):
    """
    Manually trigger full sync of all stocks data
    This will sync stock list and historical data for all stocks
    """
    try:
        logger.info("Manual trigger: Full stock data sync")
        
        # Run in background to avoid timeout
        async def do_full_sync():
            return await data_collection_service.sync_all_stocks_data()
        
        background_tasks.add_task(do_full_sync)
        
        return SyncResponse(
            code=200,
            message="Full stock data sync started in background",
            data={
                "status": "started",
                "note": "Check logs for progress. This may take several minutes."
            }
        )
    except Exception as e:
        logger.error(f"Failed to start full sync: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ===============================
# Sync Status Endpoints
# ===============================

@router.get("/status", response_model=SyncResponse)
async def get_sync_status():
    """
    Get current sync status and statistics
    """
    try:
        from app.core.database import MySQLSessionLocal, StockInfoModel
        from app.core.database import get_mongo_collection
        
        # Get MySQL stats
        db = MySQLSessionLocal()
        stock_count = db.query(StockInfoModel).filter(
            StockInfoModel.deleted == '0'
        ).count()
        db.close()
        
        # Get MongoDB stats
        prices_collection = get_mongo_collection("stock_prices")
        prices_count = prices_collection.estimated_document_count()
        
        # Get unique stocks in MongoDB
        stocks_with_prices = len(prices_collection.distinct("code"))
        
        return SyncResponse(
            code=200,
            message="Sync status retrieved",
            data={
                "mysql": {
                    "stock_info_count": stock_count
                },
                "mongodb": {
                    "stock_prices_count": prices_count,
                    "stocks_with_prices": stocks_with_prices
                }
            }
        )
    except Exception as e:
        logger.error(f"Failed to get sync status: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ===============================
# Batch Operations
# ===============================

@router.post("/batch/history", response_model=SyncResponse)
async def batch_sync_history(
    stock_codes: List[str],
    days: int = Query(60, description="Number of days to sync")
):
    """
    Batch sync historical data for multiple stocks
    """
    try:
        logger.info(f"Batch sync: {len(stock_codes)} stocks, days={days}")
        
        results = []
        for code in stock_codes:
            try:
                count = await data_collection_service.fetch_and_save_historical_data(
                    stock_code=code,
                    days=days
                )
                results.append({"code": code, "records": count, "status": "success"})
            except Exception as e:
                results.append({"code": code, "records": 0, "status": "failed", "error": str(e)})
        
        success_count = sum(1 for r in results if r["status"] == "success")
        
        return SyncResponse(
            code=200,
            message=f"Batch sync completed: {success_count}/{len(stock_codes)} stocks",
            data={
                "total": len(stock_codes),
                "success": success_count,
                "failed": len(stock_codes) - success_count,
                "details": results
            }
        )
    except Exception as e:
        logger.error(f"Failed to batch sync: {e}")
        raise HTTPException(status_code=500, detail=str(e))
