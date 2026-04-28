# med-collab-system

`med-collab-system` 是一个医疗协作与医学影像分析系统的 monorepo。当前仓库已经包含：

- Kotlin/Ktor 业务服务
- React 前端
- 独立 Python `segmentation-service`
- 医院生产环境下的 AI 边界约束

当前链路的核心原则是：

- `U-Net / segmentation-service` 负责影像分割和结构化分析
- `metric-service` 负责受控编排、正式报告生成和结果登记
- `LLM` 只负责解释既有结构化结果或正式报告，不参与正式医学分析和正式报告定稿

详细边界说明见 [docs/hospital-ai-boundary-policy.md](docs/hospital-ai-boundary-policy.md)。

## 项目结构

```text
med-collab-system/
├── api-gateway/            # 网关服务
├── auth-service/           # 认证服务
├── patient-service/        # 患者信息服务
├── metric-service/         # 影像分析编排、正式报告生成、AI 路由
├── segmentation-service/   # Python/FastAPI U-Net 推理服务
├── frontend/               # 前端界面
├── shared/                 # Kotlin 共享 DTO / 工具
├── docs/                   # 规划与边界文档
└── scripts/                # 启动与辅助脚本
```

## 本地启动前提

本仓库的一键启动脚本不会帮你准备数据库，只会构建并拉起应用进程。启动前请确认：

- Java 已安装，且可运行 Gradle 构建
- Node.js 与 npm 已安装
- 如果需要启动 `segmentation-service`，本机需有 `Python >= 3.11`
- 各服务依赖的数据库、JWT、外部配置已按本地环境准备完成

## 一键启动

推荐直接使用 [scripts/quick-start.sh](scripts/quick-start.sh)：

```bash
scripts/quick-start.sh
```

脚本会执行以下动作：

- 构建前端
- 构建 `auth-service`、`patient-service`、`metric-service`、`api-gateway`
- 为 `segmentation-service` 创建或复用虚拟环境并安装依赖
- 启动全部本地服务
- 将日志写入 `.run/logs/`

默认启动端口：

- `auth-service`: `8081`
- `patient-service`: `8082`
- `metric-service`: `8083`
- `api-gateway`: `8088`
- `segmentation-service`: `8099`
- `frontend`: `5173`

## 常用启动方式

仅启动基础链路，不启动 Python 分割服务：

```bash
scripts/quick-start.sh --without-segmentation
```

跳过构建，直接使用已有产物启动：

```bash
scripts/quick-start.sh --skip-build
```

说明：如果同时启动 `segmentation-service`，此模式要求 `segmentation-service/.venv` 或 `SEGMENTATION_VENV_DIR` 已经准备好。

单独在 `segmentation-service/` 下执行 `scripts/run-dev.sh` 时，脚本会先停止同一 `host/port` 上残留的旧 `uvicorn` 进程，再检查默认 `torch_unet` 后端需要的运行依赖；若缺少 `Pillow` 或 `torch`，会自动执行 `pip install -e ".[unet]"` 后再启动服务。

前端以 Vite dev 模式启动：

```bash
scripts/quick-start.sh --frontend-mode dev
```

使用真实 U-Net 后端启动分割服务：

```bash
scripts/quick-start.sh --segmentation-backend torch_unet
```

说明：该模式通常还需要额外提供 `SEGMENTATION_SERVICE_MODEL_WEIGHTS_PATH`。

如果你需要显式指定 Python 解释器：

```bash
scripts/quick-start.sh --segmentation-python /usr/local/bin/python3.11
```

## 启动脚本环境变量

脚本支持以下环境变量：

- `CLIENT_PORT`
  说明：前端端口，默认 `5173`
- `SEGMENTATION_HOST`
  说明：`segmentation-service` 监听地址，默认 `127.0.0.1`
- `SEGMENTATION_PORT`
  说明：`segmentation-service` 端口，默认 `8099`
- `SEGMENTATION_ENV`
  说明：`segmentation-service` 配置环境，默认 `local`
- `SEGMENTATION_BACKEND`
  说明：分割后端，默认 `torch_unet`
- `SEGMENTATION_PYTHON_BIN`
  说明：用于创建和运行 `segmentation-service` 虚拟环境的 Python
- `SEGMENTATION_VENV_DIR`
  说明：`segmentation-service` 虚拟环境目录，默认 `segmentation-service/.venv`
- `METRIC_SEGMENTATION_SERVICE_ENABLED`
  说明：`metric-service` 是否调用 Python 分割服务，默认 `true`
- `METRIC_SEGMENTATION_SERVICE_URL`
  说明：`metric-service` 调用分割服务的基础地址，默认 `http://127.0.0.1:8099`
- `METRIC_SEGMENTATION_SERVICE_TIMEOUT_SECONDS`
  说明：`metric-service` 等待分割服务响应的超时时间，默认 `30`

## 分割服务说明

`segmentation-service` 当前是独立 Python 服务，不和 Kotlin 模块打包在一起。它的职责是：

- 接收影像输入
- 执行 U-Net/Mock 分割推理
- 返回结构化分割结果
- 输出 mask / overlay / result.json 等工件

`metric-service` 在正式影像分析链路中会调用 `segmentation-service` 的 `POST /api/v1/segment` 接口；若该接口不可用或质量门禁失败，正式报告链路会失败并提示人工复核，不会回退到 LLM 生成医学结论。

单独说明见 [segmentation-service/README.md](segmentation-service/README.md) 和 [docs/segmentation-service-operation-manual.md](docs/segmentation-service-operation-manual.md)。

## 手动验证

启动完成后，可优先检查这些端点：

- `http://localhost:8083/health`
- `http://localhost:8099/health`
- `http://localhost:8099/api/v1/models/current`

脚本日志目录：

- `.run/logs/auth-service.log`
- `.run/logs/patient-service.log`
- `.run/logs/metric-service.log`
- `.run/logs/api-gateway.log`
- `.run/logs/segmentation-service.log`
- `.run/logs/frontend-preview.log` 或 `.run/logs/frontend-dev.log`

## 当前边界说明

医院生产环境下，AI 相关职责已经做了硬边界约束：

- `segmentation-service` / 结构化分析工具不能越权生成正式报告
- `metric-service` 的正式报告工具不能越权重做影像分析
- `LLM` 不能代替 U-Net 生成医学结论，不能在失败时补诊断

如果后续链路继续演进，必须同步更新：

- 策略图
- 工具契约
- 回归测试
- 文档说明
