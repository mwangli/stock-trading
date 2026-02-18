"""
Data Collection API Routes
"""
from typing import List, Optional
from fastapi import APIRouter, Query
from pydantic import BaseModel

from app.services.data_collection import data_collection_service


router = APIRouter()


class StockInfo(BaseModel):
    """Stock information model"""
    code: str
    name: str
    market: Optional[str] = None
    industry: Optional[str] = None


class HistoricalData(BaseModel):
    """Historical K-line data model"""
    date: str
    open: float
    close: float
    high: float
    low: float
    volume: int
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
    change_pct: float
    volume: int
    amount: float


@router.get("/stock/list")
async def get_stock_list():
    """
    Get A-share stock list
    """
    stocks = data_collection_service.get_stock_list_simple()
    return {
        "code": 200,
        "message": "success",
        "data": stocks
    }


@router.get("/stock/prices")
async def get_historical_data(
    symbol: str = Query(..., description="Stock code, e.g., 000001"),
    start_date: str = Query(..., description="Start date, YYYYMMDD"),
    end_date: str = Query(..., description="End date, YYYYMMDD"),
    period: str = Query("daily", description="daily/weekly/monthly"),
    adjust: str = Query("", description="Adjustment type: ''/'qfq'/'hfq'")
):
    """
    Get historical K-line data
    """
    data = data_collection_service.get_historical_data(
        symbol=symbol,
        start_date=start_date,
        end_date=end_date,
        period=period,
        adjust=adjust
    )
    return {
        "code": 200,
        "message": "success",
        "data": data
    }


@router.get("/stock/quote")
async def get_realtime_quote(
    symbol: str = Query(..., description="Stock code, e.g., 000001")
):
    """
    Get realtime quote for a single stock
    """
    quote = data_collection_service.get_realtime_quote(symbol)
    return {
        "code": 200,
        "message": "success",
        "data": quote
    }


@router.get("/stock/quotes")
async def get_realtime_quotes(
    symbols: Optional[str] = Query(None, description="Comma-separated stock codes")
):
    """
    Get realtime quotes for multiple stocks or all stocks
    """
    symbol_list = symbols.split(',') if symbols else None
    quotes = data_collection_service.get_realtime_quotes(symbol_list)
    return {
        "code": 200,
        "message": "success",
        "data": quotes
    }


@router.get("/stock/financial")
async def get_financial_report(
    symbol: str = Query(..., description="Stock code")
):
    """
    Get financial report data
    """
    data = data_collection_service.get_financial_report(symbol)
    return {
        "code": 200,
        "message": "success",
        "data": data
    }


@router.get("/news")
async def get_stock_news(
    symbol: Optional[str] = Query(None, description="Stock code (optional)")
):
    """
    Get stock news
    """
    news = data_collection_service.get_stock_news(symbol)
    return {
        "code": 200,
        "message": "success",
        "data": news
    }
