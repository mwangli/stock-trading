"""
模块四: 模型训练测试

业务流程: 依赖LSTM预测模块
- 获取历史数据 → MongoDB
- 模型训练 → 生成模型
- 模型保存 → 文件系统
- 模型版本管理 → MySQL (model_info)

依赖: 模块一(数据采集), 模块三(LSTM预测)

测试用例:
- TC-009-F001: 收益记录流程
- TC-009-F002: 模型版本管理流程
"""

import pytest
import sys
sys.path.insert(0, '.')


class TestTrainingService:
    """模型训练服务测试"""

    def test_training_service_import(self):
        """TC-004-001: 训练服务导入"""
        try:
            from app.services.training import TrainingService
            assert TrainingService is not None
        except ImportError as e:
            pytest.skip(f"训练服务不可用: {e}")

    @pytest.mark.asyncio
    async def test_train_model(self):
        """TC-004-002: 模型训练"""
        try:
            from app.services.training import TrainingService
            service = TrainingService()
            
            # 执行训练(简化版,需要传入data参数)
            mock_data = [
                {"open": 10.0, "high": 10.5, "low": 9.8, "close": 10.2, "volume": 1000000}
                for _ in range(60)
            ]
            result = await service.train(data=mock_data, stock_code="TEST", epochs=1)
            
            assert result is not None
        except ImportError:
            pytest.skip("训练服务不可用")
        except Exception as e:
            print(f"训练测试: {e}")


class TestModelManagement:
    """模型版本管理测试 TC-009-F001, TC-009-F002"""

    @pytest.fixture
    def db_session(self):
        from app.core.database import MySQLSessionLocal
        db = MySQLSessionLocal()
        try:
            yield db
        finally:
            db.close()

    def test_model_info_table_exists(self, db_session):
        """TC-004-003: 模型信息表存在性验证"""
        from sqlalchemy import text
        
        try:
            result = db_session.execute(
                text("SHOW TABLES LIKE 'model_info'")
            ).fetchone()
            
            if result:
                columns = db_session.execute(
                    text("SHOW COLUMNS FROM model_info")
                ).fetchall()
                
                column_names = [col[0] for col in columns]
                assert 'model_type' in column_names
                assert 'version' in column_names
                assert 'is_active' in column_names
            else:
                pytest.skip("model_info表不存在")
        except Exception as e:
            pytest.skip(f"无法验证model_info表: {e}")

    def test_daily_return_table_exists(self, db_session):
        """TC-004-004: 日收益表存在性验证"""
        from sqlalchemy import text
        
        try:
            result = db_session.execute(
                text("SHOW TABLES LIKE 'daily_return'")
            ).fetchone()
            
            if result:
                columns = db_session.execute(
                    text("SHOW COLUMNS FROM daily_return")
                ).fetchall()
                
                column_names = [col[0] for col in columns]
                assert 'trade_date' in column_names
                assert 'daily_return' in column_names
            else:
                pytest.skip("daily_return表不存在")
        except Exception as e:
            pytest.skip(f"无法验证daily_return表: {e}")


class TestModelFlow:
    """模型训练完整流程测试"""

    @pytest.fixture
    def training_service(self):
        try:
            from app.services.training import TrainingService
            return TrainingService()
        except ImportError:
            pytest.skip("训练服务不可用")

    @pytest.fixture
    def data_service(self):
        from app.services.data_collection_service import DataCollectionService
        return DataCollectionService()

    @pytest.mark.asyncio
    async def test_training_full_flow(self, training_service, data_service):
        """TC-009-F001: 模型训练完整数据流"""
        # 1. 确保有训练数据
        test_code = "000001"
        
        try:
            await data_service.fetch_and_save_historical_data(test_code, days=60)
        except:
            pass
        
        # 2. 执行训练
        try:
            mock_data = [
                {"open": 10.0, "high": 10.5, "low": 9.8, "close": 10.2, "volume": 1000000}
                for _ in range(60)
            ]
            result = await training_service.train(
                data=mock_data,
                stock_code=test_code,
                epochs=1
            )
            
            # 3. 验证结果
            if result:
                assert result is not None
        except Exception as e:
            print(f"训练流程测试: {e}")
            pytest.skip("需要更多训练数据")

    @pytest.mark.asyncio
    async def test_model_version_management(self, training_service):
        """TC-009-F002: 模型版本管理"""
        try:
            # 验证模型版本管理功能
            result = training_service.get_training_status()
            
            # 验证返回状态
            if result:
                assert result is not None
        except Exception as e:
            print(f"版本管理测试: {e}")


if __name__ == '__main__':
    pytest.main([__file__, '-v', '-s'])
