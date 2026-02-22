"""
财经新闻爬虫模块
采集主流财经新闻网站的新闻数据，用于FinBERT模型训练
"""
import random
import hashlib
import re
import json
from datetime import datetime, timedelta
from typing import List, Dict, Optional, Any
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup
from loguru import logger


class BaseFinanceSpider:
    """财经新闻爬虫基类"""
    
    # 常用User-Agent池
    USER_AGENTS = [
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Edge/120.0.0.0",
    ]
    
    def __init__(self):
        self.session = requests.Session()
        self.session.headers.update({
            "User-Agent": random.choice(self.USER_AGENTS),
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
            "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
            "Accept-Encoding": "gzip, deflate",
            "Connection": "keep-alive",
        })
    
    def _get_random_headers(self) -> Dict:
        """获取随机请求头"""
        headers = self.session.headers.copy()
        headers["User-Agent"] = random.choice(self.USER_AGENTS)
        return headers
    
    def _generate_news_id(self, url: str, title: str = "") -> str:
        """生成新闻唯一ID"""
        content = f"{url}_{title}_{datetime.now().date()}"
        return hashlib.md5(content.encode()).hexdigest()[:16]
    
    def _parse_datetime(self, time_str: str) -> Optional[datetime]:
        """解析时间字符串"""
        if not time_str:
            return None
        
        time_str = time_str.strip()
        now = datetime.now()
        
        # 匹配格式: "2024-01-01 10:00:00"
        try:
            return datetime.strptime(time_str, "%Y-%m-%d %H:%M:%S")
        except:
            pass
        
        # 匹配格式: "2024-01-01"
        try:
            return datetime.strptime(time_str, "%Y-%m-%d")
        except:
            pass
        
        # 匹配格式: "10:00:00"
        try:
            time_obj = datetime.strptime(time_str, "%H:%M:%S")
            return datetime(now.year, now.month, now.day, time_obj.hour, time_obj.minute, time_obj.second)
        except:
            pass
        
        # 匹配相对时间: "刚刚", "5分钟前", "1小时前", "昨天"
        if "分钟" in time_str:
            match = re.search(r'(\d+)', time_str)
            if match:
                minutes = int(match.group(1))
                return now - timedelta(minutes=minutes)
        elif "小时" in time_str:
            match = re.search(r'(\d+)', time_str)
            if match:
                hours = int(match.group(1))
                return now - timedelta(hours=hours)
        elif "昨天" in time_str:
            return now - timedelta(days=1)
        elif "天" in time_str:
            match = re.search(r'(\d+)', time_str)
            if match:
                days = int(match.group(1))
                return now - timedelta(days=days)
        
        return None
    
    def _fetch_page(self, url: str, timeout: int = 15) -> Optional[str]:
        """获取页面内容"""
        try:
            response = self.session.get(url, headers=self._get_random_headers(), timeout=timeout)
            response.encoding = 'utf-8'
            return response.text
        except Exception as e:
            logger.debug(f"Failed to fetch {url}: {e}")
            return None
    
    def _extract_content(self, html: str, selectors: List[str] = None) -> str:
        """提取文章正文"""
        if not html:
            return ""
        
        if selectors is None:
            selectors = [
                '.article-content', '.article_body', '.news_content', '.text',
                '#articleContent', '#main_content', '.contentbox', '.txtinfos',
                'article', '.post_content', '.entry-content'
            ]
        
        try:
            soup = BeautifulSoup(html, 'html.parser')
            
            # 移除脚本和样式
            for script in soup(["script", "style", "nav", "header", "footer"]):
                script.decompose()
            
            # 尝试多种选择器
            for selector in selectors:
                elem = soup.select_one(selector)
                if elem and len(elem.get_text(strip=True)) > 200:
                    text = elem.get_text(separator=' ', strip=True)
                    # 清理多余空白
                    text = re.sub(r'\s+', ' ', text)
                    return text[:20000]  # 限制长度
            
            # 如果没找到，返回body内容
            body = soup.find('body')
            if body:
                text = body.get_text(separator=' ', strip=True)
                return re.sub(r'\s+', ' ', text)[:20000]
            
            return ""
        except Exception as e:
            logger.debug(f"Failed to extract content: {e}")
            return ""
    
    def fetch_news(self, limit: int = 50) -> List[Dict[str, Any]]:
        """获取新闻列表（子类实现）"""
        raise NotImplementedError


