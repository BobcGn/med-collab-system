# segmentation-service

独立的 Python / FastAPI 分割服务：读取单张栅格图，执行 U-Net、原尺寸四连通 BFS 后处理与质量门禁，返回结构化区域并按配置保存工件。单张和顺序批量接口均已实现；本地 BUSI 准备、FP32 训练和评测已有 CLI，训练过程自动导出最佳研究模型包。

**课程模型仅用于科研：`research` / `ULTRASOUND` / 二值区域分割，不做良恶性分类或诊断。** 毫米测量为 null、风险未评估，区域平均前景概率不是临床校准置信度。权重加载、质量门禁 PASS、模型哈希或 PDF 生成均不证明临床有效性。

## 文档入口

- [API.md](API.md)：全部路由、snake_case 请求/响应、输出选项、工件与错误契约；不要把 HTTP 200 一律当作分析成功。
- [操作手册](../docs/segmentation-service-operation-manual.md)：环境、配置、prepare → train → val → 冻结 → test 一次、部署与排错。
- [模块 SPEC](SPEC.md) · [训练 SPEC](app/training/SPEC.md) · [医疗 AI 边界政策](../docs/hospital-ai-boundary-policy.md)：权威边界、已冻结实验及其限制。
- [仓库入口](../README.md) · [整栈启动脚本](../scripts/quick-start.sh)：仅在需要 Kotlin / 前端 / 数据库链路时再阅读。

本模块不管理业务账号、患者档案或报告定稿；结构化分析失败时不得由 LLM 补造结果。训练 SPEC 记录了历史 BUSI/MPS 实验及非诊断科研 PDF 联调证据，不代表本次文档同步重新执行了这些验证，也不等于完整前端、鉴权或数据库登记验收。

## 安装：Python >= 3.11 与虚拟环境

从仓库根目录进入模块；不要假设系统的 `python3` 已满足版本要求。

```bash
cd segmentation-service
python3.11 -m venv .venv
.venv/bin/python -m pip install -e .
```

基础安装包含 FastAPI、Uvicorn、NumPy、Pillow，不包含 Torch。按用途选择 [pyproject.toml](pyproject.toml) 中的 extras：

| 用途 | 安装命令 | 内容 |
| --- | --- | --- |
| Torch U-Net 推理 | `.venv/bin/python -m pip install -e '.[unet]'` | Torch |
| 本地训练与曲线 | `.venv/bin/python -m pip install -e '.[training]'` | Torch、Matplotlib |
| 开发检查 | `.venv/bin/python -m pip install -e '.[dev]'` | pytest、HTTPX、Ruff；不自动包含 Torch |

需要全部工具时：

```bash
.venv/bin/python -m pip install -e '.[unet,training,dev]'
```

以下启动与训练说明都是供操作者审阅后执行的模板，本次文档同步未执行安装、启动、训练或评分。

## 安全独立启动：先用显式 mock 联调

在 `segmentation-service/` 内，用前台 Uvicorn 绑定回环地址；不需要 Kotlin、数据库或模型数据。以下显式选择仓库 local 配置、清空继承的模型路径并关闭工件，仅用于契约联调，输出不具有医学意义：

```bash
env \
  SEGMENTATION_SERVICE_ENVIRONMENT=local \
  SEGMENTATION_SERVICE_CONFIG_FILE="" \
  SEGMENTATION_SERVICE_HOST=127.0.0.1 \
  SEGMENTATION_SERVICE_PORT=8099 \
  SEGMENTATION_SERVICE_API_PREFIX=/api/v1 \
  SEGMENTATION_SERVICE_INFERENCE_BACKEND=mock \
  SEGMENTATION_SERVICE_MODEL_MANIFEST_PATH="" \
  SEGMENTATION_SERVICE_MODEL_WEIGHTS_PATH="" \
  SEGMENTATION_SERVICE_ALLOW_RANDOM_WEIGHTS=false \
  SEGMENTATION_SERVICE_DEVICE=cpu \
  SEGMENTATION_SERVICE_RETURN_ARTIFACTS=false \
  .venv/bin/python -m uvicorn app.main:app --host 127.0.0.1 --port 8099 --app-dir .
```

Ctrl+C 停止本进程。端口冲突时先确认占用者，不自动杀进程。应用的 HOST/PORT 设置不代替 Uvicorn 的监听参数。

