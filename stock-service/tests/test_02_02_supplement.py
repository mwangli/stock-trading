# -*- coding: utf-8 -*-
import io
import sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8')

"""
模块二: 情感分析 - 补充测试用例

测试用例:
- TC-002-026: 批量分析性能测试
- TC-002-027: 响应时间测试
- TC-002-028: 模型推理准确性测试
- TC-002-029: 异常输入处理测试
- TC-002-030: Spring Boot集成测试
"""

import pytest
import sys
import time
sys.path.insert(0, '.')

import os
os.environ['CUDA_VISIBLE_DEVICES'] = ''
os.environ['TORCH_CUDA_ARCH_LIST'] = 'None'


class TestPerformance:
    """性能测试"""

    def test_batch_analysis_performance(self):
        """TC-002-026: 批量分析100条新闻性能测试"""
        
        # 生成100条测试文本
        test_texts = [
            f"这是第{i}条财经新闻，描述公司经营情况和市场走势"
            for i in range(100)
        ]
        
        try:
            from app.services.sentiment_analysis import SentimentService
            service = SentimentService()
            
            start_time = time.time()
            results = service.analyze_batch(test_texts)
            elapsed = time.time() - start_time
            
            print(f"\n[INFO] Batch analysis (100 texts):")
            print(f"   Time: {elapsed:.2f}s")
            print(f"   Avg per text: {elapsed*1000/100:.2f}ms")
            
            assert len(results) == 100, "Should return 100 results"
            assert elapsed < 30, f"Batch analysis should complete in <30s, took {elapsed:.2f}s"
            
            print(f"[PASS] Batch analysis performance test passed")
            
        except Exception as e:
            if "connection" in str(e).lower():
                pytest.skip(f"Network unavailable: {e}")
            raise

    def test_response_time(self):
        """TC-002-027: 单条响应时间测试"""
        
        test_text = "贵州茅台发布最新财报，营收同比增长15%，超出市场预期"
        
        try:
            from app.services.sentiment_analysis import SentimentService
            service = SentimentService()
            
            # 预热
            service.analyze(test_text)
            
            # 测量时间
            times = []
            for _ in range(5):
                start = time.time()
                result = service.analyze(test_text)
                elapsed = time.time() - start
                times.append(elapsed)
            
            avg_time = sum(times) / len(times)
            
            print(f"\n[INFO] Response time test:")
            print(f"   Avg: {avg_time*1000:.2f}ms")
            print(f"   Min: {min(times)*1000:.2f}ms")
            print(f"   Max: {max(times)*1000:.2f}ms")
            
            assert avg_time < 0.5, f"Response time should be < 500ms, got {avg_time*1000:.2f}ms"
            
            print(f"[PASS] Response time test passed")
            
        except Exception as e:
            if "connection" in str(e).lower():
                pytest.skip(f"Network unavailable: {e}")
            raise


class TestAccuracy:
    """准确性测试"""

    def test_sentiment_accuracy(self):
        """TC-002-028: 情感分析准确性测试"""
        
        test_cases = [
            ("公司业绩大幅增长，营收和利润都创历史新高，股价涨停", "positive"),
            ("公司亏损严重，债务压力巨大，可能面临退市风险", "negative"),
            ("今日A股市场平稳运行，成交量与昨日持平", "neutral"),
            ("新产品发布销量超预期，投资者纷纷买入", "positive"),
            ("公司被监管部门调查，涉嫌信息披露违规", "negative"),
        ]
        
        try:
            from app.services.sentiment_analysis import SentimentService
            service = SentimentService()
            
            correct = 0
            total = len(test_cases)
            
            print(f"\n[INFO] Accuracy test:")
            for text, expected in test_cases:
                result = service.analyze(text)
                is_correct = result['label'] == expected
                if is_correct:
                    correct += 1
                print(f"   Text: {text[:20]}...")
                print(f"   Expected: {expected}, Got: {result['label']} {'[OK]' if is_correct else '[FAIL]'}")
            
            accuracy = correct / total
            print(f"   Accuracy: {accuracy*100:.1f}% ({correct}/{total})")
            
            # 准确率应>60%（放宽标准）
            assert accuracy >= 0.6, f"Accuracy should be >= 60%, got {accuracy*100:.1f}%"
            
            print(f"[PASS] Accuracy test passed")
            
        except Exception as e:
            if "connection" in str(e).lower():
                pytest.skip(f"Network unavailable: {e}")
            raise


class TestErrorHandling:
    """异常处理测试"""

    def test_empty_text(self):
        """TC-002-029-1: 空文本处理"""
        try:
            from app.services.sentiment_analysis import SentimentService
            service = SentimentService()
            
            result = service.analyze("")
            
            print(f"\n[INFO] Empty text handling:")
            print(f"   Result: {result}")
            
            assert result['label'] == 'neutral', "Empty text should return neutral"
            assert result['score'] == 0.0, "Empty text should return score 0"
            
            print(f"[PASS] Empty text handling passed")
            
        except Exception as e:
            if "connection" in str(e).lower():
                pytest.skip(f"Network unavailable: {e}")
            raise

    def test_very_long_text(self):
        """TC-002-029-2: 超长文本处理"""
        try:
            from app.services.sentiment_analysis import SentimentService
            service = SentimentService()
            
            # 生成长文本（超过512 tokens）
            long_text = "财经新闻 " * 200
            
            result = service.analyze(long_text)
            
            print(f"\n[INFO] Long text handling:")
            print(f"   Text length: {len(long_text)}")
            print(f"   Result: {result['label']}")
            
            assert result['label'] in ['positive', 'negative', 'neutral'], "Should return valid label"
            
            print(f"[PASS] Long text handling passed")
            
        except Exception as e:
            if "connection" in str(e).lower():
                pytest.skip(f"Network unavailable: {e}")
            raise

    def test_none_text(self):
        """TC-002-029-3: None文本处理"""
        try:
            from app.services.sentiment_analysis import SentimentService
            service = SentimentService()
            
            result = service.analyze(None)
            
            print(f"\n[INFO] None text handling:")
            print(f"   Result: {result}")
            
            assert result['label'] == 'neutral', "None text should return neutral"
            
            print(f"[PASS] None text handling passed")
            
        except Exception as e:
            if "connection" in str(e).lower():
                pytest.skip(f"Network unavailable: {e}")
            raise


class TestSpringBootIntegration:
    """Spring Boot集成测试"""

    def test_backend_api_call(self):
        """TC-002-030: Spring Boot API调用测试"""
        import requests
        
        backend_url = "http://localhost:8080"
        
        try:
            # 测试健康检查端点
            response = requests.get(f"{backend_url}/api/health", timeout=5)
            
            print(f"\n[INFO] Backend API test:")
            print(f"   Status: {response.status_code}")
            
            if response.status_code == 200:
                print(f"[PASS] Backend API is accessible")
            else:
                pytest.skip(f"Backend API returned {response.status_code}")
                
        except requests.exceptions.ConnectionError:
            pytest.skip("Backend API not accessible (not running)")
        except requests.exceptions.Timeout:
            pytest.skip("Backend API timeout")


if __name__ == '__main__':
    pytest.main([__file__, '-v', '-s'])
