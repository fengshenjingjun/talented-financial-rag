"""
BGE-Reranker REST API server.
Wraps sentence-transformers cross-encoder for query-document relevance scoring.
"""
from fastapi import FastAPI
from pydantic import BaseModel
from sentence_transformers import CrossEncoder
from typing import List
import uvicorn
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="BGE-Reranker Service")

# Load model once at startup
MODEL_NAME = "BAAI/bge-reranker-v2-m3"
logger.info(f"Loading reranker model: {MODEL_NAME}")
model = CrossEncoder(MODEL_NAME, max_length=512)
logger.info("Reranker model loaded successfully")


class RerankRequest(BaseModel):
    query: str
    documents: List[str]
    model: str = MODEL_NAME
    top_n: int = 5


class RerankResult(BaseModel):
    index: int
    relevance_score: float


class RerankResponse(BaseModel):
    results: List[RerankResult]


@app.post("/rerank", response_model=RerankResponse)
async def rerank(request: RerankRequest):
    pairs = [(request.query, doc) for doc in request.documents]
    scores = model.predict(pairs)

    results = [
        RerankResult(index=i, relevance_score=float(score))
        for i, score in enumerate(scores)
    ]
    results.sort(key=lambda x: x.relevance_score, reverse=True)

    return RerankResponse(results=results[:request.top_n])


@app.get("/health")
async def health():
    return {"status": "UP", "model": MODEL_NAME}


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8001, log_level="info")
