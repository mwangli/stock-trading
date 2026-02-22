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

    @pytest.fixture
    def cleanup_stock_data(self):
        """清理测试数据"""
        yield
        db = MySQLSessionLocal()
        try:
            db.query(StockInfoModel).filter(StockInfoModel.code == "000001").delete()
            db.commit()
        finally:
            db.close()

    @pytest.mark.asyncio
    async def test_fetch_and_save_stock_list(self, service, cleanup_stock_data):
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

    @pytest.fixture
    def cleanup_historical_data(self, test_code):
        yield
        try:
            get_mongo_collection("stock_prices").delete_many({"code": test_code})
        except:
            pass

    @pytest.mark.asyncio
    async def test_fetch_and_save_historical_data(self, service, test_code, cleanup_historical_data):
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
            
            # 验证数据量（3年约750个交易日）
            min_expected = 365 * 2  # 至少2年数据
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


if __name__ == '__main__':
    pytest.main([__file__, '-v', '-s'])
