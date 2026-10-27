# AI_GENERATE_START--------------
ARG MODEL_IMAGE=models

FROM eclipse-temurin:17.0.16_8-jre-jammy AS models

WORKDIR /app

COPY model-files /app/model-files

FROM ${MODEL_IMAGE}

WORKDIR /app

RUN groupadd --system stock \
    && useradd --system --gid stock --home-dir /app stock \
    && mkdir -p /app/.tmp /data/models /models \
    && chown -R stock:stock /app /data /models

COPY --chown=stock:stock stock-server/app.jar /app/app.jar
COPY --chown=stock:stock documents /app/documents

USER stock

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
    CMD bash -c 'echo > /dev/tcp/127.0.0.1/8080' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
# AI_GENERATE_END--------------
