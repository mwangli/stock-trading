"""
Data Collection Service
Using direct HTTP requests to EastMoney API
"""
import requests
import json
import re
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
        Get stock news
        """
        try:
            url = "http://news.eastmoney.com/kuaixun.html"
            response = requests.get(url, headers=self.headers, timeout=10)
            # Simplified news
            return []
        except Exception as e:
            logger.error(f"Failed to fetch news: {e}")
            return []


data_collection_service = DataCollectionService()
