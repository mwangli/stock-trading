"""测试完整新闻内容获取"""
from app.services.data_collection import DataCollectionService

service = DataCollectionService()

# 获取新闻列表
print("=== 获取新闻列表 ===")
news = service.get_stock_news('000001')
print(f"获取到 {len(news)} 条新闻")

if news:
    # 测试获取第一篇文章的完整内容
    first_news = news[0]
    url = first_news['url']
    title = first_news['title']
    
    print(f"\n=== 标题: {title} ===")
    print(f"URL: {url}")
    print()
    
    print("=== 正在获取完整文章内容 ===")
    full_content = service.fetch_article_content(url)
    
    print(f"内容长度: {len(full_content)} 字符")
    print()
    print("=== 完整内容预览 (前1000字) ===")
    print(full_content[:1000])
    print("...")
