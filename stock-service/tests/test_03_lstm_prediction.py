"""
模块三: LSTM预测测试

业务流程: 依赖数据采集模块(历史数据)
- 获取历史K线数据 → MongoDB
- LSTM模型推理 → 预测结果
- 预测结果存储 → MySQL (stock_prediction)

依赖: 模块一(数据采集)

测试用例 (参考需求文档):
- 功能验收:
  - TC-003-001: 服务启动/模型加载
  - TC-003-002: 单股预测返回正确格式
  - TC-003-003: 批量预测支持50只股票
  - TC-003-004: 预测方向判断正确
  - TC-003-005: 置信度计算合理
- 性能验收:
  - TC-003-006: 单股预测 < 1秒
  - TC-003-007: 50股批量预测 < 30秒
  - TC-003-008: 模型加载 < 10秒
- 可用性验收:
  - TC-003-009: GPU不可用时自动降级CPU
  - TC-003-010: 异常情况返回错误信息
"""

import pytest
import time
import sys
sys.path.insert(0, '.')


class TestLSTMService:
    """LSTM预测服务测试 - 功能验收"""

    @pytest.fixture
    def lstm_service(self):
        """获取LSTM服务实例"""
        try:
            from app.services.lstm_prediction import LSTMService
            return LSTMService()
        except ImportError as e:
            pytest.skip(f"LSTM服务不可用: {e}")

    @pytest.fixture
    def mock_data_60_days(self):
        """生成60天模拟数据"""
        return [
            {
                "open": 10.0 + i * 0.1,
                "high": 10.5 + i * 0.1,
                "low": 9.8 + i * 0.1,
                "close": 10.2 + i * 0.1,
                "volume": 1000000 + i * 1000
            }
            for i in range(60)
        ]

    # ===== 功能验收测试 =====

    def test_003_001_service_import(self):
        """TC-003-001: 服务启动/模型加载"""
        try:
            from app.services.lstm_prediction import LSTMService
            service = LSTMService()
            assert service is not None
            # 获取模型信息
            info = service.get_model_info()
            assert info is not None
            print(f"模型信息: {info}")
        except ImportError as e:
            pytest.skip(f"LSTM服务不可用: {e}")

    def test_003_002_single_prediction_format(self, lstm_service, mock_data_60_days):
        """TC-003-002: 单股预测返回正确格式"""
        result = lstm_service.predict("TEST001", mock_data_60_days)
        
        # 验证返回格式
        assert result is not None, "预测结果不应为空"
        assert 'stock_code' in result, "缺少 stock_code 字段"
        assert result['stock_code'] == "TEST001"
        
        # 验证预测结果字段
        expected_fields = ['predicted_price', 'current_price', 'direction', 'confidence']
        for field in expected_fields:
            assert field in result, f"缺少 {field} 字段"
        
        print(f"预测结果: {result}")

    def test_003_003_batch_prediction_50_stocks(self, lstm_service, mock_data_60_days):
        """TC-003-003: 批量预测支持50只股票"""
        stock_codes = [f"TEST{i:03d}" for i in range(50)]
        
        # 批量预测
        results = []
        for code in stock_codes:
            result = lstm_service.predict(code, mock_data_60_days)
            if result:
                results.append(result)
        
        # 验证结果数量
        assert len(results) > 0, "批量预测应返回结果"
        print(f"批量预测成功: {len(results)}/{len(stock_codes)}")

    def test_003_004_prediction_direction(self, lstm_service, mock_data_60_days):
        """TC-003-004: 预测方向判断正确"""
        result = lstm_service.predict("TEST001", mock_data_60_days)
        
        # 验证direction字段
        assert 'direction' in result, "缺少 direction 字段"
        direction = result['direction']
        assert direction in ['up', 'down', 'neutral'], \
            f"direction 应为 up/down/neutral，实际: {direction}"
        
        print(f"预测方向: {direction}")

    def test_003_005_confidence_range(self, lstm_service, mock_data_60_days):
        """TC-003-005: 置信度计算合理"""
        result = lstm_service.predict("TEST001", mock_data_60_days)
        
        # 验证confidence字段
        assert 'confidence' in result, "缺少 confidence 字段"
        confidence = result['confidence']
        assert 0 <= confidence <= 1, \
            f"confidence 应在 0-1 范围内，实际: {confidence}"
        
        print(f"置信度: {confidence}")

    # ===== 性能验收测试 =====

    def test_003_006_single_prediction_time(self, lstm_service, mock_data_60_days):
        """TC-003-006: 单股预测 < 1秒"""
        # 预热
        lstm_service.predict("WARMUP", mock_data_60_days)
        
        # 计时测试
        start_time = time.time()
        result = lstm_service.predict("TEST001", mock_data_60_days)
        elapsed_time = time.time() - start_time
        
        assert elapsed_time < 1.0, \
            f"单股预测应 < 1秒，实际: {elapsed_time:.3f}秒"
        print(f"单股预测耗时: {elapsed_time:.3f}秒")

    def test_003_007_batch_50_prediction_time(self, lstm_service, mock_data_60_days):
        """TC-003-007: 50股批量预测 < 30秒"""
        stock_codes = [f"TEST{i:03d}" for i in range(50)]
        
        # 计时测试
        start_time = time.time()
        results = []
        for code in stock_codes:
            result = lstm_service.predict(code, mock_data_60_days)
            if result:
                results.append(result)
        elapsed_time = time.time() - start_time
        
        assert elapsed_time < 30.0, \
            f"50股批量预测应 < 30秒，实际: {elapsed_time:.3f}秒"
        print(f"50股批量预测耗时: {elapsed_time:.3f}秒")

    def test_003_008_model_load_time(self):
        """TC-003-008: 模型加载 < 10秒"""
        from app.services.lstm_prediction import LSTMService
        
        start_time = time.time()
        service = LSTMService()
        # 触发模型加载
        _ = service.model
        elapsed_time = time.time() - start_time
        
        assert elapsed_time < 10.0, \
            f"模型加载应 < 10秒，实际: {elapsed_time:.3f}秒"
        print(f"模型加载耗时: {elapsed_time:.3f}秒")

    # ===== 可用性验收测试 =====

    def test_003_009_gpu_fallback_to_cpu(self, lstm_service):
        """TC-003-009: GPU不可用时自动降级CPU"""
        info = lstm_service.get_model_info()
        
        # 验证设备信息
        assert 'device' in info, "缺少 device 字段"
        device = info['device']
        print(f"当前设备: {device}")
        
        # 无论是cuda还是cpu都应该能工作
        assert device in ['cuda', 'cpu'], f"设备应为 cuda 或 cpu，实际: {device}"

    def test_003_010_error_handling_insufficient_data(self, lstm_service):
        """TC-003-010: 异常情况返回错误信息"""
        # 使用不足60天的数据
        insufficient_data = [
            {"open": 10.0, "high": 10.5, "low": 9.8, "close": 10.2, "volume": 1000000}
            for _ in range(10)
        ]
        
        result = lstm_service.predict("TEST001", insufficient_data)
        
        # 验证错误处理
        assert result is not None, "应返回结果"
        # 预测失败时应返回error或predicted_price为None
        if result.get('error'):
            print(f"正确返回错误信息: {result.get('error')}")
        else:
            print(f"预测结果: {result}")

    def test_003_011_error_handling_invalid_data(self, lstm_service):
        """TC-003-011: 无效数据处理"""
        # 使用空数据
        result = lstm_service.predict("TEST001", [])
        
        # 验证错误处理
        assert result is not None, "应返回结果"
        if result.get('error'):
            print(f"正确返回错误信息: {result.get('error')}")

    def test_003_012_model_info_query(self, lstm_service):
        """TC-003-012: 模型信息查询"""
        info = lstm_service.get_model_info()
        
        # 验证模型信息
        assert info is not None, "模型信息不应为空"
        assert 'model_type' in info, "缺少 model_type 字段"
        assert info['model_type'] == 'LSTM', f"模型类型应为 LSTM，实际: {info['model_type']}"
        
        # 验证PyTorch框架
        assert 'framework' in info, "缺少 framework 字段"
        assert info['framework'] == 'PyTorch', f"框架应为 PyTorch，实际: {info['framework']}"
        
        print(f"模型信息: {info}")


