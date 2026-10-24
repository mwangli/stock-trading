# AI_GENERATE_START -
ARG JRE_IMAGE=eclipse-temurin:17.0.16_8-jre-jammy

FROM ${JRE_IMAGE}

LABEL com.stock.image.role="agent-model-runtime"

WORKDIR /app

RUN groupadd --system stock \
    && useradd --system --gid stock --home-dir /app stock \
    && mkdir -p /app/.tmp /app/model-files/embedding \
    && chown -R stock:stock /app

COPY --chown=stock:stock . /app/model-files/embedding/

USER stock
# AI_GENERATE_END -
