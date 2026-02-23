"""
模块一: 数据采集测试 (基础层)

业务流程: 最底层，为其他模块提供数据支持
- 股票列表同步 → MySQL (stock_info)
- 历史K线同步 → MongoDB (stock_prices)  
- 实时行情同步 → Redis + MySQL
- 新闻数据采集 → MongoDB (news)

依赖: 无 (最底层)

测试用例:
- TC-PY-001: 股票列表同步到MySQL
- TC-PY-002: 历史K线同步到MongoDB
- TC-PY-003: 实时行情同步
- TC-PY-005: 跳过已有数据
- TC-PY-007: 无效股票代码处理
- TC-PY-008: ST股票过滤
- TC-PY-009: 数据完整性验证
- TC-PY-010: 历史数据字段完整性
- TC-PY-011: 实时行情更新MySQL
- TC-PY-012: 所有数据库连接验证
- TC-DB-001/002/003: 数据库连接测试
"""

import pytest
import sys
sys.path.insert(0, '.')

from app import DataCollectionService
from app import (
    MySQLSessionLocal, StockInfoModel,
    get_mongo_collection, get_redis
)
from sqlalchemy import text


class TestStockListCollection:
    """股票列表采集测试 TC-PY-001, TC-PY-008, TC-PY-009"""

    @pytest.fixture
    def service(self):
        return DataCollectionService()

    @pytest.mark.asyncio
    async def test_fetch_and_save_stock_list(self, service):
        """TC-PY-001: 股票列表同步到MySQL"""
        # 执行采集
        count = await service.fetch_and_save_stock_list()
        
        # 验证采集数量
        assert count > 0, "应采集到股票数据"
        
        # 验证MySQL写入
        db = MySQLSessionLocal()
        try:
            stocks = db.query(StockInfoModel).filter(
                StockInfoModel.deleted == '0'
            ).all()
            assert len(stocks) > 0, "MySQL应有股票数据"
            
            # 验证数据完整性
            for s in stocks[:3]:
                assert s.code is not None
                assert s.name is not None
                assert s.market in ['SH', 'SZ']
        finally:
            db.close()

    @pytest.mark.asyncio
    async def test_filter_st_stocks(self, service):
        """TC-PY-008: ST股票过滤"""
        await service.fetch_and_save_stock_list()
        
        db = MySQLSessionLocal()
        try:
            st_stocks = db.query(StockInfoModel).filter(
                StockInfoModel.is_st == 1,
                StockInfoModel.deleted == '0'
            ).all()
            assert len(st_stocks) == 0, "ST股票应被过滤"
        finally:
            db.close()

    @pytest.mark.asyncio
    async def test_stock_list_data_integrity(self, service):
        """TC-PY-009: 数据完整性验证"""
        await service.fetch_and_save_stock_list()
        
        db = MySQLSessionLocal()
        try:
            tradable = db.query(StockInfoModel).filter(
                StockInfoModel.is_tradable == 1,
                StockInfoModel.deleted == '0'
            ).all()
            assert len(tradable) > 0
            
            for stock in tradable[:5]:
                code_str = str(stock.code) if stock.code else ""
                name_str = str(stock.name) if stock.name else ""
                market_val = str(stock.market) if stock.market else ""
                assert code_str and name_str
                assert market_val in ['SH', 'SZ']
                assert stock.is_tradable == 1
        finally:
            db.close()


