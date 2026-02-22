"""
Database Configuration
Supports MySQL, MongoDB, and Redis
"""
from sqlalchemy import create_engine, Column, String, Integer, Float, DateTime, BigInteger, JSON
from sqlalchemy.orm import sessionmaker, declarative_base
from pymongo import MongoClient
import redis
from datetime import datetime
from app.core.config import settings

# ===============================
# MySQL Configuration
# ===============================
MYSQL_DATABASE_URL = (
    f"mysql+pymysql://{settings.MYSQL_USER}:{settings.MYSQL_PASSWORD}"
    f"@{settings.MYSQL_HOST}:{settings.MYSQL_PORT}/{settings.MYSQL_DATABASE}"
    f"?charset=utf8mb4"
)

mysql_engine = create_engine(
    MYSQL_DATABASE_URL,
    pool_pre_ping=True,
    pool_recycle=3600,
    pool_size=10,
    max_overflow=20,
    echo=settings.DEBUG
)

MySQLSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=mysql_engine)

Base = declarative_base()


def get_db():
    """Get MySQL database session"""
    db = MySQLSessionLocal()
    try:
        yield db
    finally:
        db.close()


# ===============================
# MongoDB Configuration
# ===============================
class MongoDBClient:
    """MongoDB client singleton"""
    _instance = None
    _client = None

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
            cls._client = MongoClient(settings.MONGODB_URL)
        return cls._instance

    @property
    def client(self):
        return self._client

    @property
    def db(self):
        return self._client[settings.MONGODB_DB]

    def get_collection(self, collection_name: str):
        """Get MongoDB collection"""
        return self.db[collection_name]


def get_mongodb():
    """Get MongoDB database"""
    mongo_client = MongoDBClient()
    return mongo_client.db


def get_mongo_collection(collection_name: str):
    """Get MongoDB collection"""
    mongo_client = MongoDBClient()
    return mongo_client.get_collection(collection_name)


# ===============================
# Redis Configuration
# ===============================
class RedisClient:
    """Redis client singleton"""
    _instance = None
    _client = None

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
            cls._client = redis.Redis(
                host=settings.REDIS_HOST,
                port=settings.REDIS_PORT,
                db=settings.REDIS_DB,
                password=settings.REDIS_PASSWORD or None,
                decode_responses=True
            )
        return cls._instance

    @property
    def client(self):
        return self._client


def get_redis():
    """Get Redis client"""
    redis_client = RedisClient()
    return redis_client.client


# ===============================
# Database Models
# ===============================
class StockInfoModel(Base):
    """Stock information MySQL model"""
    __tablename__ = "stock_info"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    code = Column(String(10), unique=True, nullable=False, index=True)
    name = Column(String(50), nullable=False)
    market = Column(String(2), default=None)
    industry = Column(String(50), default=None)
    listing_date = Column(DateTime, default=None)
    is_st = Column(Integer, default=0)
    is_tradable = Column(Integer, default=1)
    increase = Column(Float, default=None)
    price = Column(Float, default=None)
    predict_price = Column(Float, default=None)
    score = Column(Float, default=None)
    permission = Column(String(10), default="1")
    buy_sale_count = Column(Integer, default=0)
    selected = Column(String(1), default="0")
    deleted = Column(String(1), default="0")
    create_time = Column(DateTime, default=datetime.now)
    update_time = Column(DateTime, default=datetime.now, onupdate=datetime.now)


# ===============================
# V3.0 Database Models (Python writes, Java reads)
# ===============================

class StockSentimentModel(Base):
    """Stock sentiment analysis results - V3.0"""
    __tablename__ = "stock_sentiment"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    stock_code = Column(String(10), nullable=False, index=True)
    stock_name = Column(String(50), default=None)
    sentiment_score = Column(Float, default=None)  # -1 to 1
    positive_ratio = Column(Float, default=None)
    negative_ratio = Column(Float, default=None)
    neutral_ratio = Column(Float, default=None)
    news_count = Column(Integer, default=0)
    source = Column(String(50), default=None)
    analyze_date = Column(DateTime, default=None)
    analyze_time = Column(DateTime, default=datetime.now)
    create_time = Column(DateTime, default=datetime.now)
    update_time = Column(DateTime, default=datetime.now, onupdate=datetime.now)


class NewsSentimentModel(Base):
    """News sentiment details - V3.0"""
    __tablename__ = "news_sentiment"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    stock_code = Column(String(10), nullable=False, index=True)
    news_title = Column(String(255), default=None)
    news_content = Column(String(2000), default=None)
    sentiment_score = Column(Float, default=None)
    sentiment_label = Column(String(10), default=None)  # POSITIVE/NEGATIVE/NEUTRAL
    confidence = Column(Float, default=None)
    news_date = Column(DateTime, default=None)
    source = Column(String(50), default=None)
    create_time = Column(DateTime, default=datetime.now)


class StockPredictionModel(Base):
    """Stock price prediction results - V3.0"""
    __tablename__ = "stock_prediction"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    stock_code = Column(String(10), nullable=False, index=True)
    stock_name = Column(String(50), default=None)
    predict_date = Column(DateTime, nullable=False)
    predict_price = Column(Float, default=None)
    predict_direction = Column(String(10), default=None)  # UP/DOWN/HOLD
    confidence = Column(Float, default=None)
    model_version = Column(String(20), default=None)
    test_deviation = Column(Float, default=None)
    features = Column(JSON, default=None)
    create_time = Column(DateTime, default=datetime.now)
    update_time = Column(DateTime, default=datetime.now, onupdate=datetime.now)


