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
    
    def fetch_news(self, limit: int = 200) -> List[Dict[str, Any]]:
        """获取新浪财经新闻 - 增强版"""
        news_list = []
        
        # 扩展财经新闻列表页 - 更多分类和分页
        list_urls = [
            # 股票频道
            "https://finance.sina.com.cn/stock/",
            "https://finance.sina.com.cn/stock/news.shtml",
            "https://finance.sina.com.cn/stock/xsyx.shtml",
            "https://finance.sina.com.cn/stock/company.shtml",
            # 财经频道
            "https://finance.sina.com.cn/money/",
            "https://finance.sina.com.cn/money/bank.shtml",
            "https://finance.sina.com.cn/money/insurance.shtml",
            "https://finance.sina.com.cn/money/fund.shtml",
            "https://finance.sina.com.cn/money/zhongchou.shtml",
            # 科技频道
            "https://finance.sina.com.cn/tech/",
            "https://finance.sina.com.cn/tech/technews.shtml",
            # 宏观
            "https://finance.sina.com.cn/macro/",
            "https://finance.sina.com.cn/chanyejj/",
            # 新能源
            "https://finance.sina.com.cn/energy/",
            # 医药
            "https://finance.sina.com.cn/health/",
            # 地产
            "https://finance.sina.com.cn/realestate/",
        ]
        
        for list_url in list_urls:
            if len(news_list) >= limit:
                break
            
            html = self._fetch_page(list_url)
            if not html:
                continue
            
            try:
                soup = BeautifulSoup(html, 'html.parser')
                
                # 查找新闻链接 - 更宽松的过滤条件
                for a in soup.find_all('a', href=True):
                    href = a.get('href', '')
                    title = a.get_text(strip=True)
                    
                    # 跳过无效链接
                    if not href or not title or len(title) < 6:
                        continue
                    
                    # 必须是有效的新闻链接
                    if 'javascript' in href.lower():
                        continue
                    if href.startswith('#'):
                        continue
                    
                    # 构建完整URL
                    if not href.startswith('http'):
                        href = urljoin(self.base_url, href)
                    
                    # 跳过非新闻链接
                    if any(x in href for x in ['video', 'photo', 'blog', 'forum', 'topic', 'live', 'photo', 'vip']):
                        continue
                    
                    # 过滤无效域名
                    if 'sina.com.cn' in href and 'news' not in href and 'stock' not in href and 'money' not in href and 'tech' not in href:
                        if 'comment' in href or 'login' in href or 'register' in href:
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
            time.sleep(0.3)
        
        return news_list[:limit]


