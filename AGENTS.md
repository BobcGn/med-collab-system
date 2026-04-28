# AGENTS.md

## Scope

These instructions apply to the entire repository.

## Project Snapshot

- Monorepo for a medical collaboration and medical imaging workflow.
- Backend: Kotlin 2.2, Ktor 3.3, Gradle multi-module, Java 21.
- Frontend: React 19 with Vite 8 in `frontend/`.
- Imaging service: Python 3.11+ FastAPI app in `segmentation-service/`.
- Shared cross-service DTOs and utilities live in `shared/`.

Top-level modules:

- `auth-service`: authentication, hospitals, departments, users, notifications.
- `patient-service`: patient records and auth-aware access.
- `metric-service`: imaging orchestration, controlled AI flow, report generation, chat history.
- `api-gateway`: edge routing across backend services.
- `frontend`: React client.
- `segmentation-service`: structured segmentation and analysis service.
- `shared`: DTOs, enums, exceptions, JWT utilities, DB bootstrap helpers.

## Hard Domain Constraints

The authoritative policy is `docs/hospital-ai-boundary-policy.md`. Treat it as a hard constraint, not guidance.

Non-negotiable rules:

- LLM output must not replace segmentation, deterministic image analysis, or formal diagnosis.
- Formal medical analysis must come from `segmentation-service` or another deterministic structured-analysis tool.
- Formal report generation must consume validated structured analysis results, not free-form LLM output.
- If structured analysis fails, return failure or manual-review guidance. Do not add an LLM fallback that invents a result.
- If you change the imaging/report pipeline, keep policy, code paths, and tests aligned.

When touching the controlled imaging flow, inspect these files first:

- `metric-service/src/main/kotlin/Routing.kt`
- `metric-service/src/main/kotlin/Application.kt`
- `metric-service/src/main/kotlin/aiagent/strategy/StrategyGraph.kt`
- `metric-service/src/main/kotlin/aiagent/validation/MetricAiConversationGuard.kt`
- `metric-service/src/main/kotlin/aiagent/tools/MedicalImageAnalyzerTool.kt`
- `metric-service/src/main/kotlin/aiagent/tools/ReportGenerateTool.kt`
- `metric-service/src/main/kotlin/aiagent/tools/ToolPayloads.kt`

## Source Of Truth By Area

- Root `README.md`: repo layout, startup flow, default ports, and boundary summary.
- `scripts/quick-start.sh`: authoritative local startup flow for the full stack.
- `frontend/package.json`: frontend scripts and actual client toolchain.
- `frontend/README.md`: confirms the client is pure React. Do not reintroduce Vue.
- `segmentation-service/README.md`: service responsibilities, env vars, run modes.
- `segmentation-service/pyproject.toml`: Python dependencies, pytest config, ruff config.

Use the module-local README or manifest before assuming a command or runtime.

## Editing Guidance

- Prefer small, module-local changes unless a contract truly crosses services.
- If an API payload changes, update all affected layers together:
  - Kotlin route/service/repository code
  - shared DTOs in `shared/` when payloads are reused across services
  - frontend API client code in `frontend/src/lib/`
  - Python request/response schemas in `segmentation-service/app/schemas/`
- Preserve existing backend structure where possible:
  - Ktor wiring in `Application.kt`, `Routing.kt`, `Security.kt`, `Serialization.kt`, `HTTP.kt`
  - persistence split across `database/table`, `database/entity`, `database/repository`
  - business logic in `service/`
- For frontend work, keep changes inside `frontend/src/`. The root `package.json` only proxies scripts into `frontend/`.
- For segmentation work, keep API routes, schemas, orchestration, preprocessing, inference, postprocessing, and quality-gate concerns separated as they already are under `segmentation-service/app/`.

## Generated And Runtime Artifacts

Do not hand-edit generated or local-runtime artifacts unless the task is explicitly about them:

- `build/`
- `*/build/`
- `frontend/dist/`
- `node_modules/`
- `.gradle/`
- `.gradle-home/`
- `.kotlin/`
- `.data/`
- `.run/`
- `chat-histories/`
- `reports/`
- `generated-reports/`
- `segmentation-service/.venv/`
- `__pycache__/`
- `.DS_Store`

The repo may contain local databases, generated PDFs, chat histories, and other runtime outputs. Leave unrelated artifacts alone.

## Commands

Use the narrowest command that validates the area you changed.

Backend:

- `./gradlew test`
- `./gradlew :auth-service:test`
- `./gradlew :patient-service:test`
- `./gradlew :metric-service:test`
- `./gradlew :api-gateway:test`
- `./gradlew :shared:test`
- `./gradlew :auth-service:installDist`
- `./gradlew :patient-service:installDist`
- `./gradlew :metric-service:installDist`
- `./gradlew :api-gateway:installDist`

Frontend:

- `npm --prefix frontend run dev`
- `npm --prefix frontend run build`
- `npm --prefix frontend run preview`

Segmentation service:

- `cd segmentation-service && pip install -e .`
- `cd segmentation-service && pip install -e ".[dev]"`
- `cd segmentation-service && pip install -e ".[unet]"`
- `cd segmentation-service && pytest`
- `cd segmentation-service && ruff check .`
- `cd segmentation-service && scripts/run-dev.sh`

Full stack:

- `scripts/quick-start.sh`
- `scripts/quick-start.sh --without-segmentation`
- `scripts/quick-start.sh --frontend-mode dev`
- `scripts/quick-start.sh --skip-build`

Default local ports from the root README:

- `auth-service`: `8081`
- `patient-service`: `8082`
- `metric-service`: `8083`
- `api-gateway`: `8088`
- `segmentation-service`: `8099`
- `frontend`: `5173`

## Validation Expectations

- Run targeted tests for every code path you change.
- If you change a shared DTO or service contract, validate every consumer you touched.
- If you change the controlled imaging/report flow, validate both Kotlin and Python sides.
- If you change startup or integration behavior, prefer validating with `scripts/quick-start.sh` or the nearest narrower equivalent.
- Frontend currently has build scripts but no obvious test harness; at minimum run `npm --prefix frontend run build` after client changes unless the task is documentation-only.

Useful manual checks after a local start:

- `http://localhost:8083/health`
- `http://localhost:8099/health`
- `http://localhost:8099/api/v1/models/current`

Logs from the quick-start flow are written under `.run/logs/`.

## Practical Notes

- `rg` is not guaranteed to be installed in this environment. Fall back to `find`, `grep`, or `sed` as needed.
- The working tree may contain untracked runtime artifacts. Do not delete or reset them unless the user explicitly asks.
- Root-level Node dependencies are not the source of truth for client architecture; the actual frontend app is under `frontend/` and is React-based.
