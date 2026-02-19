"""
Test data for data collection tests
"""

# Mock AKShare stock list response
MOCK_STOCK_LIST = [
    {
        '代码': '000001',
        '名称': '平安银行',
        '最新价': 10.5,
        '涨跌幅': 2.5
    },
    {
        '代码': '000002',
        '名称': '万科A',
        '最新价': 15.2,
        '涨跌幅': -1.2
    },
    {
        '代码': '000003',
        '名称': 'ST股票示例',
        '最新价': 5.0,
        '涨跌幅': 0.0
    },
    {
        '代码': '600000',
        '名称': '浦发银行',
        '最新价': 8.8,
        '涨跌幅': 0.5
    }
]

# Mock AKShare historical data response
MOCK_HISTORICAL_DATA = [
    {
        '日期': '2024-01-15',
        '开盘': 10.0,
        '收盘': 10.5,
        '最高': 10.8,
        '最低': 9.9,
        '成交量': 1000000,
        '成交额': 10500000.0,
        '振幅': 9.0,
        '涨跌幅': 5.0,
        '涨跌额': 0.5,
        '换手率': 2.5
    },
    {
        '日期': '2024-01-16',
        '开盘': 10.5,
        '收盘': 10.2,
        '最高': 10.6,
        '最低': 10.1,
        '成交量': 800000,
        '成交额': 8400000.0,
        '振幅': 4.9,
        '涨跌幅': -2.9,
        '涨跌额': -0.3,
        '换手率': 2.0
    }
]

# Mock AKShare real-time quotes response
MOCK_REALTIME_QUOTES = [
    {
        '代码': '000001',
        '名称': '平安银行',
        '最新价': 10.5,
        '涨跌幅': 2.5,
        '成交量': 1500000
    },
    {
        '代码': '000002',
        '名称': '万科A',
        '最新价': 15.2,
        '涨跌幅': -1.2,
        '成交量': 1200000
    }
]

# Expected transformed stock data for MySQL
EXPECTED_STOCK_DATA = [
    {
        'code': '000001',
        'name': '平安银行',
        'market': 'SZ',
        'is_st': 0,
        'is_tradable': 1,
        'price': 10.5,
        'increase': 2.5
    },
    {
        'code': '000002',
        'name': '万科A',
        'market': 'SZ',
        'is_st': 0,
        'is_tradable': 1,
        'price': 15.2,
        'increase': -1.2
    },
    {
        'code': '600000',
        'name': '浦发银行',
        'market': 'SH',
        'is_st': 0,
        'is_tradable': 1,
        'price': 8.8,
        'increase': 0.5
    }
]

# Expected transformed price data for MongoDB
EXPECTED_PRICE_DATA = [
    {
        'code': '000001',
        'date': '2024-01-15',
        'price1': 10.0,
        'price2': 10.8,
        'price3': 9.9,
        'price4': 10.5,
        'trading_volume': 1000000.0,
        'trading_amount': 10500000.0,
        'amplitude': 9.0,
        'increase_rate': 5.0,
        'change_amount': 0.5,
        'exchange_rate': 2.5
    },
    {
        'code': '000001',
        'date': '2024-01-16',
        'price1': 10.5,
        'price2': 10.6,
        'price3': 10.1,
        'price4': 10.2,
        'trading_volume': 800000.0,
        'trading_amount': 8400000.0,
        'amplitude': 4.9,
        'increase_rate': -2.9,
        'change_amount': -0.3,
        'exchange_rate': 2.0
    }
]
