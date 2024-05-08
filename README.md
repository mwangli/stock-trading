## StockTrading AI小模型股票自动交易系统

### 项目文档 
https://www.yuque.com/mwangli/ha7323/axga8dz9imansvl4
### 项目展示
[http:124.220.36.95:8000](http:124.220.36.95:8000) 用户名/密码：guest
![图片](https://cdn.nlark.com/yuque/0/2024/png/410925/1712990475463-950af927-0c82-4774-8e3a-dc867f94773c.png?x-oss-process=image%2Fformat%2Cwebp%2Fresize%2Cw_1265%2Climit_0)

### 功能介绍

1. 对接证券平台，实现股票自动化交易
2. 使用QuartZ定时任务调度，每日自动更新数据
3. 使用DL4J框架实现LSTM模型指导股票买入，采用T+1短线交易策略
4. 利用K8S+GithubAction实现DevOps
5. 支持分布式离线训练

### 后期优化方向
1. 获得更多股票历史数据用于模型增量迭代训练
2. 模型超参数调优提高预测价格趋势准确率
3. 尝试对价格增长率进行预测提高实际收益率