在另一个终端、仅在服务已获授权启动后检查：

```bash
curl --fail-with-body http://127.0.0.1:8099/health
curl --fail-with-body http://127.0.0.1:8099/api/v1/models/current
```

`/health` 只读原始配置，不是 readiness；`/models/current` 首次解析依赖时才校验/加载模型，成功也不是推理性能或临床资格证明。请求样例及完整含义见 [API.md](API.md)。

[run-dev.sh](scripts/run-dev.sh) **不是无副作用的帮助/诊断入口**：它会终止匹配同 host/port 命令模式的旧 reload 进程，必要时强杀；即使配置 mock 也检查 Torch，缺依赖时自动安装 `.[unet]`。它没有帮助参数解析，不要用 `--help` 探测。优先使用上面的显式前台命令。

## 研究模型包

训练自动按原尺寸 val macro Dice 的严格改善选择最佳 epoch，并导出 `best_model/manifest.json` 及其引用的 `weights-哈希前16位.pt`。没有独立 export CLI，也没有 resume CLI；`checkpoints/last.pt` 保存进度不等于存在可恢复训练入口。

服务通过 `SEGMENTATION_SERVICE_MODEL_MANIFEST_PATH` 或 TOML 的 `model.manifest_path` 加载整个研究包，校验预处理、权重 SHA-256 与严格 state_dict 结构。包强制 Torch、禁止随机权重，并固定尺寸、通道和概率阈值；损坏时拒绝，不回退 mock。只支持仓库 UNet2D 契约，不自动适配任意网络、TorchScript 或 ONNX。

以下使用**新实验目录占位路径**；先按[手册](../docs/segmentation-service-operation-manual.md)准备有效模型包，不要指向或覆盖已封存的 `var/coursework/`。1024 是已冻结 BUSI 实验的 BFS 设置，不是通用默认或任意新数据的最优值。

```bash
env \
  SEGMENTATION_SERVICE_ENVIRONMENT=local \
  SEGMENTATION_SERVICE_CONFIG_FILE="" \
  SEGMENTATION_SERVICE_HOST=127.0.0.1 \
  SEGMENTATION_SERVICE_PORT=8099 \
  SEGMENTATION_SERVICE_API_PREFIX=/api/v1 \
  SEGMENTATION_SERVICE_INFERENCE_BACKEND=torch_unet \
  SEGMENTATION_SERVICE_MODEL_MANIFEST_PATH="$PWD/var/experiments/busi-doc-demo-001/augmented/best_model/manifest.json" \
  SEGMENTATION_SERVICE_ALLOW_RANDOM_WEIGHTS=false \
  SEGMENTATION_SERVICE_DEVICE=cpu \
  SEGMENTATION_SERVICE_MIN_REGION_PIXELS=1024 \
  SEGMENTATION_SERVICE_RETURN_ARTIFACTS=true \
  SEGMENTATION_SERVICE_ARTIFACTS_DIR="$PWD/var/experiments/busi-doc-demo-001/service-artifacts" \
  .venv/bin/python -m uvicorn app.main:app --host 127.0.0.1 --port 8099 --app-dir .
```

需要 MPS 时显式改 DEVICE 为 `mps` 并检查描述符的实际设备：**训练/评测 CLI 的 `auto` 优先 MPS，再 CPU；服务默认 CPU，服务的 `auto` 也落到 CPU，不可用的 MPS/CUDA 请求可能回到 CPU。**

manifest 中的权重哈希和响应中的 `bundle_manifest_sha256` 用于一致性追踪；后者是完整 manifest 文件字节的摘要，不是数字签名、许可/权利链认证或模型效度认证。数据、权重、患者信息和生成结果不得入 Git；模块 `var/` 已忽略，但忽略规则不替代保密与授权。

## 配置摘要

配置顺序是：**Settings 默认值 ← 一个所选 TOML ← 非空 `SEGMENTATION_SERVICE_*` 环境变量 ← 研究包强制契约**。

- `SEGMENTATION_SERVICE_CONFIG_FILE` 选择自定义 TOML；否则由 ENVIRONMENT 选择仓库 local/test TOML，未知环境名回退选择 local 文件。不是两份 TOML 叠加；所选文件缺失时使用默认值加环境覆盖。
- 不自动读取 `.env` / `.env.example`。设置与模型编排器均有进程内缓存，改配置、权重或包后需重启，没有热更新。
- 模型/工件相对路径按模块根目录解析；自定义 CONFIG_FILE 的相对路径则按进程工作目录解析，建议用绝对路径。

