########## 运行阶段：使用 CI 预先构建好的 JAR ##########
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# 创建非 root 用户
RUN groupadd -r stock && useradd -r -g stock stock

# 拷贝由 GitHub Actions 预先打好的 JAR（在构建上下文中命名为 app.jar）
COPY app.jar app.jar

# 设置文件所有权
RUN chown stock:stock app.jar

# 切换到非 root 用户
USER stock

# 暴露端口
EXPOSE 8080

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
