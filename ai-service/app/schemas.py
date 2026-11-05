from typing import Any

from pydantic import BaseModel, Field


class PredictionRequest(BaseModel):
    student_id: int = Field(gt=0)
    term_id: int = Field(gt=0)
    features: dict[str, float] | None = None


class BatchPredictionRequest(BaseModel):
    requests: list[PredictionRequest] = Field(min_length=1, max_length=500)


class ContributingFactor(BaseModel):
    factor: str
    impact: float


class PredictionResponse(BaseModel):
    student_id: int
    term_id: int
    probability: float = Field(ge=0, le=1)
    risk_level: str
    top_factors: list[ContributingFactor]
    model_version: str


class SearchResult(BaseModel):
    subject_id: int
    code: str
    title: str
    credits: int
    department_name: str
    score: float


class Recommendation(SearchResult):
    reason: str


class TrainResponse(BaseModel):
    status: str
    model_version: str
    metrics: dict[str, Any]
