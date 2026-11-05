from pathlib import Path
from threading import RLock
from typing import Any
import json

import joblib
import numpy as np
import pandas as pd
import shap
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


class IQRClipper:
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


class ModelManager:
    def __init__(self) -> None:
        self._lock = RLock()
        self.artifact: dict[str, Any] | None = None
        self.explainer: Any = None

    @property
    def ready(self) -> bool:
        return self.artifact is not None

    def load(self) -> None:
        path = Path(settings.model_path)
        if not path.exists():
            self.artifact = None
            self.explainer = None
            return
        with self._lock:
            self.artifact = joblib.load(path)
            self.explainer = self._build_explainer(self.artifact["model"])

    def reload(self) -> None:
        self.load()

    def require(self) -> dict[str, Any]:
        if self.artifact is None:
            raise ModelNotReadyError(
                "Model artifact is unavailable. Run POST /train first."
            )
        return self.artifact

    @staticmethod
    def _build_explainer(model: Any) -> Any:
        try:
            pipeline = model
            if hasattr(model, "calibrated_classifiers_"):
                pipeline = model.calibrated_classifiers_[0].estimator
            preprocess = pipeline.named_steps["preprocess"]
            estimator = pipeline.named_steps["model"]
            background = preprocess.transform(
                pd.DataFrame([{column: 0 for column in FEATURE_COLUMNS}])
            )
            return shap.Explainer(
                estimator,
                background,
                feature_names=_transformed_names(preprocess),
            )
        except (AttributeError, KeyError, TypeError, ValueError):
            return None

    def predict_one(self, frame: pd.DataFrame) -> tuple[float, list[dict[str, Any]]]:
        artifact = self.require()
        model = artifact["model"]
        values = frame[FEATURE_COLUMNS].apply(pd.to_numeric, errors="coerce").fillna(0)
        probability = float(model.predict_proba(values)[0][1])
        factors = self._factors(model, values)
        return probability, factors

    def _factors(self, model: Any, values: pd.DataFrame) -> list[dict[str, Any]]:
        try:
            pipeline = model
            if hasattr(model, "calibrated_classifiers_"):
                pipeline = model.calibrated_classifiers_[0].estimator
            transformed = pipeline.named_steps["preprocess"].transform(values)
            shap_values = self.explainer(transformed)
            contribution = np.asarray(shap_values.values)
            if contribution.ndim == 3:
                contribution = contribution[0, :, 1]
            else:
                contribution = contribution[0]
            names = _transformed_names(pipeline.named_steps["preprocess"])
            ranked = np.argsort(np.abs(contribution))[::-1][:3]
            return [
                {
                    "factor": _factor_sentence(names[index], float(contribution[index]), values),
                    "impact": round(float(contribution[index]), 4),
                }
                for index in ranked
            ]
        except (AttributeError, IndexError, KeyError, TypeError, ValueError):
            return []


def _transformed_names(preprocessor: Any) -> list[str]:
    try:
        return list(preprocessor.get_feature_names_out())
    except (AttributeError, ValueError):
        return FEATURE_COLUMNS


def _factor_sentence(name: str, impact: float, values: pd.DataFrame) -> str:
    clean = name.replace("numeric__", "").replace("categorical__", "")
    column = next(
        (feature for feature in FEATURE_COLUMNS if clean == feature or clean.startswith(f"{feature}_")),
        clean,
    )
    readable = column.replace("_", " ")
    direction = "increases" if impact > 0 else "reduces"
    value = values.iloc[0].get(column)
    suffix = f" ({value:g})" if isinstance(value, (int, float, np.number)) else ""
    return f"{readable.title()}{suffix} {direction} predicted at-risk probability."


model_manager = ModelManager()


def load_model() -> dict[str, Any]:
    return model_manager.require()


def _engine():
    return create_engine(settings.database_url, pool_pre_ping=True)


def fetch_features(student_id: int, term_id: int) -> pd.DataFrame:
    query = text(
        "SELECT student_id, term_id, "
        + ", ".join(FEATURE_COLUMNS)
        + " FROM v_ml_features "
        "WHERE student_id = :student_id AND term_id = :term_id"
    )
    engine = _engine()
    try:
        with engine.connect() as connection:
            return pd.read_sql(
                query, connection, params={"student_id": student_id, "term_id": term_id}
            )
    finally:
        engine.dispose()


