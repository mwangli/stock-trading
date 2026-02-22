"""
FinBERT Sentiment Analysis Service
Financial-domain sentiment analysis using FinBERT model
"""
import logging
from typing import Dict, List, Optional

import numpy as np
import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer, Pipeline

from app.core.config import settings

logger = logging.getLogger(__name__)


class SentimentService:
    """FinBERT-based sentiment analysis service"""

    def __init__(self):
        self.model_name = settings.FINBERT_MODEL_NAME
        self.max_length = settings.FINBERT_MAX_LENGTH
        self._model: Optional[Pipeline] = None
        self._device = "cuda" if torch.cuda.is_available() else "cpu"
        logger.info(f"SentimentService initialized. Device: {self._device}")

    @property
    def model(self) -> Pipeline:
        """Lazy load model"""
        if self._model is None:
            logger.info(f"Loading FinBERT model: {self.model_name}")
            tokenizer = AutoTokenizer.from_pretrained(self.model_name)
            model = AutoModelForSequenceClassification.from_pretrained(
                self.model_name,
                num_labels=3
            )
            model.to(self._device)
            self._model = Pipeline(
                "text-classification",
                model=model,
                tokenizer=tokenizer,
                device=0 if self._device == "cuda" else -1,
                truncation=True,
                max_length=self.max_length
            )
            logger.info("FinBERT model loaded successfully")
        return self._model

    def analyze(self, text: str) -> Dict:
        """
        Analyze sentiment of a single text

        Args:
            text: Input text (news, article, or financial statement)

        Returns:
            Dictionary with sentiment results
        """
        if not text or not text.strip():
            return {
                "label": "neutral",
                "score": 0.0,
                "confidence": 0.0,
                "probabilities": {"positive": 0.33, "neutral": 0.34, "negative": 0.33}
            }

        try:
            result = self.model(text)[0]

            # Map FinBERT labels to standard format
            label_map = {
                "positive": "positive",
                "negative": "negative",
                "neutral": "neutral"
            }

            return {
                "label": label_map.get(result["label"], "neutral"),
                "score": result["score"],
                "confidence": result["score"],
                "probabilities": self._get_probabilities(result)
            }
        except Exception as e:
            logger.error(f"Error analyzing sentiment: {e}")
            return {
                "label": "neutral",
                "score": 0.0,
                "confidence": 0.0,
                "probabilities": {"positive": 0.33, "neutral": 0.34, "negative": 0.33}
            }

    def analyze_batch(self, texts: List[str]) -> List[Dict]:
        """
        Analyze sentiment for multiple texts

        Args:
            texts: List of input texts

        Returns:
            List of sentiment results
        """
        results = []
        for text in texts:
            results.append(self.analyze(text))
        return results

    def analyze_news_batch(self, news_items: List[Dict]) -> List[Dict]:
        """
        Analyze sentiment for news items with metadata

        Args:
            news_items: List of news dictionaries with 'title' and/or 'content'

        Returns:
            List of enriched news items with sentiment
        """
        results = []
        for item in news_items:
            text = item.get("title", "") + " " + item.get("content", "")
            sentiment = self.analyze(text)
            results.append({
                **item,
                "sentiment": sentiment["label"],
                "sentiment_score": sentiment["score"],
                "sentiment_confidence": sentiment["confidence"]
            })
        return results

    def _get_probabilities(self, result: Dict) -> Dict[str, float]:
        """Get probability distribution for all classes"""
        # FinBERT returns single label with score
        # We approximate probabilities based on the score
        score = result["score"]
        label = result["label"]

        if label == "positive":
            return {
                "positive": score,
                "neutral": (1 - score) / 2,
                "negative": (1 - score) / 2
            }
        elif label == "negative":
            return {
                "positive": (1 - score) / 2,
                "neutral": (1 - score) / 2,
                "negative": score
            }
        else:
            return {
                "positive": (1 - score) / 2,
                "neutral": score,
                "negative": (1 - score) / 2
            }

    def get_market_sentiment(self, news_items: List[Dict]) -> Dict:
        """
        Calculate overall market sentiment from multiple news items

        Args:
            news_items: List of news with sentiment

        Returns:
            Aggregated market sentiment
        """
        if not news_items:
            return {
                "overall": "neutral",
                "score": 0.0,
                "positive_count": 0,
                "neutral_count": 0,
                "negative_count": 0,
                "total_count": 0
            }

        sentiments = self.analyze_news_batch(news_items)

        positive = sum(1 for s in sentiments if s["sentiment"] == "positive")
        neutral = sum(1 for s in sentiments if s["sentiment"] == "neutral")
        negative = sum(1 for s in sentiments if s["sentiment"] == "negative")
        total = len(sentiments)

        # Calculate weighted score
        scores = [s["sentiment_score"] for s in sentiments]
        avg_score = np.mean(scores) if scores else 0.0

        # Determine overall sentiment
        if positive > negative and positive > neutral:
            overall = "positive"
        elif negative > positive and negative > neutral:
            overall = "negative"
        else:
            overall = "neutral"

        return {
            "overall": overall,
            "score": avg_score,
            "positive_count": positive,
            "neutral_count": neutral,
            "negative_count": negative,
            "total_count": total
        }

    def unload(self):
        """Unload model from memory"""
        if self._model is not None:
            del self._model
            self._model = None
            if torch.cuda.is_available():
                torch.cuda.empty_cache()
            logger.info("FinBERT model unloaded")


# Singleton instance
sentiment_service = SentimentService()
