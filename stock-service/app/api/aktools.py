"""
AKTools HTTP API Service

This module provides HTTP API endpoints for AKShare data,
similar to the aktools package but implemented directly.

It allows other programming languages to access AKShare data via HTTP.
"""
from typing import Optional, List, Dict, Any
from fastapi import APIRouter, Query, HTTPException
from pydantic import BaseModel
from loguru import logger

# Import the akshare wrapper (with stubs)
from app.core.akshare_wrapper import AKShareAPI, get_akshare

router = APIRouter()


# ===============================
# Request/Response Models
# ===============================

class StockHistoricalRequest(BaseModel):
    """Request model for stock historical data"""
    symbol: str
    start_date: str
    end_date: str
    period: str = "daily"
    adjust: str = ""


# ===============================
# Public API Endpoints (No Auth Required)
# ===============================

@router.get("/stock_zh_a_spot_em")
async def api_stock_zh_a_spot_em():
    """
    A股实时行情 (东方财富)
    
    Returns real-time quotes for all A-share stocks.
    """
    try:
        data = AKShareAPI.stock_zh_a_spot_em()
        return {
            "success": True,
            "data": data,
            "message": f"Fetched {len(data)} stocks"
        }
    except Exception as e:
        logger.error(f"Error fetching stock spot: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/stock_zh_a_hist")
async def api_stock_zh_a_hist(
    symbol: str = Query(..., description="股票代码，如: 000001"),
    start_date: str = Query(..., description="开始日期，格式: YYYYMMDD"),
    end_date: str = Query(..., description="结束日期，格式: YYYYMMDD"),
    period: str = Query("daily", description="周期: daily, weekly, monthly"),
    adjust: str = Query("", description="复权类型: 空字符串, qfq, hfq")
):
    """
    A股历史K线数据
    
    Returns historical K-line data for a specific stock.
    """
    try:
        data = AKShareAPI.stock_zh_a_hist(
            symbol=symbol,
            start_date=start_date,
            end_date=end_date,
            period=period,
            adjust=adjust
        )
        return {
            "success": True,
            "data": data,
            "symbol": symbol,
            "count": len(data)
        }
    except Exception as e:
        logger.error(f"Error fetching historical data: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/stock_zh_index_spot_em")
async def api_stock_zh_index_spot_em():
    """
    中国指数实时行情
    
    Returns real-time quotes for China stock indices.
    """
    try:
        data = AKShareAPI.stock_zh_index_spot_em()
        return {
            "success": True,
            "data": data,
            "message": f"Fetched {len(data)} indices"
        }
    except Exception as e:
        logger.error(f"Error fetching index spot: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/stock_info_a_code_name")
async def api_stock_info_a_code_name():
    """
    A股股票列表
    
    Returns list of A-share stocks with codes and names.
    """
    try:
        data = AKShareAPI.stock_info_a_code_name()
        return {
            "success": True,
            "data": data,
            "message": f"Fetched {len(data)} stocks"
        }
    except Exception as e:
        logger.error(f"Error fetching stock list: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/fund_etf_hist_sina")
async def api_fund_etf_hist_sina(
    symbol: str = Query(..., description="ETF代码，如: sz159919"),
    period: str = Query("daily", description="周期: daily, weekly, monthly")
):
    """
    ETF历史数据 (新浪)
    
    Returns historical data for ETFs.
    """
    try:
        data = AKShareAPI.fund_etf_hist_sina(symbol=symbol, period=period)
        return {
            "success": True,
            "data": data,
            "symbol": symbol,
            "count": len(data)
        }
    except Exception as e:
        logger.error(f"Error fetching ETF data: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/futures_zh_daily_sina")
async def api_futures_zh_daily_sina(
    symbol: str = Query(..., description="期货代码，如: IF8888")
):
    """
    期货日常数据 (新浪)
    
    Returns daily futures data.
    """
    try:
        data = AKShareAPI.futures_zh_daily_sina(symbol=symbol)
        return {
            "success": True,
            "data": data,
            "symbol": symbol,
            "count": len(data)
        }
    except Exception as e:
        logger.error(f"Error fetching futures data: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/currency_latest")
async def api_currency_latest():
    """
    最新汇率
    
    Returns latest currency exchange rates.
    """
    try:
        data = AKShareAPI.currency_latest()
        return {
            "success": True,
            "data": data
        }
    except Exception as e:
        logger.error(f"Error fetching currency data: {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ===============================
# Info Endpoints
# ===============================

@router.get("/info")
async def api_info():
    """
    API信息
    
    Returns API information and available endpoints.
    """
    ak = get_akshare()
    return {
        "name": "AKTools HTTP API",
        "version": "1.0.0",
        "akshare_version": ak.__version__,
        "endpoints": {
            "/api/public/stock_zh_a_spot_em": "A股实时行情",
            "/api/public/stock_zh_a_hist": "A股历史K线",
            "/api/public/stock_zh_index_spot_em": "中国指数行情",
            "/api/public/stock_info_a_code_name": "A股股票列表",
            "/api/public/fund_etf_hist_sina": "ETF历史数据",
            "/api/public/futures_zh_daily_sina": "期货日常数据",
            "/api/public/currency_latest": "最新汇率",
            "/api/public/info": "API信息"
        }
    }
