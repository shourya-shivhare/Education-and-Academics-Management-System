# ai-service/

Internal Python service for ML features. It is never exposed to the browser; Spring
Boot is the only browser-facing service and calls internal AI endpoints with the
`X-Internal-Key` header.

## EDA notebook

Install the pinned project dependency set from `requirements.txt`, then open
`training/eda.ipynb`. The notebook connects with the MySQL `ml_readonly` account,
loads `v_ml_features`, joins the separate post-term `v_ml_labels` view, and
produces the required class-balance, distribution, correlation, attendance-band,
and IQR reports.

The repeatable Flyway migration `R__ml_views.sql` keeps all model features
mid-term-only. Final grade F and detention remain label-only in `v_ml_labels`.

## Train and run

After confirming the notebook results and ensuring both label classes exist:

```powershell
$env:ML_SEED_DATABASE_URL = "mysql+pymysql://flyway_user:ChangeMe_flyway_2026!@localhost:3306/edu_db"
python -m training.seed_synthetic
$env:ML_DATABASE_URL = "mysql+pymysql://ml_readonly:<password>@localhost:3306/edu_db"
python -m training.train
uvicorn app.main:app --host 127.0.0.1 --port 8001
```

Training writes `artifacts/model.joblib`, `model_meta.json`, `metrics.json`,
model-comparison CSVs, permutation importance, and ROC/PR images. To reevaluate
the saved artifact against the latest term:

```powershell
python -m training.evaluate
```

The prediction endpoint requires `X-Internal-Key` and returns HTTP 503 until a
model artifact has been trained. It reads features only from `v_ml_features`.
The browser calls the Spring endpoint `/api/risk/predict`; Spring is the only
browser-facing service and forwards the internal key to this service.

Protected API routes:

- `POST /predict` and `POST /predict-batch`
- `GET /model/info` and `GET /model/metrics`
- `POST /train`
- `GET /recommend/{student_id}` and `GET /search?q=...`

`GET /health` is the only unauthenticated route. Swagger is available at
`/docs`; use the `X-Internal-Key` header for protected operations.
