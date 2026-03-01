package com.stock.dataCollector.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MongoDB 连接测试 - 尝试不同的认证方式
 */
class MongoDBConnectionTest2 {

    // 尝试 1: 不使用 authSource
    private static final String URI_NO_AUTH_SOURCE = "mongodb://admin:Root.123456@124.220.36.95:27017/stock-trading";
    
    // 尝试 2: 使用 stock_trading 作为认证库
    private static final String URI_STOCK_AUTH = "mongodb://admin:Root.123456@124.220.36.95:27017/stock-trading?authSource=stock-trading";

    @Test
    @DisplayName("测试 MongoDB 连接 - 不使用 authSource")
    void testConnectionNoAuthSource() {
        try (MongoClient mongoClient = MongoClients.create(URI_NO_AUTH_SOURCE)) {
            MongoDatabase database = mongoClient.getDatabase("stock_trading");
            Document result = database.runCommand(new Document("ping", 1));
            assertNotNull(result);
            System.out.println("✓ 连接成功（不使用 authSource）!");
        } catch (Exception e) {
            System.out.println("✗ 失败（不使用 authSource）: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("测试 MongoDB 连接 - 使用 stock_trading 认证库")
    void testConnectionWithStockAuth() {
        try (MongoClient mongoClient = MongoClients.create(URI_STOCK_AUTH)) {
            MongoDatabase database = mongoClient.getDatabase("stock_trading");
            Document result = database.runCommand(new Document("ping", 1));
            assertNotNull(result);
            System.out.println("✓ 连接成功（stock_trading 认证库）!");
        } catch (Exception e) {
            System.out.println("✗ 失败（stock_trading 认证库）: " + e.getMessage());
        }
    }
}
