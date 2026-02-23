"""测试新闻采集脚本 - 快速版"""
import sys
sys.path.insert(0, '.')

from app.core.spiders.finance_news_spider import SinaFinanceSpider, IfengFinanceSpider, NetEaseFinanceSpider

print("=== 测试各爬虫获取的新闻数量 ===\n")

# 测试 SinaFinanceSpider - 快速
print("1. SinaFinanceSpider:")
sina = SinaFinanceSpider()
sina_news = sina.fetch_news(limit=50)
print(f"   获取到 {len(sina_news)} 条新闻")

# 测试 IfengFinanceSpider - 快速
print("\n2. IfengFinanceSpider:")
ifeng = IfengFinanceSpider()
ifeng_news = ifeng.fetch_news(limit=50)
print(f"   获取到 {len(ifeng_news)} 条新闻")

# 测试 NetEaseFinanceSpider - 快速
print("\n3. NetEaseFinanceSpider:")
netease = NetEaseFinanceSpider()
netease_news = netease.fetch_news(limit=50)
print(f"   获取到 {len(netease_news)} 条新闻")

# 总计
total = len(sina_news) + len(ifeng_news) + len(netease_news)
print(f"\n=== 总计: {total} 条新闻 ===")

# 检查MongoDB中已有的新闻数量
print("\n=== 检查MongoDB中的新闻 ===")
try:
    from app.core.database import get_mongo_collection
    collection = get_mongo_collection("news")
    db_count = collection.count_documents({})
    print(f"MongoDB中新闻数量: {db_count}")
except Exception as e:
    print(f"无法连接MongoDB: {e}")
