"""
Database Configuration
Supports MySQL, MongoDB, and Redis
"""
from sqlalchemy import create_engine, Column, String, Integer, Float, DateTime, BigInteger
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