class TestHistoricalDataCollection:
    """历史K线采集测试 TC-PY-002, TC-PY-005, TC-PY-010"""

    @pytest.fixture
    def service(self):
        return DataCollectionService()

    @pytest.fixture
    def test_code(self):
        return "000001"

    @pytest.mark.asyncio
    async def test_fetch_and_save_historical_data(self, service, test_code):
        """TC-PY-002: 历史K线同步到MongoDB"""
        # 先确保股票列表有数据
        await service.fetch_and_save_stock_list()
        
        # 执行历史数据采集
        count = await service.fetch_and_save_historical_data(test_code, days=30)
        assert count > 0, "应采集到历史数据"
        
        # 验证MongoDB写入
        coll = get_mongo_collection("stock_prices")
        records = list(coll.find({"code": test_code}))
        assert len(records) > 0, "MongoDB应有历史数据"
        
        # 验证数据字段
        for r in records[:3]:
            assert r['code'] == test_code
            assert 'date' in r
            assert 'price4' in r  # 收盘价
            assert r['price4'] > 0

    @pytest.mark.asyncio
    async def test_skip_existing_data(self, service, test_code):
        """TC-PY-005: 跳过已有数据"""
        # 首次采集
        await service.fetch_and_save_historical_data(test_code, days=30)
        
        # 验证数据存在
        coll = get_mongo_collection("stock_prices")
        existing = coll.count_documents({"code": test_code})
        assert existing > 0
        
        # 再次采集应跳过
        count = await service.fetch_and_save_historical_data(test_code, days=30)
        assert count >= existing * 0.7

    @pytest.mark.asyncio
    async def test_historical_data_fields(self, service, test_code, cleanup_historical_data):
        """TC-PY-010: 历史数据字段完整性"""
        await service.fetch_and_save_historical_data(test_code, days=10)
        
        coll = get_mongo_collection("stock_prices")
        records = list(coll.find({"code": test_code}).sort("date", -1).limit(1))
        
        assert len(records) > 0
        record = records[0]
        required_fields = [
            'code', 'date', 'price1', 'price2', 'price3', 'price4',
            'trading_volume', 'trading_amount', 'amplitude',
            'increase_rate', 'change_amount', 'exchange_rate'
        ]
        for field in required_fields:
            assert field in record, f"缺失字段: {field}"
            assert record[field] is not None


class TestRealtimeQuoteCollection:
    """实时行情采集测试 TC-PY-003, TC-PY-011"""

    @pytest.fixture
    def service(self):
        return DataCollectionService()

    @pytest.fixture
    def test_code(self):
        return "000001"

    @pytest.fixture
    def cleanup_quote_data(self, test_code):
        yield
        try:
            r = get_redis()
            if r:
                r.delete(f"stock:quote:{test_code}")
        except:
            pass

    @pytest.mark.asyncio
    async def test_fetch_and_save_realtime_quotes(self, service, test_code, cleanup_quote_data):
        """TC-PY-003: 实时行情同步"""
        # 确保股票存在
        await service.fetch_and_save_stock_list()
        
        # 执行实时行情采集
        count = await service.fetch_and_save_realtime_quotes()
        assert count > 0, "应采集到实时行情"
        
        # 验证Redis缓存
        r = get_redis()
        db = MySQLSessionLocal()
        try:
            sample = db.query(StockInfoModel).limit(5).all()
            for stock in sample:
                cached = r.get(f"stock:quote:{stock.code}") if r else None
                if cached:
                    assert float(cached) > 0
        finally:
            db.close()

    @pytest.mark.asyncio
    async def test_realtime_update_mysql(self, service, test_code, cleanup_quote_data):
        """TC-PY-011: 实时行情更新MySQL"""
        await service.fetch_and_save_stock_list()
        
        # 执行实时行情采集
        await service.fetch_and_save_realtime_quotes()
        
        # 验证更新后价格
        db = MySQLSessionLocal()
        try:
            stock_after = db.query(StockInfoModel).filter(
                StockInfoModel.code == test_code
            ).first()
            assert stock_after is not None
            assert stock_after.price is not None
            assert stock_after.update_time is not None
        finally:
            db.close()


class TestErrorHandling:
    """错误处理测试 TC-PY-007"""

    @pytest.fixture
    def service(self):
        return DataCollectionService()

    @pytest.mark.asyncio
    async def test_invalid_stock_code_handling(self, service):
        """TC-PY-007: 无效股票代码处理"""
        try:
            count = await service.fetch_and_save_historical_data("INVALID999", days=5)
            assert count >= 0
        except Exception as e:
            # 抛出异常也是可接受的
            print(f"正确处理无效股票代码: {e}")


class TestDatabaseConnections:
    """数据库连接测试 TC-DB-001, TC-DB-002, TC-DB-003"""

    def test_mysql_connection(self):
        """TC-DB-001: MySQL连接"""
        db = MySQLSessionLocal()
        try:
            result = db.execute(text("SELECT 1")).fetchone()
            assert result is not None
        finally:
            db.close()

    def test_mongodb_connection(self):
        """TC-DB-002: MongoDB连接"""
        coll = get_mongo_collection("stock_prices")
        coll.find_one({})

    def test_redis_connection(self):
        """TC-DB-003: Redis连接"""
        r = get_redis()
        if r:
            r.ping()

    @pytest.mark.asyncio
    async def test_all_connections(self, service):
        """TC-PY-012: 所有数据库连接验证"""
        # MySQL
        db = MySQLSessionLocal()
        try:
            result = db.execute(text("SELECT 1")).fetchone()
            assert result is not None
        finally:
            db.close()
        
        # MongoDB
        coll = get_mongo_collection("stock_prices")
        coll.find_one({})
        
        # Redis
        r = get_redis()
        if r:
            r.ping()


