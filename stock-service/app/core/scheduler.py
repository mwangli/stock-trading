"""
Unified Scheduler for Stock Trading AI Service
Uses APScheduler for all scheduled tasks:
- MOD-001: Data Collection
- MOD-002: Sentiment Analysis + News Collection
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
from app.core.eastmoney_client import EastMoneyClient
from app.services.model_iteration import ModelIterationService


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

        # MOD-002: News Collection + Sentiment Analysis
        if settings.NEWS_COLLECTION_ENABLED:
            self._add_news_collection_jobs()
        if settings.SENTIMENT_ANALYSIS_ENABLED:
            self._add_sentiment_analysis_jobs()

        # MOD-003: LSTM Prediction
        if settings.LSTM_PREDICTION_ENABLED:
            self._add_lstm_prediction_jobs()

        # MOD-004: Stock Ranking
        if settings.STOCK_RANKING_ENABLED:
            self._add_stock_ranking_jobs()

        # MOD-005: Model Iteration
        if settings.MODEL_ITERATION_ENABLED:
            self._add_model_iteration_jobs()

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
        """MOD-001: Sync historical data - incremental (1 day)."""
        log_id = self._log_task_start("Sync Historical Data", "DATA_COLLECTION", "MOD-001")
        logger.info("[MOD-001] Starting historical data sync (incremental)...")
        try:
            db = MySQLSessionLocal()
            # 获取所有可交易股票，不限制数量
            stocks = (
                db.query(StockInfoModel)
                .filter(StockInfoModel.deleted == "0", StockInfoModel.is_tradable == 1)
                .all()
            )
            db.close()
            
            # 使用增量同步天数（1天）
            from app.core.config import settings
            days = settings.DATA_COLLECTION_HISTORY_DAYS_INCREMENTAL
            
            logger.info(f"[MOD-001] Syncing {len(stocks)} stocks with {days} days of data")
            
            # 使用信号量限制并发数
            semaphore = asyncio.Semaphore(settings.DATA_COLLECTION_THREAD_POOL_SIZE)
            
            async def sync_with_limit(code: str):
                async with semaphore:
                    try:
                        return await data_collection_service.fetch_and_save_historical_data(code, days=days)
                    except Exception as e:
                        logger.error(f"[MOD-001] Failed to sync {code}: {e}")
                        return 0
            
            # 批量并发处理
            batch_size = 100
            total_records = 0
            for i in range(0, len(stocks), batch_size):
                batch = [s.code for s in stocks[i:i+batch_size]]
                tasks = [sync_with_limit(code) for code in batch]
                results = await asyncio.gather(*tasks, return_exceptions=True)
                
                for result in results:
                    if isinstance(result, int):
                        total_records += result
                
                logger.info(f"[MOD-001] Progress: {min(i+batch_size, len(stocks))}/{len(stocks)} stocks processed")
            
            self._log_task_end(log_id, "SUCCESS", total_records)
            logger.info(f"[MOD-001] Historical data sync completed: {total_records} records for {len(stocks)} stocks")
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
    # MOD-002: News Collection Jobs
    # ===============================


    def _add_news_collection_jobs(self):
        """Add news collection scheduled jobs."""
        # 定时新闻采集任务 - 使用爬虫采集主流财经网站新闻
        self.scheduler.add_job(
            self._sync_news_job,
            trigger=CronTrigger.from_crontab(settings.NEWS_COLLECTION_CRON.replace("?", "*")),
            id="mod002_sync_news",
            name="MOD-002: Sync News (Crawler)",
            replace_existing=True,
        )
        # 初始化新闻采集任务 - 采集近30天历史新闻
        self.scheduler.add_job(
            self._init_news_collection_job,
            trigger=CronTrigger.from_crontab("0 0 10 * * *"),  # 每天10:00执行一次
            id="mod002_init_news",
            name="MOD-002: Init News Collection",
            replace_existing=True,
        )

    async def _sync_news_job(self):
        """MOD-002: Sync news for all stocks to MongoDB using crawler."""
        log_id = self._log_task_start("Sync News (Crawler)", "NEWS_COLLECTION", "MOD-002")
        logger.info("[MOD-002] Starting news sync using crawler...")

        try:
            # 使用爬虫采集财经新闻
            from app.core.spiders.finance_news_spider import finance_news_spider
            
            # 多轮采集以达到1000+条新闻
            total_saved = 0
            rounds = 3  # 采集3轮
            
            for round_num in range(1, rounds + 1):
                logger.info(f"[MOD-002] 第 {round_num}/{rounds} 轮采集...")
                
                # 从多个新闻源采集新闻
                news_list = finance_news_spider.fetch_all_news(limit_per_spider=200)
                
                if not news_list:
                    logger.warning(f"[MOD-002] 第 {round_num} 轮: 未获取到新闻")
                    continue
                
                logger.info(f"[MOD-002] 第 {round_num} 轮: 获取 {len(news_list)} 条新闻")
                
                # 保存到MongoDB
                saved_count = finance_news_spider.save_to_mongodb(news_list, "news")
                total_saved += saved_count
                logger.info(f"[MOD-002] 第 {round_num} 轮: 保存 {saved_count} 条新闻")
            
            # 检查最终数量
            from app.core.database import get_mongo_collection
            collection = get_mongo_collection("news")
            final_count = collection.count_documents({})
            
            logger.info(f"[MOD-002] 新闻同步完成: 本次保存 {total_saved} 条, MongoDB总计 {final_count} 条")
            
            self._log_task_end(log_id, "SUCCESS", total_saved)
        except Exception as e:
            self._log_task_end(log_id, "FAILED", error_message=str(e))
            logger.error(f"[MOD-002] News sync failed: {e}")

    async def _init_news_collection_job(self):
        """MOD-002: Initial news collection - fetch historical news for all stocks."""
        log_id = self._log_task_start("Init News Collection", "NEWS_COLLECTION", "MOD-002")
        logger.info("[MOD-002] Starting initial news collection (30 days history)...")

        try:
            # 获取所有可交易股票
            db = MySQLSessionLocal()
            stocks = (
                db.query(StockInfoModel)
                .filter(StockInfoModel.deleted == "0", StockInfoModel.is_tradable == 1)
                .all()
            )
            db.close()

            logger.info(f"[MOD-002] Collecting news for {len(stocks)} stocks")

            # 使用爬虫采集财经新闻（通用新闻，不针对单个股票）
            from app.core.spiders.finance_news_spider import finance_news_spider
            
            total_saved = 0
            
            # 分批采集历史新闻（30天）
            for batch_start in range(0, len(stocks), 100):
                batch = stocks[batch_start:batch_start + 100]
                
                # 采集多轮以获取更多新闻
                for round_num in range(3):
                    news_list = finance_news_spider.fetch_all_news(limit_per_spider=50)
                    if news_list:
                        saved = finance_news_spider.save_to_mongodb(news_list, "news")
                        total_saved += saved
                        logger.info(f"[MOD-002] Round {round_num + 1}: saved {saved} news items")
                
                logger.info(f"[MOD-002] Progress: {min(batch_start + 100, len(stocks))}/{len(stocks)} stocks processed")

            self._log_task_end(log_id, "SUCCESS", total_saved)
            logger.info(f"[MOD-002] Initial news collection completed: {total_saved} news items")
        except Exception as e:
            self._log_task_end(log_id, "FAILED", error_message=str(e))
            logger.error(f"[MOD-002] Initial news collection failed: {e}")

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
            from app.services.sentiment_analysis import sentiment_service

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
            from app.services.lstm_prediction import lstm_service

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

    # ===============================
    # MOD-005: Model Iteration Jobs
    # ===============================
    def _add_model_iteration_jobs(self):
        """Add model iteration scheduled jobs."""
        # 性能数据收集 (每天 16:15)
        self.scheduler.add_job(
            self._collect_performance_job,
            trigger=CronTrigger.from_crontab(settings.MODEL_ITERATION_CRON_PERFORMANCE.replace("?", "*")),
            id="mod005_collect_performance",
            name="MOD-005: Collect Performance Data",
            replace_existing=True,
        )
        # 模型评估 (每天 16:30)
        self.scheduler.add_job(
            self._evaluate_model_job,
            trigger=CronTrigger.from_crontab(settings.MODEL_ITERATION_CRON_EVALUATE.replace("?", "*")),
            id="mod005_evaluate_model",
            name="MOD-005: Model Evaluation",
            replace_existing=True,
        )
        # 模型训练 (每天 20:00)
        self.scheduler.add_job(
            self._train_model_job,
            trigger=CronTrigger.from_crontab(settings.MODEL_ITERATION_CRON_TRAIN.replace("?", "*")),
            id="mod005_train_model",
            name="MOD-005: Model Training",
            replace_existing=True,
        )

    async def _collect_performance_job(self):
        """MOD-005: Collect performance data."""
        log_id = self._log_task_start("Collect Performance Data", "MODEL_ITERATION", "MOD-005")
        logger.info("[MOD-005] Starting performance data collection...")
        
        try:
            with ModelIterationService() as service:
                result = service.collect_performance_data(days=30)
                records_count = result.get("total_trades", 0)
                self._log_task_end(log_id, "SUCCESS", records_count)
                logger.info(f"[MOD-005] Performance data collection completed: {records_count} records")
        except Exception as e:
            self._log_task_end(log_id, "FAILED", error_message=str(e))
            logger.error(f"[MOD-005] Performance data collection failed: {e}")

    async def _evaluate_model_job(self):
        """MOD-005: Evaluate model performance."""
        log_id = self._log_task_start("Model Evaluation", "MODEL_ITERATION", "MOD-005")
        logger.info("[MOD-005] Starting model evaluation...")
        
        try:
            with ModelIterationService() as service:
                # 评估所有模型类型
                model_types = ["sentiment", "lstm", "ranking"]
                evaluated_count = 0
                
                for model_type in model_types:
                    try:
                        evaluation = service.evaluate_model(model_type)
                        if evaluation:
                            evaluated_count += 1
                            logger.info(f"[MOD-005] Evaluated {model_type}: win_rate={evaluation.win_rate}, avg_return={evaluation.avg_return}")
                    except Exception as e:
                        logger.warning(f"[MOD-005] Failed to evaluate {model_type}: {e}")
                
                self._log_task_end(log_id, "SUCCESS", evaluated_count)
                logger.info(f"[MOD-005] Model evaluation completed: {evaluated_count} models")
        except Exception as e:
            self._log_task_end(log_id, "FAILED", error_message=str(e))
            logger.error(f"[MOD-005] Model evaluation failed: {e}")

    async def _train_model_job(self):
        """MOD-005: Train models based on performance."""
        log_id = self._log_task_start("Model Training", "MODEL_ITERATION", "MOD-005")
        logger.info("[MOD-005] Starting model training check...")
        
        try:
            with ModelIterationService() as service:
                # 检查是否需要训练
                stats = service.get_performance_stats(days=30)
                
                # 检查是否触发训练条件
                consecutive_losses = stats.get("consecutive_losses", 0)
                win_rate = stats.get("win_rate", 0)
                
                should_train = (
                    consecutive_losses >= settings.MODEL_ITERATION_CONSECUTIVE_LOSS_THRESHOLD or
                    win_rate < settings.MODEL_ITERATION_WIN_RATE_THRESHOLD
                )
                
                if should_train:
                    logger.info(f"[MOD-005] Training conditions met: consecutive_losses={consecutive_losses}, win_rate={win_rate}")
                    # 触发训练
                    model_types = ["sentiment", "lstm", "ranking"]
                    trained_count = 0
                    
                    for model_type in model_types:
                        try:
                            task = service.train_model(model_type, force=False)
                            if task:
                                trained_count += 1
                                logger.info(f"[MOD-005] Training triggered for {model_type}: task_id={task.id}")
                        except Exception as e:
                            logger.warning(f"[MOD-005] Failed to train {model_type}: {e}")
                    
                    self._log_task_end(log_id, "SUCCESS", trained_count)
                    logger.info(f"[MOD-005] Model training completed: {trained_count} models")
                else:
                    logger.info(f"[MOD-005] Training not needed: consecutive_losses={consecutive_losses}, win_rate={win_rate}")
                    self._log_task_end(log_id, "SUCCESS", 0)
        except Exception as e:
            self._log_task_end(log_id, "FAILED", error_message=str(e))
            logger.error(f"[MOD-005] Model training check failed: {e}")


# Singleton instance
scheduler = UnifiedScheduler()


def init_scheduler():
    """Initialize and start the scheduler."""
    scheduler.start()


def shutdown_scheduler():
    """Shutdown the scheduler."""
    scheduler.stop()
