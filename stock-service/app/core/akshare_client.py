"""
AKShare API Client
- AKShareAPI: AKShare API包装类
- _install_stubs(): 环境兼容stub安装
"""
import sys
import types
import pandas as pd
from typing import Dict
from loguru import logger


# ===============================
# AKShare Stub (环境兼容)
# ===============================
def _install_stubs():
    """安装stub模块，解决py_mini_racer和curl_cffi无法安装的环境问题"""
    if 'py_mini_racer' not in sys.modules:
        py_mini_racer = types.ModuleType('py_mini_racer')

        class MiniRacer:
            def __init__(self):
                self._context = {}

            def eval(self, js_code: str):
                return None

            def execute(self, js_code: str):
                return None

        py_mini_racer.MiniRacer = MiniRacer
        sys.modules['py_mini_racer'] = py_mini_racer

    if 'curl_cffi' not in sys.modules:
        curl_cffi = types.ModuleType('curl_cffi')

        import requests

        class StubSession:
            def __init__(self, impersonate: str = None):
                self.impersonate = impersonate
                self._session = requests.Session()

            def get(self, url: str, **kwargs):
                return self._session.get(url, **kwargs)

            def post(self, url: str, **kwargs):
                return self._session.post(url, **kwargs)

            def request(self, method: str, url: str, **kwargs):
                return self._session.request(method, url, **kwargs)

        curl_cffi.requests = types.ModuleType('curl_cffi.requests')
        curl_cffi.requests.Session = StubSession
        curl_cffi.__version__ = "0.0.0-stub"
        sys.modules['curl_cffi'] = curl_cffi
        sys.modules['curl_cffi.requests'] = curl_cffi.requests


# ===============================
# AKShare API Wrapper
# ===============================
class AKShareAPI:
    """AKShare API包装类"""

    @staticmethod
    def stock_zh_a_spot_em() -> Dict:
        """获取A股实时行情"""
        import akshare as _ak
        df = _ak.stock_zh_a_spot_em()
        return df.to_dict(orient='records')

    @staticmethod
    def stock_zh_a_hist(symbol: str, start_date: str, end_date: str,
                        period: str = "daily", adjust: str = "") -> Dict:
        """获取A股历史K线数据"""
        import akshare as _ak
        df = _ak.stock_zh_a_hist(
            symbol=symbol, period=period,
            start_date=start_date, end_date=end_date, adjust=adjust
        )
        return df.to_dict(orient='records')

    @staticmethod
    def stock_zh_index_spot_em() -> Dict:
        """获取中国指数实时行情"""
        import akshare as _ak
        df = _ak.stock_zh_index_spot_em()
        return df.to_dict(orient='records')

    @staticmethod
    def stock_info_a_code_name() -> Dict:
        """获取A股股票列表(含代码和名称)"""
        import akshare as _ak
        df = _ak.stock_info_a_code_name()
        return df.to_dict(orient='records')

    @staticmethod
    def fund_etf_hist_sina(symbol: str, period: str = "daily",
                           start_date: str = None, end_date: str = None) -> Dict:
        """获取ETF历史数据"""
        import akshare as _ak
        df = _ak.fund_etf_hist_sina(symbol=symbol, period=period)
        return df.to_dict(orient='records')

    @staticmethod
    def futures_zh_daily_sina(symbol: str) -> Dict:
        """获取期货每日数据"""
        import akshare as _ak
        df = _ak.futures_zh_daily_sina(symbol=symbol)
        return df.to_dict(orient='records')

    @staticmethod
    def currency_latest() -> Dict:
        """获取最新汇率"""
        import akshare as _ak
        df = _ak.currency_latest()
        return df.to_dict(orient='records')


# ===============================
# Singleton Instance
# ===============================
akshare_api = AKShareAPI()
