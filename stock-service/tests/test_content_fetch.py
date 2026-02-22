"""
测试新闻内容获取功能
"""
import pytest
from app.services.data_collection import EastMoneyClient


class TestNewsContentFetch:
    """测试新闻内容获取"""

    def setup_method(self):
        """每个测试方法前执行"""
        self.client = EastMoneyClient()

    def test_get_stock_news(self):
        """测试获取股票新闻"""
        news = self.client.get_stock_news('000001')
        assert news is not None
        assert isinstance(news, list)

    def test_fetch_article_content(self):
        """测试获取文章完整内容"""
        # 先获取新闻
        news = self.client.get_stock_news('000001')
        
        if news:
            first_news = news[0]
            url = first_news.get('url')
            
            if url:
                content = self.client.fetch_article_content(url)
                assert isinstance(content, str)

    def test_fetch_news_with_content(self):
        """测试获取带完整内容的新闻"""
        news = self.client.fetch_news_with_content('000001')
        
        if news:
            # 验证新闻结构
            for item in news:
                assert 'title' in item or 'content' in item
