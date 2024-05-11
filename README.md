## StockTrading AI小模型股票自动交易系统

### 项目文档 
https://www.yuque.com/mwangli/ha7323/axga8dz9imansvl4
### 项目展示
[http:124.220.36.95:8000](http:124.220.36.95:8000) 用户名/密码：guest
### 页面展示：
1. 收益数据统计
![图片](https://cdn.nlark.com/yuque/0/2024/png/410925/1715424205213-b97128b2-e823-4cb2-9699-b81dd9d35922.png?x-oss-process=image%2Fformat%2Cwebp%2Fresize%2Cw_1280%2Climit_0)
2. 交易订单查询
![图片](https://cdn.nlark.com/yuque/0/2024/png/410925/1715424232855-68e7ea8c-e6f7-451d-b31f-459e9c332697.png?x-oss-process=image%2Fformat%2Cwebp%2Fresize%2Cw_1280%2Climit_0)
3. 股票价格查看
![图片](https://cdn.nlark.com/yuque/0/2024/png/410925/1715424259073-f2ed43a0-8b74-449c-bdca-48920e29f404.png?x-oss-process=image%2Fformat%2Cwebp%2Fresize%2Cw_1280%2Climit_0)
4. 模型预测表现
![图片](https://cdn.nlark.com/yuque/0/2024/png/410925/1715424288063-33d41606-fc4e-459d-b5a7-0f5940bf56cf.png?x-oss-process=image%2Fformat%2Cwebp%2Fresize%2Cw_1280%2Climit_0)
5. 定时任务调度
![图片](https://cdn.nlark.com/yuque/0/2024/png/410925/1715424303631-9d16bfd8-5a04-48a8-8b89-c389de3ad5e2.png?x-oss-process=image%2Fformat%2Cwebp%2Fresize%2Cw_1280%2Climit_0)
6. 实时日志跟踪
![图片](https://cdn.nlark.com/yuque/0/2024/png/410925/1715424340357-68712517-fcbe-497e-8ddb-73a073c44e70.png?x-oss-process=image%2Fformat%2Cwebp%2Fresize%2Cw_1280%2Climit_0)

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
