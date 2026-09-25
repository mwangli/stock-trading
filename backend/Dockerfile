# AI_GENERATE_START -
ARG MAVEN_IMAGE=maven:3.9.11-eclipse-temurin-17
ARG JRE_IMAGE=eclipse-temurin:17.0.16_8-jre-jammy

FROM ${MAVEN_IMAGE} AS builder

WORKDIR /workspace

COPY pom.xml ./pom.xml
COPY backend/pom.xml ./backend/pom.xml
COPY frontend-v2/pom.xml ./frontend-v2/pom.xml

RUN mvn -pl backend -am dependency:go-offline -DskipTests

COPY backend/src ./backend/src

RUN mvn -pl backend -am clean package -DskipTests \
    && cp backend/target/*.jar /workspace/app.jar

FROM ${JRE_IMAGE}

WORKDIR /app

RUN groupadd --system stock \
    && useradd --system --gid stock --home-dir /app stock \
    && mkdir -p /app/.tmp /models \
    && chown -R stock:stock /app /models

COPY --from=builder --chown=stock:stock /workspace/app.jar /app/app.jar

USER stock

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
    CMD bash -c 'echo > /dev/tcp/127.0.0.1/8080' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
# AI_GENERATE_END -
