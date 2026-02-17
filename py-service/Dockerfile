# ===============================
# Stage 1: Base Image
# ===============================
FROM python:3.11-slim as base

# Set environment variables
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    curl \
    git \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# ===============================
# Stage 2: Dependencies
# ===============================
FROM base as dependencies

# Copy requirements
COPY requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir -r requirements.txt

# ===============================
# Stage 3: Application
# ===============================
FROM base as production

# Create non-root user
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

# Copy installed packages
COPY --from=dependencies /usr/local/lib/python3.11/site-packages /usr/local/lib/python3.11/site-packages
COPY --from=dependencies /usr/local/bin /usr/local/bin

# Copy application code
COPY --chown=appuser:appgroup app/ ./app/
COPY --chown=appuser:appgroup models/ ./models/
COPY --chown=appuser:appgroup data/ ./data/
COPY --chown=appuser:appgroup logs/ ./logs/

# Create model and data directories
RUN mkdir -p /app/models /app/data /app/logs && \
    chown -R appuser:appgroup /app/

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8001

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8001/health || exit 1

# Run application
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
