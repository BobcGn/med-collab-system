# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Monorepo for a **medical collaboration and medical imaging analysis system**. Six services:

| Module | Tech | Port | Purpose |
|---|---|---|---|
| `api-gateway/` | Kotlin/Ktor | 8088 | Edge routing, JWT auth, request forwarding |
| `auth-service/` | Kotlin/Ktor | 8081 | Auth, users, hospitals, departments, WebSocket notifications |
| `patient-service/` | Kotlin/Ktor | 8082 | Patient records CRUD, search, statistics |
| `metric-service/` | Kotlin/Ktor | 8083 | Imaging orchestration, AI pipeline, report generation (PDF) |
| `segmentation-service/` | Python/FastAPI | 8099 | U-Net image segmentation inference |
| `frontend/` | React 19 + Vite 8 | 5173 | Web UI |

Shared DTOs, enums, and JWT utils in `shared/`. Docs in `docs/`.

## Core Constraint: AI Boundary

Hard rule (see `docs/hospital-ai-boundary-policy.md`):
- **U-Net** (`segmentation-service`) does image segmentation and structured analysis
- **Report tool** generates formal PDF reports from validated structured data
- **LLM (DeepSeek)** only explains existing structured results — never does segmentation, diagnosis, or report finalization
- Failure at any stage returns manual-review guidance, never an LLM fallback

When touching the imaging/report pipeline, inspect these first:
- `metric-service/src/main/kotlin/aiagent/strategy/StrategyGraph.kt`
- `metric-service/src/main/kotlin/aiagent/validation/MetricAiConversationGuard.kt`
- `metric-service/src/main/kotlin/aiagent/tools/MedicalImageAnalyzerTool.kt`
- `metric-service/src/main/kotlin/aiagent/tools/ReportGenerateTool.kt`
- `metric-service/src/main/kotlin/aiagent/tools/ToolPayloads.kt`
- `metric-service/src/main/kotlin/aiagent/tools/SegmentationServiceClient.kt`

## Commands

### Backend (Gradle)
```
./gradlew test                                              # all tests
./gradlew :auth-service:test                                # single module
./gradlew :metric-service:installDist                       # build distribution
```

### Frontend
```
npm --prefix frontend run dev
npm --prefix frontend run build
npm --prefix frontend run preview
```

### Segmentation Service (Python)
```
cd segmentation-service && pip install -e ".[dev]"          # install with dev deps
cd segmentation-service && pip install -e ".[unet]"         # install with torch
cd segmentation-service && pytest                           # run tests
cd segmentation-service && ruff check .                     # lint
cd segmentation-service && scripts/run-dev.sh               # start dev server
```

### Full Stack
```
scripts/quick-start.sh                                      # build & start all
scripts/quick-start.sh --without-segmentation               # skip Python service
scripts/quick-start.sh --frontend-mode dev                  # Vite dev mode
scripts/quick-start.sh --segmentation-backend torch_unet    # real U-Net
```

### Manual Verification (after start)
```
curl http://localhost:8083/health
curl http://localhost:8099/health
curl http://localhost:8099/api/v1/models/current
```
Logs go to `.run/logs/<service>.log`.

## Key Environment Variables

- `METRIC_SEGMENTATION_SERVICE_ENABLED` — metric-service calls segmentation (default `false`). Only enable when segmentation has real weights loaded.
- `METRIC_SEGMENTATION_SERVICE_URL` — default `http://127.0.0.1:8099`
- `METRIC_SEGMENTATION_SERVICE_TIMEOUT_SECONDS` — default `30`
- `SEGMENTATION_BACKEND` — `torch_unet` (default) or `mock`
- `SEGMENTATION_SERVICE_MODEL_WEIGHTS_PATH` — path to model weights

## Architecture Notes

- All frontend requests go through `api-gateway`, which authenticates at the edge
- Auth is JWT-based (HMAC256), shared secret across all services
- `metric-service` orchestrates imaging: receives images → calls segmentation-service → generates PDF → persists results
- Python service is self-contained with clean layered architecture under `app/`: `api/` > `services/` > `inference/` > `preprocessing/` > `postprocessing/`
- API payload changes must be updated across: Kotlin route/service/repo, shared DTOs, frontend API client, Python schemas
- Frontend has no test harness yet — at minimum run `npm --prefix frontend run build` after client changes

## Source of Truth Per Area

- `README.md` — repo layout, startup flow, default ports
- `scripts/quick-start.sh` — authoritative local startup
- `frontend/package.json` — actual frontend toolchain (root `package.json` only proxies into `frontend/`)
- `segmentation-service/pyproject.toml` — Python deps, pytest config, ruff config
- `AGENTS.md` — supplemental agent instructions (snapshot, commands, per-area sources)
