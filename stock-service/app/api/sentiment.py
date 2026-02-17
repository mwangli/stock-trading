"""
Sentiment Analysis API Routes
"""
from typing import List, Optional

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from app.services.sentiment import sentiment_service

router = APIRouter()


# Request/Response Models
class SentimentRequest(BaseModel):
    """Sentiment analysis request"""
    text: str = Field(..., description="Text to analyze", min_length=1, max_length=5120)


class SentimentBatchRequest(BaseModel):
    """Batch sentiment analysis request"""
    texts: List[str] = Field(..., description="List of texts to analyze", min_length=1, max_length=100)


class NewsItem(BaseModel):
    """News item with title and content"""
    title: Optional[str] = None
    content: Optional[str] = None
    url: Optional[str] = None
    published_at: Optional[str] = None


class NewsBatchRequest(BaseModel):
    """Batch news sentiment request"""
    news: List[NewsItem] = Field(..., description="List of news items")


class SentimentResponse(BaseModel):
    """Sentiment analysis response"""
    label: str
    score: float
    confidence: float
    probabilities: dict


class MarketSentimentResponse(BaseModel):
    """Market sentiment response"""
    overall: str
    score: float
    positive_count: int
    neutral_count: int
    negative_count: int
    total_count: int


# Routes
@router.post("/analyze", response_model=SentimentResponse)
async def analyze_sentiment(request: SentimentRequest):
    """
    Analyze sentiment of a single text

    Uses FinBERT model trained on financial text
    """
    try:
        result = sentiment_service.analyze(request.text)
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Analysis failed: {str(e)}")


@router.post("/analyze/batch", response_model=List[SentimentResponse])
async def analyze_batch(request: SentimentBatchRequest):
    """
    Analyze sentiment for multiple texts
    """
    try:
        results = sentiment_service.analyze_batch(request.texts)
        return results
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Batch analysis failed: {str(e)}")


@router.post("/analyze/news", response_model=List[dict])
async def analyze_news(request: NewsBatchRequest):
    """
    Analyze sentiment for news items

    Returns enriched news with sentiment scores
    """
    try:
        news_items = [item.model_dump() for item in request.news]
        results = sentiment_service.analyze_news_batch(news_items)
        return results
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"News analysis failed: {str(e)}")


@router.get("/market/{stock_code}", response_model=MarketSentimentResponse)
async def get_market_sentiment(stock_code: str, news_count: int = 20):
    """
    Get overall market sentiment for a stock

    Based on recent news analysis
    """
    # This would typically fetch news from database or external API
    # For now, return a placeholder
    raise HTTPException(status_code=501, detail="External news API integration pending")


@router.post("/market/analyze", response_model=MarketSentimentResponse)
async def analyze_market_sentiment(request: NewsBatchRequest):
    """
    Calculate market sentiment from provided news
    """
    try:
        news_items = [item.model_dump() for item in request.news]
        result = sentiment_service.get_market_sentiment(news_items)
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Market analysis failed: {str(e)}")


@router.get("/model/info")
async def get_model_info():
    """Get sentiment model information"""
    return {
        "model": "FinBERT",
        "model_name": sentiment_service.model_name,
        "max_length": sentiment_service.max_length,
        "device": sentiment_service._device
    }
