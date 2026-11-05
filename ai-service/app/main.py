from contextlib import asynccontextmanager
from typing import Any

from fastapi import Depends, FastAPI, Header, HTTPException, Query
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from .config import settings
from .model import (
    ModelNotReadyError,
    model_info,
    model_manager,
    model_metrics,
    predict,
    recommend_subjects,
    search_subjects,
)
from .schemas import (
    BatchPredictionRequest,
    PredictionRequest,
    PredictionResponse,
    Recommendation,
    SearchResult,
    TrainResponse,
)


@asynccontextmanager
async def lifespan(_: FastAPI):
    model_manager.load()
    yield


app = FastAPI(
    title="Education Management Internal ML Service",
    version="2.0.0",
    lifespan=lifespan,
)


def require_internal_key(
    x_internal_key: str | None = Header(default=None),
) -> None:
    if x_internal_key != settings.internal_key:
        raise HTTPException(status_code=401, detail="Invalid internal service key.")


@app.exception_handler(RequestValidationError)
async def validation_error_handler(_, exc: RequestValidationError) -> JSONResponse:
    return JSONResponse(
        status_code=422,
        content={"detail": "Malformed request.", "errors": exc.errors()},
    )


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "UP",
        "service": "ai-service",
        "model_loaded": model_manager.ready,
    }


def _prediction(request: PredictionRequest) -> PredictionResponse:
    try:
        return PredictionResponse(**predict(request.student_id, request.term_id, request.features))
    except LookupError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except ModelNotReadyError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc


@app.post("/predict", response_model=PredictionResponse, dependencies=[Depends(require_internal_key)])
def prediction(request: PredictionRequest) -> PredictionResponse:
    return _prediction(request)


@app.post("/predict/risk", response_model=PredictionResponse, dependencies=[Depends(require_internal_key)])
def legacy_prediction(request: PredictionRequest) -> PredictionResponse:
    return _prediction(request)


@app.post(
    "/predict-batch",
    response_model=list[PredictionResponse],
    dependencies=[Depends(require_internal_key)],
)
def prediction_batch(request: BatchPredictionRequest) -> list[PredictionResponse]:
    return [_prediction(item) for item in request.requests]


@app.get("/model/info", dependencies=[Depends(require_internal_key)])
def info() -> dict[str, Any]:
    try:
        return model_info()
    except ModelNotReadyError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc


@app.get("/model/metrics", dependencies=[Depends(require_internal_key)])
def metrics() -> dict[str, Any]:
    try:
        return model_metrics()
    except ModelNotReadyError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc


@app.post("/train", response_model=TrainResponse, dependencies=[Depends(require_internal_key)])
def train() -> TrainResponse:
    try:
        from training.train import main as train_main

        train_main()
        model_manager.reload()
        return TrainResponse(
            status="trained",
            model_version=str(model_manager.require().get("model_version", "unknown")),
            metrics=model_metrics(),
        )
    except ModelNotReadyError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except (RuntimeError, OSError, ValueError) as exc:
        raise HTTPException(status_code=500, detail=f"Training failed: {exc}") from exc


@app.get(
    "/recommend/{student_id}",
    response_model=list[Recommendation],
    dependencies=[Depends(require_internal_key)],
)
def recommendations(
    student_id: int,
    limit: int = Query(default=10, ge=1, le=50),
) -> list[Recommendation]:
    try:
        return [Recommendation(**item) for item in recommend_subjects(student_id, limit)]
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.get(
    "/search",
    response_model=list[SearchResult],
    dependencies=[Depends(require_internal_key)],
)
def search(
    q: str = Query(min_length=2, max_length=200),
    limit: int = Query(default=20, ge=1, le=50),
) -> list[SearchResult]:
    return [SearchResult(**item) for item in search_subjects(q.strip(), limit)]
