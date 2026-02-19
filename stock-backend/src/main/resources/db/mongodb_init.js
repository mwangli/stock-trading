// ========================================================
// MongoDB股票交易系统初始化脚本
// 版本: 1.0
// 创建日期: 2026-02-19
// MongoDB版本: 4.4+
// ========================================================

// 使用数据库
use stock_trading;

// ========================================================
// 1. 创建股票历史价格集合
// ========================================================

// 创建集合(如果不存在)
db.createCollection("stock_prices", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["code", "name", "date", "price1", "price2", "price3", "price4"],
      properties: {
        code: {
          bsonType: "string",
          description: "股票代码，必填"
        },
        name: {
          bsonType: "string",
          description: "股票名称，必填"
        },
        date: {
          bsonType: "string",
          description: "交易日期(YYYY-MM-DD)，必填"
        },
        price1: {
          bsonType: ["double", "int"],
          description: "开盘价，必填"
        },
        price2: {
          bsonType: ["double", "int"],
          description: "最高价，必填"
        },
        price3: {
          bsonType: ["double", "int"],
          description: "最低价，必填"
        },
        price4: {
          bsonType: ["double", "int"],
          description: "收盘价，必填"
        }
      }
    }
  }
});

// ========================================================
// 2. 创建索引
// ========================================================

// 复合唯一索引: 股票代码+日期(防止重复数据)
db.stock_prices.createIndex({ "code": 1, "date": 1 }, { 
  unique: true,
  name: "idx_code_date_unique"
});

// 复合索引: 股票代码+日期降序(用于倒序查询)
db.stock_prices.createIndex({ "code": 1, "date": -1 }, {
  name: "idx_code_date_desc"
});

// 单字段索引
db.stock_prices.createIndex({ "code": 1 }, { name: "idx_code" });
db.stock_prices.createIndex({ "date": -1 }, { name: "idx_date_desc" });

// ========================================================
// 3. 插入示例数据(测试用)
// ========================================================

