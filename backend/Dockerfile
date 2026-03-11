# 构建阶段：使用维护中的 Eclipse Temurin 17 镜像
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# 复制 pom.xml 并下载依赖
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源代码并构建
COPY src ./src
RUN mvn clean package -DskipTests -B

# 运行阶段：使用精简版 Eclipse Temurin 17 JRE 镜像
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# 创建非 root 用户
RUN groupadd -r stock && useradd -r -g stock stock

# 复制构建好的 jar 包
COPY --from=builder /app/target/*.jar app.jar

# 设置文件所有权
RUN chown stock:stock app.jar

# 切换到非 root 用户
USER stock

# 暴露端口
EXPOSE 8080

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
