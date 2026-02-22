"""
Stock AI Service - Application Package
导出所有公共接口供外部使用
"""

# 数据采集服务
from app.services.data_collection_service import DataCollectionService, data_collection_service, pd_not_null

# 数据库连接
from app.core.database import (
    MySQLSessionLocal,
    get_db,
    get_mongo_collection,
    get_redis,
    StockInfoModel,
    init_databases,
)

# 客户端
from app.core.eastmoney_client import EastMoneyClient, east_money_client
from app.core.akshare_client import AKShareAPI, akshare_api

__all__ = [
    # 数据采集
    'DataCollectionService',
    'data_collection_service',
    'pd_not_null',
    # 数据库
    'MySQLSessionLocal',
    'get_db',
    'get_mongo_collection',
    'get_redis',
    'StockInfoModel',
    'init_databases',
    # 客户端
    'EastMoneyClient',
    'east_money_client',
    'AKShareAPI',
    'akshare_api',
]
