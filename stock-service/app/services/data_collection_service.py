"""
Data Collection Service with Direct Database Write
Python service directly collects data and writes to MySQL/MongoDB/Redis
"""
import asyncio
from datetime import datetime, timedelta
from typing import List, Dict, Any, Optional
import akshare as ak
from loguru import logger
from tenacity import retry, stop_after_attempt, wait_exponential
from sqlalchemy.orm import Session
from sqlalchemy import text

from app.core.config import settings
from app.core.database import (
    get_mysql_db, get_mongo_collection, get_redis,
    MySQLSessionLocal, StockInfoModel
)


class DataCollectionService:
    """
    Stock data collection service that directly writes to databases
    Replaces Java -> Python HTTP -> Database flow with Python -> Database directly
    """

    def __init__(self):
        self.redis_client = get_redis()
        self.quote_cache_ttl = 60  # 1 minute
        self.history_days = settings.DATA_COLLECTION_HISTORY_DAYS
        self.batch_size = settings.DATA_COLLECTION_BATCH_SIZE

    # ===============================
    # Stock List Collection (MySQL)
    # ===============================
    @retry(
        stop=stop_after_attempt(settings.DATA_COLLECTION_MAX_RETRIES),
        wait=wait_exponential(multiplier=1, min=1, max=10),
        reraise=True
    )
    async def fetch_and_save_stock_list(self) -> int:
        """
        Fetch A-share stock list from AKShare and save to MySQL
        
        Returns:
            int: Number of stocks saved
        """
        logger.info("Starting to fetch stock list from AKShare...")
        
        try:
            # 1. Fetch stock list from AKShare
            df = ak.stock_zh_a_spot_em()
            logger.info(f"Fetched {len(df)} stocks from AKShare")
            
            # 2. Filter and transform data
            stocks_to_save = []
            for _, row in df.iterrows():
                code = str(row.get('代码', ''))
                name = str(row.get('名称', ''))
                
                # Skip invalid data
                if not code or not name:
                    continue
                
                # Filter ST stocks
                is_st = 'ST' in name or '*ST' in name
                if is_st:
                    continue
                
                # Determine market
                market = 'SH' if code.startswith('6') else 'SZ'
                
                stock_data = {
                    'code': code,
                    'name': name,
                    'market': market,
                    'is_st': 1 if is_st else 0,
                    'is_tradable': 0 if is_st else 1,
                    'price': float(row.get('最新价', 0)) if pd_not_null(row.get('最新价')) else None,
                    'increase': float(row.get('涨跌幅', 0)) if pd_not_null(row.get('涨跌幅')) else None,
                    'deleted': '0',
                    'create_time': datetime.now(),
                    'update_time': datetime.now()
                }
                stocks_to_save.append(stock_data)
            
            logger.info(f"After filtering: {len(stocks_to_save)} tradable stocks")
            
            # 3. Save to MySQL
            saved_count = await self._save_stocks_to_mysql(stocks_to_save)
            logger.info(f"Successfully saved {saved_count} stocks to MySQL")
            
            return saved_count
            
        except Exception as e:
            logger.error(f"Failed to fetch and save stock list: {e}")
            raise

    async def _save_stocks_to_mysql(self, stocks: List[Dict]) -> int:
        """Save stocks to MySQL with upsert logic"""
        db = MySQLSessionLocal()
        try:
            saved_count = 0
            for stock in stocks:
                try:
                    # Check if exists
                    existing = db.query(StockInfoModel).filter(
                        StockInfoModel.code == stock['code']
                    ).first()
                    
                    if existing:
                        # Update existing
                        existing.name = stock['name']
                        existing.market = stock['market']
                        existing.is_st = stock['is_st']
                        existing.is_tradable = stock['is_tradable']
                        existing.price = stock['price']
                        existing.increase = stock['increase']
                        existing.update_time = datetime.now()
                    else:
                        # Insert new
                        new_stock = StockInfoModel(**stock)
                        db.add(new_stock)
                    
                    saved_count += 1
                    
                    # Batch commit
                    if saved_count % self.batch_size == 0:
                        db.commit()
                        logger.debug(f"Committed batch of {self.batch_size} stocks")
                
                except Exception as e:
                    logger.error(f"Failed to save stock {stock.get('code')}: {e}")
                    db.rollback()
                    continue
            
            # Final commit
            db.commit()
            return saved_count
            
        except Exception as e:
            db.rollback()
            logger.error(f"Failed to save stocks to MySQL: {e}")
            raise
        finally:
            db.close()

    # ===============================
    # Historical Data Collection (MongoDB)
    # ===============================
    @retry(
        stop=stop_after_attempt(settings.DATA_COLLECTION_MAX_RETRIES),
        wait=wait_exponential(multiplier=1, min=1, max=10),
        reraise=True
    )
    async def fetch_and_save_historical_data(
        self, 
        stock_code: str, 
        days: int = None
    ) -> int:
        """
        Fetch historical K-line data and save to MongoDB
        
        Args:
            stock_code: Stock code
            days: Number of days to fetch (default: settings.DATA_COLLECTION_HISTORY_DAYS)
            
        Returns:
            int: Number of records saved
        """
        if days is None:
            days = self.history_days
            
        logger.info(f"Fetching historical data for {stock_code}, days={days}")
        
        try:
            # 1. Calculate date range
            end_date = datetime.now()
            start_date = end_date - timedelta(days=days)
            
            start_str = start_date.strftime('%Y%m%d')
            end_str = end_date.strftime('%Y%m%d')
            
            # 2. Check existing data in MongoDB
            collection = get_mongo_collection("stock_prices")
            existing_count = collection.count_documents({
                'code': stock_code,
                'date': {
                    '$gte': start_date.strftime('%Y-%m-%d'),
                    '$lte': end_date.strftime('%Y-%m-%d')
                }
            })
            
            if existing_count >= days * 0.7:  # 70% threshold
                logger.info(f"Sufficient data exists for {stock_code}: {existing_count} records")
                return existing_count
            
            # 3. Fetch from AKShare
            df = ak.stock_zh_a_hist(
                symbol=stock_code,
                period="daily",
                start_date=start_str,
                end_date=end_str,
                adjust=""
            )
            
            if df.empty:
                logger.warning(f"No historical data returned for {stock_code}")
                return 0
            
            # 4. Transform data
            records_to_save = []
            for _, row in df.iterrows():
                record = {
                    'code': stock_code,
                    'date': row.get('日期'),
                    'price1': float(row.get('开盘', 0)),
                    'price2': float(row.get('最高', 0)),
                    'price3': float(row.get('最低', 0)),
                    'price4': float(row.get('收盘', 0)),
                    'trading_volume': float(row.get('成交量', 0)),
                    'trading_amount': float(row.get('成交额', 0)),
                    'amplitude': float(row.get('振幅', 0)),
                    'increase_rate': float(row.get('涨跌幅', 0)),
                    'change_amount': float(row.get('涨跌额', 0)),
                    'exchange_rate': float(row.get('换手率', 0)),
                    'today_open_price': float(row.get('开盘', 0)),
                    'yesterday_close_price': float(row.get('昨收', 0)) if '昨收' in row else float(row.get('收盘', 0)),
                    'create_time': datetime.now()
                }
                records_to_save.append(record)
            
            # 5. Save to MongoDB
            saved_count = await self._save_prices_to_mongodb(records_to_save)
            logger.info(f"Saved {saved_count} historical records for {stock_code}")
            
            return saved_count
            
        except Exception as e:
            logger.error(f"Failed to fetch historical data for {stock_code}: {e}")
            raise

    async def _save_prices_to_mongodb(self, records: List[Dict]) -> int:
        """Save price records to MongoDB with upsert logic"""
        collection = get_mongo_collection("stock_prices")
        
        saved_count = 0
        for record in records:
            try:
                # Upsert: update if exists, insert if not
                result = collection.update_one(
                    {
                        'code': record['code'],
                        'date': record['date']
                    },
                    {'$set': record},
                    upsert=True
                )
                
                if result.upserted_id or result.modified_count:
                    saved_count += 1
                    
            except Exception as e:
                logger.error(f"Failed to save price record: {e}")
                continue
        
        return saved_count

    # ===============================
    # Real-time Quote Collection (Redis + MySQL)
    # ===============================
    @retry(
        stop=stop_after_attempt(settings.DATA_COLLECTION_MAX_RETRIES),
        wait=wait_exponential(multiplier=1, min=1, max=10),
        reraise=True
    )
    async def fetch_and_save_realtime_quotes(self) -> int:
        """
        Fetch real-time quotes and update Redis and MySQL
        
        Returns:
            int: Number of quotes updated
        """
        logger.info("Fetching real-time quotes...")
        
        try:
            # 1. Fetch all stocks' real-time quotes from AKShare
            df = ak.stock_zh_a_spot_em()
            
            if df.empty:
                logger.warning("No real-time quotes returned")
                return 0
            
            updated_count = 0
            
            # 2. Update Redis cache and MySQL
            for _, row in df.iterrows():
                code = str(row.get('代码', ''))
                if not code:
                    continue
                
                try:
                    price = float(row.get('最新价', 0)) if pd_not_null(row.get('最新价')) else None
                    change_pct = float(row.get('涨跌幅', 0)) if pd_not_null(row.get('涨跌幅')) else None
                    
                    if price is None:
                        continue
                    
                    # Update Redis cache
                    cache_key = f"stock:quote:{code}"
                    self.redis_client.setex(
                        cache_key,
                        self.quote_cache_ttl,
                        str(price)
                    )
                    
                    # Update MySQL
                    await self._update_stock_price_in_mysql(code, price, change_pct)
                    
                    updated_count += 1
                    
                except Exception as e:
                    logger.error(f"Failed to update quote for {code}: {e}")
                    continue
            
            logger.info(f"Updated {updated_count} real-time quotes")
            return updated_count
            
        except Exception as e:
            logger.error(f"Failed to fetch real-time quotes: {e}")
            raise

    async def _update_stock_price_in_mysql(
        self, 
        code: str, 
        price: float, 
        change_pct: float = None
    ):
        """Update stock price in MySQL"""
        db = MySQLSessionLocal()
        try:
            db.execute(
                text("""
                    UPDATE stock_info 
                    SET price = :price, 
                        increase = :increase,
                        update_time = :update_time
                    WHERE code = :code
                """),
                {
                    'code': code,
                    'price': price,
                    'increase': change_pct,
                    'update_time': datetime.now()
                }
            )
            db.commit()
        except Exception as e:
            db.rollback()
            logger.error(f"Failed to update price for {code}: {e}")
        finally:
            db.close()

    # ===============================
    # Full Sync Operations
    # ===============================
    async def sync_all_stocks_data(self) -> Dict[str, Any]:
        """
        Full synchronization of all stocks data
        
        Returns:
            dict: Sync statistics
        """
        logger.info("Starting full stock data synchronization...")
        start_time = datetime.now()
        
        stats = {
            'stock_list_saved': 0,
            'historical_records_saved': 0,
            'errors': []
        }
        
        try:
            # 1. Sync stock list
            logger.info("Step 1: Syncing stock list...")
            stock_count = await self.fetch_and_save_stock_list()
            stats['stock_list_saved'] = stock_count
            
            # 2. Get all stock codes
            db = MySQLSessionLocal()
            stocks = db.query(StockInfoModel).filter(
                StockInfoModel.deleted == '0',
                StockInfoModel.is_tradable == 1
            ).all()
            db.close()
            
            stock_codes = [s.code for s in stocks]
            logger.info(f"Step 2: Syncing historical data for {len(stock_codes)} stocks...")
            
            # 3. Sync historical data for each stock (with concurrency limit)
            semaphore = asyncio.Semaphore(settings.DATA_COLLECTION_THREAD_POOL_SIZE)
            
            async def sync_with_limit(code: str):
                async with semaphore:
                    try:
                        count = await self.fetch_and_save_historical_data(code)
                        return count
                    except Exception as e:
                        logger.error(f"Failed to sync {code}: {e}")
                        stats['errors'].append(f"{code}: {str(e)}")
                        return 0
            
            # Process in batches
            batch_size = 100
            for i in range(0, len(stock_codes), batch_size):
                batch = stock_codes[i:i+batch_size]
                tasks = [sync_with_limit(code) for code in batch]
                results = await asyncio.gather(*tasks, return_exceptions=True)
                
                for result in results:
                    if isinstance(result, int):
                        stats['historical_records_saved'] += result
                
                logger.info(f"Progress: {min(i+batch_size, len(stock_codes))}/{len(stock_codes)} stocks processed")
            
            duration = (datetime.now() - start_time).total_seconds()
            logger.info(f"Full sync completed in {duration:.2f}s")
            logger.info(f"Statistics: {stats}")
            
            return stats
            
        except Exception as e:
            logger.error(f"Full sync failed: {e}")
            stats['errors'].append(str(e))
            return stats


def pd_not_null(value) -> bool:
    """Check if pandas value is not null"""
    import pandas as pd
    return pd.notna(value) and value != '-'


# Singleton instance
data_collection_service = DataCollectionService()
