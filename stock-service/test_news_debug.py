"""测试新闻采集脚本"""
import sys
sys.path.insert(0, '.')

from app.core.spiders.finance_news_spider import finance_news_spider, SinaFinanceSpider, EastMoneySpider

print("=== 测试各爬虫获取的新闻数量 ===\n")

# 测试 SinaFinanceSpider
print("1. SinaFinanceSpider:")
sina = SinaFinanceSpider()
sina_news = sina.fetch_news(limit=100)
print(f"   获取到 {len(sina_news)} 条新闻")

# 测试 EastMoneySpider
print("\n2. EastMoneySpider:")
eastmoney = EastMoneySpider()
em_news = eastmoney.fetch_news(limit=100)
print(f"   获取到 {len(em_news)} 条新闻")

# 测试调度器
print("\n3. FinanceNewsSpider (调度器):")
all_news = finance_news_spider.fetch_all_news(limit_per_spider=50)
print(f"   总共获取到 {len(all_news)} 条新闻")

# 按来源统计
from collections import Counter
sources = Counter([n.get('source', 'Unknown') for n in all_news])
print("\n按来源统计:")
for source, count in sorted(sources.items(), key=lambda x: -x[1]):
    print(f"   {source}: {count}")
