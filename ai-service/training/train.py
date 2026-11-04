"""Time-aware training for the leakage-safe student risk model.

The database contains synthetic coursework data. This script deliberately keeps
the latest term untouched until final evaluation.
"""

from __future__ import annotations

import json
import os
import sys
from datetime import date
from pathlib import Path
from typing import Any

import joblib
import numpy as np
import pandas as pd
from imblearn.over_sampling import SMOTE
from imblearn.pipeline import Pipeline
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import HistGradientBoostingClassifier, RandomForestClassifier
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import GridSearchCV, StratifiedKFold
from sklearn.preprocessing import OneHotEncoder, StandardScaler
from sqlalchemy import create_engine, text

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from app.model import FEATURE_COLUMNS, IQRClipper  # noqa: E402


ROOT = Path(__file__).resolve().parents[1]
ARTIFACT_DIR = ROOT / "artifacts"
PRECISION_FLOOR = float(os.getenv("ML_PRECISION_FLOOR", "0.50"))

CATEGORICAL_COLUMNS = ["program_id", "year_of_study"]
NUMERIC_COLUMNS = [column for column in FEATURE_COLUMNS if column not in CATEGORICAL_COLUMNS]


def database_url() -> str:
    return os.getenv(
        "ML_DATABASE_URL",
        "mysql+pymysql://ml_readonly:ChangeMe_ml_2026!@localhost:3306/edu_db",
    )


def load_dataset() -> pd.DataFrame:
    engine = create_engine(database_url(), pool_pre_ping=True)
    query = text("SELECT * FROM v_ml_features")
    labels = text("SELECT student_id, term_id, at_risk FROM v_ml_labels")
    try:
        with engine.connect() as connection:
            features = pd.read_sql(query, connection)
            outcome = pd.read_sql(labels, connection)
    finally:
        engine.dispose()
    data = features.merge(outcome, on=["student_id", "term_id"], how="inner")
    data["start_date"] = pd.to_datetime(data["term_start_date"])
    data["at_risk"] = data["at_risk"].astype(int)
    return data.sort_values(["start_date", "term_id", "student_id"]).reset_index(drop=True)


def make_preprocessor() -> ColumnTransformer:
    numeric = Pipeline(
        [
            ("imputer", SimpleImputer(strategy="median")),
            ("iqr_clip", IQRClipper()),
            ("scale", StandardScaler()),
        ]
    )
    categorical = Pipeline(
        [
            ("imputer", SimpleImputer(strategy="most_frequent")),
            ("one_hot", OneHotEncoder(handle_unknown="ignore", sparse_output=False)),
        ]
    )
    return ColumnTransformer(
        [
            ("numeric", numeric, NUMERIC_COLUMNS),
            ("categorical", categorical, CATEGORICAL_COLUMNS),
        ],
        remainder="drop",
    )


def model_specs() -> list[tuple[str, Any, dict[str, list[Any]]]]:
    return [
        (
            "logistic_regression",
            LogisticRegression(max_iter=2000, random_state=42),
            {"model__C": [0.1, 1.0, 10.0]},
        ),
        (
            "random_forest",
            RandomForestClassifier(random_state=42, n_jobs=-1),
            {"model__n_estimators": [200], "model__max_depth": [None, 8]},
        ),
        (
            "hist_gradient_boosting",
            HistGradientBoostingClassifier(random_state=42),
            {"model__learning_rate": [0.05, 0.1], "model__max_leaf_nodes": [15, 31]},
        ),
    ]


def build_pipeline(model: Any, balance: str) -> Pipeline:
    sampler: Any = SMOTE(random_state=42) if balance == "smote" else "passthrough"
    return Pipeline(
        [
            ("preprocess", make_preprocessor()),
            ("sampler", sampler),
            ("model", model),
        ]
    )


