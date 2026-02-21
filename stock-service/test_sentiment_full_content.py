"""测试情感分析 - 使用完整的新闻内容"""
from app.services.data_collection import DataCollectionService
from app.services.sentiment import SentimentService
from app.core.database import get_mongo_collection

# 创建服务
data_service = DataCollectionService()
sentiment_service = SentimentService()

print("=== 步骤1: 获取新闻并获取完整内容 ===")
news = data_service.fetch_news_with_content('000001')
print(f"获取到 {len(news)} 条新闻")

if news:
    first = news[0]
    print(f"\n第一条新闻:")
    print(f"  标题: {first['title']}")
    print(f"  内容长度: {len(first.get('content', ''))} 字符")
    print(f"  内容预览: {first['content'][:200]}...")

print("\n=== 步骤2: 情感分析 ===")
sentiments = sentiment_service.analyze_news_batch(news)

print(f"\n情感分析结果:")
for i, s in enumerate(sentiments[:3]):
    title = s.get('title', '')[:40]
    sentiment = s.get('sentiment')
    score = s.get('sentiment_score')
    confidence = s.get('sentiment_confidence')
    print(f"  [{i+1}] {title}...")
    print(f"      情感: {sentiment}, 分数: {score:.4f}, 置信度: {confidence:.4f}")

print("\n=== 步骤3: 市场整体情绪 ===")
market = sentiment_service.get_market_sentiment(sentiments)
print(f"  整体情绪: {market.get('overall')}")
print(f"  综合分数: {market.get('score'):.4f}")
print(f"  正面: {market.get('positive_count')}, 中性: {market.get('neutral_count')}, 负面: {market.get('negative_count')}")
print(f"  总计: {market.get('total_count')}")

print("\n=== 成功! 使用完整新闻内容进行情感分析 ===")
