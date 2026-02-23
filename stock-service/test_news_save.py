"""测试新闻保存到MongoDB"""
import sys
sys.path.insert(0, '.')

from app.core.spiders.finance_news_spider import SinaFinanceSpider
from app.core.database import get_mongo_collection

print("=== 测试新闻保存到MongoDB ===\n")

# 获取新闻
print("1. 获取新闻...")
sina = SinaFinanceSpider()
news_list = sina.fetch_news(limit=20)
print(f"   获取到 {len(news_list)} 条新闻")

# 检查MongoDB
print("\n2. 检查MongoDB...")
collection = get_mongo_collection("news")
db_count = collection.count_documents({})
print(f"   当前MongoDB中有 {db_count} 条新闻")

# 打印前几条现有新闻的URL
print("\n3. MongoDB中现有新闻的URL:")
existing_urls = []
for doc in collection.find().limit(5):
    url = doc.get('url', 'N/A')
    existing_urls.append(url)
    print(f"   - {url[:80]}...")

# 尝试保存新闻
print("\n4. 尝试保存新闻...")
saved_count = 0
skipped_count = 0
for news in news_list:
    url = news.get('url', '')
    # 检查是否已存在
    if collection.find_one({"url": url}):
        skipped_count += 1
        print(f"   跳过(已存在): {url[:60]}...")
        continue
    
    # 保存
    try:
        result = collection.insert_one(news)
        if result.inserted_id:
            saved_count += 1
    except Exception as e:
        print(f"   保存失败: {url[:60]}... 错误: {e}")

print(f"\n   保存成功: {saved_count} 条")
print(f"   跳过(已存在): {skipped_count} 条")

# 最终数量
final_count = collection.count_documents({})
print(f"\n5. 最终MongoDB中有 {final_count} 条新闻")
