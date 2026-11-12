# segmentation-service

`segmentation-service` 是一个独立的 Python/FastAPI 推理服务，职责是接收影像分割请求、执行 U-Net 推理流水线，并向上游返回结构化分析结果。

当前模块已经包含：

- FastAPI 应用入口
- 配置加载与日志初始化
- 健康检查接口
- 模型信息接口
- 单张 / 批量分割接口
- 图像加载、预处理、后处理和质量门禁
- 默认启用真实 `torch_unet` 后端，同时保留 `mock` 作为显式联调/测试降级选项
- 从路由到 orchestrator 的完整调用链
- 本地 BUSI 数据准备、MPS/CPU 训练、原尺寸评测与科研模型包导出
- 课程模型包的用途/哈希/模态校验、像素测量和跨服务非诊断报告

**已完成真实 BUSI 的 MPS 训练、参数封存、内部测试评测，以及 Python → Kotlin → 非诊断科研 PDF 的实际联调；这些证据不构成患者独立验证或医学有效性证明。**

## 推荐启动方式

如果你要启动整套系统，优先在仓库根目录执行：

```bash
./scripts/quick-start.sh
```

该脚本会自动为本服务准备虚拟环境并启动进程。

## 单独启动

### 1. 准备环境

本服务要求：

- `Python >= 3.11`
- 基础依赖：`fastapi`、`uvicorn`、`numpy`、`pillow`
- 若启用真实 U-Net：额外安装 `torch`

推荐命令：

```bash
python3.11 -m venv .venv
source .venv/bin/activate
pip install -e .
```

安装真实 Torch U-Net 运行依赖：

```bash
pip install -e ".[unet]"
```

### 2. 配置环境变量

可参考 [.env.example](.env.example)。

常用变量：

- `SEGMENTATION_SERVICE_ENVIRONMENT=local`
- `SEGMENTATION_SERVICE_HOST=127.0.0.1`
- `SEGMENTATION_SERVICE_INFERENCE_BACKEND=torch_unet`
- `SEGMENTATION_SERVICE_PORT=8099`
- `SEGMENTATION_SERVICE_MODEL_WEIGHTS_PATH=/absolute/path/to/model.pt`
- `SEGMENTATION_SERVICE_MODEL_MANIFEST_PATH=/absolute/path/to/best_model/manifest.json`（课程模型包，优先于裸权重配置）

### 3. 启动服务

可直接执行：

```bash
scripts/run-dev.sh
```

该脚本会优先使用当前激活的虚拟环境，其次回退到 `segmentation-service/.venv`；启动前会先停止同一 `host/port` 上残留的旧 `uvicorn` 进程，并在发现 `Pillow`、`torch` 等运行依赖缺失时自动执行 `pip install -e ".[unet]"`。

或手动执行：

```bash
uvicorn app.main:app --reload --host 127.0.0.1 --port 8099 --app-dir .
```

## 核心接口

- `GET /health`
- `GET /api/v1/models/current`
- `POST /api/v1/segment`

## 配置来源

使用 `SEGMENTATION_SERVICE_CONFIG_FILE` 指定的 TOML，未指定时使用 `configs/<environment>/app.toml`，再由环境变量覆盖；两个 TOML 不是叠加关系。默认环境为 `local`。

课程模型包派生的结构、尺寸、通道和概率阈值优先于旧配置，强制真实 Torch、禁用随机权重和未标定的物理尺寸。BFS 默认最小区域为 16；如需覆盖，请显式配置并确保离线评测与服务使用同一值。

## 后端说明

- `mock`
  - 用于联调和契约测试，不依赖真实模型权重
- `torch_unet`
  - 使用 PyTorch 2D U-Net 执行真实推理
  - 本地默认配置即使用该后端
  - 需要安装 `.[unet]` 依赖
  - 建议配置 `SEGMENTATION_SERVICE_MODEL_WEIGHTS_PATH` 以加载训练权重
  - 若未配置权重且 `allow_random_weights=true`，仍会执行真实 U-Net 前向，但输出仅适合联调

补充说明：

- 当前仓库中的 `metric-service` 本地默认不会自动把 `segmentation-service` 作为正式报告链路输入
- 显式设置 `METRIC_SEGMENTATION_SERVICE_ENABLED=true` 后才启用远程分析；调用方还校验状态、身份、模态及区域等条件
- 课程包始终标记 `research`，只产生像素测量及非诊断科研报告，不能因 `weights_loaded=true` 或 PASS 而转为临床正式结果