def split_by_term(data: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    terms = list(data.sort_values("start_date")["term_id"].drop_duplicates())
    if len(terms) < 3:
        raise RuntimeError("At least three chronological terms are required for train/validation/test.")
    test_term = terms[-1]
    validation_term = terms[-2]
    fit_terms = terms[:-2]
    return (
        data[data["term_id"].isin(fit_terms)].copy(),
        data[data["term_id"] == validation_term].copy(),
        data[data["term_id"] == test_term].copy(),
    )


def threshold_for_precision(y_true: pd.Series, probabilities: np.ndarray) -> tuple[float, dict[str, float]]:
    from sklearn.metrics import precision_recall_curve

    precision, recall, thresholds = precision_recall_curve(y_true, probabilities)
    candidates = np.where(precision[:-1] >= PRECISION_FLOOR)[0]
    if len(candidates) == 0:
        index = int(np.argmax(precision[:-1]))
    else:
        index = int(candidates[np.argmax(recall[:-1][candidates])])
    threshold = float(thresholds[index]) if len(thresholds) else 0.5
    return threshold, {"validation_precision": float(precision[index]), "validation_recall": float(recall[index])}


def main() -> None:
    from sklearn.calibration import CalibratedClassifierCV
    from sklearn.metrics import precision_score, recall_score

    data = load_dataset()
    if data["at_risk"].nunique() < 2:
        raise RuntimeError("Training requires both at-risk and non-at-risk labels.")
    fit, validation, held_out = split_by_term(data)
    if fit["at_risk"].value_counts().min() < 5:
        raise RuntimeError("The fitting data needs at least five rows in each class for 5-fold CV.")

    X_fit, y_fit = fit[FEATURE_COLUMNS], fit["at_risk"]
    cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=42)
    rows: list[dict[str, Any]] = []
    candidates: list[tuple[dict[str, Any], Any]] = []

    for model_name, base_model, base_grid in model_specs():
        for balance in ("class_weight_balanced", "smote"):
            model = base_model
            if balance == "class_weight_balanced" and hasattr(model, "class_weight"):
                model = model.set_params(class_weight="balanced")
            elif hasattr(model, "class_weight"):
                model = model.set_params(class_weight=None)
            pipeline = build_pipeline(model, balance)
            search = GridSearchCV(
                pipeline,
                base_grid,
                scoring={"precision": "precision", "recall": "recall", "f1": "f1"},
                refit=False,
                cv=cv,
                n_jobs=-1,
                error_score="raise",
            )
            search.fit(X_fit, y_fit)
            results = pd.DataFrame(search.cv_results_)
            for index, result in results.iterrows():
                candidate = {
                    "model": model_name,
                    "balance": balance,
                    "params": result["params"],
                    "cv_precision": float(result["mean_test_precision"]),
                    "cv_recall": float(result["mean_test_recall"]),
                    "cv_f1": float(result["mean_test_f1"]),
                }
                rows.append(candidate)
                candidates.append((candidate, pipeline.set_params(**result["params"])))

    eligible = [item for item in candidates if item[0]["cv_precision"] >= PRECISION_FLOOR]
    if not eligible:
        eligible = candidates
    selected_meta, selected_pipeline = max(
        eligible,
        key=lambda item: (item[0]["cv_recall"], item[0]["cv_precision"], item[0]["cv_f1"]),
    )
    selected_pipeline.fit(X_fit, y_fit)

    calibrated = CalibratedClassifierCV(selected_pipeline, method="sigmoid", cv=cv, n_jobs=-1)
    calibrated.fit(X_fit, y_fit)
    validation_probability = calibrated.predict_proba(validation[FEATURE_COLUMNS])[:, 1]
    threshold, threshold_metrics = threshold_for_precision(validation["at_risk"], validation_probability)

    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    joblib.dump(
        {
            "model": calibrated,
            "threshold": threshold,
            "model_version": "risk-model-v2",
            "feature_columns": FEATURE_COLUMNS,
        },
        ARTIFACT_DIR / "model.joblib",
    )
    pd.DataFrame(rows).to_csv(ARTIFACT_DIR / "model_comparison.csv", index=False)
    metadata = {
        "version": "risk-model-v2",
        "date": date.today().isoformat(),
        "data_note": "Training data is synthetic coursework data.",
        "features": FEATURE_COLUMNS,
        "selected_model": selected_meta,
        "threshold": threshold,
        "threshold_selection": threshold_metrics,
        "terms": {
            "fit": sorted(map(int, fit["term_id"].unique())),
            "validation": sorted(map(int, validation["term_id"].unique())),
            "held_out": sorted(map(int, held_out["term_id"].unique())),
        },
    }
    (ARTIFACT_DIR / "model_meta.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")

    from training.evaluate import evaluate_frame

    metrics = evaluate_frame(
        calibrated,
        threshold,
        held_out,
        ARTIFACT_DIR,
        comparison=pd.DataFrame(rows),
    )
    metadata["metrics"] = metrics
    (ARTIFACT_DIR / "model_meta.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    precision = precision_score(
        validation["at_risk"], validation_probability >= threshold, zero_division=0
    )
    recall = recall_score(validation["at_risk"], validation_probability >= threshold, zero_division=0)
    print("Honest summary: trained on synthetic data; results are not evidence of production performance.")
    print(f"Selected {selected_meta['model']} with {selected_meta['balance']}.")
    print(f"Validation threshold={threshold:.4f}, precision={precision:.3f}, recall={recall:.3f}.")
    print(f"Held-out latest-term recall={metrics['recall']:.3f}, PR-AUC={metrics['pr_auc']:.3f}.")
    print(f"Artifacts saved under {ARTIFACT_DIR}.")


if __name__ == "__main__":
    main()