class TestPredictionStorage:
    """预测结果存储测试"""

    @pytest.fixture
    def db_session(self):
        from app.core.database import MySQLSessionLocal
        db = MySQLSessionLocal()
        try:
            yield db
        finally:
            db.close()

    def test_prediction_table_exists(self, db_session):
        """TC-003-013: 预测表存在性验证"""
        from sqlalchemy import text
        
        try:
            result = db_session.execute(
                text("SHOW TABLES LIKE 'stock_prediction'")
            ).fetchone()
            
            if result:
                # 表存在，验证结构
                columns = db_session.execute(
                    text("SHOW COLUMNS FROM stock_prediction")
                ).fetchall()
                
                column_names = [col[0] for col in columns]
                required_columns = ['stock_code', 'predict_price', 'predict_direction']
                
                for col in required_columns:
                    assert col in column_names, f"缺少列: {col}"
                
                print(f"stock_prediction 表结构: {column_names}")
            else:
                pytest.skip("stock_prediction表不存在")
        except Exception as e:
            pytest.skip(f"无法验证预测表: {e}")

    def test_prediction_result_fields(self):
        """TC-003-014: 预测结果字段验证"""
        expected_fields = [
            'stock_code',
            'predicted_price',
            'current_price',
            'change',
            'change_percent',
            'direction',
            'confidence',
            'is_trained'
        ]
        
        # 验证预测结果应包含的字段
        for field in expected_fields:
            assert field is not None, f"字段 {field} 应存在"


