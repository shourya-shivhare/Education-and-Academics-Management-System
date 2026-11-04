from typing import Any

from pydantic import BaseModel, Field


class RiskRequest(BaseModel):
    student_id: int = Field(gt=0)
    term_id: int = Field(gt=0)


class RiskResponse(BaseModel):
    student_id: int
    term_id: int
    at_risk_probability: float
    at_risk: bool
    model_version: str
    features: dict[str, Any]