class SinaFinanceSpider(BaseFinanceSpider):
    """新浪财经爬虫"""
    
    def __init__(self):
        super().__init__()
        self.base_url = "https://finance.sina.com.cn"
    
    def fetch_news(self, limit: int = 50) -> List[Dict[str, Any]]:
        """获取新浪财经新闻"""
        news_list = []
        
        # 财经新闻列表页
        list_urls = [
            "https://finance.sina.com.cn/stock/",
            "https://finance.sina.com.cn/realstock/company/sz000001/nc.shtml",
            "https://finance.sina.com.cn/money/",
            "https://finance.sina.com.cn/tech/",
        ]
        
        for list_url in list_urls:
            if len(news_list) >= limit:
                break
            
            html = self._fetch_page(list_url)
            if not html:
                continue
            
            try:
                soup = BeautifulSoup(html, 'html.parser')
                
                # 查找新闻链接
                for a in soup.find_all('a', href=True):
                    href = a.get('href', '')
                    title = a.get_text(strip=True)
                    
                    # 跳过无效链接
                    if not href or not title or len(title) < 5:
                        continue
                    if '/stock/' not in href and '/money/' not in href:
                        continue
                    if 'javascript' in href.lower():
                        continue
                    
                    # 构建完整URL
                    if not href.startswith('http'):
                        href = urljoin(self.base_url, href)
                    
                    # 跳过非新闻链接
                    if any(x in href for x in ['video', 'photo', 'blog', 'forum', 'topic']):
                        continue
                    
                    news_item = {
                        'news_id': self._generate_news_id(href, title),
                        'title': title,
                        'url': href,
                        'source': '新浪财经',
                        'pub_time': datetime.now(),
                        'create_time': datetime.now(),
                    }
                    
                    if news_item not in news_list:
                        news_list.append(news_item)
                        
            except Exception as e:
                logger.debug(f"Failed to parse {list_url}: {e}")
            
            # 礼貌性延迟
            import time
            time.sleep(0.5)
        
        return news_list[:limit]


class EastMoneySpider(BaseFinanceSpider):
    """东方财富爬虫"""
    
    def __init__(self):
        super().__init__()
        self.base_url = "https://www.eastmoney.com"
    
    def fetch_news(self, limit: int = 50) -> List[Dict[str, Any]]:
        """获取东方财富新闻"""
        news_list = []
        
        # 财经新闻列表页
        list_urls = [
            "https://stock.eastmoney.com/",
            "https://finance.eastmoney.com/",
            "https://news.eastmoney.com/",
        ]
        
        for list_url in list_urls:
            if len(news_list) >= limit:
                break
            
            html = self._fetch_page(list_url)
            if not html:
                continue
            
            try:
                soup = BeautifulSoup(html, 'html.parser')
                
                for a in soup.find_all('a', href=True):
                    href = a.get('href', '')
                    title = a.get_text(strip=True)
                    
                    if not href or not title or len(title) < 5:
                        continue
                    
                    if not href.startswith('http'):
                        href = urljoin(self.base_url, href)
                    
                    if any(x in href for x in ['video', 'photo', 'blog', 'topic', '#']):
                        continue
                    
                    news_item = {
                        'news_id': self._generate_news_id(href, title),
                        'title': title,
                        'url': href,
                        'source': '东方财富',
                        'pub_time': datetime.now(),
                        'create_time': datetime.now(),
                    }
                    
                    if news_item not in news_list:
                        news_list.append(news_item)
                        
            except Exception as e:
                logger.debug(f"Failed to parse {list_url}: {e}")
            
            import time
            time.sleep(0.5)
        
        return news_list[:limit]


class IfengFinanceSpider(BaseFinanceSpider):
    """凤凰网财经爬虫"""
    
    def __init__(self):
        super().__init__()
        self.base_url = "https://finance.ifeng.com"
    
    def fetch_news(self, limit: int = 50) -> List[Dict[str, Any]]:
        """获取凤凰网财经新闻"""
        news_list = []
        
        list_urls = [
            "https://finance.ifeng.com/",
            "https://finance.ifeng.com/stock/",
            "https://finance.ifeng.com/money/",
        ]
        
        for list_url in list_urls:
            if len(news_list) >= limit:
                break
            
            html = self._fetch_page(list_url)
            if not html:
                continue
            
            try:
                soup = BeautifulSoup(html, 'html.parser')
                
                for a in soup.find_all('a', href=True):
                    href = a.get('href', '')
                    title = a.get_text(strip=True)
                    
                    if not href or not title or len(title) < 5:
                        continue
                    
                    if not href.startswith('http'):
                        href = urljoin(self.base_url, href)
                    
                    news_item = {
                        'news_id': self._generate_news_id(href, title),
                        'title': title,
                        'url': href,
                        'source': '凤凰网财经',
                        'pub_time': datetime.now(),
                        'create_time': datetime.now(),
                    }
                    
                    if news_item not in news_list:
                        news_list.append(news_item)
                        
            except Exception as e:
                logger.debug(f"Failed to parse {list_url}: {e}")
            
            import time
            time.sleep(0.5)
        
        return news_list[:limit]


