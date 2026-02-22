# -*- coding: utf-8 -*-
import sys
import io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8')

"""
独立运行的 FinBERT 情感分析测试
使用 subprocess 隔离 torch 加载，避免 DLL 冲突
"""
import subprocess
import sys

# 运行独立的测试脚本
test_script = '''
# -*- coding: utf-8 -*-
import os
os.environ['CUDA_VISIBLE_DEVICES'] = ''

import sys
sys.path.insert(0, '.')

from app.services.sentiment_analysis import SentimentService

# 测试正面文本
positive_text = "贵州茅台发布最新财报，营收同比增长15%，超出市场预期，股价大涨"
result = SentimentService().analyze(positive_text)

print("RESULT:", result)
print("LABEL:", result.get("label"))
print("SCORE:", result.get("score"))
'''

# 将测试脚本写入临时文件
import tempfile
import os

with tempfile.NamedTemporaryFile(mode='w', suffix='.py', delete=False, encoding='utf-8') as f:
    f.write(test_script)
    temp_file = f.name

try:
    # 运行测试脚本
    result = subprocess.run(
        [sys.executable, temp_file],
        capture_output=True,
        text=True,
        timeout=300,
        cwd=os.path.dirname(os.path.dirname(os.path.abspath(__file__)) or '.')
    )
    
    print("STDOUT:", result.stdout)
    print("STDERR:", result.stderr)
    print("RETURN CODE:", result.returncode)
    
except subprocess.TimeoutExpired:
    print("TEST TIMEOUT - 可能正在下载模型")
except Exception as e:
    print(f"ERROR: {e}")
finally:
    os.unlink(temp_file)
