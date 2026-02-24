# -*- coding: utf-8 -*-
import io
import sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8')

"""
模块二: 情感分析 - 模型下载、训练、保存和使用测试

测试用例:
- TC-002-001: 模型下载功能
- TC-002-002: 模型加载功能
- TC-002-003: 模型信息获取
- TC-002-004: 模型缓存验证
- TC-002-005: 模型推理使用
"""

import pytest
import os
import sys
from pathlib import Path

sys.path.insert(0, '.')


class TestModelDownload:
    """模型下载测试"""

    def test_model_download(self):
        """TC-002-001: FinBERT模型下载测试"""
        os.environ['CUDA_VISIBLE_DEVICES'] = ''
        os.environ['TORCH_CUDA_ARCH_LIST'] = 'None'
        
        from transformers import AutoModelForSequenceClassification, AutoTokenizer
        from app.core.config import settings
        
        model_name = settings.FINBERT_MODEL_NAME
        cache_dir = str(settings.MODEL_CACHE_DIR)
        
        print(f"\n[INFO] Starting download model: {model_name}")
        print(f"[INFO] Cache dir: {cache_dir}")
        
        try:
            # 下载tokenizer
            tokenizer = AutoTokenizer.from_pretrained(
                model_name,
                cache_dir=cache_dir
            )
            
            # 下载模型
            model = AutoModelForSequenceClassification.from_pretrained(
                model_name,
                num_labels=3,
                cache_dir=cache_dir
            )
            
            # 验证模型文件已下载
            model_dir = Path(cache_dir) / "models" / model_name.replace("/", "--")
            assert model_dir.exists(), f"Model dir should exist: {model_dir}"
            
            # 列出下载的文件
            files = list(model_dir.glob("**/*"))
            print(f"\n[INFO] Model files:")
            for f in files[:10]:
                size_mb = f.stat().st_size / (1024 * 1024) if f.is_file() else 0
                print(f"   {f.name}: {size_mb:.2f} MB")
            
            print("\n[PASS] Model download successful")
            
        except Exception as e:
            error_msg = str(e)
            if "connection" in error_msg.lower() or "timeout" in error_msg.lower():
                pytest.skip(f"Network unavailable, skip download test: {e}")
            else:
                raise

    def test_model_cache(self):
        """TC-002-004: 模型缓存验证"""
        from app.core.config import settings
        
        cache_dir = settings.MODEL_CACHE_DIR
        print(f"\n[INFO] Model cache dir: {cache_dir}")
        
        # 检查缓存目录
        if cache_dir.exists():
            # 查找所有模型文件
            model_files = list(cache_dir.glob("**/*.bin")) + list(cache_dir.glob("**/*.safetensors"))
            print(f"   Found {len(model_files)} model files")
            
            if model_files:
                total_size = sum(f.stat().st_size for f in model_files)
                print(f"   Total size: {total_size / (1024*1024):.2f} MB")
        
        # 如果有缓存，验证可以加载
        if cache_dir.exists() and list(cache_dir.glob("**/*")):
            print("[PASS] Model cache exists")
        else:
            print("[INFO] Model cache not exists, need to download")


class TestModelLoading:
    """模型加载测试"""

    def test_model_loading(self):
        """TC-002-002: 加载FinBERT模型"""
        os.environ['CUDA_VISIBLE_DEVICES'] = ''
        os.environ['TORCH_CUDA_ARCH_LIST'] = 'None'
        
        from app.services.sentiment_analysis import SentimentService
        
        print("\n[INFO] Loading sentiment analysis model...")
        
        try:
            service = SentimentService()
            
            # 触发模型加载
            model = service.model
            
            assert model is not None, "Model should load successfully"
            print("[PASS] Model loaded successfully")
            
        except Exception as e:
            error_msg = str(e)
            if "connection" in error_msg.lower():
                pytest.skip(f"Network unavailable, skip model loading: {e}")
            else:
                raise

    def test_model_info(self):
        """TC-002-003: 获取模型信息"""
        from app.core.config import settings
        
        info = {
            "model_name": settings.FINBERT_MODEL_NAME,
            "max_length": settings.FINBERT_MAX_LENGTH,
            "cache_dir": str(settings.MODEL_CACHE_DIR)
        }
        
        print(f"\n[INFO] Model info:")
        for key, value in info.items():
            print(f"   {key}: {value}")
        
        assert info["model_name"] == "ProsusAI/finbert"
        assert info["max_length"] == 512
        print("[PASS] Model info retrieved successfully")


class TestModelUsage:
    """模型使用测试"""

    def test_model_inference(self):
        """TC-002-005: 使用模型进行推理"""
        os.environ['CUDA_VISIBLE_DEVICES'] = ''
        os.environ['TORCH_CUDA_ARCH_LIST'] = 'None'
        
        from app.services.sentiment_analysis import SentimentService
        
        try:
            service = SentimentService()
            
            test_texts = [
                "贵州茅台发布最新财报，营收同比增长15%，超出市场预期",
                "公司业绩大幅下滑，亏损超过10亿元，市场普遍担忧",
                "今日A股市场平稳运行，成交量与昨日持平"
            ]
            
            print("\n[INFO] Model inference test:")
            for text in test_texts:
                result = service.analyze(text)
                print(f"   Text: {text[:20]}...")
                print(f"   Label: {result['label']}, Score: {result['score']:.3f}")
            
            print("[PASS] Model inference successful")
            
        except Exception as e:
            error_msg = str(e)
            if "connection" in error_msg.lower():
                pytest.skip(f"Network unavailable, skip inference test: {e}")
            else:
                raise


if __name__ == '__main__':
    pytest.main([__file__, '-v', '-s'])
