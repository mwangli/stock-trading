"""
Unified Scheduler for Stock Trading AI Service
Uses APScheduler for all scheduled tasks:
- MOD-001: Data Collection
- MOD-002: Sentiment Analysis
- MOD-003: LSTM Prediction
- MOD-004: Stock Ranking

All results are written directly to MySQL for Java backend to read.
"""
import asyncio
from datetime import datetime
from typing import Optional

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.cron import CronTrigger
from loguru import logger

from app.core.config import settings
from app.core.database import (
    MySQLSessionLocal,
    StockInfoModel,
    TaskExecutionLogModel,
    StockSentimentModel,
    StockPredictionModel,
    StockRankingModel,
)
from app.services.data_collection_service import data_collection_service
from app.services.data_collection import EastMoneyClient


class UnifiedScheduler:
    """
    Unified Scheduler for all Python scheduled tasks.
    Writes results directly to MySQL - Java reads from DB.
    """

    def __init__(self):
        self.scheduler = AsyncIOScheduler()
        self.is_running = False
        self.eastmoney_client = EastMoneyClient()

    def start(self):
        """Start all scheduled tasks based on enabled flags."""
        if self.is_running:
            logger.warning("Scheduler is already running")
            return

        logger.info("Starting Unified Scheduler...")

        # MOD-001: Data Collection
        if settings.DATA_COLLECTION_ENABLED:
            self._add_data_collection_jobs()

        # MOD-002: Sentiment Analysis
        if settings.SENTIMENT_ANALYSIS_ENABLED:
            self._add_sentiment_analysis_jobs()

        # MOD-003: LSTM Prediction
        if settings.LSTM_PREDICTION_ENABLED:
            self._add_lstm_prediction_jobs()

        # MOD-004: Stock Ranking
        if settings.STOCK_RANKING_ENABLED:
            self._add_stock_ranking_jobs()

        self.scheduler.start()
        self.is_running = True
        self._log_scheduled_jobs()

    def stop(self):
        """Stop the scheduler."""
        if not self.is_running:
            return
        logger.info("Stopping Unified Scheduler...")
        self.scheduler.shutdown()
        self.is_running = False

    def _log_scheduled_jobs(self):
        """Log all scheduled jobs."""
        jobs = self.scheduler.get_jobs()
        logger.info(f"Scheduler: {len(jobs)} jobs scheduled")
        for job in jobs:
            logger.info(f"  - [{job.id}] {job.name}: {job.trigger}")

    # ===============================
    # Task Logging Helpers
    # ===============================
    def _log_task_start(self, task_name: str, task_type: str, module_id: str) -> int:
        """Log task start and return log ID."""
        db = MySQLSessionLocal()
        try:
            log = TaskExecutionLogModel(
                task_name=task_name,
                task_type=task_type,
                module_id=module_id,
                status="STARTED",
                start_time=datetime.now(),
                records_processed=0,
            )
            db.add(log)
            db.commit()
            return log.id
        except Exception as e:
            logger.error(f"Failed to log task start: {e}")
            return -1
        finally:
            db.close()

    def _log_task_end(
        self,
        log_id: int,
        status: str,
        records_processed: int = 0,
        error_message: Optional[str] = None,
    ):
        """Log task end."""
        if log_id < 0:
            return

        db = MySQLSessionLocal()
        try:
            log = db.query(TaskExecutionLogModel).filter(TaskExecutionLogModel.id == log_id).first()
            if log:
                log.status = status
                log.end_time = datetime.now()
                log.records_processed = records_processed
                if log.start_time:
                    log.duration = int((log.end_time - log.start_time).total_seconds() * 1000)
                if error_message:
                    log.error_message = error_message[:2000]
                db.commit()
        except Exception as e:
            logger.error(f"Failed to log task end: {e}")
        finally:
            db.close()

    # ===============================
    # MOD-001: Data Collection Jobs
    # ===============================
    def _add_data_collection_jobs(self):
        """Add data collection scheduled jobs."""
        self.scheduler.add_job(
            self._sync_stock_list_job,
            trigger=CronTrigger.from_crontab(settings.DATA_COLLECTION_CRON_STOCK_LIST.replace("?", "*")),
            id="mod001_sync_stock_list",
            name="MOD-001: Sync Stock List",
            replace_existing=True,
        )
        self.scheduler.add_job(
            self._sync_historical_data_job,
            trigger=CronTrigger.from_crontab(settings.DATA_COLLECTION_CRON_HISTORICAL.replace("?", "*")),
            id="mod001_sync_historical",
            name="MOD-001: Sync Historical Data",
            replace_existing=True,
        )
        self.scheduler.add_job(
            self._sync_realtime_quotes_job,
            trigger=CronTrigger.from_crontab(settings.DATA_COLLECTION_CRON_REALTIME.replace("?", "*")),
            id="mod001_sync_quotes",
            name="MOD-001: Sync Real-time Quotes",
            replace_existing=True,
        )

    async def _sync_stock_list_job(self):
        """MOD-001: Sync stock list."""
        log_id = self._log_task_start("Sync Stock List", "DATA_COLLECTION", "MOD-001")
        logger.info("[MOD-001] Starting stock list sync...")
        try:
            count = await data_collection_service.fetch_and_save_stock_list()
            self._log_task_end(log_id, "SUCCESS", count)
            logger.info(f"[MOD-001] Stock list sync completed: {count} stocks")
        except Exception as e:
            self._log_task_end(log_id, "FAILED", error_message=str(e))
            logger.error(f"[MOD-001] Stock list sync failed: {e}")

    async def _sync_historical_data_job(self):
        """MOD-001: Sync historical data."""
        log_id = self._log_task_start("Sync Historical Data", "DATA_COLLECTION", "MOD-001")
        logger.info("[MOD-001] Starting historical data sync...")
        try:
            db = MySQLSessionLocal()
            stocks = (
                db.query(StockInfoModel)
                .filter(StockInfoModel.deleted == "0", StockInfoModel.is_tradable == 1)
                .limit(100)
                .all()
            )
            db.close()

            for stock in stocks:
                try:
                    await data_collection_service.fetch_and_save_historical_data(stock.code, days=1)
                except Exception as e:
                    logger.error(f"[MOD-001] Failed to sync {stock.code}: {e}")
                    continue

            self._log_task_end(log_id, "SUCCESS", len(stocks))
            logger.info(f"[MOD-001] Historical data sync completed for {len(stocks)} stocks")
        except Exception as e:
            self._log_task_end(log_id, "FAILED", error_message=str(e))
            logger.error(f"[MOD-001] Historical data sync failed: {e}")

    async def _sync_realtime_quotes_job(self):
        """MOD-001: Sync real-time quotes."""
        try:
            count = await data_collection_service.fetch_and_save_realtime_quotes()
            logger.debug(f"[MOD-001] Real-time quotes sync: {count} quotes")
        except Exception as e:
            logger.error(f"[MOD-001] Real-time quotes sync failed: {e}")

    # ===============================
    # MOD-002: Sentiment Analysis Jobs
    # ===============================
    def _add_sentiment_analysis_jobs(self):
        """Add sentiment analysis scheduled jobs."""
        self.scheduler.add_job(
            self._sentiment_analysis_job,
            trigger=CronTrigger.from_crontab(settings.SENTIMENT_ANALYSIS_CRON.replace("?", "*")),
            id="mod002_sentiment_analysis",
            name="MOD-002: Sentiment Analysis",
            replace_existing=True,
        )

    async def _sentiment_analysis_job(self):
        """MOD-002: Analyze stock sentiments."""
        log_id = self._log_task_start("Sentiment Analysis", "SENTIMENT", "MOD-002")
        logger.info("[MOD-002] Starting sentiment analysis...")

        try:
            from app.services.sentiment import sentiment_service

            db = MySQLSessionLocal()
            stocks = (
                db.query(StockInfoModel)
                .filter(StockInfoModel.deleted == "0", StockInfoModel.is_tradable == 1)
                .limit(100)
                .all()
            )

            analyzed_count = 0
            today = datetime.now().date()

            for stock in stocks:
                try:
                    news_items = await self._fetch_stock_news(stock.code)
                    if not news_items:
                        continue

                    sentiments = sentiment_service.analyze_news_batch(news_items)
                    market_sentiment = sentiment_service.get_market_sentiment(sentiments)

                    sentiment_record = StockSentimentModel(
                        stock_code=stock.code,
                        stock_name=stock.name,
                        sentiment_score=market_sentiment.get("score", 0),
                        positive_ratio=market_sentiment.get("positive_count", 0)
                        / max(market_sentiment.get("total_count", 1), 1),
                        negative_ratio=market_sentiment.get("negative_count", 0)
                        / max(market_sentiment.get("total_count", 1), 1),
                        neutral_ratio=market_sentiment.get("neutral_count", 0)
                        / max(market_sentiment.get("total_count", 1), 1),
                        news_count=market_sentiment.get("total_count", 0),
                        source="aknews",
                        analyze_date=today,
                        analyze_time=datetime.now(),
                    )
                    db.add(sentiment_record)
                    analyzed_count += 1
                except Exception as e:
                    logger.error(f"[MOD-002] Failed to analyze {stock.code}: {e}")
                    continue

            db.commit()
            db.close()
            self._log_task_end(log_id, "SUCCESS", analyzed_count)
            logger.info(f"[MOD-002] Sentiment analysis completed: {analyzed_count} stocks")
        except Exception as e:
            self._log_task_end(log_id, "FAILED", error_message=str(e))
            logger.error(f"[MOD-002] Sentiment analysis failed: {e}")

    async def _fetch_stock_news(self, stock_code: str) -> list:
        """Fetch news for a specific stock using EastMoneyClient."""
        try:
            news_list = self.eastmoney_client.get_stock_news(stock_code)
            return news_list if news_list else []
        except Exception as e:
            logger.debug(f"[MOD-002] No news for {stock_code}: {e}")
            return []

    # ===============================
    # MOD-003: LSTM Prediction Jobs
    # ===============================
    def _add_lstm_prediction_jobs(self):
        """Add LSTM prediction scheduled jobs."""
        self.scheduler.add_job(
            self._lstm_prediction_job,
            trigger=CronTrigger.from_crontab(settings.LSTM_PREDICTION_CRON.replace("?", "*")),
            id="mod003_lstm_prediction",
            name="MOD-003: LSTM Prediction",
            replace_existing=True,
        )

    async def _lstm_prediction_job(self):
        """MOD-003: Predict stock prices using LSTM."""
        log_id = self._log_task_start("LSTM Prediction", "PREDICTION", "MOD-003")
        logger.info("[MOD-003] Starting LSTM prediction...")

        try:
            from app.services.lstm import lstm_service

            db = MySQLSessionLocal()
            stocks = (
                db.query(StockInfoModel)
                .filter(StockInfoModel.deleted == "0", StockInfoModel.is_tradable == 1)
                .limit(50)
                .all()
            )

            predicted_count = 0
            predict_date = datetime.now().date()

            for stock in stocks:
                try:
                    historical_data = await data_collection_service.get_historical_data(stock.code, days=60)

                    if not historical_data or len(historical_data) < 30:
                        continue

                    result = lstm_service.predict(stock.code, historical_data)

                    if result and result.get("prediction"):
                        prediction_record = StockPredictionModel(
                            stock_code=stock.code,
                            stock_name=stock.name,
                            predict_date=predict_date,
                            predict_price=result.get("prediction"),
                            predict_direction=result.get("direction", "HOLD"),
                            confidence=result.get("confidence", 0),
                            model_version=result.get("model_version", "v1.0"),
                            test_deviation=result.get("test_deviation"),
                        )
                        db.add(prediction_record)
                        predicted_count += 1
                except Exception as e:
                    logger.error(f"[MOD-003] Failed to predict {stock.code}: {e}")
                    continue

            db.commit()
            db.close()
            self._log_task_end(log_id, "SUCCESS", predicted_count)
            logger.info(f"[MOD-003] LSTM prediction completed: {predicted_count} stocks")
        except Exception as e:
            self._log_task_end(log_id, "FAILED", error_message=str(e))
            logger.error(f"[MOD-003] LSTM prediction failed: {e}")

    # ===============================
    # MOD-004: Stock Ranking Jobs
    # ===============================
    def _add_stock_ranking_jobs(self):
        """Add stock ranking scheduled jobs."""
        self.scheduler.add_job(
            self._stock_ranking_job,
            trigger=CronTrigger.from_crontab(settings.STOCK_RANKING_CRON.replace("?", "*")),
            id="mod004_stock_ranking",
            name="MOD-004: Stock Ranking",
            replace_existing=True,
        )

    async def _stock_ranking_job(self):
        """MOD-004: Calculate composite stock rankings."""
        log_id = self._log_task_start("Stock Ranking", "RANKING", "MOD-004")
        logger.info("[MOD-004] Starting stock ranking...")

        try:
            db = MySQLSessionLocal()
            today = datetime.now().date()

            stocks = db.query(StockInfoModel).filter(StockInfoModel.deleted == "0", StockInfoModel.is_tradable == 1).all()

            # Get sentiment and prediction data
            sentiments = db.query(StockSentimentModel).filter(StockSentimentModel.analyze_date >= today).all()
            sentiment_map = {s.stock_code: s for s in sentiments}

            predictions = db.query(StockPredictionModel).filter(StockPredictionModel.predict_date >= today).all()
            prediction_map = {p.stock_code: p for p in predictions}

            # Calculate rankings
            rankings = []
            for stock in stocks:
                try:
                    sentiment_score = sentiment_map.get(stock.code, None)
                    prediction = prediction_map.get(stock.code, None)

                    # Calculate composite score
                    score = 50.0  # Base score

                    if sentiment_score and sentiment_score.sentiment_score:
                        score += sentiment_score.sentiment_score * 20

                    if prediction:
                        if prediction.predict_direction == "UP":
                            score += 20
                        elif prediction.predict_direction == "DOWN":
                            score -= 10
                        if prediction.confidence:
                            score += prediction.confidence * 10

                    if stock.score:
                        score += stock.score * 0.3

                    rankings.append(
                        {
                            "stock": stock,
                            "composite_score": min(max(score, 0), 100),
                            "sentiment_score": sentiment_score.sentiment_score if sentiment_score else None,
                            "momentum_score": 50 + (prediction.confidence * 50)
                            if prediction and prediction.confidence
                            else 50,
                            "valuation_score": 50,
                            "technical_score": 50,
                        }
                    )
                except Exception as e:
                    logger.error(f"[MOD-004] Error ranking {stock.code}: {e}")
                    continue

            # Sort by composite score and save
            rankings.sort(key=lambda x: x["composite_score"], reverse=True)

            for rank, item in enumerate(rankings, 1):
                ranking_record = StockRankingModel(
                    stock_code=item["stock"].code,
                    stock_name=item["stock"].name,
                    composite_score=item["composite_score"],
                    sentiment_score=item["sentiment_score"],
                    momentum_score=item["momentum_score"],
                    valuation_score=item["valuation_score"],
                    technical_score=item["technical_score"],
                    rank_date=today,
                    rank_position=rank,
                )
                db.add(ranking_record)

            db.commit()
            db.close()
            self._log_task_end(log_id, "SUCCESS", len(rankings))
            logger.info(f"[MOD-004] Stock ranking completed: {len(rankings)} stocks")
        except Exception as e:
            self._log_task_end(log_id, "FAILED", error_message=str(e))
            logger.error(f"[MOD-004] Stock ranking failed: {e}")


# Singleton instance
scheduler = UnifiedScheduler()


def init_scheduler():
    """Initialize and start the scheduler."""
    scheduler.start()


def shutdown_scheduler():
    """Shutdown the scheduler."""
    scheduler.stop()
