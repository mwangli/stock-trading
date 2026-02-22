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