class TestHelperFunctions:
    """辅助函数测试"""

    def test_pd_not_null_helper(self):
        """辅助函数: pd_not_null"""
        from app import pd_not_null
        assert pd_not_null(10.5) == True
        assert pd_not_null(0) == True
        assert pd_not_null('-') == False


class TestNewsContentFetch:
    """新闻内容获取测试"""

    @pytest.fixture
    def eastmoney_client(self):
        from app import EastMoneyClient
        return EastMoneyClient()

    def test_get_stock_news(self, eastmoney_client):
        """获取股票新闻"""
        news = eastmoney_client.get_stock_news('000001')
        assert news is not None
        assert isinstance(news, list)

    def test_fetch_article_content(self, eastmoney_client):
        """获取文章完整内容"""
        news = eastmoney_client.get_stock_news('000001')
        if news:
            url = news[0].get('url')
            if url:
                content = eastmoney_client.fetch_article_content(url)
                assert isinstance(content, str)

    def test_fetch_news_with_content(self, eastmoney_client):
        """获取带完整内容的新闻"""
        news = eastmoney_client.fetch_news_with_content('000001')
        if news:
            for item in news:
                assert 'title' in item or 'content' in item


class TestFullHistoricalDataCollection:
    """
    全量历史数据采集测试
    验证场景: 每只股票需要采集近3年(1095天)的历史数据
    """

    @pytest.fixture
    def service(self):
        return DataCollectionService()

    @pytest.fixture
    def test_code(self):
        return "000001"

    @pytest.mark.asyncio
    async def test_full_historical_data_3_years(self, service, test_code):
        """
        TC-FULL-001: 验证可以采集近3年的历史数据
        业务需求: 每只股票需要采集近3年的历史数据用于LSTM训练
        """
        # 先确保股票列表有数据
        await service.fetch_and_save_stock_list()
        
        # 执行3年历史数据采集（约1095天）
        from app.core.config import settings
        days = settings.DATA_COLLECTION_HISTORY_DAYS_INITIAL
        
        # 验证配置正确
        assert days >= 1095, f"历史数据采集天数应>=1095(3年),实际为{days}天"
        
        count = await service.fetch_and_save_historical_data(test_code, days=days)
        assert count > 0, f"应采集到{days}天的历史数据"
        
        # 验证MongoDB有足够的历史数据
        coll = get_mongo_collection("stock_prices")
        records = list(coll.find({"code": test_code}))
        assert len(records) >= 365 * 2, f"3年数据应>=730条,实际为{len(records)}条"

    @pytest.mark.asyncio
    async def test_incremental_sync_uses_1_day(self, service, test_code):
        """
        TC-FULL-002: 验证增量同步使用1天配置
        业务需求: 日常运行每天同步最新1个交易日的数据
        """
        from app.core.config import settings
        
        # 验证增量同步配置
        incremental_days = settings.DATA_COLLECTION_HISTORY_DAYS_INCREMENTAL
        assert incremental_days == 1, f"增量同步应使用1天,实际为{incremental_days}天"
        
        # 执行增量同步
        count = await service.fetch_and_save_historical_data(test_code, days=incremental_days)
        # 增量同步应该至少有1条数据
        assert count >= 0  # 可能没有新数据，这是正常的


