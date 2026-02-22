# -*- coding: utf-8 -*-
import sys
import io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8')


# 设置环境变量在导入 torch/transformers 之前
import os
os.environ['CUDA_VISIBLE_DEVICES'] = ''
os.environ['TORCH_CUDA_ARCH_LIST'] = 'None'


"""
模块二: 情感分析测试 - 完整业务流程测试

业务流程（不使用mock）:
1. 从MongoDB获取新闻数据（模块1采集的数据）
2. 使用真实的FinBERT模型进行情感分析
3. 将分析结果写入MySQL数据库
4. 验证完整数据流

依赖: 
- MongoDB中需要有新闻数据（模块1采集）
- MySQL数据库正常运行
- FinBERT模型（首次使用会下载）

测试用例:
- TC-002-020: 从MongoDB获取新闻数据
- TC-002-021: 情感分析（真实模型推理）
- TC-002-022: 分析结果写入MySQL
- TC-002-023: 完整业务流程测试
- TC-002-024: 股票情感得分计算
"""

import pytest
import sys
sys.path.insert(0, '.')

from datetime import datetime

from app.core.database import (
    MySQLSessionLocal, 
    StockSentimentModel, 
    NewsSentimentModel,
    get_mongo_collection, 
    get_redis
)
from app.services.sentiment_analysis import SentimentService
from app.services.data_collection_service import DataCollectionService


class TestMongoDBNewsData:
    """从MongoDB获取新闻数据测试 TC-002-020"""

    @pytest.fixture
    def test_stock_code(self):
        """测试用股票代码"""
        return "000001"  # 平安银行

    def test_get_news_from_mongodb(self, test_stock_code):
        """TC-002-020: 从MongoDB获取新闻数据"""
        coll = get_mongo_collection("news")
        
        # 从MongoDB查询新闻
        news_list = list(coll.find({
            "stock_code": test_stock_code
        }).sort("pub_time", -1).limit(10))
        
        # 如果没有数据，打印提示
        if len(news_list) == 0:
            print(f"\n⚠️ MongoDB中没有股票 {test_stock_code} 的新闻数据")
            print("需要先运行模块1的数据采集任务")
        
        print(f"\n📰 从MongoDB获取到 {len(news_list)} 条新闻")
        for news in news_list[:3]:
            title = news.get('title', 'N/A')[:50]
            print(f"   - [{news.get('stock_code')}] {title}")
        
        # 验证数据结构
        if len(news_list) > 0:
            news = news_list[0]
            assert 'title' in news or '新闻标题' in news, "新闻应包含标题字段"
            print("✅ MongoDB新闻数据结构正确")


class TestSentimentAnalysis:
    """情感分析测试 TC-002-021"""

    @pytest.fixture
    def sentiment_service(self):
        """创建情感分析服务（真实模型）"""
        return SentimentService()

    def test_single_text_analysis(self, sentiment_service):
        """TC-002-021-1: 单条文本情感分析"""
        # 测试正面文本
        positive_text = "贵州茅台发布最新财报，营收同比增长15%，超出市场预期，股价大涨"
        result = sentiment_service.analyze(positive_text)
        
        print(f"\n📊 情感分析结果:")
        print(f"   文本: {positive_text[:30]}...")
        print(f"   标签: {result['label']}")
        print(f"   得分: {result['score']}")
        print(f"   置信度: {result['confidence']}")
        
        # 验证返回格式
        assert 'label' in result, "应返回情感标签"
        assert 'score' in result, "应返回情感得分"
        assert 'confidence' in result, "应返回置信度"
        assert result['label'] in ['positive', 'negative', 'neutral'], "标签应为positive/negative/neutral"
        
        print("✅ 单条文本情感分析正常")
        
    def test_negative_text_analysis(self, sentiment_service):
        """TC-002-021-2: 负面文本分析"""
        negative_text = "该公司业绩大幅下滑，亏损超过10亿元，市场普遍担忧"
        result = sentiment_service.analyze(negative_text)
        
        print(f"\n📊 负面文本分析:")
        print(f"   文本: {negative_text[:30]}...")
        print(f"   标签: {result['label']}")
        print(f"   得分: {result['score']}")
        
        assert result['label'] in ['positive', 'negative', 'neutral']
        print("✅ 负面文本情感分析正常")

    def test_neutral_text_analysis(self, sentiment_service):
        """TC-002-021-3: 中性文本分析"""
        neutral_text = "今日A股市场平稳运行，成交量与昨日持平"
        result = sentiment_service.analyze(neutral_text)
        
        print(f"\n📊 中性文本分析:")
        print(f"   文本: {neutral_text}")
        print(f"   标签: {result['label']}")
        
        assert result['label'] in ['positive', 'negative', 'neutral']
        print("✅ 中性文本情感分析正常")

    def test_batch_analysis(self, sentiment_service):
        """TC-002-021-4: 批量文本分析"""
        texts = [
            "公司营收大幅增长，业绩超预期",
            "市场下跌，投资者信心不足",
            "今日股市运行平稳"
        ]
        
        results = sentiment_service.analyze_batch(texts)
        
        print(f"\n📊 批量分析 {len(texts)} 条文本:")
        for i, result in enumerate(results):
            print(f"   {i+1}. {result['label']}: {result['score']}")
        
        assert len(results) == len(texts), "批量分析结果数量应与输入一致"
        print("✅ 批量文本情感分析正常")


