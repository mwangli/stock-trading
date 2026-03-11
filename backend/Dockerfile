# 构建阶段：使用维护中的 Eclipse Temurin 17 镜像
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# 复制父 POM 和后端模块 POM（构建上下文需为仓库根目录）
COPY pom.xml ./pom.xml
COPY backend/pom.xml ./backend/pom.xml

# 复制后端源码
COPY backend/src ./backend/src

# 预下载依赖并构建后端模块
RUN mvn -pl backend -am dependency:go-offline -B
RUN mvn -pl backend -am clean package -DskipTests -B

# 运行阶段：使用精简版 Eclipse Temurin 17 JRE 镜像
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# 创建非 root 用户
RUN groupadd -r stock && useradd -r -g stock stock

# 复制构建好的后端 jar 包
COPY --from=builder /app/backend/target/*.jar app.jar

# 设置文件所有权
RUN chown stock:stock app.jar

# 切换到非 root 用户
USER stock

# 暴露端口
EXPOSE 8080

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
