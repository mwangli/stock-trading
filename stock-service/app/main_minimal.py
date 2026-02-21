"""
Minimal FastAPI app for data collection testing only
"""
import sys

# Patch sentiment to avoid PyTorch
sys.modules['app.services.sentiment'] = type(sys)('sentiment')
sys.modules['app.services.sentiment.sentiment_service'] = type(sys)('sentiment_service')

import logging
import os
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

# Configure logging
os.makedirs('logs', exist_ok=True)
logging.basicConfig(level=logging.INFO)

# Import routers
from app.api import data_collection

app = FastAPI(
    title="Stock Data Collection Service",
    description="Minimal service for data collection APIs",
    version="1.0.0"
)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Register router
app.include_router(data_collection.router, prefix="/api/data", tags=["Data Collection"])

@app.get("/")
async def root():
    return {"status": "ok", "service": "Stock Data Collection"}

@app.get("/health")
async def health():
    return {"status": "healthy"}