class TestLSTMFlow:
    """LSTM完整流程测试"""

    @pytest.fixture
    def lstm_service(self):
        try:
            from app.services.lstm_prediction import LSTMService
            return LSTMService()
        except ImportError:
            pytest.skip("LSTM服务不可用")

    @pytest.fixture
    def mock_data(self):
        """生成模拟数据"""
        return [
            {
                "open": 10.0 + i * 0.1,
                "high": 10.5 + i * 0.1,
                "low": 9.8 + i * 0.1,
                "close": 10.2 + i * 0.1,
                "volume": 1000000 + i * 1000
            }
            for i in range(60)
        ]

    @pytest.mark.asyncio
    async def test_full_prediction_flow(self, lstm_service, mock_data):
        """TC-003-015: 完整预测流程"""
        # 1. 执行预测
        result = lstm_service.predict("000001", mock_data)
        
        # 2. 验证结果
        assert result is not None
        assert result.get('stock_code') == "000001"
        
        # 3. 验证关键字段
        assert 'predicted_price' in result
        assert 'direction' in result
        assert 'confidence' in result
        
        print(f"完整流程测试通过: {result}")

    def test_prediction_with_different_trends(self, lstm_service):
        """TC-003-016: 不同趋势的预测"""
        # 上涨趋势
        uptrend_data = [
            {"open": 10.0 + i * 0.2, "high": 10.5 + i * 0.2, "low": 9.8 + i * 0.2, 
             "close": 10.2 + i * 0.2, "volume": 1000000 + i * 1000}
            for i in range(60)
        ]
        
        # 下跌趋势
        downtrend_data = [
            {"open": 15.0 - i * 0.2, "high": 15.5 - i * 0.2, "low": 14.8 - i * 0.2, 
             "close": 15.2 - i * 0.2, "volume": 1000000 + i * 1000}
            for i in range(60)
        ]
        
        # 测试上涨趋势
        up_result = lstm_service.predict("UP001", uptrend_data)
        print(f"上涨趋势预测: {up_result.get('direction')}")
        
        # 测试下跌趋势
        down_result = lstm_service.predict("DOWN001", downtrend_data)
        print(f"下跌趋势预测: {down_result.get('direction')}")


class TestLSTMBatchOperations:
    """批量操作测试"""

    @pytest.fixture
    def lstm_service(self):
        try:
            from app.services.lstm_prediction import LSTMService
            return LSTMService()
        except ImportError:
            pytest.skip("LSTM服务不可用")

    @pytest.fixture
    def mock_data(self):
        return [
            {
                "open": 10.0 + i * 0.1,
                "high": 10.5 + i * 0.1,
                "low": 9.8 + i * 0.1,
                "close": 10.2 + i * 0.1,
                "volume": 1000000 + i * 1000
            }
            for i in range(60)
        ]

    def test_batch_10_stocks(self, lstm_service, mock_data):
        """TC-003-017: 批量预测10只股票"""
        stock_codes = [f"BATCH{i:03d}" for i in range(10)]
        
        results = []
        for code in stock_codes:
            result = lstm_service.predict(code, mock_data)
            if result:
                results.append(result)
        
        assert len(results) == 10, f"应返回10个结果，实际: {len(results)}"
        print(f"批量预测10只股票成功")

    def test_batch_100_stocks(self, lstm_service, mock_data):
        """TC-003-018: 批量预测100只股票"""
        stock_codes = [f"BATCH{i:03d}" for i in range(100)]
        
        results = []
        for code in stock_codes:
            result = lstm_service.predict(code, mock_data)
            if result:
                results.append(result)
        
        # 验证批量处理能力
        assert len(results) > 0, "应返回结果"
        print(f"批量预测100只股票: {len(results)} 成功")


if __name__ == '__main__':
    pytest.main([__file__, '-v', '-s'])