class StockRankingModel(Base):
    """Stock composite ranking - V3.0"""
    __tablename__ = "stock_ranking"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    stock_code = Column(String(10), nullable=False, index=True)
    stock_name = Column(String(50), default=None)
    composite_score = Column(Float, default=None)  # 0-100
    sentiment_score = Column(Float, default=None)
    momentum_score = Column(Float, default=None)
    valuation_score = Column(Float, default=None)
    technical_score = Column(Float, default=None)
    rank_date = Column(DateTime, nullable=False)
    rank_position = Column(Integer, default=None)
    create_time = Column(DateTime, default=datetime.now)
    update_time = Column(DateTime, default=datetime.now, onupdate=datetime.now)


class TaskExecutionLogModel(Base):
    """Task execution log - V3.0"""
    __tablename__ = "task_execution_log"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    task_name = Column(String(100), nullable=False, index=True)
    task_type = Column(String(50), default=None)
    module_id = Column(String(20), default=None, index=True)
    status = Column(String(20), default=None, index=True)  # STARTED/SUCCESS/FAILED
    start_time = Column(DateTime, default=None)
    end_time = Column(DateTime, default=None)
    duration = Column(Integer, default=None)  # milliseconds
    records_processed = Column(Integer, default=0)
    error_message = Column(String(2000), default=None)
    execute_info = Column(JSON, default=None)
    create_time = Column(DateTime, default=datetime.now)


# ===============================
# Model Iteration Tables (V2.0)
# ===============================

class PerformanceRecordModel(Base):
    """Model performance records - tracks trading performance"""
    __tablename__ = "performance_records"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    trade_date = Column(DateTime, nullable=False, index=True)
    daily_return = Column(Float, default=None)  # 日收益率
    cumulative_return = Column(Float, default=None)  # 累计收益率
    win_count = Column(Integer, default=0)  # 盈利次数
    loss_count = Column(Integer, default=0)  # 亏损次数
    total_trades = Column(Integer, default=0)  # 总交易次数
    max_drawdown = Column(Float, default=None)  # 最大回撤
    model_version = Column(String(50), default=None)  # 模型版本
    create_time = Column(DateTime, default=datetime.now)
    update_time = Column(DateTime, default=datetime.now, onupdate=datetime.now)


class ModelVersionModel(Base):
    """Model version management"""
    __tablename__ = "model_versions"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    model_type = Column(String(50), nullable=False, index=True)  # LSTM/FinBERT
    version = Column(String(50), nullable=False)  # 版本号
    file_path = Column(String(255), nullable=False)  # 模型文件路径
    train_date = Column(DateTime, nullable=False)  # 训练日期
    accuracy = Column(Float, default=None)  # 准确率
    is_active = Column(Integer, default=0)  # 是否当前活跃
    train_params = Column(JSON, default=None)  # 训练参数
    performance_stats = Column(JSON, default=None)  # 表现统计
    create_time = Column(DateTime, default=datetime.now)


class TrainingTaskModel(Base):
    """Training task records"""
    __tablename__ = "training_tasks"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    task_id = Column(String(100), nullable=False, unique=True, index=True)  # 任务ID
    model_type = Column(String(50), nullable=False)  # 模型类型
    status = Column(String(20), nullable=False, index=True)  # PENDING/RUNNING/COMPLETED/FAILED
    start_time = Column(DateTime, default=None)
    end_time = Column(DateTime, default=None)
    error_message = Column(String(2000), default=None)
    new_version = Column(String(50), default=None)
    create_time = Column(DateTime, default=datetime.now)


class ModelEvaluationModel(Base):
    """Model evaluation results"""
    __tablename__ = "model_evaluation_results"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    model_type = Column(String(50), nullable=False, index=True)
    eval_date = Column(DateTime, nullable=False, index=True)
    period_days = Column(Integer, default=30)  # 评估周期天数
    total_return = Column(Float, default=None)  # 总收益率
    win_rate = Column(Float, default=None)  # 胜率
    max_drawdown = Column(Float, default=None)  # 最大回撤
    consecutive_loss_days = Column(Integer, default=0)  # 连续亏损天数
    score = Column(Integer, default=0)  # 综合得分
    need_retrain = Column(Integer, default=0)  # 是否需要重训练
    reason = Column(String(255), default=None)  # 触发原因
    create_time = Column(DateTime, default=datetime.now)



# Create MySQL tables
def init_mysql_tables():
    """Initialize MySQL tables"""
    Base.metadata.create_all(bind=mysql_engine)


# Initialize MongoDB indexes
def init_mongodb_indexes():
    """Initialize MongoDB indexes"""
    mongo_client = MongoDBClient()
    
    # Stock prices collection indexes
    prices_collection = mongo_client.get_collection("stock_prices")
    prices_collection.create_index([("code", 1), ("date", 1)], unique=True)
    prices_collection.create_index([("code", 1), ("date", -1)])
    prices_collection.create_index([("code", 1)])
    prices_collection.create_index([("date", -1)])
    
    # News collection indexes
    news_collection = mongo_client.get_collection("news")
    news_collection.create_index([("news_id", 1)], unique=True)
    news_collection.create_index([("stock_code", 1)])
    news_collection.create_index([("publish_time", -1)])
    news_collection.create_index([("url", 1)], unique=True)


# Initialize all databases
def init_databases():
    """Initialize all databases"""
    init_mysql_tables()
    init_mongodb_indexes()