db.stock_prices.insertMany([
  // 贵州茅台(600519) - 5天K线数据
  {
    "code": "600519",
    "name": "贵州茅台",
    "date": "2024-01-10",
    "price1": 1780.00,
    "price2": 1800.00,
    "price3": 1775.00,
    "price4": 1795.00,
    "increaseRate": 0.85,
    "newPrice": 1795.00,
    "riseFallRange": 0.85,
    "riseFallAmount": 15.00,
    "tradingVolume": 25000,
    "tradingAmount": 44875000,
    "amplitude": 1.40,
    "highPrice": 1800.00,
    "lowPrice": 1775.00,
    "todayOpenPrice": 1780.00,
    "yesterdayClosePrice": 1780.00,
    "volumeRate": 1.02,
    "exchangeRate": 0.20,
    "profitRate": 0.85,
    "realProfitRate": 0.84
  },
  {
    "code": "600519",
    "name": "贵州茅台",
    "date": "2024-01-11",
    "price1": 1795.00,
    "price2": 1815.00,
    "price3": 1790.00,
    "price4": 1810.00,
    "increaseRate": 0.84,
    "newPrice": 1810.00,
    "riseFallRange": 0.84,
    "riseFallAmount": 15.00,
    "tradingVolume": 28000,
    "tradingAmount": 50680000,
    "amplitude": 1.39,
    "highPrice": 1815.00,
    "lowPrice": 1790.00,
    "todayOpenPrice": 1795.00,
    "yesterdayClosePrice": 1795.00,
    "volumeRate": 1.12,
    "exchangeRate": 0.22,
    "profitRate": 1.69,
    "realProfitRate": 1.68
  },
  {
    "code": "600519",
    "name": "贵州茅台",
    "date": "2024-01-12",
    "price1": 1810.00,
    "price2": 1820.00,
    "price3": 1805.00,
    "price4": 1815.00,
    "increaseRate": 0.28,
    "newPrice": 1815.00,
    "riseFallRange": 0.28,
    "riseFallAmount": 5.00,
    "tradingVolume": 22000,
    "tradingAmount": 39930000,
    "amplitude": 0.83,
    "highPrice": 1820.00,
    "lowPrice": 1805.00,
    "todayOpenPrice": 1810.00,
    "yesterdayClosePrice": 1810.00,
    "volumeRate": 0.88,
    "exchangeRate": 0.18,
    "profitRate": 1.97,
    "realProfitRate": 1.96
  },
  {
    "code": "600519",
    "name": "贵州茅台",
    "date": "2024-01-15",
    "price1": 1815.00,
    "price2": 1830.00,
    "price3": 1810.00,
    "price4": 1825.00,
    "increaseRate": 0.55,
    "newPrice": 1825.00,
    "riseFallRange": 0.55,
    "riseFallAmount": 10.00,
    "tradingVolume": 30000,
    "tradingAmount": 54750000,
    "amplitude": 1.10,
    "highPrice": 1830.00,
    "lowPrice": 1810.00,
    "todayOpenPrice": 1815.00,
    "yesterdayClosePrice": 1815.00,
    "volumeRate": 1.20,
    "exchangeRate": 0.24,
    "profitRate": 2.53,
    "realProfitRate": 2.52
  },
  {
    "code": "600519",
    "name": "贵州茅台",
    "date": "2024-01-16",
    "price1": 1825.00,
    "price2": 1840.00,
    "price3": 1820.00,
    "price4": 1835.00,
    "increaseRate": 0.55,
    "newPrice": 1835.00,
    "riseFallRange": 0.55,
    "riseFallAmount": 10.00,
    "tradingVolume": 32000,
    "tradingAmount": 58720000,
    "amplitude": 1.09,
    "highPrice": 1840.00,
    "lowPrice": 1820.00,
    "todayOpenPrice": 1825.00,
    "yesterdayClosePrice": 1825.00,
    "volumeRate": 1.07,
    "exchangeRate": 0.25,
    "profitRate": 3.09,
    "realProfitRate": 3.08
  },
  
  // 中国平安(601318) - 5天K线数据
  {
    "code": "601318",
    "name": "中国平安",
    "date": "2024-01-10",
    "price1": 44.50,
    "price2": 45.20,
    "price3": 44.30,
    "price4": 44.80,
    "increaseRate": 0.67,
    "newPrice": 44.80,
    "riseFallRange": 0.67,
    "riseFallAmount": 0.30,
    "tradingVolume": 850000,
    "tradingAmount": 38080000,
    "amplitude": 2.02,
    "highPrice": 45.20,
    "lowPrice": 44.30,
    "todayOpenPrice": 44.50,
    "yesterdayClosePrice": 44.50,
    "volumeRate": 1.10,
    "exchangeRate": 0.78,
    "profitRate": 0.67,
    "realProfitRate": 0.66
  },
  {
    "code": "601318",
    "name": "中国平安",
    "date": "2024-01-11",
    "price1": 44.80,
    "price2": 45.50,
    "price3": 44.60,
    "price4": 45.20,
    "increaseRate": 0.89,
    "newPrice": 45.20,
    "riseFallRange": 0.89,
    "riseFallAmount": 0.40,
    "tradingVolume": 920000,
    "tradingAmount": 41584000,
    "amplitude": 2.01,
    "highPrice": 45.50,
    "lowPrice": 44.60,
    "todayOpenPrice": 44.80,
    "yesterdayClosePrice": 44.80,
    "volumeRate": 1.08,
    "exchangeRate": 0.85,
    "profitRate": 1.57,
    "realProfitRate": 1.56
  },
  {
    "code": "601318",
    "name": "中国平安",
    "date": "2024-01-12",
    "price1": 45.20,
    "price2": 45.80,
    "price3": 45.00,
    "price4": 45.60,
    "increaseRate": 0.88,
    "newPrice": 45.60,
    "riseFallRange": 0.88,
    "riseFallAmount": 0.40,
    "tradingVolume": 880000,
    "tradingAmount": 40128000,
    "amplitude": 1.77,
    "highPrice": 45.80,
    "lowPrice": 45.00,
    "todayOpenPrice": 45.20,
    "yesterdayClosePrice": 45.20,
    "volumeRate": 0.96,
    "exchangeRate": 0.81,
    "profitRate": 2.47,
    "realProfitRate": 2.46
  },
  {
    "code": "601318",
    "name": "中国平安",
    "date": "2024-01-15",
    "price1": 45.60,
    "price2": 46.20,
    "price3": 45.50,
    "price4": 45.90,
    "increaseRate": 0.66,
    "newPrice": 45.90,
    "riseFallRange": 0.66,
    "riseFallAmount": 0.30,
    "tradingVolume": 750000,
    "tradingAmount": 34425000,
    "amplitude": 1.54,
    "highPrice": 46.20,
    "lowPrice": 45.50,
    "todayOpenPrice": 45.60,
    "yesterdayClosePrice": 45.60,
    "volumeRate": 0.85,
    "exchangeRate": 0.69,
    "profitRate": 3.15,
    "realProfitRate": 3.14
  },
  {
    "code": "601318",
    "name": "中国平安",
    "date": "2024-01-16",
    "price1": 45.90,
    "price2": 46.50,
    "price3": 45.80,
    "price4": 46.10,
    "increaseRate": 0.44,
    "newPrice": 46.10,
    "riseFallRange": 0.44,
    "riseFallAmount": 0.20,
    "tradingVolume": 680000,
    "tradingAmount": 31348000,
    "amplitude": 1.53,
    "highPrice": 46.50,
    "lowPrice": 45.80,
    "todayOpenPrice": 45.90,
    "yesterdayClosePrice": 45.90,
    "volumeRate": 0.91,
    "exchangeRate": 0.63,
    "profitRate": 3.60,
    "realProfitRate": 3.58
  }
]);