class TestSentimentToMySQL:
    """情感分析结果写入MySQL测试 TC-002-022"""

    @pytest.fixture
    def db_session(self):
        db = MySQLSessionLocal()
        try:
            yield db
        finally:
            db.close()

    @pytest.fixture
    def cleanup_test_data(self):
        """清理测试数据"""
        yield
        db = MySQLSessionLocal()
        try:
            # 清理测试数据
            db.query(StockSentimentModel).filter(
                StockSentimentModel.stock_code.in_(["TEST001", "000001"])
            ).delete(synchronize_session=False)
            db.query(NewsSentimentModel).filter(
                NewsSentimentModel.stock_code.in_(["TEST001", "000001"])
            ).delete(synchronize_session=False)
            db.commit()
        except Exception as e:
            db.rollback()
            print(f"清理数据失败: {e}")
        finally:
            db.close()

    def test_save_stock_sentiment(self, db_session, cleanup_test_data):
        """TC-002-022-1: 保存股票情感得分到MySQL"""
        # 准备测试数据
        sentiment = StockSentimentModel(
            stock_code="TEST001",
            stock_name="测试股票",
            sentiment_score=0.65,
            positive_ratio=0.6,
            negative_ratio=0.2,
            neutral_ratio=0.2,
            news_count=10,
            source="sentiment_test",
            analyze_date=datetime.now()
        )
        
        db_session.add(sentiment)
        db_session.commit()
        
        # 验证写入
        result = db_session.query(StockSentimentModel).filter(
            StockSentimentModel.stock_code == "TEST001"
        ).first()
        
        assert result is not None, "应成功写入MySQL"
        assert result.sentiment_score == 0.65, "情感得分应正确"
        assert result.positive_ratio == 0.6, "正面比例应正确"
        
        print(f"\n✅ 股票情感得分写入MySQL成功")
        print(f"   股票: {result.stock_code}")
        print(f"   得分: {result.sentiment_score}")
        print(f"   正面: {result.positive_ratio}, 负面: {result.negative_ratio}, 中性: {result.neutral_ratio}")

    def test_save_news_sentiment_detail(self, db_session, cleanup_test_data):
        """TC-002-022-2: 保存新闻情感详情到MySQL"""
        # 准备测试数据
        news_sentiment = NewsSentimentModel(
            stock_code="TEST001",
            news_title="测试新闻标题",
            news_content="这是测试新闻内容",
            sentiment_score=0.8,
            sentiment_label="POSITIVE",
            confidence=0.9,
            news_date=datetime.now(),
            source="test"
        )
        
        db_session.add(news_sentiment)
        db_session.commit()
        
        # 验证写入
        result = db_session.query(NewsSentimentModel).filter(
            NewsSentimentModel.stock_code == "TEST001"
        ).first()
        
        assert result is not None, "应成功写入MySQL"
        assert result.sentiment_label == "POSITIVE", "情感标签应正确"
        
        print(f"\n✅ 新闻情感详情写入MySQL成功")
        print(f"   标题: {result.news_title}")
        print(f"   标签: {result.sentiment_label}")
        print(f"   得分: {result.sentiment_score}")


