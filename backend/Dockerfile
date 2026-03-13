########## 构建阶段：使用 Maven 编译打包 ##########
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build

# 先复制 pom.xml 以利用 Docker 缓存
COPY pom.xml .

# 再复制源码
COPY src ./src

# 使用 Maven 编译并打包（跳过测试）
RUN mvn -B clean package -DskipTests


########## 运行阶段：仅携带已打包的 JAR ##########
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# 创建非 root 用户
RUN groupadd -r stock && useradd -r -g stock stock

# 从构建阶段复制打包好的 JAR
COPY --from=build /build/target/*.jar app.jar

# 设置文件所有权
RUN chown stock:stock app.jar

# 切换到非 root 用户
USER stock

# 暴露端口
EXPOSE 8080

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