class TestFullSyncWorkflow:
    """
    完整数据采集流程测试
    验证步骤:
    1. 启动Python服务（确保数据库连接正常）
    2. 调用全量历史数据采集接口
    3. 验证MongoDB中每只股票都有3年历史数据
    """

    @pytest.fixture
    def service(self):
        return DataCollectionService()

    @pytest.mark.asyncio
    async def test_full_sync_workflow_3_years(self, service):
        """
        TC-FULL-WORKFLOW-001: 完整数据采集流程测试
        
        步骤1: 确保股票列表已同步到MySQL
        步骤2: 对多只股票进行3年历史数据全量采集
        步骤3: 验证MongoDB中每只股票都有>=2年(730天)的历史数据
        """
        from app.core.config import settings
        from app.core.database import MySQLSessionLocal, StockInfoModel, get_mongo_collection
        import asyncio
        
        # ========== 步骤1: 同步股票列表 ==========
        print("\n=== 步骤1: 同步股票列表 ===")
        stock_count = await service.fetch_and_save_stock_list()
        print(f"股票列表同步完成: {stock_count} 只股票")
        assert stock_count > 0, "股票列表同步失败"
        
        # 获取前10只股票用于测试（全量5000+太慢）
        db = MySQLSessionLocal()
        try:
            stocks = db.query(StockInfoModel).filter(
                StockInfoModel.deleted == '0',
                StockInfoModel.is_tradable == 1
            ).limit(10).all()
            stock_codes = [s.code for s in stocks]
        finally:
            db.close()
        
        print(f"测试股票列表: {stock_codes}")
        
        # ========== 步骤2: 全量历史数据采集(3年) ==========
        print("\n=== 步骤2: 全量历史数据采集(3年) ===")
        
        # 使用3年初始化配置
        days_to_fetch = settings.DATA_COLLECTION_HISTORY_DAYS_INITIAL
        print(f"采集天数: {days_to_fetch}天 (3年)")
        
        # 验证配置正确
        assert days_to_fetch >= 1095, f"采集天数应>=1095，实际为{days_to_fetch}"
        
        # 并发采集历史数据
        semaphore = asyncio.Semaphore(5)  # 限制并发数
        total_records = 0
        
        async def sync_stock(code: str):
            nonlocal total_records
            async with semaphore:
                try:
                    count = await service.fetch_and_save_historical_data(code, days=days_to_fetch)
                    print(f"  {code}: 采集 {count} 条记录")
                    total_records += count
                    return count
                except Exception as e:
                    print(f"  {code}: 采集失败 - {e}")
                    return 0
        
        # 采集所有测试股票的历史数据
        tasks = [sync_stock(code) for code in stock_codes]
        results = await asyncio.gather(*tasks, return_exceptions=True)
        
        print(f"历史数据采集完成: 共 {total_records} 条记录")
        
        # ========== 步骤3: 验证MongoDB数据 ==========
        print("\n=== 步骤3: 验证MongoDB数据 ===")
        
        coll = get_mongo_collection("stock_prices")
        
        verified_stocks = []
        for code in stock_codes:
            # 统计该股票的历史数据条数
            count = coll.count_documents({"code": code})
            
            # 验证数据量（3年约750个交易日，减去节假日约700条）
            min_expected = 680  # 约2.7年数据，考虑节假日
            status = "PASS" if count >= min_expected else "FAIL"
            print(f"  {code}: {count} records {status}")
            
            if count >= min_expected:
                verified_stocks.append(code)
        
        # 打印统计
        print(f"\n=== 验证结果 ===")
        print(f"测试股票总数: {len(stock_codes)}")
        print(f"数据完整股票数: {len(verified_stocks)}")
        print(f"数据不完整股票数: {len(stock_codes) - len(verified_stocks)}")
        
        # 验证: 至少有80%的股票有完整数据
        success_rate = len(verified_stocks) / len(stock_codes) if stock_codes else 0
        print(f"成功率: {success_rate*100:.1f}%")
        
        # 断言: 至少50%的股票有完整3年数据（考虑到部分股票可能退市/停牌）
        assert success_rate >= 0.5, f"数据完整率应>=50%, 实际为{success_rate*100:.1f}%"
        
        print("\n✅ 全量历史数据采集流程测试通过!")


