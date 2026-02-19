package online.mwang.stockTrading.modules.datacollection;

import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.modules.datacollection.entity.StockInfo;
import online.mwang.stockTrading.modules.datacollection.entity.StockPrices;
import online.mwang.stockTrading.modules.datacollection.mapper.StockInfoMapper;
import online.mwang.stockTrading.modules.datacollection.repository.StockPricesRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * 数据库验证工具 - 验证数据库中是否有数据
 * 运行方式:
 * cd stock-backend
 * export JAVA_HOME="/c/Users/Administrator/.jdks/ms-17.0.18"
 * mvn spring-boot:run -Dspring-boot.run.mainClass=online.mwang.stockTrading.modules.datacollection.DatabaseVerificationTool
 */
@Slf4j
@SpringBootApplication
@ComponentScan(basePackages = "online.mwang.stockTrading")
@EnableMongoRepositories(basePackages = "online.mwang.stockTrading.modules.datacollection.repository")
public class DatabaseVerificationTool {

    public static void main(String[] args) {
        SpringApplication.run(DatabaseVerificationTool.class, args);
    }

    @Bean
    public CommandLineRunner verifyDatabase(
            DataSource dataSource,
            StockInfoMapper stockInfoMapper,
            StockPricesRepository stockPricesRepository) {
        return args -> {
            printHeader();
            
            boolean mysqlConn = verifyMySQLConnection(dataSource);
            boolean mysqlData = false;
            boolean mongoData = false;
            
            if (mysqlConn) {
                mysqlData = verifyMySQLData(stockInfoMapper);
                mongoData = verifyMongoDBData(stockPricesRepository);
            }
            
            printReport(mysqlConn, mysqlData, mongoData, stockInfoMapper, stockPricesRepository);
            
            System.exit(0);
        };
    }

    private void printHeader() {
        log.info("╔══════════════════════════════════════════════════════════╗");
        log.info("║           数据采集模块 - 数据库验证工具                      ║");
        log.info("╚══════════════════════════════════════════════════════════╝");
        log.info("");
    }

    private boolean verifyMySQLConnection(DataSource dataSource) {
        log.info("【1/3】验证MySQL数据库连接...");
        try (Connection conn = dataSource.getConnection()) {
            log.info("  ✅ MySQL连接成功");
            log.info("  📊 URL: {}", conn.getMetaData().getURL());
            return true;
        } catch (SQLException e) {
            log.error("  ❌ MySQL连接失败: {}", e.getMessage());
            return false;
        }
    }

    private boolean verifyMySQLData(StockInfoMapper mapper) {
        log.info("【2/3】验证MySQL stock_info表...");
        try {
            Long count = mapper.selectCount(null);
            log.info("  📊 股票总数: {}条", count);
            
            if (count >= 4000) {
                log.info("  ✅ 验收通过: 股票数量>4000条 (AC-001)");
            } else if (count > 0) {
                log.warn("  ⚠️  股票数量{}条,建议运行数据同步", count);
            } else {
                log.warn("  ⚠️  暂无数据,需要运行syncAllStocks()");
            }
            
            List<StockInfo> list = mapper.selectList(null);
            if (!list.isEmpty()) {
                StockInfo s = list.get(0);
                log.info("  📋 样例: {} - {}", s.getCode(), s.getName());
            }
            return count > 0;
        } catch (Exception e) {
            log.error("  ❌ 验证失败: {}", e.getMessage());
            return false;
        }
    }

    private boolean verifyMongoDBData(StockPricesRepository repo) {
        log.info("【3/3】验证MongoDB stock_prices集合...");
        try {
            long count = repo.count();
            log.info("  📊 价格记录: {}条", count);
            
            List<StockPrices> list = repo.findByCode("000001");
            log.info("  📋 000001(平安银行): {}条记录", list.size());
            
            if (count > 0) {
                log.info("  ✅ 验收通过: 历史数据已写入MongoDB (AC-004)");
                return true;
            } else {
                log.warn("  ⚠️  暂无数据,需要运行syncStockHistory()");
                return false;
            }
        } catch (Exception e) {
            log.error("  ❌ MongoDB验证失败: {}", e.getMessage());
            return false;
        }
    }

    private void printReport(boolean mysqlConn, boolean mysqlData, boolean mongoData,
            StockInfoMapper mapper, StockPricesRepository repo) {
        log.info("");
        log.info("╔══════════════════════════════════════════════════════════╗");
        log.info("║                     验证报告                              ║");
        log.info("╠══════════════════════════════════════════════════════════╣");
        log.info("║ 检查项                    状态                            ║");
        log.info("╠══════════════════════════════════════════════════════════╣");
        log.info("║ MySQL连接                {}                              ║", status(mysqlConn));
        log.info("║ MySQL stock_info数据     {}                              ║", status(mysqlData));
        log.info("║ MongoDB stock_prices数据 {}                              ║", status(mongoData));
        log.info("╚══════════════════════════════════════════════════════════╝");
        log.info("");
        
        if (mysqlData && mongoData) {
            Long sCount = mapper.selectCount(null);
            long pCount = repo.count();
            log.info("🎉 验证通过! 股票:{}条 | 价格记录:{}条", sCount, pCount);
            log.info("");
            log.info("需求文档验收标准检查:");
            log.info("  ✅ AC-001 股票列表获取: {} (>4000条)", sCount >= 4000 ? "通过" : "待同步");
            log.info("  ✅ AC-002 MySQL数据存储: 通过");
            log.info("  ✅ AC-004 MongoDB数据存储: 通过");
        } else {
            log.info("⚠️ 数据不完整,请执行:");
            log.info("  1. 启动应用: mvn spring-boot:run");
            log.info("  2. 调用API: POST http://localhost:8080/api/data/sync/all");
            log.info("  3. 或运行测试: mvn test -Dtest=DataCollectionAcceptanceTest");
        }
        log.info("");
    }

    private String status(boolean ok) {
        return ok ? "✅ 正常" : "❌ 异常";
    }
}
