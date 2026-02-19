"""
Data Collection API Routes - Query Only
Provides data query APIs (read-only)
Data collection and write operations moved to /api/sync
"""
from typing import List, Optional
from fastapi import APIRouter, Query, HTTPException
from pydantic import BaseModel
from loguru import logger

from app.core.database import MySQLSessionLocal, StockInfoModel, get_mongo_collection, get_redis


router = APIRouter()


class StockInfo(BaseModel):
    """Stock information model"""
    code: str
    name: str
    market: Optional[str] = None
    industry: Optional[str] = None
    price: Optional[float] = None
    increase: Optional[float] = None


class HistoricalData(BaseModel):
    """Historical K-line data model"""
    date: str
    open: float
    close: float
    high: float
    low: float
    volume: float
    amount: float
    amplitude: Optional[float] = None
    change_pct: Optional[float] = None
    change_amount: Optional[float] = None
    turnover_rate: Optional[float] = None


class RealtimeQuote(BaseModel):
    """Realtime quote model"""
    code: str
    name: str
    price: float
    change_pct: Optional[float] = None
    volume: Optional[int] = None


# ===============================
# Stock List Query (from MySQL)
# ===============================

@router.get("/stock/list")
async def get_stock_list(
    page: int = Query(1, ge=1, description="Page number"),
    size: int = Query(100, ge=1, le=1000, description="Page size"),
    tradable_only: bool = Query(True, description="Only return tradable stocks")
):
    """
    Get A-share stock list from MySQL database
    Data is collected and saved by Python data collection service
    """
    try:
        db = MySQLSessionLocal()
        query = db.query(StockInfoModel).filter(StockInfoModel.deleted == '0')
        
        if tradable_only:
            query = query.filter(StockInfoModel.is_tradable == 1)
        
        total = query.count()
        stocks = query.offset((page - 1) * size).limit(size).all()
        db.close()
        
        data = [{
            "code": s.code,
            "name": s.name,
            "market": s.market,
            "industry": s.industry,
            "price": s.price,
            "increase": s.increase,
            "is_tradable": s.is_tradable == 1
        } for s in stocks]
        
        return {
            "code": 200,
            "message": "success",
            "data": {
                "total": total,
                "page": page,
                "size": size,
                "stocks": data
            }
        }
    except Exception as e:
        logger.error(f"Failed to get stock list: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/stock/{stock_code}")
async def get_stock_detail(stock_code: str):
    """
    Get single stock detail from MySQL
    """
    try:
        db = MySQLSessionLocal()
        stock = db.query(StockInfoModel).filter(
            StockInfoModel.code == stock_code,
            StockInfoModel.deleted == '0'
        ).first()
        db.close()
        
        if not stock:
            raise HTTPException(status_code=404, detail=f"Stock {stock_code} not found")
        
        return {
            "code": 200,
            "message": "success",
            "data": {
                "code": stock.code,
                "name": stock.name,
                "market": stock.market,
                "industry": stock.industry,
                "price": stock.price,
                "increase": stock.increase,
                "is_st": stock.is_st == 1,
                "is_tradable": stock.is_tradable == 1,
                "create_time": stock.create_time.isoformat() if stock.create_time else None,
                "update_time": stock.update_time.isoformat() if stock.update_time else None
            }
        }
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Failed to get stock detail: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ===============================
# Historical Data Query (from MongoDB)
# ===============================

@router.get("/stock/{stock_code}/prices")
async def get_historical_data(
    stock_code: str,
    start_date: Optional[str] = Query(None, description="Start date, YYYY-MM-DD"),
    end_date: Optional[str] = Query(None, description="End date, YYYY-MM-DD"),
    limit: int = Query(60, ge=1, le=1000, description="Number of records to return")
):
    """
    Get historical K-line data from MongoDB
    Data is collected and saved by Python data collection service
    """
    try:
        collection = get_mongo_collection("stock_prices")
        
        # Build query
        query = {"code": stock_code}
        if start_date or end_date:
            date_filter = {}
            if start_date:
                date_filter["$gte"] = start_date
            if end_date:
                date_filter["$lte"] = end_date
            query["date"] = date_filter
        
        # Get data sorted by date descending
        cursor = collection.find(query).sort("date", -1).limit(limit)
        records = list(cursor)
        
        # Transform to response format
        data = [{
            "date": r["date"],
            "open": r.get("price1"),
            "high": r.get("price2"),
            "low": r.get("price3"),
            "close": r.get("price4"),
            "volume": r.get("trading_volume"),
            "amount": r.get("trading_amount"),
            "amplitude": r.get("amplitude"),
            "change_pct": r.get("increase_rate"),
            "change_amount": r.get("change_amount"),
            "turnover_rate": r.get("exchange_rate")
        } for r in records]
        
        return {
            "code": 200,
            "message": "success",
            "data": {
                "stock_code": stock_code,
                "count": len(data),
                "prices": data
            }
        }
    except Exception as e:
        logger.error(f"Failed to get historical data: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ===============================
# Real-time Quote Query (from Redis/MySQL)
# ===============================

@router.get("/stock/{stock_code}/quote")
async def get_realtime_quote(stock_code: str):
    """
    Get realtime quote for a single stock
    First checks Redis cache, then falls back to MySQL
    """
    try:
        # Try Redis first
        redis_client = get_redis()
        cache_key = f"stock:quote:{stock_code}"
        cached_price = redis_client.get(cache_key)
        
        if cached_price:
            return {
                "code": 200,
                "message": "success",
                "data": {
                    "stock_code": stock_code,
                    "price": float(cached_price),
                    "source": "redis_cache"
                }
            }
        
        # Fallback to MySQL
        db = MySQLSessionLocal()
        stock = db.query(StockInfoModel).filter(
            StockInfoModel.code == stock_code,
            StockInfoModel.deleted == '0'
        ).first()
        db.close()
        
        if not stock:
            raise HTTPException(status_code=404, detail=f"Stock {stock_code} not found")
        
        return {
            "code": 200,
            "message": "success",
            "data": {
                "stock_code": stock_code,
                "name": stock.name,
                "price": stock.price,
                "increase": stock.increase,
                "source": "mysql"
            }
        }
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Failed to get realtime quote: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/quotes")
async def get_realtime_quotes(
    stock_codes: Optional[str] = Query(None, description="Comma-separated stock codes")
):
    """
    Get realtime quotes for multiple stocks or all stocks
    """
    try:
        redis_client = get_redis()
        
        if stock_codes:
            # Get specific stocks
            codes = stock_codes.split(',')
            results = []
            for code in codes:
                cache_key = f"stock:quote:{code}"
                price = redis_client.get(cache_key)
                if price:
                    results.append({
                        "code": code,
                        "price": float(price),
                        "source": "redis"
                    })
            
            return {
                "code": 200,
                "message": "success",
                "data": {
                    "count": len(results),
                    "quotes": results
                }
            }
        else:
            # Get all stocks from MySQL
            db = MySQLSessionLocal()
            stocks = db.query(StockInfoModel).filter(
                StockInfoModel.deleted == '0',
                StockInfoModel.is_tradable == 1
            ).limit(100).all()
            db.close()
            
            results = [{
                "code": s.code,
                "name": s.name,
                "price": s.price,
                "increase": s.increase
            } for s in stocks]
            
            return {
                "code": 200,
                "message": "success",
                "data": {
                    "count": len(results),
                    "quotes": results
                }
            }
    except Exception as e:
        logger.error(f"Failed to get realtime quotes: {e}")
        raise HTTPException(status_code=500, detail=str(e))
