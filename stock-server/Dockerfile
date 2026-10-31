# AI_GENERATE_START------------------
FROM maven:3.9.9-eclipse-temurin-17 AS backend-build

WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY stock-server/pom.xml ./stock-server/pom.xml
COPY stock-server/src/ ./stock-server/src/
RUN mvn -B -DskipTests -f stock-server/pom.xml package

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
COPY --from=backend-build /workspace/stock-server/target/app.jar /app/app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=5 \
    CMD bash -c 'echo > /dev/tcp/127.0.0.1/8080'

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
# AI_GENERATE_END------------------
