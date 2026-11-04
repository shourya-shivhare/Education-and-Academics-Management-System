import os
from dataclasses import dataclass
from pathlib import Path


SERVICE_ROOT = Path(__file__).resolve().parents[1]


@dataclass(frozen=True)
class Settings:
    database_url: str = os.getenv(
        "ML_DATABASE_URL",
        "mysql+pymysql://ml_readonly:ChangeMe_ml_2026!@localhost:3306/edu_db",
    )
    internal_key: str = os.getenv("ML_INTERNAL_KEY", "dev-internal-secret-change-me")
    model_path: str = os.getenv(
        "ML_MODEL_PATH", str(SERVICE_ROOT / "artifacts" / "model.joblib")
    )


settings = Settings()
