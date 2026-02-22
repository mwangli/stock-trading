"""
AKShare Wrapper with Environment Workarounds

This module provides a workaround for environments where py_mini_racer
and curl_cffi cannot be installed (missing C++ build tools).

It creates stub modules to allow akshare to import and function.
"""
import sys
import types
from typing import Any, Dict, Optional


def _install_stubs():
    """Install stub modules for missing dependencies"""
    if 'py_mini_racer' not in sys.modules:
        py_mini_racer = types.ModuleType('py_mini_racer')

        class MiniRacer:
            """Stub for MiniRacer"""
            def __init__(self):
                self._context = {}

            def eval(self, js_code: str) -> Any:
                """Stub eval - returns None"""
                return None

            def execute(self, js_code: str) -> Any:
                """Stub execute - returns None"""
                return None

        py_mini_racer.MiniRacer = MiniRacer
        sys.modules['py_mini_racer'] = py_mini_racer

    if 'curl_cffi' not in sys.modules:
        curl_cffi = types.ModuleType('curl_cffi')

        # Use standard requests as fallback
        import requests as _requests

        class StubSession:
            """Stub session that uses requests library"""
            def __init__(self, impersonate: str = None):
                self.impersonate = impersonate
                self._session = _requests.Session()

            def get(self, url: str, **kwargs):
                return self._session.get(url, **kwargs)

            def post(self, url: str, **kwargs):
                return self._session.post(url, **kwargs)

            def request(self, method: str, url: str, **kwargs):
                return self._session.request(method, url, **kwargs)

        curl_cffi.requests = types.ModuleType('curl_cffi.requests')
        curl_cffi.requests.Session = StubSession

        # Add version
        curl_cffi.__version__ = "0.0.0-stub"

        sys.modules['curl_cffi'] = curl_cffi
        sys.modules['curl_cffi.requests'] = curl_cffi.requests


# Install stubs before importing akshare
_install_stubs()

# Now import akshare
import akshare as _ak
from akshare import *

# Export akshare with wrapper functions
__version__ = _ak.__version__
__all__ = _ak.__all__


def get_akshare():
    """Get the akshare module instance"""
    return _ak


# Common wrapper functions for HTTP API
class AKShareAPI:
    """Wrapper class for AKShare functions used in HTTP API"""

    @staticmethod
    def stock_zh_a_spot_em() -> Dict:
        """Get A-share real-time market data"""
        df = _ak.stock_zh_a_spot_em()
        return df.to_dict(orient='records')

    @staticmethod
    def stock_zh_a_hist(symbol: str, start_date: str, end_date: str, 
                        period: str = "daily", adjust: str = "") -> Dict:
        """Get A-share historical K-line data"""
        df = _ak.stock_zh_a_hist(
            symbol=symbol,
            period=period,
            start_date=start_date,
            end_date=end_date,
            adjust=adjust
        )
        return df.to_dict(orient='records')

    @staticmethod
    def stock_zh_index_spot_em() -> Dict:
        """Get China index real-time data"""
        df = _ak.stock_zh_index_spot_em()
        return df.to_dict(orient='records')

    @staticmethod
    def stock_info_a_code_name() -> Dict:
        """Get A-share stock list with codes and names"""
        df = _ak.stock_info_a_code_name()
        return df.to_dict(orient='records')

    @staticmethod
    def fund_etf_hist_sina(symbol: str, period: str = "daily",
                           start_date: str = None, end_date: str = None) -> Dict:
        """Get ETF historical data"""
        df = _ak.fund_etf_hist_sina(symbol=symbol, period=period)
        return df.to_dict(orient='records')

    @staticmethod
    def futures_zh_daily_sina(symbol: str) -> Dict:
        """Get futures daily data"""
        df = _ak.futures_zh_daily_sina(symbol=symbol)
        return df.to_dict(orient='records')

    @staticmethod
    def currency_latest() -> Dict:
        """Get latest currency rates"""
        df = _ak.currency_latest()
        return df.to_dict(orient='records')
