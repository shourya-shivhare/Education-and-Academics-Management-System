from fastapi import Depends, FastAPI, Header, HTTPException

from .config import settings
from .model import ModelNotReadyError, predict
from .schemas import RiskRequest, RiskResponse

app = FastAPI(title="Education Management Internal ML Service", version="1.0.0")


def require_internal_key(x_internal_key: str | None = Header(default=None)) -> None:
    if x_internal_key != settings.internal_key:
        raise HTTPException(status_code=401, detail="Invalid internal service key.")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP", "service": "ai-service"}


@app.get("/model/status", dependencies=[Depends(require_internal_key)])
def model_status() -> dict[str, str | bool]:
    try:
        from .model import load_model

        artifact = load_model()
        return {
            "ready": True,
            "model_version": str(artifact.get("model_version", "unknown")),
        }
    except ModelNotReadyError as exc:
        return {"ready": False, "message": str(exc)}


@app.post(
    "/predict/risk",
    response_model=RiskResponse,
    dependencies=[Depends(require_internal_key)],
)
def risk_prediction(request: RiskRequest) -> RiskResponse:
    try:
        return RiskResponse(**predict(request.student_id, request.term_id))
    except LookupError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except ModelNotReadyError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