| 配置项 | 要点 |
| --- | --- |
| `MODEL_MANIFEST_PATH` / `MODEL_WEIGHTS_PATH` | 都需加 `SEGMENTATION_SERVICE_` 前缀；研究包优先于裸权重。裸权重模式用途仍为 unspecified |
| `INFERENCE_BACKEND` | local 默认 `torch_unet`，但缺权重且默认允许随机权重时仍可随机前向；不是已训练模型 |
| `MIN_REGION_PIXELS` | 研究包无显式覆盖为 **16**；旧 Settings/local 为 **24**；test TOML 显式为 **8**；冻结实验部署必须显式 **1024** |
| `RETURN_ARTIFACTS` / `ARTIFACTS_DIR` | 控制服务端文件写入；路径不是 URL，无下载接口 |
| `API_PREFIX` / `HOST` / `PORT` | Python 默认 `/api/v1`、`127.0.0.1`、`8099`；Kotlin 客户端固定 `/api/v1/segment` 后缀 |

随仓库 local TOML 的旧 24 不算研究包显式覆盖；自定义 TOML、显式 Settings 或非空环境变量算。**[.env.example](.env.example) 的 `SEGMENTATION_SERVICE_MIN_REGION_PIXELS=24` 一旦导入 shell 就会显式覆盖研究默认 16**；不要盲目加载它，更不能忘记冻结实验的 1024。详见[手册配置章节](../docs/segmentation-service-operation-manual.md#4-配置解析与覆盖)。

整栈脚本使用外层 `SEGMENTATION_HOST/PORT/ENV/BACKEND`，会覆盖同义的 `SEGMENTATION_SERVICE_*` 子进程值。`METRIC_SEGMENTATION_SERVICE_ENABLED` 是独立开关，源码默认 **false**；启动 Python 或选择 Torch 不会自动启用 metric 远程分析。其余接入条件见手册及 [API.md](API.md)。

## 开发回归与核心测试门禁

先安装 `.[dev,training]`（或上面的全部 extras），再从本模块目录执行。以下是供开发者选择运行的命令，**本次文档同步没有执行**；合成测试会训练小夹具、创建临时图像/工件，不下载或重新评分 BUSI，也不验证医学效果。

```bash
(
  set -eu
  mkdir -p var/ci
  OMP_NUM_THREADS=2 MKL_NUM_THREADS=2 MPLBACKEND=Agg \
    .venv/bin/python -m pytest --tb=short -q -ra --junitxml=var/ci/pytest.xml
  .venv/bin/python ../.github/scripts/check_python_training_tests.py var/ci/pytest.xml
  .venv/bin/ruff check --config pyproject.toml . ../.github/scripts/check_python_training_tests.py
)
```

[JUnit门禁](../.github/scripts/check_python_training_tests.py)要求核心训练、指标及模型包模块实际执行，不接受缺依赖导致的缺失/跳过/失败；不硬编码全套测试数量。公共CI使用CPU Torch及合成数据，不携带BUSI或权重，不能替代另行显式的真实模型HTTP/Kotlin/PDF验收。测试范围见[API证据索引](API.md)与[工作流](../.github/workflows/pr-checks.yml)。

## 训练与运行边界

从[手册的数据门禁与 prepare](../docs/segmentation-service-operation-manual.md#5-本地数据门禁与-prepare)开始；**prepare 会覆盖输出清单，train 需要新/空输出目录，evaluate 默认 `split=test`，日常必须显式 `--split val`。** train 默认 batch=4，而冻结实验显式 batch=2，不能把模板条件当作 CLI 默认。不根据已见 test 调参；已有封存成绩、数据清理与限制统一查[训练 SPEC](app/training/SPEC.md)，不要通过重复评分“验证文档”。

当前没有原生 DICOM、异步队列/回调、readiness、模型热更新、URL/本地目录白名单、统一资源限额或本服务鉴权。`return_metrics` 尚未落实关闭行为，重复请求 ID 可导致工件冲突 500，批量异常中止且已写工件不回滚，详见 [API.md](API.md)。Docker/Compose 仍有构建输入缺失、非 root 与 healthcheck 未配置、外部网络和真实包挂载等未验收问题，不推荐一键生产部署。
