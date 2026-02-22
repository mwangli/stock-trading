# -*- coding: utf-8 -*-
import sys
import io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8')

"""
模块四: 模型迭代测试 - 完整业务流程测试

测试用例:
- TC-004-010: 收益数据收集
- TC-004-011: 收益统计查询
- TC-004-012: 模型评估
- TC-004-013: 模型训练触发
- TC-004-014: 版本保存
- TC-004-015: 版本查询
- TC-004-016: 版本切换
- TC-004-017: A/B测试

依赖:
- MySQL数据库正常运行
"""

import pytest
import sys
sys.path.insert(0, '.')

from datetime import datetime, timedelta
from app.core.database import (
    MySQLSessionLocal,
    PerformanceRecordModel,
    ModelVersionModel,
    TrainingTaskModel,
    ModelEvaluationModel
)
from app.services.model_iteration import ModelIterationService


class TestPerformanceCollection:
    """收益数据收集测试 TC-004-010"""
    
    def test_collect_performance_data(self):
        """TC-004-010: 收集收益数据"""
        with ModelIterationService() as service:
            result = service.collect_performance_data(days=30)
            
            assert result is not None, "应返回收益统计"
            assert "total_return" in result, "应包含总收益率"
            
            print(f"\n✅ 收益数据收集成功")
            print(f"   总收益率: {result.get('total_return')}")
            print(f"   交易次数: {result.get('total_trades')}")


class TestPerformanceStats:
    """收益统计查询测试 TC-004-011"""
    
    @pytest.fixture
    def setup_data(self):
        """准备测试数据"""
        db = MySQLSessionLocal()
        
        # 清理旧数据
        db.query(PerformanceRecordModel).delete()
        db.commit()
        
        # 添加测试数据 - 过去7天
        for i in range(7):
            record = PerformanceRecordModel(
                trade_date=datetime.now() - timedelta(days=i),
                daily_return=0.01 if i % 2 == 0 else -0.005,
                cumulative_return=0.05,
                win_count=3 if i % 2 == 0 else 0,
                loss_count=0 if i % 2 == 0 else 2,
                total_trades=5,
                max_drawdown=0.02,
                model_version="v1.0"
            )
            db.add(record)
        
        db.commit()
        db.close()
        
        yield
        
        # 清理
        db = MySQLSessionLocal()
        db.query(PerformanceRecordModel).delete()
        db.commit()
        db.close()
    
    def test_get_performance_stats(self, setup_data):
        """TC-004-011: 获取收益统计"""
        with ModelIterationService() as service:
            result = service.get_performance_stats(days=30)
            
            assert result is not None, "应返回收益统计"
            assert "period" in result, "应包含周期"
            assert "win_rate" in result, "应包含胜率"
            assert "total_trades" in result, "应包含交易次数"
            
            print(f"\n✅ 收益统计查询成功")
            print(f"   周期: {result.get('period')}")
            print(f"   胜率: {result.get('win_rate')}")
            print(f"   总交易: {result.get('total_trades')}")
            print(f"   连续亏损天数: {result.get('consecutive_loss_days')}")


class TestModelEvaluation:
    """模型评估测试 TC-004-012"""
    
    @pytest.fixture
    def setup_evaluation_data(self):
        """准备评估测试数据"""
        db = MySQLSessionLocal()
        
        # 清理旧数据
        db.query(PerformanceRecordModel).delete()
        db.query(ModelEvaluationModel).delete()
        db.commit()
        
        # 添加测试数据 - 正面表现
        for i in range(10):
            record = PerformanceRecordModel(
                trade_date=datetime.now() - timedelta(days=i),
                daily_return=0.01,
                cumulative_return=0.10,
                win_count=8,
                loss_count=2,
                total_trades=10,
                max_drawdown=0.05,
                model_version="v1.0"
            )
            db.add(record)
        
        db.commit()
        db.close()
        
        yield
        
        # 清理
        db = MySQLSessionLocal()
        db.query(PerformanceRecordModel).delete()
        db.query(ModelEvaluationModel).delete()
        db.commit()
        db.close()
    
    def test_evaluate_model(self, setup_evaluation_data):
        """TC-004-012: 模型评估"""
        with ModelIterationService() as service:
            result = service.evaluate_model("lstm")
            
            assert result is not None, "应返回评估结果"
            assert result.model_type == "lstm", "模型类型应正确"
            assert result.score is not None, "应包含评分"
            assert result.win_rate is not None, "应包含胜率"
            
            print(f"\n✅ 模型评估成功")
            print(f"   模型类型: {result.model_type}")
            print(f"   综合得分: {result.score}")
            print(f"   胜率: {result.win_rate}")
            print(f"   是否需要重训练: {'是' if result.need_retrain else '否'}")
            print(f"   原因: {result.reason}")