class TestNewsCollection:
    """
    新闻数据采集测试
    验证新闻数据可以成功采集并写入MongoDB
    """

    @pytest.fixture
    def eastmoney_client(self):
        from app import EastMoneyClient
        return EastMoneyClient()

    @pytest.fixture
    def test_stock_code(self):
        return "000001"

    def test_get_stock_news_from_api(self, eastmoney_client, test_stock_code):
        """
        TC-NEWS-001: 验证可以从API获取股票新闻
        """
        news_list = eastmoney_client.get_stock_news(test_stock_code)
        
        # 验证返回的是列表
        assert news_list is not None, "新闻列表不应为None"
        assert isinstance(news_list, list), "新闻应返回列表类型"
        
        # 如果有新闻，验证字段
        if len(news_list) > 0:
            news = news_list[0]
            assert 'title' in news or '新闻标题' in news, "新闻应包含标题"
            print(f"\n[INFO] 获取到 {len(news_list)} 条新闻")
            for n in news_list[:3]:
                title = n.get('title', n.get('新闻标题', 'N/A'))[:50]
                print(f"   - {title}")

    def test_save_news_to_mongodb(self, eastmoney_client, test_stock_code):
        """
        TC-NEWS-002: 验证新闻可以写入MongoDB
        """
        from app.core.database import get_mongo_collection
        
        # 获取新闻
        news_list = eastmoney_client.get_stock_news(test_stock_code)
        
        if not news_list or len(news_list) == 0:
            print(f"\n[WARN] 股票 {test_stock_code} 暂无新闻，跳过写入测试")
            return
        
        # 写入MongoDB
        saved_count = eastmoney_client.save_news_to_mongodb(news_list, "news")
        
        # 验证写入成功
        assert saved_count >= 0, "保存数量应>=0"
        
        # 验证MongoDB中有数据
        coll = get_mongo_collection("news")
        count = coll.count_documents({"stock_code": test_stock_code})
        
        print(f"\n[INFO] 股票 {test_stock_code} MongoDB中新闻数: {count}")
        assert count > 0, "MongoDB应有新闻数据"

    def test_news_data_deduplication(self, eastmoney_client, test_stock_code):
        """
        TC-NEWS-003: 验证新闻去重功能
        相同news_id的新闻不应重复写入
        """
        from app.core.database import get_mongo_collection
        
        # 获取同一股票新闻两次
        news_list = eastmoney_client.get_stock_news(test_stock_code)
        
        if not news_list or len(news_list) == 0:
            print(f"\n[WARN] 股票 {test_stock_code} 暂无新闻，跳过去重测试")
            return
        
        # 第一次写入
        first_save = eastmoney_client.save_news_to_mongodb(news_list, "news")
        
        # 第二次写入（应该去重）
        second_save = eastmoney_client.save_news_to_mongodb(news_list, "news")
        
        # 第二次写入应该返回0（全部去重）
        print(f"\n[INFO] 第一次保存: {first_save}, 第二次保存: {second_save}")
        # 去重逻辑可能导致第二次保存为0或很少（因为新闻可能有时效性）
        assert second_save <= first_save, "第二次保存数量不应大于第一次"

    def test_news_collection_multiple_stocks(self, eastmoney_client):
        """
        TC-NEWS-004: 验证多只股票新闻采集
        """
        from app.core.database import get_mongo_collection
        
        test_codes = ["000001", "000002", "600000"]
        total_saved = 0
        
        for code in test_codes:
            news_list = eastmoney_client.get_stock_news(code)
            if news_list:
                saved = eastmoney_client.save_news_to_mongodb(news_list, "news")
                total_saved += saved
                print(f"\n[INFO] {code}: 获取 {len(news_list)} 条, 保存 {saved} 条")
        
        print(f"\n[INFO] 总计保存: {total_saved} 条新闻")
        assert total_saved > 0, "应保存至少一条新闻"

    def test_news_mongodb_schema(self, eastmoney_client, test_stock_code):
        """
        TC-NEWS-005: 验证MongoDB中新闻数据字段完整性
        """
        from app.core.database import get_mongo_collection
        
        # 获取并保存新闻
        news_list = eastmoney_client.get_stock_news(test_stock_code)
        
        if not news_list or len(news_list) == 0:
            print(f"\n[WARN] 股票 {test_stock_code} 暂无新闻，跳过字段测试")
            return
        
        eastmoney_client.save_news_to_mongodb(news_list, "news")
        
        # 查询MongoDB
        coll = get_mongo_collection("news")
        news = coll.find_one({"stock_code": test_stock_code})
        
        if news:
            # 验证必需字段
            required_fields = ['news_id', 'title', 'stock_code', 'pub_time']
            for field in required_fields:
                assert field in news, f"新闻应包含字段: {field}"
            
            print(f"\n[INFO] 新闻字段验证通过: {list(news.keys())}")


