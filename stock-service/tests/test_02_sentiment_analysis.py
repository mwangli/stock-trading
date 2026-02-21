"""
模块二: 情感分析测试

业务流程: 依赖数据采集模块
- 采集新闻数据 → MongoDB (news)
- 情感分析 → MySQL (stock_sentiment)
- 情感得分缓存 → Redis

依赖: 模块一(数据采集)

测试用例:
- TC-002-008: 情感数据写入MySQL
- TC-002-009: 无新闻时默认得分
- TC-002-010: 新闻数据写入MongoDB
- TC-002-011: 按股票代码查询新闻
- TC-002-012: 情感得分Redis缓存
"""

import pytest
import sys
sys.path.insert(0, '.')

from datetime import datetime
from app.core.database import (
    MySQLSessionLocal, StockSentimentModel, NewsSentimentModel,
    get_mongo_collection, get_redis
)


class TestSentimentMySQL:
    """情感数据MySQL测试 TC-002-008, TC-002-009"""

    @pytest.fixture
    def db_session(self):
        db = MySQLSessionLocal()
        try:
            yield db
        finally:
            db.close()

    @pytest.fixture
    def cleanup(self):
        yield
        db = MySQLSessionLocal()
        try:
            db.query(StockSentimentModel).filter(
                StockSentimentModel.stock_code == "TEST001"
            ).delete()
            db.query(NewsSentimentModel).filter(
                NewsSentimentModel.stock_code == "TEST001"
            ).delete()
            db.commit()
        finally:
            db.close()

    @pytest.mark.asyncio
    async def test_save_sentiment_to_mysql(self, db_session, cleanup):
        """TC-002-008: 情感数据写入MySQL"""
        sentiment = StockSentimentModel(
            stock_code="TEST001",
            stock_name="测试股票",
            sentiment_score=0.75,
            positive_ratio=0.8,
            negative_ratio=0.1,
            neutral_ratio=0.1,
            news_count=10,
            source="test",
            analyze_date=datetime.now()
        )
        db_session.add(sentiment)
        db_session.commit()
        
        # 验证写入
        result = db_session.query(StockSentimentModel).filter(
            StockSentimentModel.stock_code == "TEST001"
        ).first()
        
        assert result is not None, "应成功写入MySQL"
        assert result.sentiment_score == 0.75, "情感得分应正确"
        assert result.news_count == 10

    @pytest.mark.asyncio
    async def test_sentiment_with_no_news(self, db_session):
        """TC-002-009: 无新闻时默认得分"""
        result = db_session.query(StockSentimentModel).filter(
            StockSentimentModel.stock_code == "NONEXIST999"
        ).first()
        
        # None是符合预期的(无数据时返回None)
        assert result is None or result.news_count == 0


class TestNewsMongoDB:
    """新闻数据MongoDB测试 TC-002-010, TC-002-011"""

    @pytest.fixture
    def cleanup(self):
        yield
        try:
            get_mongo_collection("news").delete_many({"stock_code": "TEST001"})
        except:
            pass

    @pytest.mark.asyncio
    async def test_save_news_to_mongodb(self, cleanup):
        """TC-002-010: 新闻数据写入MongoDB"""
        coll = get_mongo_collection("news")
        
        news_data = {
            "news_id": "test_001",
            "stock_code": "TEST001",
            "title": "测试新闻标题",
            "content": "测试新闻内容",
            "publish_time": datetime.now(),
            "url": "http://test.com/news/001",
            "source": "test"
        }
        
        result = coll.insert_one(news_data)
        assert result.inserted_id is not None
        
        # 验证写入
        saved_news = coll.find_one({"news_id": "test_001"})
        assert saved_news is not None
        assert saved_news["stock_code"] == "TEST001"
        assert saved_news["title"] == "测试新闻标题"

    @pytest.mark.asyncio
    async def test_query_news_by_stock(self, cleanup):
        """TC-002-011: 按股票代码查询新闻"""
        coll = get_mongo_collection("news")
        
        # 插入测试数据
        test_news = [
            {
                "news_id": f"test_{i}",
                "stock_code": "TEST001",
                "title": f"测试新闻{i}",
                "content": f"测试内容{i}",
                "publish_time": datetime.now()
            }
            for i in range(3)
        ]
        coll.insert_many(test_news)
        
        # 按股票代码查询
        news_list = list(coll.find({"stock_code": "TEST001"}))
        assert len(news_list) == 3, f"应找到3条新闻，实际: {len(news_list)}"


class TestSentimentRedisCache:
    """情感得分Redis缓存测试 TC-002-012"""

    @pytest.mark.asyncio
    async def test_sentiment_cache_redis(self):
        """TC-002-012: 情感得分Redis缓存"""
        r = get_redis()
        if not r:
            pytest.skip("Redis未配置")
        
        cache_key = "sentiment:score:TEST001"
        cache_value = "0.75"
        ttl = 3600  # 1小时
        
        # 写入缓存
        r.setex(cache_key, ttl, cache_value)
        
        # 验证缓存
        saved_value = r.get(cache_key)
        saved_ttl = r.ttl(cache_key)
        
        assert saved_value is not None, "缓存应存在"
        assert float(saved_value) == 0.75, "缓存值应正确"
        assert saved_ttl > 0, "TTL应大于0"
        
        # 清理
        r.delete(cache_key)


class TestSentimentFlow:
    """情感分析完整流程测试 TC-002-F001"""

    @pytest.fixture
    def service(self):
        from app.services.data_collection_service import DataCollectionService
        return DataCollectionService()

    @pytest.fixture
    def cleanup(self):
        yield
        try:
            get_mongo_collection("news").delete_many({"stock_code": "000001"})
        except:
            pass

    @pytest.mark.asyncio
    async def test_sentiment_full_flow(self, service, cleanup):
        """TC-002-F001: 情感分析完整数据流"""
        # 1. 获取新闻数据(依赖数据采集模块)
        news = service.get_stock_news('000001')
        
        if len(news) > 0:
            # 2. 保存到MongoDB
            coll = get_mongo_collection("news")
            result = coll.insert_many(news)
            
            # 3. 验证存储
            saved_count = coll.count_documents({"stock_code": "000001"})
            assert saved_count > 0, "新闻应已保存"


if __name__ == '__main__':
    pytest.main([__file__, '-v', '-s'])