class NetEaseFinanceSpider(BaseFinanceSpider):
    """网易财经爬虫"""
    
    def __init__(self):
        super().__init__()
        self.base_url = "https://money.163.com"
    
    def fetch_news(self, limit: int = 50) -> List[Dict[str, Any]]:
        """获取网易财经新闻"""
        news_list = []
        
        list_urls = [
            "https://money.163.com/",
            "https://money.163.com/stock/",
        ]
        
        for list_url in list_urls:
            if len(news_list) >= limit:
                break
            
            html = self._fetch_page(list_url)
            if not html:
                continue
            
            try:
                soup = BeautifulSoup(html, 'html.parser')
                
                for a in soup.find_all('a', href=True):
                    href = a.get('href', '')
                    title = a.get_text(strip=True)
                    
                    if not href or not title or len(title) < 5:
                        continue
                    
                    if not href.startswith('http'):
                        href = urljoin(self.base_url, href)
                    
                    news_item = {
                        'news_id': self._generate_news_id(href, title),
                        'title': title,
                        'url': href,
                        'source': '网易财经',
                        'pub_time': datetime.now(),
                        'create_time': datetime.now(),
                    }
                    
                    if news_item not in news_list:
                        news_list.append(news_item)
                        
            except Exception as e:
                logger.debug(f"Failed to parse {list_url}: {e}")
            
            import time
            time.sleep(0.5)
        
        return news_list[:limit]


class FinanceNewsSpider:
    """财经新闻爬虫调度器"""
    
    def __init__(self):
        self.spiders: List[BaseFinanceSpider] = [
            SinaFinanceSpider(),
            EastMoneySpider(),
            IfengFinanceSpider(),
            NetEaseFinanceSpider(),
        ]
    
    def fetch_all_news(self, limit_per_spider: int = 30) -> List[Dict[str, Any]]:
        """从所有新闻源采集新闻"""
        all_news = []
        seen_urls = set()  # 用于去重
        
        for spider in self.spiders:
            try:
                logger.info(f"Fetching news from {spider.__class__.__name__}...")
                news_list = spider.fetch_news(limit=limit_per_spider)
                
                for news in news_list:
                    url = news.get('url', '')
                    if url and url not in seen_urls:
                        seen_urls.add(url)
                        all_news.append(news)
                        
                logger.info(f"Got {len(news_list)} news from {spider.__class__.__name__}")
                
            except Exception as e:
                logger.error(f"Error fetching from {spider.__class__.__name__}: {e}")
            
            # 礼貌性延迟
            import time
            time.sleep(1)
        
        return all_news
    
    def save_to_mongodb(self, news_list: List[Dict], collection_name: str = "news") -> int:
        """保存新闻到MongoDB"""
        try:
            from app.core.database import get_mongo_collection
            
            if not news_list:
                return 0
            
            collection = get_mongo_collection(collection_name)
            saved_count = 0
            
            for news in news_list:
                # 检查是否已存在
                if collection.find_one({"url": news.get('url')}):
                    continue
                
                # 尝试获取文章详细内容
                url = news.get('url', '')
                if url:
                    html = requests.get(url, headers={
                        'User-Agent': random.choice(BaseFinanceSpider.USER_AGENTS)
                    }, timeout=15).text
                    
                    content = BaseFinanceSpider()._extract_content(html)
                    if content:
                        news['content'] = content
                    else:
                        news['content'] = news.get('title', '')
                
                # 转换时间格式
                if news.get('pub_time') and isinstance(news['pub_time'], datetime):
                    news['publish_time'] = news['pub_time']
                
                if collection.insert_one(news).inserted_id:
                    saved_count += 1
            
            logger.info(f"Saved {saved_count} news items to MongoDB")
            return saved_count
            
        except Exception as e:
            logger.error(f"Failed to save news to MongoDB: {e}")
            return 0


# 单例实例
finance_news_spider = FinanceNewsSpider()
