# 数据库脚本使用说明

## 文件说明

| 文件名 | 说明 |
|--------|------|
| `schema.sql` | 数据库表结构定义脚本，包含所有建表语句和索引 |
| `data.sql` | 数据库初始化数据脚本，包含测试数据和基础配置 |

## 数据库信息

- **数据库类型**: MySQL 8.0+
- **字符集**: utf8mb4
- **排序规则**: utf8mb4_general_ci (推荐)

## 使用方法

### 1. 创建数据库

```sql
-- 登录MySQL
mysql -u root -p

-- 创建数据库
CREATE DATABASE IF NOT EXISTS stock_trading 
CHARACTER SET utf8mb4 
COLLATE utf8mb4_general_ci;

-- 使用数据库
USE stock_trading;
```

### 2. 执行表结构脚本

```bash
# 方式1: 使用命令行
mysql -u root -p stock_trading < schema.sql

# 方式2: 在MySQL客户端中执行
source /path/to/schema.sql
```

### 3. 插入初始化数据

```bash
# 方式1: 使用命令行
mysql -u root -p stock_trading < data.sql

# 方式2: 在MySQL客户端中执行
source /path/to/data.sql
```

### 4. 一键初始化(推荐)

```bash
# 创建数据库并执行所有脚本
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS stock_trading CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
mysql -u root -p stock_trading < schema.sql
mysql -u root -p stock_trading < data.sql
```

## 表结构说明

### 数据采集模块

| 表名 | 说明 | 记录数 |
|------|------|--------|
| `stock_info` | 股票基本信息表 | ~5000条 |
| `quartz_job` | 定时任务配置表 | ~6条 |

### 交易执行模块

| 表名 | 说明 | 记录数 |
|------|------|--------|
| `account_info` | 账户资金信息表 | 1条 |
| `order_info` | 订单信息表 | 动态增长 |
| `trading_record` | 交易记录表 | 动态增长 |
| `position` | 持仓记录表 | 动态增长 |

### LSTM预测模块

| 表名 | 说明 | 记录数 |
|------|------|--------|
| `model_info` | 模型信息表 | ~5000条 |

### MongoDB集合

| 集合名 | 说明 | 存储 |
|--------|------|------|
| `stock_prices` | 历史K线数据 | MongoDB |

## 常用操作

### 重置数据库

```sql
-- 危险操作! 会清空所有数据!
DROP DATABASE IF EXISTS stock_trading;
CREATE DATABASE stock_trading CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE stock_trading;
-- 然后重新执行 schema.sql 和 data.sql
```

### 查看表结构

```sql
-- 查看所有表
SHOW TABLES;

-- 查看表结构
DESC stock_info;
DESC order_info;

-- 查看建表语句
SHOW CREATE TABLE stock_info;
```

### 查看索引

```sql
-- 查看表的索引
SHOW INDEX FROM stock_info;
SHOW INDEX FROM order_info;
```

### 数据量统计

```sql
-- 统计各表数据量
SELECT 
  'stock_info' as table_name, COUNT(*) as count FROM stock_info
UNION ALL
SELECT 'quartz_job', COUNT(*) FROM quartz_job
UNION ALL
SELECT 'account_info', COUNT(*) FROM account_info
UNION ALL
SELECT 'order_info', COUNT(*) FROM order_info
UNION ALL
SELECT 'trading_record', COUNT(*) FROM trading_record
UNION ALL
SELECT 'position', COUNT(*) FROM position
UNION ALL
SELECT 'model_info', COUNT(*) FROM model_info;
```

## 配置文件

### application.yml 数据库配置示例

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/stock_trading?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
    
  data:
    mongodb:
      uri: mongodb://localhost:27017/stock_trading
```

## 注意事项

1. **字符集**: 所有表使用utf8mb4字符集，支持完整的Unicode字符(包括emoji)
2. **时区**: 所有时间字段使用数据库时区(建议在配置中设置serverTimezone=Asia/Shanghai)
3. **软删除**: 使用`deleted`字段实现软删除，避免物理删除数据
4. **索引**: 已为常用查询字段添加索引，可根据实际查询需求调整
5. **外键**: 未设置外键约束，由应用程序层保证数据一致性

## 数据备份

```bash
# 备份整个数据库
mysqldump -u root -p stock_trading > stock_trading_backup.sql

# 仅备份表结构
mysqldump -u root -p --no-data stock_trading > stock_trading_schema.sql

# 仅备份数据
mysqldump -u root -p --no-create-info stock_trading > stock_trading_data.sql
```

## 数据恢复

```bash
# 恢复数据库
mysql -u root -p stock_trading < stock_trading_backup.sql
```

## MongoDB初始化

```javascript
// 连接到MongoDB
use stock_trading;

// 创建集合
db.createCollection("stock_prices");

// 创建索引
db.stock_prices.createIndex({ "code": 1, "date": 1 }, { unique: true });
db.stock_prices.createIndex({ "code": 1, "date": -1 });
```

## 问题排查

### 1. 中文乱码

```sql
-- 检查字符集
SHOW VARIABLES LIKE 'character_set%';

-- 设置字符集
SET NAMES utf8mb4;
```

### 2. 连接失败

- 检查MySQL服务是否启动
- 检查用户名密码是否正确
- 检查数据库是否存在
- 检查防火墙设置

### 3. 权限不足

```sql
-- 授予权限
GRANT ALL PRIVILEGES ON stock_trading.* TO 'username'@'localhost';
FLUSH PRIVILEGES;
```

## 更新日志

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0 | 2026-02-19 | 初始版本，包含所有基础表结构 |
