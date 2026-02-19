"""
Scheduled Jobs for Data Collection
Uses APScheduler for定时任务
"""
import asyncio
from datetime import datetime
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.cron import CronTrigger
from loguru import logger

from app.core.config import settings
from app.services.data_collection_service import data_collection_service


class DataCollectionScheduler:
    """
    Scheduler for data collection tasks
    Replaces Java Quartz scheduler with Python APScheduler
    """

    def __init__(self):
        self.scheduler = AsyncIOScheduler()
        self.is_running = False

    def start(self):
        """Start the scheduler"""
        if self.is_running:
            logger.warning("Scheduler is already running")
            return

        if not settings.DATA_COLLECTION_ENABLED:
            logger.info("Data collection is disabled")
            return

        logger.info("Starting data collection scheduler...")

        # Schedule stock list sync - Daily at 09:00
        self.scheduler.add_job(
            self._sync_stock_list_job,
            trigger=CronTrigger.from_crontab(
                settings.DATA_COLLECTION_CRON_STOCK_LIST.replace('?', '*')
            ),
            id='sync_stock_list',
            name='Sync Stock List',
            replace_existing=True
        )

        # Schedule historical data sync - Daily at 15:30
        self.scheduler.add_job(
            self._sync_historical_data_job,
            trigger=CronTrigger.from_crontab(
                settings.DATA_COLLECTION_CRON_HISTORICAL.replace('?', '*')
            ),
            id='sync_historical_data',
            name='Sync Historical Data',
            replace_existing=True
        )

        # Schedule real-time quotes sync - Every minute during trading hours
        self.scheduler.add_job(
            self._sync_realtime_quotes_job,
            trigger=CronTrigger.from_crontab(
                settings.DATA_COLLECTION_CRON_REALTIME.replace('?', '*')
            ),
            id='sync_realtime_quotes',
            name='Sync Real-time Quotes',
            replace_existing=True
        )

        self.scheduler.start()
        self.is_running = True

        # Log scheduled jobs
        self._log_scheduled_jobs()

    def stop(self):
        """Stop the scheduler"""
        if not self.is_running:
            return

        logger.info("Stopping data collection scheduler...")
        self.scheduler.shutdown()
        self.is_running = False

    def _log_scheduled_jobs(self):
        """Log all scheduled jobs"""
        jobs = self.scheduler.get_jobs()
        logger.info(f"Scheduled {len(jobs)} data collection jobs:")
        for job in jobs:
            logger.info(f"  - {job.name}: {job.trigger}")

    # ===============================
    # Job Handlers
    # ===============================
    async def _sync_stock_list_job(self):
        """Job: Sync stock list"""
        logger.info("[Scheduled Job] Starting stock list sync...")
        try:
            count = await data_collection_service.fetch_and_save_stock_list()
            logger.info(f"[Scheduled Job] Stock list sync completed: {count} stocks saved")
        except Exception as e:
            logger.error(f"[Scheduled Job] Stock list sync failed: {e}")

    async def _sync_historical_data_job(self):
        """Job: Sync historical data for all stocks"""
        logger.info("[Scheduled Job] Starting historical data sync...")
        try:
            # Get list of stocks from MySQL
            from app.core.database import MySQLSessionLocal, StockInfoModel
            
            db = MySQLSessionLocal()
            stocks = db.query(StockInfoModel).filter(
                StockInfoModel.deleted == '0',
                StockInfoModel.is_tradable == 1
            ).limit(100).all()  # Limit to 100 stocks for daily sync
            db.close()

            # Sync historical data for each stock
            for stock in stocks:
                try:
                    await data_collection_service.fetch_and_save_historical_data(
                        stock.code,
                        days=1  # Sync only today's data
                    )
                except Exception as e:
                    logger.error(f"[Scheduled Job] Failed to sync {stock.code}: {e}")
                    continue

            logger.info(f"[Scheduled Job] Historical data sync completed for {len(stocks)} stocks")
        except Exception as e:
            logger.error(f"[Scheduled Job] Historical data sync failed: {e}")

    async def _sync_realtime_quotes_job(self):
        """Job: Sync real-time quotes"""
        logger.debug("[Scheduled Job] Starting real-time quotes sync...")
        try:
            count = await data_collection_service.fetch_and_save_realtime_quotes()
            logger.debug(f"[Scheduled Job] Real-time quotes sync completed: {count} quotes updated")
        except Exception as e:
            logger.error(f"[Scheduled Job] Real-time quotes sync failed: {e}")


# Singleton instance
scheduler = DataCollectionScheduler()


def init_scheduler():
    """Initialize and start the scheduler"""
    scheduler.start()


def shutdown_scheduler():
    """Shutdown the scheduler"""
    scheduler.stop()
