# AI_GENERATE_START--------
ARG MODEL_RUNTIME_IMAGE

FROM ${MODEL_RUNTIME_IMAGE}

COPY --chown=stock:stock app.jar /app/app.jar

USER stock

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
    CMD bash -c 'echo > /dev/tcp/127.0.0.1/8080' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
# AI_GENERATE_END--------