class TestModelTraining:
    """模型训练测试 TC-004-013"""
    
    @pytest.fixture
    def cleanup(self):
        """清理测试数据"""
        yield
        db = MySQLSessionLocal()
        db.query(TrainingTaskModel).delete()
        db.commit()
        db.close()
    
    def test_train_model(self, cleanup):
        """TC-004-013: 触发模型训练"""
        with ModelIterationService() as service:
            # 强制训练
            result = service.train_model("lstm", force=True)
            
            assert result is not None, "应返回训练任务"
            assert result.task_id is not None, "应包含任务ID"
            assert result.model_type == "lstm", "模型类型应正确"
            assert result.status == "PENDING", "状态应为PENDING"
            
            print(f"\n✅ 模型训练触发成功")
            print(f"   任务ID: {result.task_id}")
            print(f"   模型类型: {result.model_type}")
            print(f"   状态: {result.status}")


class TestVersionManagement:
    """版本管理测试 TC-004-014/015/016"""
    
    @pytest.fixture
    def cleanup_versions(self):
        """清理测试数据"""
        yield
        db = MySQLSessionLocal()
        db.query(ModelVersionModel).delete()
        db.commit()
        db.close()
    
    def test_save_and_get_versions(self, cleanup_versions):
        """TC-004-014/015: 版本保存和查询"""
        with ModelIterationService() as service:
            # 保存版本
            v1 = service.save_model_version(
                model_type="lstm",
                version="v1.0",
                file_path="/models/lstm/v1.0/model.h5",
                accuracy=0.85,
                train_params={"epochs": 50, "batch_size": 32}
            )
            
            assert v1 is not None, "应返回版本对象"
            assert v1.version == "v1.0", "版本号应正确"
            assert v1.accuracy == 0.85, "准确率应正确"
            
            # 保存第二个版本
            v2 = service.save_model_version(
                model_type="lstm",
                version="v2.0",
                file_path="/models/lstm/v2.0/model.h5",
                accuracy=0.88,
                train_params={"epochs": 60, "batch_size": 32}
            )
            
            # 查询版本列表
            versions = service.get_versions("lstm")
            assert len(versions) >= 2, "应至少有2个版本"
            
            print(f"\n✅ 版本保存和查询成功")
            print(f"   版本数: {len(versions)}")
            for v in versions[:3]:
                print(f"   - {v.version}: 准确率={v.accuracy}, 活跃={'是' if v.is_active else '否'}")
    
    def test_switch_version(self, cleanup_versions):
        """TC-004-016: 版本切换"""
        with ModelIterationService() as service:
            # 保存版本
            v1 = service.save_model_version(
                model_type="lstm",
                version="v1.0",
                file_path="/models/lstm/v1.0/model.h5",
                accuracy=0.85
            )
            
            v2 = service.save_model_version(
                model_type="lstm",
                version="v2.0",
                file_path="/models/lstm/v2.0/model.h5",
                accuracy=0.88
            )
            
            # 先将v1设为活跃
            service.switch_version("lstm", v1.id)
            
            # 查询活跃版本
            active = service.get_active_version("lstm")
            assert active is not None, "应有活跃版本"
            assert active.version == "v1.0", "v1应为活跃版本"
            
            # 切换到v2
            result = service.switch_version("lstm", v2.id)
            assert result is True, "切换应成功"
            
            # 验证切换成功
            active = service.get_active_version("lstm")
            assert active.version == "v2.0", "v2应为活跃版本"
            
            print(f"\n✅ 版本切换成功")
            print(f"   当前活跃版本: {active.version}")
            print(f"   准确率: {active.accuracy}")