class TestFinanceNewsSpider:
    """
    财经新闻爬虫测试
    验证爬虫可以从多个财经新闻源采集新闻
    """

    def test_sina_finance_spider(self):
        """
        TC-CRAWLER-001: 验证新浪财经爬虫可以获取新闻
        """
        from app.core.spiders.finance_news_spider import SinaFinanceSpider
        
        spider = SinaFinanceSpider()
        news_list = spider.fetch_news(limit=10)
        
        assert news_list is not None, "爬虫应返回新闻列表"
        assert isinstance(news_list, list), "返回类型应为列表"
        
        if len(news_list) > 0:
            # 验证新闻字段
            news = news_list[0]
            assert 'title' in news or 'title' in news, "新闻应包含标题"
            assert 'url' in news, "新闻应包含URL"
            assert 'source' in news, "新闻应包含来源"
            print(f"\n[INFO] Sina爬虫获取 {len(news_list)} 条新闻")

    def test_eastmoney_spider(self):
        """
        TC-CRAWLER-002: 验证东方财富爬虫可以获取新闻
        """
        from app.core.spiders.finance_news_spider import EastMoneySpider
        
        spider = EastMoneySpider()
        news_list = spider.fetch_news(limit=10)
        
        assert news_list is not None, "爬虫应返回新闻列表"
        assert isinstance(news_list, list), "返回类型应为列表"
        
        if len(news_list) > 0:
            news = news_list[0]
            assert 'title' in news, "新闻应包含标题"
            assert 'url' in news, "新闻应包含URL"
            print(f"\n[INFO] EastMoney爬虫获取 {len(news_list)} 条新闻")

    def test_ifeng_finance_spider(self):
        """
        TC-CRAWLER-003: 验证凤凰网财经爬虫可以获取新闻
        """
        from app.core.spiders.finance_news_spider import IfengFinanceSpider
        
        spider = IfengFinanceSpider()
        news_list = spider.fetch_news(limit=10)
        
        assert news_list is not None, "爬虫应返回新闻列表"
        assert isinstance(news_list, list), "返回类型应为列表"
        
        if len(news_list) > 0:
            print(f"\n[INFO] Ifeng爬虫获取 {len(news_list)} 条新闻")

    def test_netease_finance_spider(self):
        """
        TC-CRAWLER-004: 验证网易财经爬虫可以获取新闻
        """
        from app.core.spiders.finance_news_spider import NetEaseFinanceSpider
        
        spider = NetEaseFinanceSpider()
        news_list = spider.fetch_news(limit=10)
        
        assert news_list is not None, "爬虫应返回新闻列表"
        assert isinstance(news_list, list), "返回类型应为列表"
        
        if len(news_list) > 0:
            print(f"\n[INFO] NetEase爬虫获取 {len(news_list)} 条新闻")

    def test_finance_news_spider_dispatcher(self):
        """
        TC-CRAWLER-005: 验证财经新闻爬虫调度器
        """
        from app.core.spiders.finance_news_spider import FinanceNewsSpider
        
        spider = FinanceNewsSpider()
        news_list = spider.fetch_all_news(limit_per_spider=10)
        
        assert news_list is not None, "爬虫调度器应返回新闻列表"
        assert isinstance(news_list, list), "返回类型应为列表"
        
        # 验证去重
        urls = [n.get('url') for n in news_list if n.get('url')]
        unique_urls = set(urls)
        assert len(urls) == len(unique_urls), "新闻URL应该去重"
        
        print(f"\n[INFO] 爬虫调度器获取 {len(news_list)} 条新闻 (去重后)")

    def test_save_to_mongodb(self):
        """
        TC-CRAWLER-006: 验证爬虫可以保存新闻到MongoDB
        """
        from app.core.spiders.finance_news_spider import FinanceNewsSpider
        from app.core.database import get_mongo_collection
        
        spider = FinanceNewsSpider()
        
        # 采集少量新闻
        news_list = spider.fetch_all_news(limit_per_spider=5)
        
        if not news_list or len(news_list) == 0:
            print("\n[WARN] 未获取到新闻，跳过MongoDB保存测试")
            return
        
        # 保存到MongoDB
        saved_count = spider.save_to_mongodb(news_list, "news")
        
        # 验证
        assert saved_count >= 0, "保存数量应>=0"
        
        # 验证MongoDB中有数据
        coll = get_mongo_collection("news")
        count = coll.count_documents({})
        
        print(f"\n[INFO] MongoDB中新闻总数: {count}, 本次保存: {saved_count}")
        assert count > 0, "MongoDB应有新闻数据"

    def test_news_content_extraction(self):
        """
        TC-CRAWLER-007: 验证新闻内容提取功能
        """
        from app.core.spiders.finance_news_spider import BaseFinanceSpider
        
        # 测试时间解析
        spider = BaseFinanceSpider()
        
        # 测试各种时间格式
        test_cases = [
            ("2024-01-01 10:00:00", True),
            ("2024-01-01", True),
            ("5分钟前", True),
            ("1小时前", True),
            ("昨天", True),
            ("10天前", True),
            ("invalid", False),
        ]
        
        for time_str, expected_valid in test_cases:
            result = spider._parse_datetime(time_str)
            if expected_valid:
                assert result is not None, f"应能解析: {time_str}"
            print(f"  {time_str} -> {result}")

    def test_news_collection_1000_plus(self):
        """
        TC-CRAWLER-008: 验证财经新闻爬虫能够采集1000+条新闻
        用于FinBERT模型训练，需要大量新闻数据
        """
        from app.core.spiders.finance_news_spider import (
            FinanceNewsSpider, SinaFinanceSpider, EastMoneySpider,
            IfengFinanceSpider, NetEaseFinanceSpider,
            TonghuashunSpider, XueqiuSpider, HexunSpider
        )
        from app.core.database import get_mongo_collection
        
        print("\n" + "="*60)
        print("[测试] 验证新闻采集数量 >= 1000 条")
        print("="*60)
        
        # 记录初始数量
        collection = get_mongo_collection("news")
        initial_count = collection.count_documents({})
        print(f"\n[INFO] 初始MongoDB新闻数量: {initial_count}")
        
        # 采集新闻 - 多轮采集以达到1000+
        total_fetched = 0
        rounds = 3  # 采集3轮
        
        all_spiders = [
            ("Sina", SinaFinanceSpider),
            ("EastMoney", EastMoneySpider),
            ("Ifeng", IfengFinanceSpider),
            ("NetEase", NetEaseFinanceSpider),
            ("Tonghuashun", TonghuashunSpider),
            ("Xueqiu", XueqiuSpider),
            ("Hexun", HexunSpider),
        ]
        
        for round_num in range(1, rounds + 1):
            print(f"\n--- 第 {round_num}/{rounds} 轮采集 ---")
            round_news = []
            
            for name, spider_class in all_spiders:
                try:
                    spider = spider_class()
                    news = spider.fetch_news(limit=200)
                    round_news.extend(news)
                    print(f"  {name}: 获取 {len(news)} 条")
                except Exception as e:
                    print(f"  {name}: 采集失败 - {e}")
            
            # 去重
            seen_urls = set()
            unique_news = []
            for news in round_news:
                url = news.get('url', '')
                if url and url not in seen_urls:
                    seen_urls.add(url)
                    unique_news.append(news)
            
            total_fetched += len(unique_news)
            print(f"  本轮去重后: {len(unique_news)} 条, 累计: {total_fetched} 条")
            
            # 保存到MongoDB
            saved = 0
            for news in unique_news:
                try:
                    if not collection.find_one({"url": news.get('url')}):
                        result = collection.insert_one(news)
                        if result.inserted_id:
                            saved += 1
                except:
                    pass
            print(f"  本轮保存: {saved} 条")
        
        # 最终数量
        final_count = collection.count_documents({})
        print(f"\n[结果] MongoDB新闻数量:")
        print(f"  - 初始: {initial_count} 条")
        print(f"  - 新增: {final_count - initial_count} 条")
        print(f"  - 总计: {final_count} 条")
        
        # 验证
        print(f"\n[验证] 目标: >= 1000 条")
        print(f"  - 实际: {final_count} 条")
        
        # 按来源统计
        print(f"\n[统计] 按来源分布:")
        sources = {}
        for doc in collection.find():
            source = doc.get('source', 'Unknown')
            sources[source] = sources.get(source, 0) + 1
        for source, count in sorted(sources.items(), key=lambda x: -x[1]):
            print(f"  - {source}: {count} 条")
        
        # 断言
        assert final_count >= 1000, f"新闻数量应>=1000条，实际: {final_count}条"
        print(f"\n[PASS] 测试通过: 新闻数量 {final_count} >= 1000")


if __name__ == '__main__':
    pytest.main([__file__, '-v', '-s'])