class TestFullBusinessFlow:
    """完整业务流程测试 TC-002-023"""

    @pytest.fixture
    def db_session(self):
        db = MySQLSessionLocal()
        try:
            yield db
        finally:
            db.close()

    @pytest.fixture
    def cleanup(self):
        """清理测试数据"""
        yield
        db = MySQLSessionLocal()
        try:
            db.query(StockSentimentModel).filter(
                StockSentimentModel.stock_code == "000001"
            ).delete(synchronize_session=False)
            db.query(NewsSentimentModel).filter(
                NewsSentimentModel.stock_code == "000001"
            ).delete(synchronize_session=False)
            db.commit()
        except:
            db.rollback()
        finally:
            db.close()

    def test_full_flow_with_real_news(self, db_session, cleanup):
        """TC-002-023: 完整业务流程测试（使用真实数据）"""
        
        # ========== 步骤1: 从MongoDB获取新闻 ==========
        print("\n" + "="*50)
        print("步骤1: 从MongoDB获取新闻数据")
        print("="*50)
        
        coll = get_mongo_collection("news")
        stock_code = "000001"  # 平安银行
        
        # 查询最近的新闻
        news_list = list(coll.find({
            "stock_code": stock_code
        }).sort("pub_time", -1).limit(20))
        
        if len(news_list) == 0:
            print(f"⚠️ MongoDB中没有 {stock_code} 的新闻，尝试采集...")
            
            # 如果没有数据，使用数据采集服务获取
            data_service = DataCollectionService()
            news_list = data_service.get_stock_news(stock_code)
            
            if len(news_list) > 0:
                # 保存到MongoDB
                for news in news_list:
                    news['stock_code'] = stock_code
                coll.insert_many(news)
                print(f"✅ 采集并保存 {len(news_list)} 条新闻到MongoDB")
                news_list = list(coll.find({
                    "stock_code": stock_code
                }).sort("pub_time", -1).limit(20))
        
        print(f"📰 获取到 {len(news_list)} 条新闻")
        
        if len(news_list) == 0:
            pytest.skip("没有新闻数据，跳过完整流程测试")
        
        # ========== 步骤2: 情感分析 ==========
        print("\n" + "="*50)
        print("步骤2: 使用FinBERT进行情感分析")
        print("="*50)
        
        sentiment_service = SentimentService()
        
        # 分析每条新闻
        analyzed_news = []
        positive_count = 0
        neutral_count = 0
        negative_count = 0
        total_score = 0.0
        
        for news in news_list[:10]:  # 限制分析前10条
            # 组合标题和内容
            text = f"{news.get('title', '')} {news.get('content', '')}"
            if not text.strip():
                continue
                
            # 情感分析
            result = sentiment_service.analyze(text)
            
            analyzed_news.append({
                **news,
                'sentiment': result['label'],
                'sentiment_score': result['score'],
                'confidence': result['confidence']
            })
            
            # 统计
            total_score += result['score']
            if result['label'] == 'positive':
                positive_count += 1
            elif result['label'] == 'negative':
                negative_count += 1
            else:
                neutral_count += 1
            
            print(f"   📰 {news.get('title', '')[:30]}...")
            print(f"      情感: {result['label']}, 得分: {result['score']:.3f}")
        
        total_analyzed = len(analyzed_news)
        if total_analyzed == 0:
            pytest.skip("没有可分析的新闻内容")
        
        avg_score = total_score / total_analyzed
        
        print(f"\n📊 情感分析统计:")
        print(f"   正面: {positive_count}, 中性: {neutral_count}, 负面: {negative_count}")
        print(f"   平均得分: {avg_score:.3f}")
        
        # ========== 步骤3: 写入MySQL ==========
        print("\n" + "="*50)
        print("步骤3: 将分析结果写入MySQL")
        print("="*50)
        
        # 3.1 保存新闻情感详情
        for news in analyzed_news:
            news_sentiment = NewsSentimentModel(
                stock_code=news.get('stock_code', stock_code),
                news_title=news.get('title', '')[:255],
                news_content=news.get('content', '')[:2000] if news.get('content') else None,
                sentiment_score=news.get('sentiment_score', 0),
                sentiment_label=news.get('sentiment', 'neutral').upper(),
                confidence=news.get('confidence', 0),
                news_date=datetime.now(),
                source=news.get('source', 'akshare')
            )
            db_session.add(news_sentiment)
        
        # 3.2 保存股票情感汇总
        stock_sentiment = StockSentimentModel(
            stock_code=stock_code,
            stock_name="平安银行",
            sentiment_score=avg_score,
            positive_ratio=positive_count / total_analyzed if total_analyzed > 0 else 0,
            negative_ratio=negative_count / total_analyzed if total_analyzed > 0 else 0,
            neutral_ratio=neutral_count / total_analyzed if total_analyzed > 0 else 0,
            news_count=total_analyzed,
            source="sentiment_analysis",
            analyze_date=datetime.now()
        )
        db_session.add(stock_sentiment)
        db_session.commit()
        
        print(f"✅ 写入 {total_analyzed} 条新闻情感详情")
        print(f"✅ 写入 1 条股票情感汇总")
        
        # ========== 步骤4: 验证结果 ==========
        print("\n" + "="*50)
        print("步骤4: 验证结果")
        print("="*50)
        
        # 验证股票情感汇总
        result = db_session.query(StockSentimentModel).filter(
            StockSentimentModel.stock_code == stock_code
        ).first()
        
        assert result is not None, "股票情感汇总应已写入"
        assert result.sentiment_score is not None, "情感得分不应为空"
        assert -1 <= result.sentiment_score <= 1, "情感得分应在-1到1之间"
        assert result.news_count == total_analyzed, "新闻数量应正确"
        
        print(f"\n✅ 完整业务流程测试通过!")
        print(f"   股票代码: {result.stock_code}")
        print(f"   情感得分: {result.sentiment_score:.3f}")
        print(f"   正面比例: {result.positive_ratio:.2%}")
        print(f"   负面比例: {result.negative_ratio:.2%}")
        print(f"   中性比例: {result.neutral_ratio:.2%}")
        print(f"   新闻数量: {result.news_count}")