class TestABTest:
    """A/B测试 TC-004-017"""
    
    @pytest.fixture
    def setup_abtest_data(self):
        """准备A/B测试数据"""
        db = MySQLSessionLocal()
        
        # 清理旧数据
        db.query(ModelVersionModel).delete()
        db.commit()
        
        # 保存两个版本
        versions = [
            ModelVersionModel(
                model_type="lstm",
                version="v1.0",
                file_path="/models/lstm/v1.0/model.h5",
                train_date=datetime.now() - timedelta(days=30),
                accuracy=0.80,
                is_active=0
            ),
            ModelVersionModel(
                model_type="lstm",
                version="v2.0",
                file_path="/models/lstm/v2.0/model.h5",
                train_date=datetime.now(),
                accuracy=0.85,
                is_active=1
            )
        ]
        
        for v in versions:
            db.add(v)
        
        db.commit()
        db.close()
        
        yield
        
        # 清理
        db = MySQLSessionLocal()
        db.query(ModelVersionModel).delete()
        db.commit()
        db.close()
    
    def test_ab_test(self, setup_abtest_data):
        """TC-004-017: A/B测试"""
        with ModelIterationService() as service:
            result = service.ab_test("lstm")
            
            assert result is not None, "应返回测试结果"
            assert "status" in result, "应包含状态"
            
            print(f"\n✅ A/B测试执行成功")
            print(f"   状态: {result.get('status')}")
            if result.get("status") == "completed":
                print(f"   旧版本: {result.get('old_version')}")
                print(f"   新版本: {result.get('new_version')}")
                print(f"   提升: {result.get('improvement')}")
                print(f"   建议: {result.get('recommendation')}")


class TestFullBusinessFlow:
    """完整业务流程测试 TC-004-018"""
    
    @pytest.fixture
    def cleanup_all(self):
        """清理所有测试数据"""
        yield
        db = MySQLSessionLocal()
        db.query(PerformanceRecordModel).delete()
        db.query(ModelEvaluationModel).delete()
        db.query(ModelVersionModel).delete()
        db.query(TrainingTaskModel).delete()
        db.commit()
        db.close()
    
    def test_full_flow(self, cleanup_all):
        """TC-004-018: 完整业务流程"""
        with ModelIterationService() as service:
            # 1. 收集收益数据
            print("\n步骤1: 收集收益数据")
            perf = service.collect_performance_data(days=30)
            print(f"   完成")
            
            # 2. 获取收益统计
            print("\n步骤2: 获取收益统计")
            stats = service.get_performance_stats(days=30)
            print(f"   胜率: {stats.get('win_rate')}")
            print(f"   交易数: {stats.get('total_trades')}")
            
            # 3. 模型评估
            print("\n步骤3: 模型评估")
            eval_result = service.evaluate_model("lstm")
            print(f"   得分: {eval_result.score}")
            print(f"   需要重训练: {'是' if eval_result.need_retrain else '否'}")
            
            # 4. 触发训练
            print("\n步骤4: 触发训练")
            task = service.train_model("lstm", force=True)
            print(f"   任务ID: {task.task_id}")
            print(f"   状态: {task.status}")
            
            # 5. 保存版本
            print("\n步骤5: 保存版本")
            version = service.save_model_version(
                model_type="lstm",
                version="v1.0",
                file_path="/models/lstm/v1.0/model.h5",
                accuracy=0.85
            )
            print(f"   版本: {version.version}")
            
            # 6. 查询活跃版本
            print("\n步骤6: 查询活跃版本")
            active = service.get_active_version("lstm")
            print(f"   活跃版本: {active.version if active else '无'}")
            
            print("\n✅ 完整业务流程测试通过!")


if __name__ == '__main__':
    pytest.main([__file__, '-v', '-s'])