class EastMoneySpider(BaseFinanceSpider):
    """东方财富爬虫 - 增强版"""
    
    def __init__(self):
        super().__init__()
        self.base_url = "https://www.eastmoney.com"
    
    def fetch_news(self, limit: int = 200) -> List[Dict[str, Any]]:
        """获取东方财富新闻 - 增强版"""
        news_list = []
        
        # 扩展财经新闻列表页
        list_urls = [
            # 股票
            "https://stock.eastmoney.com/",
            "https://stock.eastmoney.com/a/cywjh.html",
            "https://stock.eastmoney.com/a/202001040913145192.html",
            "https://stock.eastmoney.com/a/czqyw.html",
            # 财经
            "https://finance.eastmoney.com/",
            "https://finance.eastmoney.com/a/cjpl.html",
            "https://finance.eastmoney.com/a/202001041234567890.html",
            "https://finance.eastmoney.com/a/dfjwz.html",
            # 新闻
            "https://news.eastmoney.com/",
            "https://news.eastmoney.com/c/cywjh.html",
            "https://news.eastmoney.com/c/gjxw.html",
            "https://news.eastmoney.com/c/gnxw.html",
            # 期货
            "https://quote.eastmoney.com/qh/",
            # 外汇
            "https://quote.eastmoney.com/wh/",
            # 基金
            "https://fund.eastmoney.com/",
            "https://fund.eastmoney.com/news/",
            # 港股
            "https://stock.eastmoney.com/hk/",
            # 美股
            "https://stock.eastmoney.com/uss/",
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
                    
                    if not href or not title or len(title) < 6:
                        continue
                    
                    if not href.startswith('http'):
                        href = urljoin(self.base_url, href)
                    
                    if any(x in href for x in ['video', 'photo', 'blog', 'topic', '#', 'login', 'register']):
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
            time.sleep(0.3)
        
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


class TonghuashunSpider(BaseFinanceSpider):
    """同花顺爬虫 - 10jqka"""
    
    def __init__(self):
        super().__init__()
        self.base_url = "https://www.10jqka.com.cn"
    
    def fetch_news(self, limit: int = 200) -> List[Dict[str, Any]]:
        """获取同花顺财经新闻 - 增强版"""
        news_list = []
        
        # 同花顺财经新闻列表页 - 覆盖多个频道
        list_urls = [
            # 股票首页
            "https://stock.10jqka.com.cn/",
            "https://stock.10jqka.com.cn/news/",
            "https://stock.10jqka.com.cn/ggjy/",
            "https://stock.10jqka.com.cn/xuangu/",
            # 财经
            "https://finance.10jqka.com.cn/",
            "https://finance.10jqka.com.cn/cjzx/",
            # 要闻
            "https://www.10jqka.com.cn/news/headline/",
            "https://www.10jqka.com.cn/news/ggsj/",
            # 沪深
            "https://stockpage.10jqka.com.cn/",
            "https://stockpage.10jqka.com.cn/news/",
            # 港股
            "https://hk.10jqka.com.cn/",
            # 美股
            "https://us.10jqka.com.cn/",
            # 期货
            "https://futures.10jqka.com.cn/",
            # 基金
            "https://fund.10jqka.com.cn/",
            # 观点
            "https://view.10jqka.com.cn/",
            "https://view.10jqka.com.cn/stock/",
            "https://view.10jqka.com.cn/macro/",
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
                    
                    # 跳过无效链接
                    if not href or not title or len(title) < 6:
                        continue
                    
                    # 必须是有效的新闻链接
                    if 'javascript' in href.lower():
                        continue
                    if href.startswith('#'):
                        continue
                    
                    # 构建完整URL
                    if not href.startswith('http'):
                        href = urljoin(self.base_url, href)
                    
                    # 跳过非新闻链接
                    if any(x in href for x in ['video', 'photo', 'blog', 'forum', 'topic', 'live', 'vip', 'quote', 'chart']):
                        continue
                    
                    # 过滤无效域名
                    if '10jqka.com.cn' in href:
                        # 必须是内容页
                        if not any(x in href for x in ['news', 'article', 'stock', 'view', 'finance', 'fund', 'futures']):
                            if 'comment' in href or 'login' in href or 'register' in href:
                                continue
                    
                    news_item = {
                        'news_id': self._generate_news_id(href, title),
                        'title': title,
                        'url': href,
                        'source': '同花顺',
                        'pub_time': datetime.now(),
                        'create_time': datetime.now(),
                    }
                    
                    if news_item not in news_list:
                        news_list.append(news_item)
                        
            except Exception as e:
                logger.debug(f"Failed to parse {list_url}: {e}")
            
            # 礼貌性延迟
            import time
            time.sleep(0.3)
        
        return news_list[:limit]


class XueqiuSpider(BaseFinanceSpider):
    """雪球财经爬虫"""
    
    def __init__(self):
        super().__init__()
        self.base_url = "https://xueqiu.com"
    
    def fetch_news(self, limit: int = 200) -> List[Dict[str, Any]]:
        """获取雪球财经新闻 - 增强版"""
        news_list = []
        
        # 雪球财经新闻列表页
        list_urls = [
            # 首页精选
            "https://xueqiu.com/",
            "https://xueqiu.com/hq",
            "https://xueqiu.com/news/all",
            "https://xueqiu.com/kuaixun",
            # 股票
            "https://xueqiu.com/stock/",
            "https://xueqiu.com/stock/kline",
            # 组合
            "https://xueqiu.com/portfolio",
            # 私募
            "https://xueqiu.com/p",
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
                    
                    # 跳过无效链接
                    if not href or not title or len(title) < 6:
                        continue
                    
                    # 必须是有效的新闻链接
                    if 'javascript' in href.lower():
                        continue
                    if href.startswith('#'):
                        continue
                    
                    # 构建完整URL
                    if not href.startswith('http'):
                        href = urljoin(self.base_url, href)
                    
                    # 雪球文章链接通常包含 /status/
                    if 'xueqiu.com' not in href:
                        continue
                    
                    # 跳过非新闻链接
                    if any(x in href for x in ['video', 'photo', 'vip', 'u/', 'stockchart']):
                        continue
                    
                    news_item = {
                        'news_id': self._generate_news_id(href, title),
                        'title': title,
                        'url': href,
                        'source': '雪球',
                        'pub_time': datetime.now(),
                        'create_time': datetime.now(),
                    }
                    
                    if news_item not in news_list:
                        news_list.append(news_item)
                        
            except Exception as e:
                logger.debug(f"Failed to parse {list_url}: {e}")
            
            # 礼貌性延迟
            import time
            time.sleep(0.3)
        
        return news_list[:limit]


class HexunSpider(BaseFinanceSpider):
    """和讯网财经爬虫"""
    
    def __init__(self):
        super().__init__()
        self.base_url = "https://www.hexun.com"
    
    def fetch_news(self, limit: int = 100) -> List[Dict[str, Any]]:
        """获取和讯网财经新闻"""
        news_list = []
        
        list_urls = [
            "https://www.hexun.com/",
            "https://stock.hexun.com/",
            "https://money.hexun.com/",
            "https://news.hexun.com/",
            "https://fund.hexun.com/",
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
                        'source': '和讯网',
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
            TonghuashunSpider(),
            XueqiuSpider(),
            HexunSpider(),
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
