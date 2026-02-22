"""
Model Iteration Service - V2.0
Python 定时任务 → 训练模型 → 写入MySQL → Java直接查询
"""
import logging
import uuid
from datetime import datetime, timedelta
from typing import Optional, List, Dict, Any
from sqlalchemy.orm import Session
from sqlalchemy import desc

from app.core.database import (
    MySQLSessionLocal,
    PerformanceRecordModel,
    ModelVersionModel,
    TrainingTaskModel,
    ModelEvaluationModel
)

logger = logging.getLogger(__name__)


class ModelIterationService:
    """模型迭代服务 - V2.0架构"""
    
    def __init__(self):
        self.db: Session = MySQLSessionLocal()
    
    def __enter__(self):
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        self.db.close()
    
    # ===============================
    # F-001: 收益监控
    # ===============================
    
    def collect_performance_data(self, days: int = 30) -> Dict[str, Any]:
        """
        收集收益数据 - 从交易记录中汇总
        这里简化处理，实际应该从交易执行模块获取真实交易记录
        """
        logger.info(f"Collecting performance data for last {days} days")
        
        # 简化实现：生成模拟数据
        # 实际应该从 stock_trades 或相关表查询
        today = datetime.now()
        stats = {
            "total_return": 0.0,
            "win_count": 0,
            "loss_count": 0,
            "total_trades": 0,
            "max_drawdown": 0.0,
        }
        
        # TODO: 从实际交易表查询
        # trades = self.db.query(TradeRecord).filter(...).all()
        
        # 保存到 performance_records
        record = PerformanceRecordModel(
            trade_date=today,
            daily_return=stats.get("daily_return"),
            cumulative_return=stats.get("cumulative_return"),
            win_count=stats.get("win_count", 0),
            loss_count=stats.get("loss_count", 0),
            total_trades=stats.get("total_trades", 0),
            max_drawdown=stats.get("max_drawdown"),
            model_version=None
        )
        
        self.db.add(record)
        self.db.commit()
        
        logger.info(f"Performance data collected: {stats}")
        return stats
    
    def get_performance_records(self, model_type: str = None, days: int = 30) -> List[PerformanceRecordModel]:
        """获取收益记录"""
        start_date = datetime.now() - timedelta(days=days)
        query = self.db.query(PerformanceRecordModel).filter(
            PerformanceRecordModel.trade_date >= start_date
        )
        return query.order_by(desc(PerformanceRecordModel.trade_date)).all()
    
    def get_performance_stats(self, days: int = 30) -> Dict[str, Any]:
        """获取收益统计"""
        records = self.get_performance_records(days=days)
        
        if not records:
            return {
                "period": f"{days}d",
                "total_return": 0.0,
                "win_rate": 0.0,
                "max_drawdown": 0.0,
                "total_trades": 0,
                "avg_return": 0.0,
                "consecutive_loss_days": 0
            }
        
        total_trades = sum(r.total_trades for r in records)
        win_count = sum(r.win_count for r in records)
        loss_count = sum(r.loss_count for r in records)
        
        win_rate = win_count / total_trades if total_trades > 0 else 0.0
        avg_return = sum(r.daily_return or 0 for r in records) / len(records)
        max_dd = max((r.max_drawdown or 0 for r in records), default=0.0)
        
        # 计算连续亏损天数
        consecutive_loss = 0
        for r in records:
            if (r.daily_return or 0) < 0:
                consecutive_loss += 1
            else:
                break
        
        return {
            "period": f"{days}d",
            "total_return": sum(r.cumulative_return or 0 for r in records),
            "win_rate": win_rate,
            "max_drawdown": max_dd,
            "total_trades": total_trades,
            "avg_return": avg_return,
            "consecutive_loss_days": consecutive_loss
        }
    
    # ===============================
    # F-002: 模型评估
    # ===============================
    
    def evaluate_model(self, model_type: str) -> ModelEvaluationModel:
        """模型评估 - 判断是否需要重训练"""
        logger.info(f"Evaluating model: {model_type}")
        
        # 获取最近30天收益数据
        records = self.get_performance_records(model_type=model_type, days=30)
        
        if not records:
            logger.warning("No performance records for evaluation")
            result = ModelEvaluationModel(
                model_type=model_type,
                eval_date=datetime.now(),
                period_days=30,
                score=50,
                need_retrain=0,
                reason="No data"
            )
            self.db.add(result)
            self.db.commit()
            return result
        
        # 计算评估指标
        total_trades = sum(r.total_trades for r in records)
        win_count = sum(r.win_count for r in records)
        win_rate = win_count / total_trades if total_trades > 0 else 0.0
        total_return = sum(r.daily_return or 0 for r in records)
        max_dd = max((r.max_drawdown or 0 for r in records), default=0.0)
        
        # 连续亏损天数
        consecutive_loss = 0
        for r in records:
            if (r.daily_return or 0) < 0:
                consecutive_loss += 1
            else:
                break
        
        # 计算综合得分 (0-100)
        score = 50
        if win_rate >= 0.5:
            score += 20
        elif win_rate >= 0.4:
            score += 10
        
        if total_return > 0:
            score += 15
        elif total_return > -0.05:
            score += 5
        
        if max_dd < 0.1:
            score += 15
        elif max_dd < 0.2:
            score += 5
        
        score = min(100, max(0, score))
        
        # 检查触发条件
        need_retrain = 0
        reasons = []
        
        if consecutive_loss >= 10:
            need_retrain = 1
            reasons.append(f"连续{consecutive_loss}天亏损")
        
        if win_rate < 0.4:
            need_retrain = 1
            reasons.append(f"胜率{win_rate:.1%}低于40%")
        
        if score < 50:
            need_retrain = 1
            reasons.append(f"综合得分{score}低于50分")
        
        # 保存评估结果
        result = ModelEvaluationModel(
            model_type=model_type,
            eval_date=datetime.now(),
            period_days=30,
            total_return=total_return,
            win_rate=win_rate,
            max_drawdown=max_dd,
            consecutive_loss_days=consecutive_loss,
            score=score,
            need_retrain=need_retrain,
            reason="; ".join(reasons) if reasons else "正常"
        )
        
        self.db.add(result)
        self.db.commit()
        
        logger.info(f"Evaluation result: score={score}, need_retrain={need_retrain}")
        return result
    
    # ===============================
    # F-003: 模型重训练
    # ===============================
    
    def train_model(self, model_type: str, force: bool = False) -> Optional[TrainingTaskModel]:
        """触发模型训练"""
        logger.info(f"Training model: {model_type}, force={force}")
        
        # 检查是否需要训练
        if not force:
            eval_result = self.evaluate_model(model_type)
            if not eval_result.need_retrain:
                logger.info("Model does not need retraining")
                return None
        
        # 创建训练任务
        task_id = f"train_{model_type}_{datetime.now().strftime('%Y%m%d%H%M%S')}"
        
        task = TrainingTaskModel(
            task_id=task_id,
            model_type=model_type,
            status="PENDING",
            start_time=None,
            end_time=None,
            error_message=None,
            new_version=None
        )
        
        self.db.add(task)
        self.db.commit()
        
        # TODO: 实际执行训练
        # 1. 收集训练数据
        # 2. 训练模型
        # 3. 验证效果
        # 4. 保存模型
        
        logger.info(f"Training task created: {task_id}")
        return task
    
    def get_training_task(self, task_id: str) -> Optional[TrainingTaskModel]:
        """获取训练任务"""
        return self.db.query(TrainingTaskModel).filter(
            TrainingTaskModel.task_id == task_id
        ).first()
    
    def get_training_tasks(self, status: str = None, limit: int = 10) -> List[TrainingTaskModel]:
        """获取训练任务列表"""
        query = self.db.query(TrainingTaskModel)
        if status:
            query = query.filter(TrainingTaskModel.status == status)
        return query.order_by(desc(TrainingTaskModel.create_time)).limit(limit).all()
    
    # ===============================
    # F-004: 版本管理
    # ===============================
    
    def save_model_version(
        self,
        model_type: str,
        version: str,
        file_path: str,
        accuracy: float = None,
        train_params: Dict = None,
        performance_stats: Dict = None
    ) -> ModelVersionModel:
        """保存模型版本"""
        logger.info(f"Saving model version: {model_type} - {version}")
        
        # 检查是否已存在相同版本
        existing = self.db.query(ModelVersionModel).filter(
            ModelVersionModel.model_type == model_type,
            ModelVersionModel.version == version
        ).first()
        
        if existing:
            logger.warning(f"Version {version} already exists for {model_type}")
            return existing
        
        # 创建新版本
        model_version = ModelVersionModel(
            model_type=model_type,
            version=version,
            file_path=file_path,
            train_date=datetime.now(),
            accuracy=accuracy,
            is_active=0,  # 默认非活跃
            train_params=train_params,
            performance_stats=performance_stats
        )
        
        self.db.add(model_version)
        self.db.commit()
        
        logger.info(f"Model version saved: {model_type} - {version}")
        return model_version
    
    def get_versions(self, model_type: str = None, active_only: bool = False) -> List[ModelVersionModel]:
        """获取模型版本列表"""
        query = self.db.query(ModelVersionModel)
        
        if model_type:
            query = query.filter(ModelVersionModel.model_type == model_type)
        
        if active_only:
            query = query.filter(ModelVersionModel.is_active == 1)
        
        return query.order_by(desc(ModelVersionModel.create_time)).all()
    
    def get_active_version(self, model_type: str) -> Optional[ModelVersionModel]:
        """获取当前活跃模型版本"""
        return self.db.query(ModelVersionModel).filter(
            ModelVersionModel.model_type == model_type,
            ModelVersionModel.is_active == 1
        ).first()
    
    def switch_version(self, model_type: str, version_id: int) -> bool:
        """切换模型版本"""
        logger.info(f"Switching model version: {model_type} - {version_id}")
        
        # 查找目标版本
        target_version = self.db.query(ModelVersionModel).filter(
            ModelVersionModel.id == version_id,
            ModelVersionModel.model_type == model_type
        ).first()
        
        if not target_version:
            logger.error(f"Version {version_id} not found for {model_type}")
            return False
        
        # 将所有该类型版本设为非活跃
        self.db.query(ModelVersionModel).filter(
            ModelVersionModel.model_type == model_type
        ).update({"is_active": 0})
        
        # 激活目标版本
        target_version.is_active = 1
        self.db.commit()
        
        logger.info(f"Model version switched to: {target_version.version}")
        return True
    
    # ===============================
    # F-005: A/B测试
    # ===============================
    
    def ab_test(self, model_type: str) -> Dict[str, Any]:
        """
        A/B测试 - 对比新旧模型
        简化实现
        """
        logger.info(f"Running A/B test for {model_type}")
        
        versions = self.get_versions(model_type=model_type)
        
        if len(versions) < 2:
            return {
                "status": "insufficient_data",
                "message": "需要至少2个版本才能进行A/B测试",
                "versions_count": len(versions)
            }
        
        # 获取最近两个版本
        old_version = versions[1]  # 较旧的版本
        new_version = versions[0]  # 最新的版本
        
        # TODO: 实际执行回测对比
        # 简化返回
        result = {
            "status": "completed",
            "old_version": old_version.version,
            "new_version": new_version.version,
            "old_accuracy": old_version.accuracy,
            "new_accuracy": new_version.accuracy,
            "improvement": (new_version.accuracy or 0) - (old_version.accuracy or 0),
            "recommendation": "上线新版本" if new_version.accuracy > old_version.accuracy else "保持旧版本"
        }
        
        return result


# 全局服务实例
model_iteration_service = ModelIterationService()
