from app.core.database import get_mongo_collection, MySQLSessionLocal, StockSentimentModel

# 检查MongoDB
collection = get_mongo_collection('news')
total = collection.count_documents({})
print(f'MongoDB news collection: {total} documents')

collection2 = get_mongo_collection('stock_prices')
total2 = collection2.count_documents({})
print(f'MongoDB stock_prices collection: {total2} documents')

# 检查MySQL
db = MySQLSessionLocal()
count = db.query(StockSentimentModel).count()
print(f'MySQL stock_sentiment table: {count} records')
db.close()
