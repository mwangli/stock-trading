"""
EastMoney HTTP Client
- EastMoneyClient: 东方财富HTTP API客户端
"""
import requests
import pandas as pd
import re
from datetime import datetime
from typing import List, Optional, Dict, Any
from loguru import logger


# ===============================
# EastMoney HTTP Client
# ===============================
class EastMoneyClient:
    """东方财富HTTP API客户端"""

    def __init__(self):
        self.headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
            'Referer': 'http://quote.eastmoney.com/'
        }

    # -------- 股票列表 --------
    def get_stock_list_simple(self) -> List[Dict[str, Any]]:
        """获取简化版A股列表(code, name)"""
        try:
            url = "http://28.push2.eastmoney.com/api/qt/clist/get"
            params = {
                'pn': 1, 'pz': 5000, 'po': 1, 'np': 1, 'fltt': 2, 'invt': 2, 'fid': 'f3',
                'fs': 'm:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23',
                'fields': 'f1,f2,f3,f4,f5,f6,f12,f13,f14'
            }
            response = requests.get(url, params=params, headers=self.headers, timeout=30)
            data = response.json()
            
            result = []
            if 'data' in data and 'diff' in data['data']:
                for item in data['data']['diff']:
                    code = str(item.get('f12', ''))
                    name = item.get('f14', '')
                    if code:
                        result.append({'code': code, 'name': name})
            
            logger.info(f"Fetched {len(result)} stocks")
            return result
        except Exception as e:
            logger.error(f"Failed to fetch stock list: {e}")
            return []

    def get_stock_list(self) -> List[Dict[str, Any]]:
        """获取完整A股列表"""
        return self.get_stock_list_simple()

    # -------- 历史K线 --------
    def get_historical_data(self, symbol: str, start_date: str, end_date: str,
                           period: str = "daily", adjust: str = "") -> List[Dict[str, Any]]:
        """获取历史K线数据"""
        try:
            market = '1' if symbol.startswith('6') else '0'
            url = "http://push2his.eastmoney.com/api/qt/stock/kline/get"
            params = {
                'secid': f"{market}.{symbol}",
                'fields1': 'f1,f2,f3,f4,f5,f6',
                'fields2': 'f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61',
                'klt': 101 if period == "daily" else 102 if period == "weekly" else 103,
                'fqt': 0 if adjust == "" else 1 if adjust == "qfq" else 2,
                'beg': start_date, 'end': end_date, 'lmt': 1000000
            }
            
            response = requests.get(url, params=params, headers=self.headers, timeout=30)
            data = response.json()
            
            result = []
            if 'data' in data and 'klines' in data['data']:
                for kline in data['data']['klines']:
                    parts = kline.split(',')
                    if len(parts) >= 6:
                        result.append({
                            'date': parts[0], 'open': float(parts[1]), 'close': float(parts[2]),
                            'high': float(parts[3]), 'low': float(parts[4]),
                            'volume': int(parts[5]) if parts[5] else 0,
                            'amount': float(parts[6]) if len(parts) > 6 and parts[6] else 0,
                            'amplitude': float(parts[7]) if len(parts) > 7 and parts[7] else 0,
                            'change_pct': float(parts[8]) if len(parts) > 8 and parts[8] else 0,
                            'change_amount': float(parts[9]) if len(parts) > 9 and parts[9] else 0,
                            'turnover_rate': float(parts[10]) if len(parts) > 10 and parts[10] else 0
                        })
            
            logger.info(f"Fetched {len(result)} records for {symbol}")
            return result
        except Exception as e:
            logger.error(f"Failed to fetch historical data for {symbol}: {e}")
            return []

    def get_stock_daily(self, symbol: str, date: str) -> Optional[Dict[str, Any]]:
        """获取单日K线数据"""
        return self.get_historical_data(symbol, date, date)

    # -------- 实时行情 --------
    def get_realtime_quote(self, symbol: str) -> Optional[Dict[str, Any]]:
        """获取单只股票实时行情"""
        try:
            market = '1' if symbol.startswith('6') else '0'
            url = "http://push2.eastmoney.com/api/qt/stock/get"
            params = {
                'secid': f"{market}.{symbol}",
                'fields': 'f43,f44,f45,f46,f47,f48,f50,f57,f58,f170'
            }
            
            response = requests.get(url, params=params, headers=self.headers, timeout=10)
            data = response.json()
            
            if 'data' in data and data['data']:
                item = data['data']
                return {
                    'code': symbol, 'name': item.get('f58', ''),
                    'price': item.get('f43', 0) / 1000 if item.get('f43') else 0,
                    'change_pct': item.get('f170', 0) / 100 if item.get('f170') else 0,
                    'volume': item.get('f47', 0), 'amount': item.get('f48', 0),
                    'high': item.get('f44', 0) / 1000 if item.get('f44') else 0,
                    'low': item.get('f45', 0) / 1000 if item.get('f45') else 0,
                    'open': item.get('f46', 0) / 1000 if item.get('f46') else 0,
                    'prev_close': item.get('f50', 0) / 1000 if item.get('f50') else 0
                }
            return None
        except Exception as e:
            logger.error(f"Failed to fetch quote for {symbol}: {e}")
            return None

    def get_realtime_quotes(self, symbols: Optional[List[str]] = None) -> List[Dict[str, Any]]:
        """获取实时行情列表"""
        try:
            if symbols:
                return [q for q in [self.get_realtime_quote(s) for s in symbols] if q]
            
            url = "http://push2.eastmoney.com/api/qt/clist/get"
            params = {
                'pn': 1, 'pz': 5000, 'po': 1, 'np': 1, 'fltt': 2, 'invt': 2, 'fid': 'f3',
                'fs': 'm:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23',
                'fields': 'f2,f3,f4,f5,f6,f12,f13,f14'
            }
            
            response = requests.get(url, params=params, headers=self.headers, timeout=30)
            data = response.json()
            
            result = []
            if 'data' in data and 'diff' in data['data']:
                for item in data['data']['diff']:
                    result.append({
                        'code': item.get('f12', ''), 'name': item.get('f14', ''),
                        'price': item.get('f2', ''), 'change_pct': item.get('f3', ''),
                        'volume': item.get('f5', ''), 'amount': item.get('f6', '')
                    })
            
            logger.info(f"Fetched {len(result)} quotes")
            return result
        except Exception as e:
            logger.error(f"Failed to fetch realtime quotes: {e}")
            return []

    # -------- 新闻数据 --------
    def get_stock_news(self, symbol: Optional[str] = None) -> List[Dict[str, Any]]:
        """获取股票新闻"""
        try:
            import akshare as ak
            df = ak.stock_news_em(symbol=symbol) if symbol else ak.stock_news_em(symbol='all')
            
            if df is None or df.empty:
                return []
            
            news_list = []
            for _, row in df.iterrows():
                news_item = {
                    'stock_code': str(row['关键词']) if pd.notna(row.get('关键词')) else '',
                    'title': str(row['新闻标题']) if pd.notna(row.get('新闻标题')) else '',
                    'content': str(row['新闻内容']) if pd.notna(row.get('新闻内容')) else '',
                    'pub_time': str(row['发布时间']) if pd.notna(row.get('发布时间')) else '',
                    'source': str(row['文章来源']) if pd.notna(row.get('文章来源')) else '',
                    'url': str(row['新闻链接']) if pd.notna(row.get('新闻链接')) else '',
                    'news_id': self._generate_news_id(row),
                    'create_time': datetime.now()
                }
                news_list.append(news_item)
            
            return news_list
        except Exception as e:
            logger.error(f"Failed to fetch news: {e}")
            return []

    def _generate_news_id(self, row) -> str:
        """生成新闻唯一ID"""
        try:
            import hashlib
            content = f"{row.get('关键词', '')}_{row.get('新闻标题', '')}_{row.get('发布时间', '')}"
            return hashlib.md5(content.encode()).hexdigest()[:16]
        except:
            return f"news_{datetime.now().timestamp()}"

    def get_market_news(self, limit: int = 30) -> List[Dict[str, Any]]:
        """获取市场新闻"""
        try:
            import akshare as ak
            df = ak.stock_news_em(symbol='all')
            
            if df is None or df.empty:
                return []
            
            return [
                {
                    'stock_code': str(row['关键词']) if pd.notna(row.get('关键词')) else '',
                    'title': str(row['新闻标题']) if pd.notna(row.get('新闻标题')) else '',
                    'content': str(row['新闻内容']) if pd.notna(row.get('新闻内容')) else '',
                    'pub_time': str(row['发布时间']) if pd.notna(row.get('发布时间')) else '',
                    'source': str(row['文章来源']) if pd.notna(row.get('文章来源')) else '',
                    'url': str(row['新闻链接']) if pd.notna(row.get('新闻链接')) else '',
                    'news_id': self._generate_news_id(row),
                    'create_time': datetime.now()
                }
                for _, row in df.head(limit).iterrows()
            ]
        except Exception as e:
            logger.error(f"Failed to fetch market news: {e}")
            return []

    def fetch_article_content(self, url: str) -> str:
        """获取文章完整内容"""
        try:
            if not url:
                return ""
            
            response = requests.get(url, headers=self.headers, timeout=15)
            response.encoding = 'utf-8'
            html = response.text
            
            try:
                from bs4 import BeautifulSoup
                soup = BeautifulSoup(html, 'html.parser')
                
                for script in soup(["script", "style"]):
                    script.decompose()
                
                selectors = ['.contentbox', '.txtinfos', '.news_content', '.article_content',
                           '#articleContent', '#Main_Content_Art', '.zw', '.text', 'article']
                
                content_elem = None
                for selector in selectors:
                    elem = soup.select_one(selector)
                    if elem and len(elem.get_text(strip=True)) > 100:
                        content_elem = elem
                        break
                
                if not content_elem:
                    content_elem = soup.find('body')
                
                content = content_elem.get_text(separator=' ', strip=True) if content_elem else ""
            except ImportError:
                content = re.sub(r'<[^>]+>', ' ', html)
                content = re.sub(r'\s+', ' ', content).strip()
            
            # HTML实体清理
            for old, new in [('&nbsp;', ' '), ('&amp;', '&'), ('&lt;', '<'),
                            ('&gt;', '>'), ('&quot;', '"')]:
                content = content.replace(old, new)
            
            return content[:10000]
        except Exception as e:
            logger.debug(f"Failed to fetch article content: {e}")
            return ""

    def fetch_news_with_content(self, symbol: str) -> List[Dict[str, Any]]:
        """获取带完整内容的新闻"""
        news_list = self.get_stock_news(symbol)
        if not news_list:
            return []
        
        for news in news_list:
            if news.get('url'):
                full_content = self.fetch_article_content(news['url'])
                if len(full_content) > 50:
                    news['content'] = full_content
        
        return news_list

    # -------- 新闻存储 --------
    def save_news_to_mongodb(self, news_list: List[Dict], collection_name: str = "news") -> int:
        """保存新闻到MongoDB"""
        try:
            from app.core.database import get_mongo_collection
            
            if not news_list:
                return 0
            
            collection = get_mongo_collection(collection_name)
            saved_count = 0
            
            for news in news_list:
                if collection.find_one({"news_id": news.get('news_id')}):
                    continue
                
                if 'create_time' in news and hasattr(news['create_time'], 'isoformat'):
                    news['create_time'] = news['create_time'].isoformat()
                
                if news.get('pub_time'):
                    try:
                        news['publish_time'] = datetime.strptime(news['pub_time'], '%Y-%m-%d %H:%M:%S')
                    except:
                        pass
                
                if collection.insert_one(news).inserted_id:
                    saved_count += 1
            
            logger.info(f"Saved {saved_count} news items to MongoDB")
            return saved_count
        except Exception as e:
            logger.error(f"Failed to save news to MongoDB: {e}")
            return 0


# ===============================
# Singleton Instance
# ===============================
east_money_client = EastMoneyClient()
