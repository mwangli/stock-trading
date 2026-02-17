# 交易执行模块

## 模块职责

对接证券公司API，执行股票买卖操作，管理订单和持仓。

## 需求列表

### 1. 交易执行需求

| 需求ID | 需求描述 | 优先级 | 对接系统 |
|--------|----------|--------|----------|
| TE-001 | 收盘前买入（14:50-14:55） | P0 | 中信证券API |
| TE-002 | 次日收盘前卖出（14:50-14:55） | P0 | 中信证券API |
| TE-003 | 持仓查询 | P0 | 中信证券API |
| TE-004 | 账户资金查询 | P0 | 中信证券API |
| TE-005 | 委托状态查询 | P1 | 中信证券API |
| TE-006 | 止损自动卖出 | P0 | 中信证券API |

### 2. 用户交互流程

```
09:30 ───── 11:30 ───────────────────────────── 14:50
  │           │                                    │
  ▼           ▼                                    ▼
┌──────┐  ┌──────┐                          ┌──────┐
│卖出持仓│  │数据采集│                          │执行买入│
│释放资金│  │智能分析│                          │满仓操作│
└──────┘  └──────┘                          └──────┘
              │                                    │
              ▼                                    ▼
        ┌──────────┐                        ┌──────────┐
        │ 综合选股  │──────────────────────▶│ 风控检查 │
        │ 选出Top1 │                        │ 日<3%   │
        └──────────┘                        └──────────┘
                                                   │
                              持仓过夜              ▼
                        ─────────────────────────────
                                                 次日09:30
                                                       │
                                                       ▼
                                                    循环
```

## 外部依赖

- 中信证券API

## 输入接口

```java
public interface TradeExecutionService {
    OrderResult executeBuy(String stockCode, int quantity);
    OrderResult executeSell(String stockCode, int quantity);
    List<Position> getHoldingPositions();
    AccountInfo getAccountInfo();
}
```

## 输出数据格式

```json
{
    "orderId": "ORDER123456",
    "stockCode": "600519",
    "stockName": "贵州茅台",
    "type": "buy",
    "quantity": 100,
    "price": 1850.00,
    "status": "pending",
    "timestamp": "2024-01-15T14:50:00"
}
```
