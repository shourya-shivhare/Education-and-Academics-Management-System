"""Held-out-term evaluation and diagnostic plots for the risk model."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

import joblib
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from sklearn.inspection import permutation_importance
from sklearn.metrics import (
    accuracy_score,
    average_precision_score,
    confusion_matrix,
    f1_score,
    precision_recall_curve,
    precision_score,
    recall_score,
    roc_auc_score,
    roc_curve,
)

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from app.model import FEATURE_COLUMNS  # noqa: E402
from training.train import load_dataset, split_by_term  # noqa: E402


def evaluate_frame(
    model: Any,
    threshold: float,
    data: pd.DataFrame,
    output_dir: Path,
    comparison: pd.DataFrame | None = None,
) -> dict[str, Any]:
    output_dir.mkdir(parents=True, exist_ok=True)
    X, y = data[FEATURE_COLUMNS], data["at_risk"].astype(int)
    probabilities = model.predict_proba(X)[:, 1]
    predictions = (probabilities >= threshold).astype(int)
    rule_predictions = (data["attendance_pct_midterm"].fillna(100) < 75).astype(int)
    metrics = {
        "accuracy": float(accuracy_score(y, predictions)),
        "precision": float(precision_score(y, predictions, zero_division=0)),
        "recall": float(recall_score(y, predictions, zero_division=0)),
        "f1": float(f1_score(y, predictions, zero_division=0)),
        "roc_auc": float(roc_auc_score(y, probabilities)) if y.nunique() > 1 else None,
        "pr_auc": float(average_precision_score(y, probabilities)),
        "threshold": float(threshold),
        "confusion_matrix": confusion_matrix(y, predictions).tolist(),
        "attendance_rule": {
            "threshold": 75,
            "accuracy": float(accuracy_score(y, rule_predictions)),
            "precision": float(precision_score(y, rule_predictions, zero_division=0)),
            "recall": float(recall_score(y, rule_predictions, zero_division=0)),
            "f1": float(f1_score(y, rule_predictions, zero_division=0)),
        },
        "rows": int(len(data)),
        "data_note": "Held-out results use synthetic coursework data.",
    }
    (output_dir / "metrics.json").write_text(json.dumps(metrics, indent=2), encoding="utf-8")

    if y.nunique() > 1:
        fpr, tpr, _ = roc_curve(y, probabilities)
        plt.figure(figsize=(7, 5))
        plt.plot(fpr, tpr, label=f"Model ROC-AUC={metrics['roc_auc']:.3f}")
        plt.plot([0, 1], [0, 1], "--", color="gray")
        plt.xlabel("False positive rate")
        plt.ylabel("True positive rate")
        plt.title("Held-out latest-term ROC curve")
        plt.legend()
        plt.tight_layout()
        plt.savefig(output_dir / "roc_curve.png", dpi=150)
        plt.close()

    precision, recall, _ = precision_recall_curve(y, probabilities)
    plt.figure(figsize=(7, 5))
    plt.plot(recall, precision, label=f"PR-AUC={metrics['pr_auc']:.3f}")
    plt.axhline(metrics["attendance_rule"]["precision"], linestyle="--", color="gray", label="Attendance rule precision")
    plt.xlabel("Recall")
    plt.ylabel("Precision")
    plt.title("Held-out latest-term precision-recall curve")
    plt.legend()
    plt.tight_layout()
    plt.savefig(output_dir / "pr_curve.png", dpi=150)
    plt.close()

    permutation = permutation_importance(
        model, X, y, scoring="average_precision", n_repeats=10, random_state=42, n_jobs=-1
    )
    importance = pd.DataFrame(
        {"feature": FEATURE_COLUMNS, "importance_mean": permutation.importances_mean, "importance_std": permutation.importances_std}
    ).sort_values("importance_mean", ascending=False)
    importance.to_csv(output_dir / "permutation_importance.csv", index=False)
    plt.figure(figsize=(8, 5))
    top = importance.head(10).sort_values("importance_mean")
    plt.barh(top["feature"], top["importance_mean"], xerr=top["importance_std"])
    plt.title("Permutation importance (average precision)")
    plt.tight_layout()
    plt.savefig(output_dir / "permutation_importance.png", dpi=150)
    plt.close()

    if comparison is not None:
        comparison.to_csv(output_dir / "three_model_comparison.csv", index=False)
    return metrics


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default="artifacts/model.joblib")
    parser.add_argument("--output", default="artifacts")
    args = parser.parse_args()
    artifact = joblib.load(args.model)
    data = load_dataset()
    _, _, held_out = split_by_term(data)
    metrics = evaluate_frame(
        artifact["model"],
        float(artifact["threshold"]),
        held_out,
        Path(args.output),
    )
    print("Honest summary: evaluation is on one latest synthetic term and is not production evidence.")
    print(json.dumps(metrics, indent=2))


if __name__ == "__main__":
    main()
