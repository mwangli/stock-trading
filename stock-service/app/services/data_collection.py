"""
Data Collection Service
Using direct HTTP requests to EastMoney API and AKShare
"""
import requests
import json
import re
import pandas as pd
from datetime import datetime, timedelta
from typing import List, Optional, Dict, Any
from loguru import logger


class DataCollectionService:
    """Stock data collection service using direct API calls"""

    def __init__(self):
        self.headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
            'Referer': 'http://quote.eastmoney.com/'
        }

    def get_stock_list_simple(self) -> List[Dict[str, Any]]:
        """
        Get simplified A-share stock list (code, name)
        """
        try:
            logger.info("Fetching A-share stock list...")
            url = "http://28.push2.eastmoney.com/api/qt/clist/get"
            params = {
                'pn': 1,
                'pz': 5000,
                'po': 1,
                'np': 1,
                'fltt': 2,
                'invt': 2,
                'fid': 'f3',
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
                        result.append({
                            'code': code,
                            'name': name
                        })
            
            logger.info(f"Fetched {len(result)} stocks")
            return result
        except Exception as e:
            logger.error(f"Failed to fetch stock list: {e}")
            return []

    def get_stock_list(self) -> List[Dict[str, Any]]:
        """
        Get A-share stock list with full info
        """
        return self.get_stock_list_simple()

    def get_historical_data(
        self,
        symbol: str,
        start_date: str,
        end_date: str,
        period: str = "daily",
        adjust: str = ""
    ) -> List[Dict[str, Any]]:
        """
        Get historical K-line data for a stock
        
        Args:
            symbol: Stock code (e.g., '000001')
            start_date: Start date (YYYYMMDD)
            end_date: End date (YYYYMMDD)
            period: daily/weekly/monthly
            adjust: ""/"qfq"/"hfq" for no adjustment/forward/backward
        """
        try:
            logger.info(f"Fetching historical data for {symbol}...")
            
            # Convert date format
            start_str = f"{start_date[:4]}-{start_date[4:6]}-{start_date[6:]}"
            end_str = f"{end_date[:4]}-{end_date[4:6]}-{end_date[6:]}"
            
            # Determine market code
            if symbol.startswith('6'):
                market = '1'
            elif symbol.startswith('0') or symbol.startswith('3'):
                market = '0'
            else:
                market = '0'
            
            # EastMoney API for K-line
            url = f"http://push2his.eastmoney.com/api/qt/stock/kline/get"
            params = {
                'secid': f"{market}.{symbol}",
                'fields1': 'f1,f2,f3,f4,f5,f6',
                'fields2': 'f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61',
                'klt': 101 if period == "daily" else 102 if period == "weekly" else 103,
                'fqt': 0 if adjust == "" else 1 if adjust == "qfq" else 2,
                'beg': start_date,
                'end': end_date,
                'lmt': 1000000
            }
            
            response = requests.get(url, params=params, headers=self.headers, timeout=30)
            data = response.json()
            
            result = []
            if 'data' in data and 'klines' in data['data']:
                for kline in data['data']['klines']:
                    parts = kline.split(',')
                    if len(parts) >= 6:
                        result.append({
                            'date': parts[0],
                            'open': float(parts[1]),
                            'close': float(parts[2]),
                            'high': float(parts[3]),
                            'low': float(parts[4]),
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

    def get_realtime_quote(self, symbol: str) -> Optional[Dict[str, Any]]:
        """
        Get realtime quote for a single stock
        """
        try:
            # Determine market code
            if symbol.startswith('6'):
                market = '1'
            elif symbol.startswith('0') or symbol.startswith('3'):
                market = '0'
            else:
                market = '0'
            
            url = "http://push2.eastmoney.com/api/qt/stock/get"
            params = {
                'secid': f"{market}.{symbol}",
                'fields': 'f43,f44,f45,f46,f47,f48,f50,f51,f52,f55,f57,f58,f59,f60,f116,f117,f162,f167,f168,f169,f170,f171,f173,f177'
            }
            
            response = requests.get(url, params=params, headers=self.headers, timeout=10)
            data = response.json()
            
            if 'data' in data and data['data']:
                item = data['data']
                return {
                    'code': symbol,
                    'name': item.get('f58', ''),
                    'price': item.get('f43', 0) / 1000 if item.get('f43') else 0,
                    'change_pct': item.get('f170', 0) / 100 if item.get('f170') else 0,
                    'volume': item.get('f47', 0),
                    'amount': item.get('f48', 0),
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
        """
        Get realtime quotes for all A-shares or specified symbols
        """
        try:
            logger.info("Fetching realtime quotes...")
            
            if symbols:
                # Get specified stocks
                result = []
                for symbol in symbols:
                    quote = self.get_realtime_quote(symbol)
                    if quote:
                        result.append(quote)
                return result
            else:
                # Get all stocks
                url = "http://push2.eastmoney.com/api/qt/clist/get"
                params = {
                    'pn': 1,
                    'pz': 5000,
                    'po': 1,
                    'np': 1,
                    'fltt': 2,
                    'invt': 2,
                    'fid': 'f3',
                    'fs': 'm:0+t:6,m:0+t:80,m:1+t:2,m:1+t:23',
                    'fields': 'f2,f3,f4,f5,f6,f12,f13,f14,f100,f104,f105,f106'
                }
                
                response = requests.get(url, params=params, headers=self.headers, timeout=30)
                data = response.json()
                
                result = []
                if 'data' in data and 'diff' in data['data']:
                    for item in data['data']['diff']:
                        result.append({
                            'code': item.get('f12', ''),
                            'name': item.get('f14', ''),
                            'price': item.get('f2', ''),
                            'change_pct': item.get('f3', ''),
                            'volume': item.get('f5', ''),
                            'amount': item.get('f6', '')
                        })
                
                logger.info(f"Fetched {len(result)} quotes")
                return result
        except Exception as e:
            logger.error(f"Failed to fetch realtime quotes: {e}")
            return []

    def get_stock_daily(self, symbol: str, date: str) -> Optional[Dict[str, Any]]:
        """
        Get daily K-line data
        """
        return self.get_historical_data(symbol, date, date)

    def get_financial_report(self, symbol: str) -> Dict[str, Any]:
        """
        Get financial report data
        """
        try:
            logger.info(f"Fetching financial report for {symbol}...")
            # Simplified financial data
            return {
                'symbol': symbol,
                'data': []
            }
        except Exception as e:
            logger.error(f"Failed to fetch financial report for {symbol}: {e}")
            return {'symbol': symbol, 'data': []}

    def get_stock_news(self, symbol: Optional[str] = None) -> List[Dict[str, Any]]:
        """
        Get stock news using AKShare - fetches real news data
        
        Args:
            symbol: Stock code (e.g., '000001'). If None, fetches general market news
            
        Returns:
            List of news items with title, content, source, publish_time, url
        """
        try:
            import akshare as ak
            
            if symbol:
                # Fetch news for specific stock
                logger.info(f"Fetching news for stock {symbol}...")
                df = ak.stock_news_em(symbol=symbol)
            else:
                # Fetch general market news
                logger.info("Fetching general market news...")
                df = ak.stock_news_em(symbol='all')
            
            if df is None or df.empty:
                logger.warning(f"No news found for {symbol}")
                return []
            
            # AKShare returns columns: 关键词, 新闻标题, 新闻内容, 发布时间, 新闻来源, 新闻链接
            news_list = []
            for _, row in df.iterrows():
                news_item = {
                    'stock_code': str(row['关键词']) if pd.notna(row.get('关键词')) else '',
                    'title': str(row['新闻标题']) if pd.notna(row.get('新闻标题')) else '',
                    'content': str(row['新闻内容']) if pd.notna(row.get('新闻内容')) else '',
                    'pub_time': str(row['发布时间']) if pd.notna(row.get('发布时间')) else '',
                    'source': str(row['文章来源']) if pd.notna(row.get('文章来源')) else '',
                    'url': str(row['新闻链接']) if pd.notna(row.get('新闻链接')) else '',
                    'news_id': self._generate_news_id_from_row(row),
                    'create_time': datetime.now()
                }
                news_list.append(news_item)
            
            logger.info(f"Fetched {len(news_list)} news items")
            return news_list
            
        except Exception as e:
            logger.error(f"Failed to fetch news: {e}")
            return []
    
    def _generate_news_id_from_row(self, row) -> str:
        """Generate unique news ID from row data using column names"""
        try:
            import hashlib
            content = f"{row.get('关键词', '')}_{row.get('新闻标题', '')}_{row.get('发布时间', '')}"
            return hashlib.md5(content.encode()).hexdigest()[:16]
        except:
            return f"news_{datetime.now().timestamp()}"
    
    def get_market_news(self, limit: int = 30) -> List[Dict[str, Any]]:
        """
        Get general market news (not stock-specific)
        
        Args:
            limit: Maximum number of news items to return
            
        Returns:
            List of market news items
        """
        try:
            import akshare as ak
            
            logger.info(f"Fetching market news (limit={limit})...")
            # stock_news_em with 'all' gets general news
            df = ak.stock_news_em(symbol='all')
            
            if df is None or df.empty:
                logger.warning("No market news found")
                return []
            
            news_list = []
            for idx, row in df.head(limit).iterrows():
                news_item = {
                    'stock_code': str(row['关键词']) if pd.notna(row.get('关键词')) else '',
                    'title': str(row['新闻标题']) if pd.notna(row.get('新闻标题')) else '',
                    'content': str(row['新闻内容']) if pd.notna(row.get('新闻内容')) else '',
                    'pub_time': str(row['发布时间']) if pd.notna(row.get('发布时间')) else '',
                    'source': str(row['文章来源']) if pd.notna(row.get('文章来源')) else '',
                    'url': str(row['新闻链接']) if pd.notna(row.get('新闻链接')) else '',
                    'news_id': self._generate_news_id_from_row(row),
                    'create_time': datetime.now()
                }
                news_list.append(news_item)
            
            logger.info(f"Fetched {len(news_list)} market news items")
            return news_list
            
        except Exception as e:
            logger.error(f"Failed to fetch market news: {e}")
            return []

    def fetch_article_content(self, url: str) -> str:
        """
        Fetch full article content from news URL
        
        Args:
            url: News article URL
            
        Returns:
            Full article content as string
        """
        try:
            if not url:
                return ""
            
            response = requests.get(url, headers=self.headers, timeout=15)
            response.encoding = 'utf-8'
            html = response.text
            
            # Try to parse with BeautifulSoup if available
            try:
                from bs4 import BeautifulSoup
                soup = BeautifulSoup(html, 'html.parser')
                
                # Remove script and style elements
                for script in soup(["script", "style"]):
                    script.decompose()
                
                # Try to find article content - EastMoney specific selectors
                content_elem = None
                
                # Common content selectors for financial news sites
                selectors = [
                    '.contentbox',      # EastMoney
                    '.txtinfos',        # EastMoney  
                    '.news_content',
                    '.article_content',
                    '#articleContent',
                    '#Main_Content_Art',
                    '.zw',              # Article body
                    '.text',            # Text content
                    'article',
                    '.main_content',
                ]
                
                for selector in selectors:
                    content_elem = soup.select_one(selector)
                    if content_elem:
                        text = content_elem.get_text(separator=' ', strip=True)
                        if len(text) > 100:  # Must have substantial content
                            content_elem = content_elem
                            break
                
                # If no specific content found, get text from body
                if not content_elem:
                    body = soup.find('body')
                    if body:
                        content_elem = body
                
                if content_elem:
                    # Get text
                    content = content_elem.get_text(separator=' ', strip=True)
                else:
                    content = ""
                    
            except ImportError:
                # Fallback to regex if BeautifulSoup not available
                import re
                content = re.sub(r'<[^>]+>', ' ', html)
                content = re.sub(r'\s+', ' ', content).strip()
            
            # Clean up
            content = content.replace('&nbsp;', ' ')
            content = content.replace('&amp;', '&')
            content = content.replace('&lt;', '<')
            content = content.replace('&gt;', '>')
            content = content.replace('&quot;', '"')
            
            return content[:10000]  # Limit to 10000 chars
            
        except Exception as e:
            logger.debug(f"Failed to fetch article content from {url}: {e}")
            return ""

    def fetch_news_with_content(self, symbol: str) -> List[Dict[str, Any]]:
        """
        Get stock news with full article content
        
        Args:
            symbol: Stock code
            
        Returns:
            List of news items with full content
        """
        import time
        
        news_list = self.get_stock_news(symbol)
        
        if not news_list:
            return []
        
        # Fetch full content for each news item
        for news in news_list:
            url = news.get('url', '')
            if url:
                full_content = self.fetch_article_content(url)
                # If we got real content, use it; otherwise keep original
                if len(full_content) > 50:  # Threshold for real content
                    news['content'] = full_content
                time.sleep(0.3)  # Rate limiting
        
        return news_list

    def save_news_to_mongodb(self, news_list: List[Dict[str, Any]], collection_name: str = "news", fetch_full_content: bool = False) -> int:
        """
        Save news data to MongoDB
        
        Args:
            news_list: List of news items to save
            collection_name: MongoDB collection name
            fetch_full_content: Whether to fetch full article content from URLs
            
        Returns:
            Number of items saved
        """
        try:
            from app.core.database import get_mongo_collection
            import time
            
            if not news_list:
                return 0
            
            collection = get_mongo_collection(collection_name)
            
            # Use news_id or url as unique identifier to avoid duplicates
            saved_count = 0
            for news in news_list:
                # Check if already exists
                existing = collection.find_one({"news_id": news.get('news_id')})
                if existing:
                    continue
                
                # Fetch full content if requested
                if fetch_full_content and news.get('url'):
                    full_content = self.fetch_article_content(news['url'])
                    if len(full_content) > 50:
                        news['content'] = full_content
                    time.sleep(0.3)
                
                # Convert datetime to string for MongoDB compatibility
                if 'create_time' in news and hasattr(news['create_time'], 'isoformat'):
                    news['create_time'] = news['create_time'].isoformat()
                
                # Parse pub_time to datetime
                if news.get('pub_time'):
                    try:
                        news['publish_time'] = datetime.strptime(news['pub_time'], '%Y-%m-%d %H:%M:%S')
                    except:
                        news['publish_time'] = news['pub_time']
                
                result = collection.insert_one(news)
                if result.inserted_id:
                    saved_count += 1
            
            logger.info(f"Saved {saved_count} news items to MongoDB")
            return saved_count
            
        except Exception as e:
            logger.error(f"Failed to save news to MongoDB: {e}")
            return 0

    def collect_historical_news(
        self, 
        symbols: List[str], 
        months: int = 3,
        collection_name: str = "news"
    ) -> Dict[str, Any]:
        """
        Collect historical news for specified stocks (last N months)
        
        Note: AKShare stock_news_em returns latest 100 news only.
        This method collects available news and filters by date.
        
        Args:
            symbols: List of stock codes
            months: Number of months to look back (default 3)
            collection_name: MongoDB collection name
            
        Returns:
            Summary of collection results
        """
        try:
            from datetime import timedelta
            import time
            
            # Calculate start date
            start_date = datetime.now() - timedelta(days=months * 30)
            
            total_fetched = 0
            total_saved = 0
            failed_symbols = []
            
            logger.info(f"Starting historical news collection for {len(symbols)} stocks (last {months} months)")
            
            for i, symbol in enumerate(symbols):
                try:
                    # Get news for this stock
                    news_list = self.get_stock_news(symbol)
                    
                    if not news_list:
                        failed_symbols.append(symbol)
                        continue
                    
                    total_fetched += len(news_list)
                    
                    # Filter by date
                    filtered_news = []
                    for news in news_list:
                        pub_time_str = news.get('pub_time', '')
                        if pub_time_str:
                            try:
                                pub_date = datetime.strptime(pub_time_str, '%Y-%m-%d %H:%M:%S')
                                if pub_date >= start_date:
                                    filtered_news.append(news)
                            except:
                                # If date parsing fails, include the news
                                filtered_news.append(news)
                    
                    # Save to MongoDB
                    if filtered_news:
                        saved = self.save_news_to_mongodb(filtered_news, collection_name)
                        total_saved += saved
                    
                    # Progress logging
                    if (i + 1) % 10 == 0:
                        logger.info(f"Progress: {i+1}/{len(symbols)} stocks processed")
                    
                    # Rate limiting
                    time.sleep(0.5)
                    
                except Exception as e:
                    logger.error(f"Failed to collect news for {symbol}: {e}")
                    failed_symbols.append(symbol)
                    continue
            
            result = {
                'total_symbols': len(symbols),
                'total_fetched': total_fetched,
                'total_saved': total_saved,
                'failed_count': len(failed_symbols),
                'failed_symbols': failed_symbols[:10]  # Limit to first 10
            }
            
            logger.info(f"Historical news collection completed: {result}")
            return result
            
        except Exception as e:
            logger.error(f"Historical news collection failed: {e}")
            return {
                'total_symbols': len(symbols),
                'total_fetched': 0,
                'total_saved': 0,
                'failed_count': len(symbols),
                'failed_symbols': symbols,
                'error': str(e)
            }


data_collection_service = DataCollectionService()