def predict(student_id: int, term_id: int, overrides: dict[str, float] | None = None) -> dict[str, Any]:
    frame = fetch_features(student_id, term_id)
    if frame.empty:
        raise LookupError("No mid-term feature row exists for this student and term.")
    if overrides:
        for key, value in overrides.items():
            if key in FEATURE_COLUMNS:
                frame.loc[frame.index[0], key] = value
    probability, factors = model_manager.predict_one(frame)
    return {
        "student_id": student_id,
        "term_id": term_id,
        "probability": round(probability, 4),
        "risk_level": risk_level(probability),
        "top_factors": factors,
        "model_version": str(model_manager.require().get("model_version", "unknown")),
    }


def risk_level(probability: float) -> str:
    if probability >= 0.66:
        return "HIGH"
    if probability >= 0.33:
        return "MEDIUM"
    return "LOW"


def _factor_sentence_legacy(name: str, impact: float) -> str:
    return f"{name} contributes {'positively' if impact > 0 else 'negatively'} to risk."


def model_info() -> dict[str, Any]:
    artifact = model_manager.require()
    return {
        "ready": True,
        "model_version": artifact.get("model_version", "unknown"),
        "features": artifact.get("feature_columns", FEATURE_COLUMNS),
        "threshold": artifact.get("threshold", 0.5),
    }


def model_metrics() -> dict[str, Any]:
    artifact = model_manager.require()
    metrics_path = Path(settings.model_path).with_name("metrics.json")
    if not metrics_path.exists():
        return {}
    return json.loads(metrics_path.read_text(encoding="utf-8"))


def _read_catalog() -> pd.DataFrame:
    engine = _engine()
    try:
        with engine.connect() as connection:
            return pd.read_sql(
                text(
                    "SELECT subject_id, code, title, credits, syllabus, "
                    "department_name, prerequisite_codes FROM v_subject_catalog"
                ),
                connection,
            )
    finally:
        engine.dispose()


def search_subjects(query: str, limit: int = 20) -> list[dict[str, Any]]:
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.metrics.pairwise import cosine_similarity

    catalog = _read_catalog()
    if catalog.empty:
        return []
    documents = (
        catalog[["title", "syllabus", "code", "department_name"]]
        .fillna("")
        .astype(str)
        .agg(" ".join, axis=1)
    )
    matrix = TfidfVectorizer(stop_words="english").fit_transform(
        pd.concat([documents, pd.Series([query])], ignore_index=True)
    )
    scores = cosine_similarity(matrix[-1], matrix[:-1]).ravel()
    catalog = catalog.assign(score=scores).sort_values("score", ascending=False)
    return catalog.head(limit).apply(_search_row, axis=1).tolist()


def recommend_subjects(student_id: int, limit: int = 10) -> list[dict[str, Any]]:
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.metrics.pairwise import cosine_similarity

    catalog = _read_catalog()
    if catalog.empty:
        return []
    engine = _engine()
    try:
        with engine.connect() as connection:
            transcript = pd.read_sql(
                text(
                    "SELECT subject_code, subject_title, grade_point, grade "
                    "FROM v_ml_student_profile WHERE student_id = :student_id"
                ),
                connection,
                params={"student_id": student_id},
            )
    finally:
        engine.dispose()
    passed = set(transcript.loc[transcript["grade"] != "F", "subject_code"].dropna())
    taken = set(transcript["subject_code"].dropna())
    eligible = catalog[
        ~catalog["code"].isin(taken)
        & catalog["prerequisite_codes"].fillna("").apply(
            lambda value: all(code.strip() in passed for code in str(value).split(",") if code.strip())
        )
    ].copy()
    if eligible.empty:
        return []
    documents = eligible[["title", "syllabus", "code", "department_name"]].fillna("").astype(str).agg(" ".join, axis=1)
    profile_parts = transcript[transcript["grade_point"].notna()]
    profile = " ".join(
        (row["subject_title"] + " ") * max(1, round(float(row["grade_point"])))
        for _, row in profile_parts.iterrows()
    ) or "foundations academic"
    matrix = TfidfVectorizer(stop_words="english").fit_transform(
        pd.concat([documents, pd.Series([profile])], ignore_index=True)
    )
    scores = cosine_similarity(matrix[-1], matrix[:-1]).ravel()
    eligible = eligible.assign(score=scores).sort_values("score", ascending=False)
    return eligible.head(limit).apply(
        lambda row: {**_search_row(row), "reason": "Matches the student's weighted prior-subject profile and satisfied prerequisites."},
        axis=1,
    ).tolist()


def _search_row(row: pd.Series) -> dict[str, Any]:
    return {
        "subject_id": int(row["subject_id"]),
        "code": str(row["code"]),
        "title": str(row["title"]),
        "credits": int(row["credits"]),
        "department_name": str(row["department_name"]),
        "score": round(float(row["score"]), 4),
    }
