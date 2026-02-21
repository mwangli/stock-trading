"""Debug article content fetching"""
import requests

url = "http://finance.eastmoney.com/a/202602133650195752.html"

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'http://quote.eastmoney.com/'
}

response = requests.get(url, headers=headers, timeout=15)
print(f"Status: {response.status_code}")
print(f"Encoding: {response.encoding}")
print(f"Content length: {len(response.text)}")
print()
print("=== First 2000 chars ===")
print(response.text[:2000])
