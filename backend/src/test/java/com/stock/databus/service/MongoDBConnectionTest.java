package com.stock.databus.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MongoDB 连接测试（直接测试，无需 Spring）
 */
class MongoDBConnectionTest {

    private static final String MONGO_URI = "mongodb://admin:Root.123456@124.220.36.95:27017/stock-trading?authSource=admin";

    @Test
    @DisplayName("测试 MongoDB 连接")
    void testMongoDBConnection() {
        try (MongoClient mongoClient = MongoClients.create(MONGO_URI)) {
            MongoDatabase database = mongoClient.getDatabase("stock_trading");
            
            // 测试连接
            Document result = database.runCommand(new Document("ping", 1));
            assertNotNull(result);
            System.out.println("✓ MongoDB 连接成功！");
            System.out.println("  连接成功！");
        } catch (Exception e) {
            fail("MongoDB 连接失败：" + e.getMessage());
        }
    }

    @Test
    @DisplayName("测试查询 stock_prices 集合")
    void testQueryStockPrices() {
        try (MongoClient mongoClient = MongoClients.create(MONGO_URI)) {
            MongoDatabase database = mongoClient.getDatabase("stock_trading");
            
            // 查询集合中的文档数量
            long count = database.getCollection("stock_prices").countDocuments();
            
            System.out.println("✓ stock_prices 集合查询成功！");
            System.out.println("  文档数量：" + count);
            
            if (count > 0) {
                // 查询第一只股票的数据量
                var firstDoc = database.getCollection("stock_prices").find().first();
                if (firstDoc != null) {
                    String code = firstDoc.getString("code");
                    long stockCount = database.getCollection("stock_prices")
                        .countDocuments(new Document("code", code));
                    
                    System.out.println("  示例股票：" + code);
                    System.out.println("  该股票数据量：" + stockCount + " 条");
                    
                    // 统计所有股票的数据量
                    var pipeline = java.util.Arrays.asList(
                        new Document("$group", new Document("_id", "$code").append("count", new Document("$sum", 1))),
                        new Document("$sort", new Document("count", -1)),
                        new Document("$limit", 10)
                    );
                    
                    System.out.println("\n  数据量 Top 10 股票:");
                    database.getCollection("stock_prices").aggregate(pipeline)
                        .forEach(doc -> System.out.println("    " + doc.getString("_id") + ": " + doc.getInteger("count") + " 条"));
                }
            }
            
            assertTrue(count >= 0, "查询应成功");
        } catch (Exception e) {
            fail("查询失败：" + e.getMessage());
        }
    }
}
