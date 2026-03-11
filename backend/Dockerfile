# 运行阶段：直接运行预先打包好的后端 JAR（由 CI 先执行 mvn package）
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# 创建非 root 用户
RUN groupadd -r stock && useradd -r -g stock stock

# 从构建上下文中复制已打包好的后端 JAR（backend/target 下）
COPY backend/target/*.jar app.jar

# 设置文件所有权
RUN chown stock:stock app.jar

# 切换到非 root 用户
USER stock

# 暴露端口
EXPOSE 8080

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
