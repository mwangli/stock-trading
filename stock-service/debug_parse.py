"""Debug article content parsing"""
import requests
from bs4 import BeautifulSoup
import re

url = "http://finance.eastmoney.com/a/202602133650195752.html"

headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': 'http://quote.eastmoney.com/'
}

response = requests.get(url, headers=headers, timeout=15)
response.encoding = 'utf-8'
html = response.text

soup = BeautifulSoup(html, 'html.parser')

# Remove script and style elements
for script in soup(["script", "style"]):
    script.decompose()

print("=== Looking for content elements ===")

# Try to find various content containers
selectors = [
    '.news_content',
    '.article_content', 
    '#articleContent',
    '#Main_Content_Art',
    '.txt',
    'article',
    '.content',
    '.main_content',
    '.body-content',
    '.zw',
]

for selector in selectors:
    elem = soup.select_one(selector)
    if elem:
        text = elem.get_text(separator=' ', strip=True)
        print(f"Found {selector}: {len(text)} chars")
        if len(text) > 100:
            print(f"  Preview: {text[:200]}...")

# Also try looking for paragraphs
print("\n=== Looking for paragraphs ===")
paras = soup.find_all('p')
print(f"Found {len(paras)} <p> tags")

# Look for specific class containing "内容" or "正文"
print("\n=== Looking for content-related classes ===")
for elem in soup.find_all(class_=re.compile(r'content|txt|正文|内容|art'))[:10]:
    print(f"  {elem.name}.{elem.get('class')}: {len(elem.get_text(strip=True))} chars")
