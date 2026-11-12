# CLAUDE.md

Guidance for Claude Code contributors. Read [AGENTS.md](AGENTS.md) for repository-wide rules, commands and generated-artifact exclusions. This repository is a medical collaboration prototype and research-validation system, **not an accepted hospital/clinical deployment**.

## Read the right document

- [README](README.md): prerequisites, local startup, ports, default-versus-research behavior.
- [Documentation index](docs/README.md) and [API reference](docs/api-reference.md): current module guides and real endpoint contracts, including known defects and unverified behavior.
- [architecture.md](architecture.md) and applicable module `SPEC.md`: authoritative responsibilities, decisions and trust boundaries. Existing specs remain draft until human review; documentation synchronization does not activate them.
- [Hospital AI policy](docs/hospital-ai-boundary-policy.md): hard constraints. The integration-plan document is a historical proposal/implementation comparison, not the current protocol.
- [scripts/quick-start.sh](scripts/quick-start.sh): actual full-stack startup; it skips tests, inherits configuration and does not start a database server.
- [frontend/package.json](frontend/package.json) and [segmentation-service/pyproject.toml](segmentation-service/pyproject.toml): actual toolchains/dependencies, not legacy root node_modules.

## Current implementation

Four Java21/Kotlin/Ktor backends run on default ports auth8081, patient8082, metric8083 and gateway8088. Python/FastAPI segmentation is separate on8099; React19/Vite8 frontend uses5173 in quick-start, while standalone preview defaults4173. `shared` is a library with no server port/run entry.

REST normally uses the gateway, but metric clients can fall back to direct calls. Notification/AI WebSockets connect directly to auth/metric; Ktor gateway has no WS proxy. Do not describe the gateway as the only security boundary.

HTTP/WS image requests call `executeControlledMetricReportPipeline`, **not the Koog strategy graph**. Text responses are fixed scope messages, not an implemented multi-turn LLM explanation service. A configured DeepSeek Provider does not make the route run it.

`METRIC_SEGMENTATION_SERVICE_ENABLED` defaults false, using legacy local heuristics. Enabling Python startup or choosing torch_unet does not mean trained weights are loaded or metric remote inference is enabled. Legacy formal-looking PDF output and its HU-like/mm/blood-flow/risk rules are not medically validated measurements.

The research path preserves `research`/`ULTRASOUND`, pixel geometry, null physical measurements/uncalibrated confidence and a non-diagnostic `_RESEARCH_REPORT`. Never turn model loading, hashes or quality PASS into clinical qualification. LLM output must not replace analysis, failed segmentation or formal report facts; failure returns failure/manual review.

PDF creation occurs before browser confirmation. Analysis persistence and registration of the existing report are separate requests; the unused graph's registration node only logs. There is no atomic end-to-end registration, reliable retry/checkpoint or complete provenance preservation across tools, frontend and database DTOs.

## Cross-layer and safety checks

Before changing the controlled flow, inspect Routing/Application, StrategyGraph, MetricAiConversationGuard, MedicalImageAnalyzerTool, ReportGenerateTool, ToolPayloads, ResearchSegmentationPayload and SegmentationServiceClient; see the exact paths in AGENTS.md.

API changes require joint review of Kotlin routes/services, shared DTOs, frontend consumers and Python schemas. JSON is not globally uniform: Python uses snake_case; metric tool/POST/storage uses `kind`, ordinary GET defaults to `type`. Nullable `GeneralMetric.confidence`/millimetres must not be replaced by zero or fixed95. See module APIs rather than inventing a universal wrapper or endpoints from DTO/helper names.

JWT, menu roles and caller hospital/patient fields do not prove resource authorization. Existing public admin registration, partial-only checks, direct metric CRUD, debug-token logging, raw path/URL loading and container configuration are documented gaps, not security designs to copy. Do not reproduce real credentials or patient data in documentation/tests/logs.

## Common validation commands

Run from repository root unless noted; these are choices, not a command batch that must start every service.

```bash
./gradlew :shared:test :patient-service:test :metric-service:test
npm --prefix frontend ci --include=optional
npm --prefix frontend run build
```

Python uses its module venv and `.[dev,training]`; exact pytest/Ruff/JUnit gate commands are in [segmentation README](segmentation-service/README.md). Core tests must actually execute, not skip for missing Torch. auth/gateway currently have no test source; NO-SOURCE is not coverage. Real Python→Kotlin→PDF integration is explicit opt-in, not established by its default skip.

Documentation-only changes can validate links, syntax and schema examples without starting services/DB or retraining. Do not rescore/tune the sealed BUSI test set for documentation updates. Keep data, weights, generated reports and runtime evidence ignored; even historically tracked vendor/generated docs are not editing targets. Never activate specs, submit a review or merge merely because documentation or CI passes.
