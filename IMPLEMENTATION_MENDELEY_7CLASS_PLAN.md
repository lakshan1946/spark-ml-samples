# 7-Class Lyrics Classification Plan (Mendeley Dataset)

## Goal
Upgrade the current lyrics module from binary genre classification to 7-class classification using:
- pop
- country
- blues
- jazz
- reggae
- rock
- hip hop

Required outcomes:
1. Train/test split = 80/20.
2. Spark MLlib pipeline for multi-class lyrics classification.
3. Web UI where user pastes lyrics and gets visual probabilities (chart).

## Current Project Baseline
- Backend: Spring Boot in `api` module.
- ML logic: Spark pipeline services in `spark-driver`.
- Executor-side transformations: `spark-distributed-library`.
- Existing lyrics endpoint currently designed around binary classes.

## Dataset Details
Input file: `dataset/Mendeley dataset.csv`

Observed columns include:
- `artist_name`, `track_name`, `release_date`
- `genre` (target label)
- `lyrics` (raw text)
- engineered numeric columns such as `dating`, `violence`, `danceability`, `energy`, etc.
- `topic`, `age`

Notes:
- First column is an index-like unnamed field; ignore it.
- Some values may contain encoding artifacts in names; enforce UTF-8 read.
- We will treat `genre` as the supervised label and filter to the required 7 classes only.

## High-Level Architecture Changes
1. Add a new 7-class lyrics pipeline service (keep old binary pipeline untouched for compatibility).
2. Add train and predict endpoints dedicated to the new pipeline.
3. Save trained model under a new directory in `training-set/lyrics/model-7class/`.
4. Update static frontend page to include:
   - text area for lyrics input
   - predict button
   - visual probability chart (bar or pie)

## Data Preparation Strategy
1. Load CSV with header and schema inference (or explicit schema).
2. Keep only rows where:
   - `genre` in {pop, country, blues, jazz, reggae, rock, hip hop}
   - `lyrics` is non-null and non-empty.
3. Normalize text:
   - lower-case
   - remove punctuation/noise
   - optional stop-word filtering
4. Split dataset with `randomSplit([0.8, 0.2], seed)`.
5. Build string label mapping using `StringIndexer` on `genre`.

## MLlib Pipeline Design
Recommended baseline pipeline:
1. `RegexTokenizer` on `lyrics` -> `tokens`
2. `StopWordsRemover` -> `filteredTokens`
3. `HashingTF` -> `rawFeatures`
4. `IDF` -> `features`
5. `StringIndexer` for label -> `label`
6. Classifier (start with one):
   - Option A: `LogisticRegression` with multinomial setup
   - Option B: `OneVsRest` + linear classifier
   - Option C: `RandomForestClassifier`

Recommended first implementation: multinomial Logistic Regression for speed and solid baseline.

## Training and Evaluation
Train flow:
1. Build/train pipeline on 80% split.
2. Evaluate on 20% split.
3. Compute and log:
   - accuracy
   - weighted precision
   - weighted recall
   - weighted F1
   - confusion matrix (optional but useful)
4. Persist fitted pipeline model to `training-set/lyrics/model-7class/`.
5. Persist label index mapping for UI/API readability (index -> genre).

## Prediction Flow
For one lyrics input:
1. Build a one-row DataFrame with input text.
2. Transform using saved pipeline model.
3. Return:
   - predicted genre label
   - per-class probabilities as name/value pairs
   - optional confidence score (max probability)

## API Contract (Proposed)
### Train endpoint
- Method: `GET`
- Path: `/lyrics7/train`
- Response:
  - split sizes
  - metrics
  - model path

### Predict endpoint
- Method: `POST`
- Path: `/lyrics7/predict`
- Request body: raw text or JSON with lyrics field
- Response example:
```
{
  "predictedGenre": "rock",
  "confidence": 0.81,
  "probabilities": [
    {"genre": "pop", "value": 0.04},
    {"genre": "country", "value": 0.02},
    {"genre": "blues", "value": 0.03},
    {"genre": "jazz", "value": 0.01},
    {"genre": "reggae", "value": 0.02},
    {"genre": "rock", "value": 0.81},
    {"genre": "hip hop", "value": 0.07}
  ]
}
```

## Web UI Plan
Location: existing static page in `api/src/main/resources/static/index.html`.

Add a new section/card:
1. Multi-line lyrics input box.
2. Predict button.
3. Result panel:
   - predicted genre + confidence text
   - chart (bar chart recommended for 7 classes)

Visualization library options:
- Chart.js (simple and lightweight)
- ECharts (more advanced)

Recommended: Chart.js bar chart with class probabilities.

## Implementation Steps (Execution Order)
1. Add new configuration properties in application config:
   - mendeley dataset path
   - new model directory path
   - optional split seed
2. Implement new service in `spark-driver` for 7-class train/predict.
3. Add API service/controller wrappers in `api` module.
4. Add/extend DTOs for probability response.
5. Update frontend to call `/lyrics7/predict` and draw chart.
6. Add launcher/runtime property overrides in `start-local.ps1` for new paths.
7. Run end-to-end test from UI.

## Validation Checklist
- Dataset loads without encoding errors.
- Exactly 7 classes present after filtering.
- Train/test split is approximately 80/20.
- Model trains and saves successfully.
- Predict endpoint returns probabilities for all 7 classes.
- UI renders visual chart and updates on each prediction.

## Risks and Mitigations
1. Class imbalance across genres
- Mitigation: inspect class counts, use class weights or stratified approach if needed.

2. Noisy/short lyrics text
- Mitigation: minimum token threshold, fallback messaging for short inputs.

3. Legacy Spark/Windows constraints
- Mitigation: keep `winutils` + `file:///` jar URI setup from launcher.

## Next Deliverables
After this document, implementation can proceed in phases:
1. Backend 7-class pipeline + endpoints.
2. Frontend visual prediction component.
3. End-to-end tests and metric report.
