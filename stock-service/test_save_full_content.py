"""测试保存完整文章内容到MongoDB"""
from app.services.data_collection import DataCollectionService
from app.core.database import get_mongo_collection

service = DataCollectionService()

# 获取新闻并获取完整内容
print("=== 获取新闻并获取完整内容 ===")
news = service.fetch_news_with_content('000001')
print(f"获取到 {len(news)} 条新闻")

if news:
    first = news[0]
    print(f"\n标题: {first['title']}")
    print(f"内容长度: {len(first.get('content', ''))} 字符")
    print(f"内容预览: {first['content'][:300]}...")

# 保存到MongoDB（获取完整内容）
print("\n=== 保存到MongoDB（获取完整内容）===")

# 清理旧数据
collection = get_mongo_collection("news")
collection.delete_many({"stock_code": "000001"})

# 保存带完整内容的新闻
saved = service.save_news_to_mongodb(news, "news", fetch_full_content=True)
print(f"保存了 {saved} 条")

# 验证
print("\n=== 验证MongoDB数据 ===")
db_news = list(collection.find({"stock_code": "000001"}))
print(f"MongoDB中有 {len(db_news)} 条")

for n in db_news[:2]:
    content = n.get('content', '')
    print(f"\n标题: {n.get('title')}")
    print(f"内容长度: {len(content)} 字符")
    print(f"内容预览: {content[:300]}...")
