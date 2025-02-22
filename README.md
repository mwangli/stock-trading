## StockTrading AI小模型股票自动交易系统

### ISSUES: 2025-02-09：目前暂时无法从ZX证券平台请求的到股票的接口数据，待后期优化

### 1.项目文档 
https://www.yuque.com/mwangli/ha7323/axga8dz9imansvl4

### 2.项目展示
[http:124.220.36.95:8000](http:124.220.36.95:8000) 用户名/密码：guest

### 3.功能介绍
1. 对接证券平台，实现股票自动化交易
2. 使用QuartZ定时任务调度，每日自动更新数据
3. 使用DL4J框架实现LSTM模型指导股票买入，采用T+1短线交易策略
4. 利用K8S+GithubAction实现DevOps
5. 支持分布式离线训练

### 4.后期优化方向
1. 获得更多股票历史数据用于模型增量迭代训练
2. 模型超参数调优提高预测价格趋势准确率

### 5.页面展示：
1. 收益数据统计![image](https://github.com/mwangli/stock-trading/assets/48406369/4b22cc32-c6b9-4a9d-a9df-c29f65a4a5bb)
2. 交易订单查询![image](https://github.com/mwangli/stock-trading/assets/48406369/bd16016b-4085-413d-a609-1643922616c9)
3. 股票价格查看![image](https://github.com/mwangli/stock-trading/assets/48406369/e080bff3-cc17-4fa3-b642-9a9ea8d3b241)
4. 模型预测表现![image](https://github.com/mwangli/stock-trading/assets/48406369/8d6272ac-773f-4a7d-9993-faf0694f9707)
5. 定时任务调度![image](https://github.com/mwangli/stock-trading/assets/48406369/bb10ea48-2f1a-401d-bca4-823d51e8f5bc)
6. 实时日志跟踪![image](https://github.com/mwangli/stock-trading/assets/48406369/4aaf1d15-6049-4913-b972-c7b6146dbf66)

