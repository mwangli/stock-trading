## StockTrading AI小模型股票自动交易系统

### 项目文档 
https://www.yuque.com/mwangli/ha7323/axga8dz9imansvl4
### 项目展示
[http:124.220.36.95:8000](http:124.220.36.95:8000)
![图片](https://cdn.nlark.com/yuque/0/2024/jpeg/410925/1712554594862-e1ca43e6-2e26-41d7-b5c0-70f4ab7b4dca.jpeg?x-oss-process=image%2Fformat%2Cwebp)

### 功能介绍

1. 对接证券平台，实现股票自动化交易
2. 使用QuartZ定时任务调度，每日自动更新数据
3. 使用DL4J框架实现LSTM模型指导股票买入，采用T+1短线交易策略
4. 利用K8S+GithubAction实现DevOps

### 后期优化方向
1. 获得更多股票历史数据用于训练
2. 模型超参数调优
3. 实现增量训练
4. DL4J代码框架优化
