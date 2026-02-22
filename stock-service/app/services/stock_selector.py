"""
综合选股服务 (Stock Selection Service)
整合情感分析、LSTM预测双因子，计算综合得分，选出最优股票
"""
import logging
import asyncio
from concurrent.futures import ThreadPoolExecutor, TimeoutError
from datetime import datetime
from typing import Dict, List, Optional, Tuple

import numpy as np

from app.core.config import settings

logger = logging.getLogger(__name__)

# 因子权重配置
LSTM_WEIGHT = 0.6  # LSTM因子权重
SENTIMENT_WEIGHT = 0.4  # 情感因子权重

# 超时配置
FACTOR_TIMEOUT = 30  # 单因子超时时间（秒）
TOTAL_TIMEOUT = 30  # 总超时时间（秒）


class StockSelector:
    """
    综合选股服务
    
    整合情感分析和技术分析两个因子，计算综合得分，选出最优股票
    """

    def __init__(self):
        self._sentiment_service = None
        self._lstm_service = None
        self._executor = ThreadPoolExecutor(max_workers=4)
        logger.info("StockSelector initialized")

    @property
    def sentiment_service(self):
        """懒加载情感分析服务"""
        if self._sentiment_service is None:
            try:
                from app.services.sentiment_analysis import SentimentService
                self._sentiment_service = SentimentService()
            except Exception as e:
                logger.warning(f"Failed to load sentiment service: {e}")
        return self._sentiment_service

    @property
    def lstm_service(self):
        """懒加载LSTM预测服务"""
        if self._lstm_service is None:
            try:
                from app.services.lstm_prediction import LSTMService
                self._lstm_service = LSTMService()
            except Exception as e:
                logger.warning(f"Failed to load LSTM service: {e}")
        return self._lstm_service

    def _normalize_lstm_score(self, change_percent: float) -> float:
        """
        将LSTM预测涨跌幅归一化到0-100分
        
        Args:
            change_percent: 预测涨跌幅 (%)
            
        Returns:
            归一化后的分数 (0-100)
        """
        # 涨跌幅范围假设为 -10% 到 +10%，归一化到 0-100
        min_change = -10.0
        max_change = 10.0
        
        # 使用sigmoid函数平滑映射
        score = 50 + (change_percent * 5)  # 将 -10~10 映射到 0~100
        score = max(0, min(100, score))  # 限制在0-100范围内
        
        return round(score, 2)

    def _normalize_sentiment_score(self, sentiment_score: float) -> float:
        """
        将情感得分归一化到0-100分
        
        Args:
            sentiment_score: 情感得分 (-1~1)
            
        Returns:
            归一化后的分数 (0-100)
        """
        # 将 -1~1 映射到 0~100
        score = (sentiment_score + 1) * 50
        score = max(0, min(100, score))  # 限制在0-100范围内
        
        return round(score, 2)

    def _calculate_comprehensive_score(
        self, 
        lstm_score: float, 
        sentiment_score: float
    ) -> float:
        """
        计算综合得分
        
        综合得分 = LSTM因子得分 × 0.6 + 情感因子得分 × 0.4
        
        Args:
            lstm_score: LSTM因子得分 (0-100)
            sentiment_score: 情感因子得分 (0-100)
            
        Returns:
            综合得分 (0-100)
        """
        comprehensive = lstm_score * LSTM_WEIGHT + sentiment_score * SENTIMENT_WEIGHT
        return round(comprehensive, 2)

    def _get_sentiment_score_from_label(self, label: str, score: float) -> float:
        """
        根据情感标签获取标准化的情感得分
        
        Args:
            label: 情感标签 (positive/negative/neutral)
            score: 原始得分
            
        Returns:
            标准化后的情感得分 (-1~1)
        """
        label_score_map = {
            "positive": 1.0,
            "negative": -1.0,
            "neutral": 0.0
        }
        base_score = label_score_map.get(label, 0.0)
        # 结合原始得分进行调整
        return base_score * score

    def get_stock_score(
        self, 
        stock_code: str, 
        stock_data: List[Dict],
        news_texts: Optional[List[str]] = None
    ) -> Dict:
        """
        获取单只股票的综合评分
        
        Args:
            stock_code: 股票代码
            stock_data: 股票历史数据 (用于LSTM预测)
            news_texts: 股票相关新闻文本列表 (用于情感分析)
            
        Returns:
            包含各因子得分和综合得分的字典
        """
        lstm_score = 0.0
        sentiment_score = 0.0
        lstm_error = None
        sentiment_error = None

        # 1. 获取LSTM因子得分
        try:
            if self.lstm_service and stock_data:
                lstm_result = self.lstm_service.predict(stock_code, stock_data)
                if lstm_result and 'change_percent' in lstm_result:
                    change_percent = lstm_result.get('change_percent', 0)
                    lstm_score = self._normalize_lstm_score(change_percent)
                    logger.debug(f"Stock {stock_code} LSTM score: {lstm_score}, change: {change_percent}%")
                elif lstm_result and 'error' in lstm_result:
                    lstm_error = lstm_result.get('error')
                    logger.warning(f"LSTM prediction error for {stock_code}: {lstm_error}")
            else:
                lstm_error = "LSTM service unavailable or no data"
        except Exception as e:
            lstm_error = str(e)
            logger.error(f"Error getting LSTM score for {stock_code}: {e}")

        # 2. 获取情感因子得分
        try:
            if self.sentiment_service and news_texts:
                # 分析多条新闻
                sentiments = []
                for text in news_texts[:5]:  # 最多取5条
                    result = self.sentiment_service.analyze(text)
                    if result:
                        label = result.get('label', 'neutral')
                        score = result.get('score', 0.5)
                        sentiments.append(self._get_sentiment_score_from_label(label, score))
                
                if sentiments:
                    avg_sentiment = sum(sentiments) / len(sentiments)
                    sentiment_score = self._normalize_sentiment_score(avg_sentiment)
                    logger.debug(f"Stock {stock_code} sentiment score: {sentiment_score}, avg: {avg_sentiment}")
                else:
                    sentiment_score = 50.0  # 默认中性
            elif news_texts is None or len(news_texts) == 0:
                # 没有新闻数据时使用中性分数
                sentiment_score = 50.0
                sentiment_error = "No news data available"
            else:
                sentiment_error = "Sentiment service unavailable"
        except Exception as e:
            sentiment_error = str(e)
            logger.error(f"Error getting sentiment score for {stock_code}: {e}")

        # 3. 计算综合得分
        comprehensive_score = self._calculate_comprehensive_score(lstm_score, sentiment_score)

        return {
            "stock_code": stock_code,
            "overall_score": comprehensive_score,
            "lstm_score": lstm_score,
            "sentiment_score": sentiment_score,
            "lstm_error": lstm_error,
            "sentiment_error": sentiment_error,
            "analyzed_at": datetime.now().isoformat()
        }

    def get_comprehensive_ranking(
        self, 
        stock_codes: List[str],
        stock_data_map: Dict[str, List[Dict]],
        news_map: Optional[Dict[str, List[str]]] = None
    ) -> List[Dict]:
        """
        获取多只股票的综合排名
        
        Args:
            stock_codes: 股票代码列表
            stock_data_map: 股票代码 -> 历史数据的映射
            news_map: 股票代码 -> 新闻列表的映射
            
        Returns:
            按综合得分排序的股票列表
        """
        results = []
        
        for stock_code in stock_codes:
            stock_data = stock_data_map.get(stock_code, [])
            news_texts = news_map.get(stock_code, []) if news_map else []
            
            score_result = self.get_stock_score(stock_code, stock_data, news_texts)
            results.append(score_result)
        
        # 按综合得分降序排序
        results.sort(key=lambda x: x.get('overall_score', 0), reverse=True)
        
        return results

    def select_best_stock(
        self,
        stock_codes: List[str],
        stock_data_map: Dict[str, List[Dict]],
        news_map: Optional[Dict[str, List[str]]] = None,
        top_n: int = 1
    ) -> Dict:
        """
        选出最优股票
        
        Args:
            stock_codes: 候选股票池
            stock_data_map: 股票代码 -> 历史数据的映射
            news_map: 股票代码 -> 新闻列表的映射
            top_n: 选出数量，默认1
            
        Returns:
            选股结果
        """
        # 过滤ST、停牌股票
        valid_codes = self._filter_st_stocks(stock_codes)
        
        if not valid_codes:
            return {
                "selected_stock": None,
                "alternatives": [],
                "analyzed_at": datetime.now().isoformat(),
                "error": "No valid stocks in pool after filtering"
            }
        
        # 获取综合排名
        rankings = self.get_comprehensive_ranking(valid_codes, stock_data_map, news_map)
        
        if not rankings:
            return {
                "selected_stock": None,
                "alternatives": [],
                "analyzed_at": datetime.now().isoformat(),
                "error": "Failed to analyze any stock"
            }
        
        # 选出主推股票
        selected = rankings[0] if rankings else None
        
        # 选出备选股票 (Top 3，排除主推)
        alternatives = rankings[1:top_n + 3] if len(rankings) > 1 else []
        
        return {
            "selected_stock": selected,
            "alternatives": alternatives,
            "total_analyzed": len(rankings),
            "analyzed_at": datetime.now().isoformat()
        }

    def _filter_st_stocks(self, stock_codes: List[str]) -> List[str]:
        """
        过滤ST、停牌股票
        
        Args:
            stock_codes: 股票代码列表
            
        Returns:
            过滤后的股票列表
        """
        # ST股票代码通常包含 "ST", "*ST", "S*", "S" 等前缀
        # 这里做简单过滤，实际应该查询数据库获取股票状态
        filtered = []
        for code in stock_codes:
            # 跳过明显是ST的股票代码
            if 'ST' in code.upper() or '*ST' in code.upper():
                logger.debug(f"Filtered out ST stock: {code}")
                continue
            filtered.append(code)
        
        return filtered

    def get_parallel_factors(
        self,
        stock_code: str,
        stock_data: List[Dict],
        news_texts: Optional[List[str]] = None
    ) -> Tuple[Dict, Dict]:
        """
        并行获取双因子（用于异步调用）
        
        Args:
            stock_code: 股票代码
            stock_data: 股票历史数据
            news_texts: 新闻文本列表
            
        Returns:
            (LSTM结果, 情感分析结果)
        """
        lstm_result = {}
        sentiment_result = {}

        # 并行执行两个因子的获取
        loop = asyncio.new_event_loop()
        try:
            lstm_future = loop.run_in_executor(
                self._executor,
                self._get_lstm_factor,
                stock_code,
                stock_data
            )
            
            sentiment_future = loop.run_in_executor(
                self._executor,
                self._get_sentiment_factor,
                stock_code,
                news_texts or []
            )
            
            # 等待所有结果，设置总超时
            done, pending = loop.run_until_complete(
                asyncio.wait(
                    [lstm_future, sentiment_future],
                    timeout=TOTAL_TIMEOUT
                )
            )
            
            if lstm_future in done and not lstm_future.exception():
                lstm_result = lstm_future.result()
            else:
                logger.warning(f"LSTM factor timeout or error for {stock_code}")
                
            if sentiment_future in done and not sentiment_future.exception():
                sentiment_result = sentiment_future.result()
            else:
                logger.warning(f"Sentiment factor timeout or error for {stock_code}")
                
        except Exception as e:
            logger.error(f"Error in parallel factor retrieval: {e}")
        finally:
            loop.close()
        
        return lstm_result, sentiment_result

    def _get_lstm_factor(self, stock_code: str, stock_data: List[Dict]) -> Dict:
        """获取LSTM因子"""
        try:
            if self.lstm_service and stock_data:
                result = self.lstm_service.predict(stock_code, stock_data)
                return result or {}
        except TimeoutError:
            logger.warning(f"LSTM factor timeout for {stock_code}")
        except Exception as e:
            logger.error(f"Error getting LSTM factor: {e}")
        return {}

    def _get_sentiment_factor(self, stock_code: str, news_texts: List[str]) -> Dict:
        """获取情感因子"""
        try:
            if self.sentiment_service and news_texts:
                sentiments = []
                for text in news_texts[:5]:
                    result = self.sentiment_service.analyze(text)
                    if result:
                        sentiments.append(result)
                return {"sentiments": sentiments}
        except TimeoutError:
            logger.warning(f"Sentiment factor timeout for {stock_code}")
        except Exception as e:
            logger.error(f"Error getting sentiment factor: {e}")
        return {}

    def close(self):
        """关闭服务"""
        if self._executor:
            self._executor.shutdown(wait=False)
            logger.info("StockSelector executor shut down")


# 单例实例
stock_selector = StockSelector()
