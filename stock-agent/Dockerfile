# AI_GENERATE_START --
ARG JRE_IMAGE=eclipse-temurin:17.0.16_8-jre-jammy

FROM ${JRE_IMAGE}

WORKDIR /app

RUN groupadd --system stock \
    && useradd --system --gid stock --home-dir /app stock \
    && mkdir -p /app/.tmp \
    && chown -R stock:stock /app

COPY --chown=stock:stock app.jar /app/app.jar
COPY --from=model-files --chown=stock:stock . /app/model-files/embedding/

USER stock

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
    CMD bash -c 'echo > /dev/tcp/127.0.0.1/8081' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
# AI_GENERATE_END --