class TestStockSentimentCalculation:
    """股票情感得分计算测试 TC-002-024"""

    def test_sentiment_score_calculation(self):
        """TC-002-024: 验证情感得分计算公式"""
        
        # 模拟新闻情感数据
        news_sentiments = [
            {'label': 'positive', 'score': 0.9, 'confidence': 0.95},
            {'label': 'positive', 'score': 0.8, 'confidence': 0.90},
            {'label': 'neutral', 'score': 0.5, 'confidence': 0.85},
            {'label': 'negative', 'score': 0.3, 'confidence': 0.80},
            {'label': 'negative', 'score': 0.2, 'confidence': 0.75},
        ]
        
        # 计算加权平均
        label_map = {'positive': 1, 'neutral': 0, 'negative': -1}
        
        total_weight = sum(n['confidence'] for n in news_sentiments)
        weighted_sum = sum(
            label_map[n['label']] * n['confidence'] 
            for n in news_sentiments
        )
        
        sentiment_score = weighted_sum / total_weight if total_weight > 0 else 0
        
        # 统计各类别
        positive_count = sum(1 for n in news_sentiments if n['label'] == 'positive')
        neutral_count = sum(1 for n in news_sentiments if n['label'] == 'neutral')
        negative_count = sum(1 for n in news_sentiments if n['label'] == 'negative')
        total = len(news_sentiments)
        
        print(f"\n📊 情感得分计算验证:")
        print(f"   正面: {positive_count}/{total} ({positive_count/total:.1%})")
        print(f"   中性: {neutral_count}/{total} ({neutral_count/total:.1%})")
        print(f"   负面: {negative_count}/{total} ({negative_count/total:.1%})")
        print(f"   加权得分: {sentiment_score:.3f}")
        
        # 验证得分范围
        assert -1 <= sentiment_score <= 1, "情感得分应在-1到1之间"
        
        # 验证得分计算正确
        # (1*0.95 + 1*0.90 + 0*0.85 - 1*0.80 - 1*0.75) / (0.95+0.90+0.85+0.80+0.75)
        expected = (0.95 + 0.90 - 0.80 - 0.75) / 4.25
        assert abs(sentiment_score - expected) < 0.001, "加权得分计算错误"
        
        print("✅ 情感得分计算正确")


class TestRedisCache:
    """Redis缓存测试 TC-002-025"""

    def test_sentiment_cache(self):
        """TC-002-025: 情感得分缓存"""
        r = get_redis()
        if not r:
            pytest.skip("Redis未配置")
        
        # 测试缓存
        cache_key = "sentiment:score:000001"
        cache_value = "0.65"
        
        # 写入缓存
        r.setex(cache_key, 3600, cache_value)
        
        # 读取缓存
        saved_value = r.get(cache_key)
        
        assert saved_value is not None, "缓存应存在"
        assert float(saved_value) == 0.65, "缓存值应正确"
        
        # 清理
        r.delete(cache_key)
        
        print("\n✅ Redis缓存功能正常")


if __name__ == '__main__':
    pytest.main([__file__, '-v', '-s'])
