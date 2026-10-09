# AI_GENERATE_START --
FROM python:3.14-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /workspace

RUN groupadd --system stockmodels \
    && useradd --system --gid stockmodels --home-dir /workspace stockmodels

COPY pyproject.toml README.md /workspace/
COPY src /workspace/src

RUN python -m pip install --upgrade pip \
    && python -m pip install . \
    && mkdir -p /workspace/runtime/datasets /workspace/runtime/runs /workspace/runtime/artifacts /workspace/runtime/cache \
    && chown -R stockmodels:stockmodels /workspace

USER stockmodels

ENTRYPOINT ["python", "-m", "stock_models"]
CMD ["--help"]
# AI_GENERATE_END --
