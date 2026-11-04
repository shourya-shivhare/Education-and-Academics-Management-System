from pathlib import Path
from typing import Any

import joblib
import numpy as np
import pandas as pd
from sklearn.base import BaseEstimator, TransformerMixin
from sqlalchemy import create_engine, text

from .config import settings

FEATURE_COLUMNS = [
    "attendance_pct_midterm",
    "internal1_avg_pct",
    "internal2_avg_pct",
    "assignment_submission_rate",
    "prev_term_gpa",
    "cgpa_so_far",
    "backlog_count",
    "fee_delay_days",
    "credits_this_term",
    "program_id",
    "year_of_study",
    "marks_trend",
    "is_first_year",
]


class IQRClipper(BaseEstimator, TransformerMixin):
    """Clip numeric columns to training-set Tukey fences."""

    def fit(self, X: Any, y: Any = None) -> "IQRClipper":
        values = np.asarray(X, dtype=float)
        q1 = np.nanpercentile(values, 25, axis=0)
        q3 = np.nanpercentile(values, 75, axis=0)
        iqr = q3 - q1
        self.lower_ = q1 - 1.5 * iqr
        self.upper_ = q3 + 1.5 * iqr
        return self

    def transform(self, X: Any) -> np.ndarray:
        return np.clip(np.asarray(X, dtype=float), self.lower_, self.upper_)


class ModelNotReadyError(RuntimeError):
    pass


def load_model() -> dict[str, Any]:
    path = Path(settings.model_path)
    if not path.exists():
        raise ModelNotReadyError(
            f"Model artifact not found at {path}. Run training/train.py first."
        )
    return joblib.load(path)


def fetch_features(student_id: int, term_id: int) -> pd.DataFrame:
    engine = create_engine(settings.database_url, pool_pre_ping=True)
    query = text(
        "SELECT student_id, term_id, "
        + ", ".join(FEATURE_COLUMNS)
        + " FROM v_ml_features "
        "WHERE student_id = :student_id AND term_id = :term_id"
    )
    try:
        with engine.connect() as connection:
            frame = pd.read_sql(
                query,
                connection,
                params={"student_id": student_id, "term_id": term_id},
            )
    finally:
        engine.dispose()
    return frame


def predict(student_id: int, term_id: int) -> dict[str, Any]:
    frame = fetch_features(student_id, term_id)
    if frame.empty:
        raise LookupError("No mid-term feature row exists for this student and term.")

    artifact = load_model()
    model = artifact["model"]
    values = frame[FEATURE_COLUMNS].apply(pd.to_numeric, errors="coerce").fillna(0)
    probability = float(model.predict_proba(values)[0][1])
    return {
        "student_id": student_id,
        "term_id": term_id,
        "at_risk_probability": round(probability, 4),
        "at_risk": probability >= float(artifact.get("threshold", 0.5)),
        "model_version": str(artifact.get("model_version", "unknown")),
        "features": frame.iloc[0][FEATURE_COLUMNS].where(pd.notna(frame.iloc[0][FEATURE_COLUMNS]), None).to_dict(),
    }