## 运行验证

启动后可检查：

```bash
curl http://localhost:8099/health
curl http://localhost:8099/api/v1/models/current
```

`/health` 不是模型就绪检查；`/models/current` 会加载/校验当前模型，课程包还返回 `intended_use`、`bundle_manifest_sha256` 和 `supported_image_types`。

## UNet 课程实验

以下命令均在 `segmentation-service/` 内执行。需要 Python 3.11+；本机默认 `python3` 可能仍为 3.9。不要把数据、权重、患者信息或生成结果提交到 Git，示例统一写入已忽略的 `var/`。

### 环境和数据门禁

```bash
python3.11 -m venv .venv
.venv/bin/python -m pip install -e '.[dev,training]'
.venv/bin/python -c 'import torch; print(torch.__version__, torch.backends.mps.is_available())'
```

准备工具只接受已解压的 `benign/`、`malignant/`、`normal/` PNG 目录，不提供未经核实的自动下载或 ZIP 解压。应先阅读原发布页和许可、保留来源版本与归档校验记录；`--source-verified` 只是人工确认声明，不会替你核实网页。本次用户确认的来源为 [Kaggle 镜像](https://www.kaggle.com/datasets/aryashah2k/breast-ultrasound-images-dataset)，页面标示 CC0；保留[原始论文](https://doi.org/10.1016/j.dib.2019.104863)署名，不将镜像声明当作独立权利链认证。

在确实核实后设置 `BUSI_DATA_ROOT`、`BUSI_SOURCE_URL`、`BUSI_LICENSE`，再执行：

```bash
: "${BUSI_DATA_ROOT:?请设置已核实来源的本地数据目录}"
: "${BUSI_SOURCE_URL:?请设置已核实的原始发布地址}"
: "${BUSI_LICENSE:?请填写实际许可，不要猜测}"
.venv/bin/python -m app.training.prepare \
  --data-root "$BUSI_DATA_ROOT" --output var/coursework/dataset.json \
  --source-url "$BUSI_SOURCE_URL" --license "$BUSI_LICENSE" --source-verified
```

同图多个 mask 合并，重复灰度像素分组后按类别近似 70/15/15 划分。没有患者 ID 时不能声称患者独立验证。清单和所有源图/标注须保持不变；评测会检查训练时记录的哈希。

本次原始数据在 `var/coursework/raw/Dataset_BUSI_with_GT/`。用户批准排除同像素不同 mask 的 benign (433) / malignant (145) 后，派生数据在 `var/coursework/curated-v1/Dataset_BUSI_with_GT/`，清理记录在同级 `curation.json`。清单为 778 图，544 train / 118 val / 116 test；原始文件未删除或修改。

### 对照训练与评测

先做少量 epoch/小样本的流程检查，再使用固定配置进行真实实验。下面的 20 epoch、batch=2 是可调整的起始参数，不是精度或耗时承诺。每次使用新/空输出目录，避免覆盖旧实验或输入清单。本次下列输出已存在，重复实验须另命名，不覆盖已封存记录或根据已见测试成绩调参。

```bash
.venv/bin/python -m app.training.train \
  --manifest var/coursework/dataset.json --output-dir var/coursework/plain \
  --device mps --epochs 20 --batch-size 2 --seed 42 --patience 5
.venv/bin/python -m app.training.train \
  --manifest var/coursework/dataset.json --output-dir var/coursework/augmented \
  --device mps --epochs 20 --batch-size 2 --seed 42 --patience 5 --augment
```

`--device auto` 优先 MPS，缺失时使用 CPU 并记录；显式 `mps` 不可用则失败。训练保存 `run.json`、`history.csv/json`、`curves.png`、`checkpoints/last.pt` 和 `best_model/manifest.json`。最佳权重依据原尺寸验证集 Dice，不根据测试集选择；checkpoint 不提供任意恢复/反序列化入口。

```bash
# 仅用验证集确定后处理参数；改参数时使用新的输出目录。
.venv/bin/python -m app.training.evaluate \
  --bundle var/coursework/augmented/best_model/manifest.json \
  --manifest var/coursework/dataset.json --output-dir var/coursework/validation \
  --split val --device mps --min-region-pixels 1024 --otsu-polarity validation

# 固定参数后评测测试集；测试集不能临时覆盖概率阈值。
.venv/bin/python -m app.training.evaluate \
  --bundle var/coursework/augmented/best_model/manifest.json \
  --manifest var/coursework/dataset.json --output-dir var/coursework/augmented-test \
  --device mps --min-region-pixels 1024 --otsu-polarity validation
.venv/bin/python -m app.training.evaluate \
  --bundle var/coursework/plain/best_model/manifest.json \
  --manifest var/coursework/dataset.json --output-dir var/coursework/plain-test \
  --device mps --min-region-pixels 1024 --otsu-polarity validation
```

每次评测输出 `evaluation.json`、`per_image.csv` 和 `examples/`，包含 UNet 原始预测、BFS 后处理和 Otsu 基线。比较两次训练的原始预测才是增强消融，原始/BFS 使用同一权重才是后处理消融。不要把两次模型都变化的结果归因于 BFS。

Dice/IoU 按原图逐图汇总，双空为 1、单空为 0；另外列出有目标组和空标注假阳性，避免总体均值误导。BFS 可能增加小目标漏检，需结合失败图分析。阈值、seed、训练条件和测试数据保持固定，结果不佳也如实记录。

本次阈值网格、选择依据及冻结时间在 `var/coursework/validation-selection.json`，汇总在 `var/coursework/results/comparison.csv` 和 `summary.json`。训练曲线在两个运行目录的 `curves.png`，失败/高分样例在 `results/`，都不是临床验证。具体结论和局限由[训练规格](app/training/SPEC.md)记录。

### 加载研究模型包

```bash
SEGMENTATION_SERVICE_MODEL_MANIFEST_PATH="$PWD/var/coursework/augmented/best_model/manifest.json" \
SEGMENTATION_SERVICE_DEVICE=mps \
SEGMENTATION_SERVICE_MIN_REGION_PIXELS=1024 \
.venv/bin/python -m uvicorn app.main:app --host 127.0.0.1 --port 8099
```

本次选中增强模型，概率阈值固定 0.5、BFS 过滤阈值为验证集选定的 **1024 原图像素**，不是服务默认 16。也可以检查并加载本地生成的 `var/coursework/deployment.env`（仅含科研模型设置，不切换医院生产默认值）。

该前台命令不会停掉其他进程；如端口已占用，先确认所属进程。模型包缺失、校验失败或模态不是 ULTRASOUND 时拒绝推理。区域仅报告 `pixel_count`、`width_px`、`height_px` 等像素量，`estimated_size_mm=null`、`severity=not_assessed`；前景平均概率不是患病概率。在线空预测仍需复核，不表示患者正常。

服务不提供生产授权、完整资源限额或临床效度保障，请仅用于受控本地科研。

### 验证

```bash
.venv/bin/pytest
.venv/bin/python -m app.training.prepare --help
.venv/bin/python -m app.training.train --help
.venv/bin/python -m app.training.evaluate --help
```

真实服务联调测试为可选测试：未设置环境变量时默认跳过，不能把跳过当作联调通过。本次已启动真实本地 HTTP 服务并运行全部 shared/metric 测试，验证了真实权重、像素量、研究标签和 PDF；不含数据库登记和前端鉴权。

如需复验，在另一个终端按上文启动研究模型服务，然后在**仓库根目录**执行：

```bash
METRIC_RESEARCH_TEST_URL=http://127.0.0.1:8099 \
METRIC_RESEARCH_TEST_IMAGE="$PWD/segmentation-service/var/coursework/curated-v1/Dataset_BUSI_with_GT/benign/benign (10).png" \
JAVA_HOME=$(/usr/libexec/java_home -v 21) \
./gradlew :metric-service:test \
  --tests 'aiagent.tools.ResearchSegmentationLiveIntegrationTest' --no-daemon --rerun-tasks
```

可选 `METRIC_RESEARCH_TEST_BUNDLE_SHA256` 用于核对预期包，`METRIC_RESEARCH_TEST_OUTPUT` 指定忽略目录下的 JSON 时会保留该 payload 和测试 PDF；否则测试结束删除自己的 PDF。本次验证记录在 `var/coursework/live-verification.json`，其中包含报告文件位置。跨服务客户端固定 HTTP/1.1，避免 JDK h2c 升级在 Uvicorn 中引发空请求体 422。

原理与实验边界见 [SPEC.md](SPEC.md) 和 [训练规格](app/training/SPEC.md)；PyTorch 的 [MPS](https://docs.pytorch.org/docs/stable/notes/mps.html)、[基础训练教程](https://docs.pytorch.org/tutorials/beginner/basics/intro.html)及 [UNet 原作者页面](https://lmb.informatik.uni-freiburg.de/people/ronneber/u-net/)可作为学习入口。
