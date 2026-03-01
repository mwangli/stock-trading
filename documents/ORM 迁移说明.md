# ORM 框架技术栈变更说明

## 文档信息

| 项目 | 内容 |
|------|------|
| 变更类型 | 技术栈调整 |
| 变更日期 | 2026-03-01 |
| 影响范围 | 后端持久层 |

## 变更概述

将项目 ORM 框架从 **MyBatis-Plus** 统一迁移到 **Spring Data JPA**。

## 变更原因

### 原有方案 (MyBatis-Plus)
- 需要手动管理数据库表结构
- 需要编写 SQL 脚本或依赖数据库迁移工具
- Repository 接口需要手动实现 CRUD 方法

### 新方案 (Spring Data JPA)
- ✅ **自动建库建表**: 通过 `ddl-auto` 配置自动管理表结构
- ✅ **零 SQL 脚本**: 无需手动编写建表语句
- ✅ **Repository 自动化**: 继承 JpaRepository 即可获得完整 CRUD 功能
- ✅ **与 Spring Boot 深度集成**: 配置简单，开箱即用
- ✅ **领域模型驱动**: 使用 JPA 注解定义实体，代码即文档

## 技术对比

| 特性 | MyBatis-Plus | Spring Data JPA |
|------|--------------|-----------------|
| 表结构管理 | 手动 SQL 脚本 | 自动建表 (ddl-auto) |
| CRUD 方法 | 手动编写或 BaseMapper | JpaRepository 自动提供 |
| 查询方法 | XML 或注解 SQL | 方法名自动推导 |
| 关联映射 | 手动配置 | 注解自动映射 |
| 学习曲线 | 中等 | 低 |
| 适合场景 | 复杂 SQL 查询 | 领域模型驱动开发 |

## 变更内容

### 1. pom.xml 依赖调整

**移除:**
```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-boot-starter</artifactId>
    <version>3.5.5</version>
</dependency>
```

**保留:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

### 2. application.yml 配置

**JPA 配置 (启用自动建表):**
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update  # 自动建库建表
    show-sql: true
    properties:
      hibernate:
        format_sql: true  # 格式化 SQL
```

**ddl-auto 选项说明:**
- `create`: 每次启动都重新建表（数据会丢失）
- `create-drop`: 启动时建表，关闭时删表
- `update`: 自动更新表结构（推荐开发环境）
- `validate`: 仅验证表结构，不创建
- `none`: 禁用 DDL（推荐生产环境）

### 3. Repository 接口改造

**之前 (MyBatis-Plus):**
```java
@Mapper
public interface StockRepository {
    StockInfo selectByStockCode(String code);
    List<StockInfo> selectAll();
    int insert(StockInfo info);
    int update(StockInfo info);
}
```

**之后 (Spring Data JPA):**
```java
public interface StockRepository extends JpaRepository<StockInfo, Long> {
    Optional<StockInfo> findByStockCode(String code);
    List<StockInfo> findAll();
    // save(), delete(), findById() 等方法自动提供
}
```

### 4. Entity 实体类改造

**之前 (MyBatis-Plus):**
```java
@Data
@TableName("stock_info")
public class StockInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String stockCode;
    private String stockName;
    // ...
}
```

**之后 (Spring Data JPA):**
```java
@Data
@Entity
@Table(name = "stock_info")
public class StockInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "stock_code", unique = true)
    private String stockCode;
    
    @Column(name = "stock_name")
    private String stockName;
    // ...
}
```

### 5. MongoDB 配置 (保持不变)

```yaml
spring:
  data:
    mongodb:
      host: 124.220.36.95
      port: 27017
      database: stock_trading
```

**Repository 接口:**
```java
public interface PriceRepository extends MongoRepository<StockPrice, String> {
    List<StockPrice> findByStockCodeAndTradeDateBetween(
        String code, LocalDate start, LocalDate end
    );
}
```

## 迁移步骤

### Phase 1: 依赖更新 (已完成)
- [x] 从 pom.xml 移除 MyBatis-Plus 依赖
- [x] 确认 spring-boot-starter-data-jpa 已引入

### Phase 2: 配置更新 (已完成)
- [x] 更新 application.yml 的 JPA 配置
- [x] 启用 ddl-auto: update

### Phase 3: 实体类改造 (待执行)
- [ ] 将所有 `@TableName` 改为 `@Entity` + `@Table`
- [ ] 将所有 `@TableId` 改为 `@Id` + `@GeneratedValue`
- [ ] 添加 `@Column` 注解指定列名和约束

### Phase 4: Repository 改造 (待执行)
- [ ] 将所有 Mapper 接口改为继承 JpaRepository
- [ ] 使用方法名自动推导查询
- [ ] 移除 XML 映射文件

### Phase 5: Service 层调整 (待执行)
- [ ] 调整注入的 Repository 类型
- [ ] 使用 JpaRepository 提供的 save()/findAll() 等方法
- [ ] 移除 SQL 相关代码

### Phase 6: 测试验证 (待执行)
- [ ] 启动应用验证自动建表
- [ ] 运行单元测试
- [ ] 运行集成测试
- [ ] 验证数据读写正确性

## 注意事项

### 1. 数据库初始化

首次启动时，JPA 会自动创建所有 Entity 对应的表。确保：
- 数据库已创建（可通过 `createDatabaseIfNotExist=true` 自动创建）
- 数据库用户有 DDL 权限（开发环境）

### 2. 表结构变更

`ddl-auto: update` 会自动：
- 创建新表
- 添加新列
- 修改列类型（兼容情况下）

**不会自动执行:**
- 删除列
- 重命名表/列
- 修改约束（如 unique、not null）

需要手动调整时，使用 Flyway 或 Liquibase。

### 3. 性能优化

JPA 默认配置适合大多数场景，复杂查询可考虑：
- 使用 `@Query` 自定义 JPQL
- 使用 `@EntityGraph` 优化关联加载
- 启用二级缓存

### 4. 生产环境建议

生产环境建议：
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # 或 none，禁用自动建表
```

使用 Flyway/Liquibase 管理表结构变更。

## 影响范围

### 受影响的模块

| 模块 | 影响程度 | 说明 |
|------|----------|------|
| databus | 高 | 所有 Repository 需改造 |
| models | 中 | 部分实体类需调整 |
| strategy | 低 | 主要使用服务层接口 |
| executor | 高 | 交易相关 Repository 需改造 |

### 不受影响的部分

- Controller 层
- 前端应用
- 数据库连接配置
- MongoDB 使用
- Redis 使用

## 参考资料

- [Spring Data JPA 官方文档](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/)
- [Hibernate ORM 文档](https://hibernate.org/orm/documentation/)
- [JPA 注解详解](https://www.baeldung.com/jpa-annotations)

## 后续工作

1. **实体类改造**: 按照 JPA 规范调整所有实体类
2. **Repository 改造**: 继承 JpaRepository，使用方法名推导
3. **测试验证**: 确保所有功能正常
4. **性能调优**: 根据需要优化查询和缓存
5. **文档更新**: 更新开发文档和 API 文档
