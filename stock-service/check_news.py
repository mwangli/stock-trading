"""查看MongoDB新闻数据"""
from app.core.database import get_mongo_collection

collection = get_mongo_collection('news')

# 总数
total = collection.count_documents({})
print(f'=== MongoDB news 表 ===')
print(f'总记录数: {total}\n')

# 最近数据
print('=== 最近5条新闻 ===')
for n in collection.find().sort('pub_time', -1).limit(5):
    title = n.get('title', 'N/A')[:50]
    print(f"  [{n.get('stock_code')}] {n.get('pub_time')}")
    print(f"     {title}")
    print()

# 按股票统计
print('=== 按股票代码统计 ===')
stocks = {}
for n in collection.find():
    code = n.get('stock_code', 'unknown')
    stocks[code] = stocks.get(code, 0) + 1

for code, count in sorted(stocks.items(), key=lambda x: -x[1]):
    print(f'  {code}: {count} 条')
