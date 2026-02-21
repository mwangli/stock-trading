"""查看MongoDB中的完整新闻内容"""
from app.core.database import get_mongo_collection

collection = get_mongo_collection('news')
total = collection.count_documents({})
print(f'Total news: {total}')

for n in collection.find().limit(2):
    content = n.get('content', '')
    print(f"\n标题: {n.get('title')[:50]}")
    print(f"内容长度: {len(content)} 字符")
    print(f"内容预览: {content[:200]}...")
