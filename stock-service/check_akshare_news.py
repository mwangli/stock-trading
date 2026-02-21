"""查看AKShare新闻数据详情"""
import akshare as ak

# 获取新闻
df = ak.stock_news_em(symbol='000001')
print('=== AKShare stock_news_em 返回字段 ===')
print('列名:', list(df.columns))
print()

# 详细查看每列数据
print('=== 第一条新闻详情 ===')
row = df.iloc[0]
for col in df.columns:
    print(f'{col}:')
    print(f'  {row[col]}')
    print()

# 检查是否有其他新闻API
print('=== 尝试其他新闻API ===')
try:
    # 财经新闻
    df2 = ak.stock_news_em(symbol='all')
    print(f'stock_news_em(symbol=all): {len(df2)} 条')
except Exception as e:
    print(f'stock_news_em(symbol=all) error: {e}')