// ========================================================
// 4. 查看索引信息
// ========================================================

print("=== MongoDB初始化完成 ===");
print("集合列表:");
db.getCollectionNames().forEach(function(name) {
  print("  - " + name);
});

print("\nstock_prices集合索引:");
db.stock_prices.getIndexes().forEach(function(index) {
  printjson(index);
});

print("\n数据量统计:");
print("  stock_prices: " + db.stock_prices.countDocuments() + " 条");

print("\n示例数据:");
db.stock_prices.find({code: "600519"}).limit(3).forEach(function(doc) {
  printjson({
    code: doc.code,
    name: doc.name,
    date: doc.date,
    open: doc.price1,
    high: doc.price2,
    low: doc.price3,
    close: doc.price4,
    change: doc.increaseRate
  });
});

// ========================================================
// 5. 常用查询示例
// ========================================================

/*
// 查询某只股票的历史数据(倒序)
db.stock_prices.find({code: "600519"}).sort({date: -1}).limit(60);

// 查询某日期范围内的数据
db.stock_prices.find({
  code: "600519",
  date: {$gte: "2024-01-01", $lte: "2024-01-31"}
}).sort({date: 1});

// 查询最新的价格
db.stock_prices.find({code: "600519"}).sort({date: -1}).limit(1);

// 聚合查询: 计算平均成交量
db.stock_prices.aggregate([
  {$match: {code: "600519"}},
  {$group: {_id: "$code", avgVolume: {$avg: "$tradingVolume"}}}
]);

// 删除某只股票的所有数据
db.stock_prices.deleteMany({code: "600519"});

// 删除整个集合
db.stock_prices.drop();
*/

// ========================================================
// 6. 数据导入导出命令(在shell中执行)
// ========================================================

/*
// 导出数据
mongoexport --db stock_trading --collection stock_prices --out stock_prices.json

// 导入数据
mongoimport --db stock_trading --collection stock_prices --file stock_prices.json

// 备份数据库
mongodump --db stock_trading --out /backup/mongodb/

// 恢复数据库
mongorestore --db stock_trading /backup/mongodb/stock_trading/
*/
