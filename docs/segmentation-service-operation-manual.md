# segmentation-service 操作手册

## 1. 范围、权威来源与执行前提

本文是**当前实现的操作指南**，不是待建目录或未来功能清单。FastAPI 入口、配置、单张/顺序批量分割、质量门禁、工件存储，以及本地 BUSI 准备、训练、评测和研究模型包导出均已存在。

- [模块 README](../segmentation-service/README.md)：安装与独立启动入口。
- [API.md](../segmentation-service/API.md)：路由、完整 snake_case 字段、输入来源、输出选项、状态与错误的唯一操作参考；本文不重复接口表。
- [模块 SPEC](../segmentation-service/SPEC.md)、[训练 SPEC](../segmentation-service/app/training/SPEC.md)、[医疗 AI 边界政策](hospital-ai-boundary-policy.md)：行为与医疗边界的权威来源；文档与实现冲突时不能据操作示例扩大能力。

课程包固定 `intended_use=research`，仅支持 `ULTRASOUND` 单张栅格图的单通道二值区域分割，不做良恶性分类或诊断。研究区域的 `estimated_size_mm=null`、`severity=not_assessed`，位置是图像象限，前景概率未经临床校准。不能补造毫米、血流、恶性风险或固定临床置信度；旧模式的全局间距、规则值或旧常量也不能当作患者有效测量。失败只能说明失败或提示人工复核，不允许 LLM 补造分析或报告结论。

**本次文档同步未安装依赖、启动服务、执行数据准备、训练、pytest 或 test split 评分。** 下列命令都是未执行的操作模板。历史实验/联调证据统一看训练 SPEC；不能把本文当作新性能、Docker、数据库登记或完整前端/鉴权验收。

除特别说明外，命令的工作目录为 `segmentation-service/`。数据清单、训练、评测、工件与自选 TOML 示例均放在模块已忽略的 `var/experiments/busi-doc-demo-001/` 下：这是占位的新实验名，实际执行前必须换成自己未使用的目录。**新路径仅隔离文件，不产生独立数据，也不能消除已经看过 test 的事实。** 已封存的 `var/coursework/` 仅供有权限者只读查证，不覆盖其清单、权重、选择记录或结果。

## 2. 环境与安装

### 2.1 Python 与 extras

要求 **Python >= 3.11**，依赖以 [pyproject.toml](../segmentation-service/pyproject.toml) 为准。系统 `python3` 可能仍为旧版；为本模块建立 venv，不向系统 Python 混装依赖。

```bash
cd segmentation-service
python3.11 --version
python3.11 -m venv .venv
.venv/bin/python -m pip install -e '.[unet,training,dev]'
.venv/bin/python -m pip check
```

按需缩减安装范围：

| 安装项 | 用途与依赖 |
| --- | --- |
| `-e .` | FastAPI、Uvicorn、NumPy、Pillow；可手动启动 mock，也可 prepare，不含 Torch |
| `-e '.[unet]'` | 基础依赖 + Torch，供 Torch U-Net 推理 |
| `-e '.[training]'` | 基础依赖 + Torch、Matplotlib，供训练、评测与曲线 |
| `-e '.[dev]'` | 基础依赖 + pytest、HTTPX、Ruff；不会自动安装 Torch/Matplotlib |

可用 `source .venv/bin/activate` 激活；本文仍显式使用 `.venv/bin/python`，避免终端切换后跑错解释器。Torch 的可安装 wheel、操作系统和设备支持需在实际环境核实；当前依赖范围不是跨所有 Python/硬件组合的验收承诺。

可在已安装相应 extras 后单独检查环境，不读取数据或初始化服务：

```bash
.venv/bin/python - <<'PY'
import sys
from importlib.metadata import version

import torch

print("python:", sys.version)
for package in ("fastapi", "uvicorn", "numpy", "pillow", "torch", "matplotlib"):
    print(package, version(package))
print("mps:", hasattr(torch.backends, "mps") and torch.backends.mps.is_available())
PY
```

### 2.2 设备不是同一套 auto 规则

| 入口 | 实际规则 |
| --- | --- |
| train / evaluate CLI | FP32；`--device auto` 优先可用 MPS，否则 CPU。显式 `--device mps` 不可用则报错。CLI 只接受 auto/mps/cpu，没有 CUDA 选项 |
| Torch 服务 | 默认 `device=cpu`；仅可用的显式 mps 或 cuda 请求进入相应设备，其他情况回 CPU，**`auto` 不会自动选择 MPS/CUDA**。以 `/models/current` 或响应描述符的实际 device 为准 |
| mock | 不运行 Torch 模型，设备字段只回显设置，不能据此评估加速性能 |

本次冻结硬件、Torch 版本和条件见[训练 SPEC](../segmentation-service/app/training/SPEC.md)。相同 seed 不保证跨硬件逐位复现；不要把服务回退 CPU 误认成 MPS 性能结果。

## 3. 现有组件与模型包

### 3.1 组件定位

| 路径 | 当前职责 |
| --- | --- |
| [app/main.py](../segmentation-service/app/main.py)、[app/api/](../segmentation-service/app/api/) | 应用、路由、缓存的依赖构建；不是完整鉴权层 |
| [app/core/config/settings.py](../segmentation-service/app/core/config/settings.py) | 选择 TOML、环境覆盖、路径与研究包有效设置 |
| [app/preprocessing/](../segmentation-service/app/preprocessing/) | 栅格读取、灰度、双线性拉伸与归一化；没有 DICOM/窗宽窗位转换 |
| [app/inference/](../segmentation-service/app/inference/) | mock / Torch UNet2D、受限权重加载、模型包和流水线；没有 ONNX/TensorRT 自动适配 |
| [app/postprocessing/](../segmentation-service/app/postprocessing/) | 原尺寸浮点概率恢复后的二值化、四连通 BFS、像素几何与区域组装 |
| [app/services/](../segmentation-service/app/services/) | 同步编排、质量门禁、mask/overlay/result.json 写入 |
| [app/training/](../segmentation-service/app/training/) | 本地数据清单、训练、评测；复用推理变换、模型与 BFS |
| [tests/](../segmentation-service/tests/) | 单元及合成集成回归；不代表临床验证 |

`app/adapters/` 目前主要是包占位，不是回调或结果上报实现。仓库没有可直接使用的模块 `deployments/k8s/` 部署；旧目录示意不能当作已交付能力。实际容器文件在仓库根目录的 [docker/](../docker/)。

### 3.2 可部署的研究包是什么

训练会在 val macro Dice 严格改善时**自动导出** `best_model/`：

```text
var/experiments/busi-doc-demo-001/augmented/
├── run.json
├── history.csv
├── history.json
├── curves.png
├── checkpoints/last.pt
└── best_model/
    ├── manifest.json
    └── weights-哈希前16位.pt
```

`curves.png` 可由 `--no-plots` 关闭。每次改善可能留下不同哈希命名的权重，**以当前 manifest 指向的文件为准**。部署至少保留该 manifest 和它引用的权重，不要把文件改名、遗漏或混用其他 run 的权重。

包记录 schema、固定的 `unet2d_v1` 架构、research 用途、单通道/单输出与 ULTRASOUND、尺寸、预处理、概率阈值、权重完整 SHA-256、训练配置与数据溯源。预处理为灰度双线性拉伸、`[0,1]` → `[-1,1]`，mask 用 nearest；前景概率以 float32 恢复原图尺寸，不经 8-bit 图像量化。

- 服务校验 manifest、预处理版本、权重哈希、严格 state_dict 键与形状，强制 Torch 并禁用随机权重。缺失/损坏/不兼容时拒绝，不回退 mock。
- 只有仓库内 UNet2D 契约，不是任意 `.pt` 网络加载器，不支持 TorchScript 或 ONNX 自动转换。裸权重虽可受限加载，但没有研究包的用途和完整溯源，不能替代课程包部署。
- **没有独立 export CLI，也没有 resume CLI。** `checkpoints/last.pt` 保存优化器/epoch 等本地训练状态，不等于存在受支持的恢复入口；不要编造 `--resume` 或 `app.training.export` 命令。
- 权重 `sha256` 用来检测文件变化；响应的 `bundle_manifest_sha256` 是完整 manifest 原始字节的摘要，二者不是同一哈希。它们都不是数字签名、来源许可/权利认证、医学效度认证或临床版本批准。

模型包实现见 [model_bundle.py](../segmentation-service/app/inference/model_bundle.py)；对外模型字段的精确含义见 [API.md 第 3.2、6 节](../segmentation-service/API.md)。数据、权重和生成结果留在忽略目录，不进入 Git；真实患者身份、密钥或医院数据也不应进入课程样例、日志或截图。

## 4. 配置解析与覆盖

### 4.1 顺序与文件选择

运行时优先级为：

```text
Settings 内建默认值
  ← 一个所选 TOML
  ← 非空 SEGMENTATION_SERVICE_* 环境变量
  ← 已校验研究包的强制契约
```

1. 有非空 `SEGMENTATION_SERVICE_CONFIG_FILE` 时只选择那份 TOML，不再叠读 local/test。
2. 否则用 `SEGMENTATION_SERVICE_ENVIRONMENT` 选择仓库 [local TOML](../segmentation-service/configs/local/app.toml) 或 [test TOML](../segmentation-service/configs/test/app.toml)，默认 local。未知环境名回退**选择 local 文件**，但非空环境字符串仍可作为响应中的 environment，不代表产生了对应环境配置。
3. 所选 TOML 缺失时按内建默认值加环境覆盖继续，不会再补读另一份 TOML；格式错误或非法配置并不等于“缺失”，可能直接解析失败。
4. 映射表内环境变量只有非空字符串才覆盖；空字符串不代表把可选值设成 null。未知变量不会自动成为设置。完整映射见 [settings.py](../segmentation-service/app/core/config/settings.py)。
5. 研究包再派生有效设置：强制 Torch、禁用随机权重、固定模型结构/尺寸/通道/概率阈值，并取消物理间距。它不把 BFS 阈值写入包，也不强制选择 MPS。

**没有 dotenv 自动加载。** 复制 `.env.example` 成 `.env` 不会自动生效；不要盲目 source 示例。显式导入 shell 后的变量才参与覆盖，且示例中的旧 BFS=24 会影响研究设置。

`get_settings()` 与模型编排器都在进程内缓存。修改环境、TOML、manifest 或权重后必须停止并重启进程；没有在线模型切换/热更新。直接启动 Uvicorn 时 HOST/PORT 环境设置只是应用配置摘要，实际监听仍由 `--host/--port` 决定。

路径规则也不同：CONFIG_FILE 的相对路径按**进程工作目录**选择；模型 manifest、裸权重和工件的相对路径按 **segmentation-service 根目录**解析。部署时尽量使用绝对路径，避免把它们误当作 TOML 所在目录或调用方机器的路径。

### 4.2 常用设置与 BFS 的四种值

下表环境变量均需完整的 `SEGMENTATION_SERVICE_` 前缀；这里只列运行关注项，不替代完整配置映射。

| TOML 项 | 环境变量后缀 | 说明 |
| --- | --- | --- |
| `api.prefix` / `api.host` / `api.port` | `API_PREFIX` / `HOST` / `PORT` | Python local 默认 `/api/v1` / `127.0.0.1` / `8099`；test TOML 端口为 8092 |
| `inference.backend` / `inference.device` | `INFERENCE_BACKEND` / `DEVICE` | local 默认为 torch_unet / cpu；test 为 mock / cpu |
| `model.manifest_path` / `model.weights_path` | `MODEL_MANIFEST_PATH` / `MODEL_WEIGHTS_PATH` | 研究包优先于裸权重，结构/阈值以包为准 |
| `model.allow_random_weights` | `ALLOW_RANDOM_WEIGHTS` | 旧模式默认 true，未配权重也可随机前向；研究包强制 false |
| `inference.probability_threshold` | `PROBABILITY_THRESHOLD` | 旧默认 0.5；研究包使用 manifest 阈值，不受部署覆盖 |
| `inference.min_region_pixels` | `MIN_REGION_PIXELS` | 原图像素计数；小于阈值删除，等于阈值保留，见下表 |
| `inference.min_confidence` | `MIN_CONFIDENCE` | 默认 0.55 的启发式质量门禁，与像素二值化阈值不是同一概念 |
| `inference.pixel_spacing_mm` | `PIXEL_SPACING_MM` | 旧默认 0.7 不是患者标定；研究包强制 null，环境设置不能恢复毫米测量 |
| `storage.artifacts_dir` / `storage.return_artifacts` | `ARTIFACTS_DIR` / `RETURN_ARTIFACTS` | local 默认写 `var/artifacts`；示例另选新实验目录，路径不是 URL |
| `inference.request_timeout_seconds` | `REQUEST_TIMEOUT_SECONDS` | 旧默认 30，但没有落实推理取消/整批截止时间，不能据此承诺资源保护 |

| BFS 场景 | `min_region_pixels`（原图像素） |
| --- | --- |
| 研究包无显式覆盖 | **16** |
| 旧 Settings / 仓库 local TOML | **24** |
| 仓库 test TOML 显式配置 | **8** |
| 已冻结 BUSI 实验与对应部署 | **必须显式 1024**，不是通用默认 |

仓库默认路径 local TOML 的旧 24 不视为研究包显式覆盖；显式 Settings 参数、自定义 CONFIG_FILE 中的值、非空 `SEGMENTATION_SERVICE_MIN_REGION_PIXELS` 都算覆盖。因此 **[.env.example](../segmentation-service/.env.example) 的 `SEGMENTATION_SERVICE_MIN_REGION_PIXELS=24` 若导入 shell，会显式覆盖研究默认 16**，也不符合冻结部署的 1024。加载研究包并不自动复现冻结实验。

如需自选 TOML，下面只是供人工保存到新 `var/experiments/busi-doc-demo-001/research.toml` 的内容示例；本次不创建/修改任何配置文件。选用时在启动命令中显式指定 CONFIG_FILE 的绝对路径，并审查已有环境变量覆盖。

```toml
[api]
host = "127.0.0.1"
port = 8099
prefix = "/api/v1"

[inference]
backend = "torch_unet"
device = "cpu"
min_region_pixels = 1024

[model]
manifest_path = "var/experiments/busi-doc-demo-001/augmented/best_model/manifest.json"
allow_random_weights = false

[storage]
artifacts_dir = "var/experiments/busi-doc-demo-001/service-artifacts"
return_artifacts = true
```

## 5. 本地数据门禁与 prepare

### 5.1 先人工核实，再声明确认

prepare **只接收已解压的本地 BUSI PNG**，目录含 `benign/`、`malignant/`、`normal/`。不下载、不抓取网页、不解压 ZIP，也不接受任意外部队列适配。

运行前人工完成：

1. 阅读实际来源、许可及使用限制，保留原发布地址、镜像/下载修订信息、归档校验和与署名引用；不能因一个布尔 flag 就认定数据已授权。
2. 只在获准的本地科研数据上操作。原始数据保留不变；清理/排除应在独立派生副本中记录原因和人工批准，不为了通过校验猜改标注。
3. 使用未用过的新输出清单路径，放在训练输出目录之外。**prepare 的 JSON 写入会覆盖已有输出文件**，没有 train 那样的空目录保护；尤其不能指向 `var/coursework/` 的封存清单。

历史用户确认的镜像、原始论文引用、许可声明和冲突组清理记录见[训练 SPEC「本次 BUSI 数据与溯源」](../segmentation-service/app/training/SPEC.md)。镜像声明不是独立权利链认证；它也不能替代另一次数据获取的人工核查。本文不重新下载或重新整理封存数据。

### 5.2 清单命令与输出

先在自己的终端设置下列值，**不要复制猜测的许可或未经核实的 URL**：

| shell 变量 | 应填写的内容 |
| --- | --- |
| `BUSI_DATA_ROOT` | 已审查、已解压的本地绝对目录，例如新实验目录下的 `data/Dataset_BUSI_with_GT` |
| `BUSI_SOURCE_URL` | 人工阅读过的实际公开 HTTP(S) 来源地址，不带凭证 |
| `BUSI_LICENSE` | 实际核实并记录的许可名称，不用占位词冒充核实 |
| `BUSI_SOURCE_REVIEWED` | 仅在以上审查完成后手动设为 `yes`；下面的保护不是自动核实 |

下面的子 shell 避免失败时退出交互终端。它只在人工确认之后才传 `--source-verified`，还额外拒绝覆盖已有清单；这层保护不属于 CLI 自带能力。

```bash
(
  set -eu
  : "${BUSI_DATA_ROOT:?先设置人工核实的已解压本地目录}"
  : "${BUSI_SOURCE_URL:?先设置人工核实的来源地址}"
  : "${BUSI_LICENSE:?先填写人工核实的许可}"
  [ "${BUSI_SOURCE_REVIEWED:-no}" = yes ] || {
    printf '%s\n' '来源和许可尚未人工核实，停止。' >&2
    exit 1
  }
  manifest='var/experiments/busi-doc-demo-001/dataset-v1.json'
  [ ! -e "$manifest" ] || {
    printf '%s\n' '清单路径已存在，请选择全新路径。' >&2
    exit 1
  }
  .venv/bin/python -m app.training.prepare \
    --data-root "$BUSI_DATA_ROOT" \
    --output "$manifest" \
    --source-url "$BUSI_SOURCE_URL" \
    --license "$BUSI_LICENSE" \
    --source-verified \
    --seed 42 --train-fraction 0.7 --val-fraction 0.15
)
```

行为检查：

- 同原图多个 mask 取并集；非 normal 图必须有 mask，normal 缺 mask 时显式当作零前景。检查 PNG、尺寸、二值性、孤立 mask、损坏文件与路径安全。
- 以训练使用的**灰度像素内容**分组，完全重复组不跨 split；同像素但并集标注冲突会拒绝。不能通过重命名消除重复或跳过冲突。
- seed 默认 42，默认按类别/重复组近似 70/15/15；不是保证每类精确相同比例。`split_unit=duplicate_group`、`patient_independence_verified=false`，没有患者 ID，不能声称患者独立。
- 成功才打印清单路径和 split/类别/组数；OSError/ValueError 经 argparse 以退出码 2 结束，不应将部分输出当作成功。
- `--source-verified` 只产生 `user-confirmed` 操作者声明，不联网核实来源或认证许可。

保存清单与原图/mask 后保持不变。训练记录清单完整字节哈希和文件摘要，评测必须匹配训练时封存版本。历史固定的数据清理、样本清单数量与局限以[训练 SPEC](../segmentation-service/app/training/SPEC.md)为准，不从新命令推测或伪造一次“复验”。

## 6. train：对照、最佳模型与不可恢复入口

### 6.1 CLI 默认值不等于冻结实验

只对 train 优化，按**原尺寸 val image-macro Dice** 的严格改善选择最佳 epoch；同分保留较早 epoch。完整数据清单的校验会检查所有 split，但不授权用 test 选模型。

| 项目 | train CLI 默认 | 已冻结 BUSI 条件 / 本文模板 |
| --- | --- | --- |
| 精度与模型 | FP32，UNet2D，单通道二值 | 相同 |
| epochs | 20（允许 1–1000） | 最多 20 |
| **batch-size** | **4** | **显式 2** |
| image-size / base-channels | 256 / 32 | 显式 256 / 32 |
| learning-rate / seed | 0.001 / 42 | 显式 0.001 / 42 |
| threshold | 0.5 | 显式 0.5，随模型包固定 |
| patience | 未设置，不按 patience 早停 | 显式 5 |
| augment | 关闭 | 基线关闭；增强组加 `--augment` |
| device | auto：MPS → CPU | 模板 auto，实际设备需记录；不是服务的 auto |

损失为 BCEWithLogitsLoss + 逐图 Soft Dice，优化器 Adam；包括空标注样本。增强仅训练集成对翻转，按 seed/epoch/ID 决定，val/test 不增强。不承诺固定耗时、精度或增强必然优于基线。

### 6.2 隔离输出的训练模板

**`--output-dir` 必须新建或为空。** 清单不要放在 run 输出目录内；非空目录会被拒绝，不应删除旧证据来强行重用。使用新的 plain/augmented 兄弟目录，保持同一清单和预声明条件：

```bash
.venv/bin/python -m app.training.train \
  --manifest var/experiments/busi-doc-demo-001/dataset-v1.json \
  --output-dir var/experiments/busi-doc-demo-001/plain \
  --device auto --epochs 20 --batch-size 2 \
  --image-size 256 --base-channels 32 --learning-rate 0.001 \
  --seed 42 --patience 5 --threshold 0.5

.venv/bin/python -m app.training.train \
  --manifest var/experiments/busi-doc-demo-001/dataset-v1.json \
  --output-dir var/experiments/busi-doc-demo-001/augmented \
  --device auto --epochs 20 --batch-size 2 \
  --image-size 256 --base-channels 32 --learning-rate 0.001 \
  --seed 42 --patience 5 --threshold 0.5 --augment
```

这些是隔离路径下的操作模板，不是重跑既有 BUSI 封存实验的要求；已有 test 已看过，不能借新目录开展基于该 test 的调参。历史模型选择、实际完成轮数和最佳 epoch 请查[训练 SPEC](../segmentation-service/app/training/SPEC.md)，不要把命令中的最多轮数当作已完成轮数。

| 产物 | 用途 |
| --- | --- |
| `run.json` | 状态、有效配置、seed、依赖版本、实际设备、FP32、数据摘要和选择规则；检查最终 completed，而非只看目录存在 |
| `history.csv` / `history.json`、`curves.png` | 各 epoch 损失/指标；train Dice 基于缩放 mask，val Dice 基于原尺寸，不应混称同尺度 |
| `best_model/manifest.json` + 所引权重 | 最佳 val 模型自动导出的推理包；不需要额外 export 命令 |
| `checkpoints/last.pt` | 最后一轮本地状态，可能不是最佳模型；**没有恢复训练 CLI**，不要把它当部署包或恢复承诺 |

`--no-plots` 仅关闭曲线绘图，不会关闭训练。中断后保留现场、检查日志和状态；当前没有 resume，若另行批准从头训练需新 run 目录，并如实记录中断/重跑，不覆盖原记录。

## 7. val → 人工冻结 → test 一次

### 7.1 先识别危险默认

**evaluate 省略 `--split` 就是 `test`。日常检查、后处理比较一律显式 `--split val`。** 它不支持 train split，要求同一训练封存清单及文件摘要；没有新外部队列的独立评测流程，不能把任意新队列清单塞给已有包，也不能修改 hash 绕过检查。

其他默认：`--device auto`、BFS 最小区域 16、Otsu 极性 dark、最多 8 张对照图；概率阈值默认取模型包。`--threshold` 仅允许 val，本次覆盖不会写回包，test 禁止覆盖。不要把只在 val 临时覆盖过的阈值误认为部署契约；本文固定使用训练时声明的 0.5。

### 7.2 只在 val 选择

以下显式展示 1024 的配置。**1024 是历史冻结实验在 val 上选定的值，不是服务默认，也不代表对任意数据最优。** 该实验的候选网格、选择规则和限制见训练 SPEC；不根据已见 test 重新搜索。

```bash
.venv/bin/python -m app.training.evaluate \
  --bundle var/experiments/busi-doc-demo-001/augmented/best_model/manifest.json \
  --manifest var/experiments/busi-doc-demo-001/dataset-v1.json \
  --output-dir var/experiments/busi-doc-demo-001/val-augmented-bfs1024 \
  --split val --device auto --min-region-pixels 1024 \
  --otsu-polarity validation --max-examples 8
```

若是事前获准且 test 未见的实验，应先声明候选/同分规则，只在 val 比较，每次用独立输出目录。基线模型也只能在同一封存清单与既定条件下比较。**本手册不提供针对已见 BUSI test 的继续调参或外部队列评测方案。**

每次评测输出 `evaluation.json`、`per_image.csv`，以及启用样例时的 `examples/`，同时报告 `unet_raw`、同权重 `unet_bfs` 与 Otsu。输出不是任意覆盖安全：实现会拒绝已有 evaluation.json/per_image.csv，但操作上仍应一律使用新目录。

读取结果时：

- Dice/IoU/precision/recall 在原图逐图计算，再做 macro 汇总；双空 Dice/IoU=1、单空=0，无定义 precision/recall 仅双空记 1。
- 同时看有前景组与空标注组的误检图像率/前景比例；组缺失应是 null，不是完美得分。均值不等于临床准确率。
- 比较不同训练的 raw 预测才是增强对照；同权重 raw/BFS 才是后处理对照，不能把换模型的收益都归给 BFS。
- BFS 在原尺寸按四连通分量删除小区域，可能删除真实小目标或清空预测，不能预设它必然有益；样例按清单顺序截取，不代表完整失败分析。
- `--otsu-polarity validation` 只用 val Dice 在 dark/bright 中选方向（同分 dark），不是从 test 标签选方向。记录 evaluation.json 中实际选择。

### 7.3 人工冻结不是一个隐藏的 CLI

在首次查看 test 前，由操作者记录并封存到新的 `var/experiments/busi-doc-demo-001/validation-selection.txt`（或自定新路径）：

1. 数据清单及资产摘要、split、seed、患者独立性限制。
2. 选中 run/最佳 epoch、包路径与完整 manifest SHA-256、所引权重 SHA-256。
3. 概率阈值、BFS 原图像素阈值、Otsu 实际 dark/bright 方向、模型/增强选择依据及同分规则。
4. 验证结果文件与摘要、选择时间、操作者、实际设备/依赖、计划的 test 比较范围。

例如只读计算待记录的文件摘要，不覆盖选择记录：

```bash
shasum -a 256 \
  var/experiments/busi-doc-demo-001/dataset-v1.json \
  var/experiments/busi-doc-demo-001/augmented/best_model/manifest.json \
  var/experiments/busi-doc-demo-001/val-augmented-bfs1024/evaluation.json
```

保存人工记录后也应计算其摘要。CLI 不会自动生成这份选择记录，也不会自动限制 test 次数；目录防覆盖不等于实验独立性门禁。已有 BUSI 的冻结记录与成绩由[训练 SPEC](../segmentation-service/app/training/SPEC.md)指向本地封存证据，不重新评分来补文档。

### 7.4 固定后 test 仅一次

**仅适用于 test 尚未查看、选择已冻结且获准执行的实验；不要对已有封存 BUSI test 执行此模板。** 下面仅对选定模型运行一次，显式 `--split test`、不传 `--threshold`。`FROZEN_OTSU_POLARITY` 必须来自刚才的 val/冻结记录，而不是根据 test 结果填写。

```bash
(
  set -eu
  : "${FROZEN_OTSU_POLARITY:?先从val读取并封存dark或bright方向}"
  case "$FROZEN_OTSU_POLARITY" in
    dark|bright) ;;
    *) printf '%s\n' '冻结方向只能为 dark 或 bright。' >&2; exit 1 ;;
  esac
  .venv/bin/python -m app.training.evaluate \
    --bundle var/experiments/busi-doc-demo-001/augmented/best_model/manifest.json \
    --manifest var/experiments/busi-doc-demo-001/dataset-v1.json \
    --output-dir var/experiments/busi-doc-demo-001/test-once \
    --split test --device auto --min-region-pixels 1024 \
    --otsu-polarity "$FROZEN_OTSU_POLARITY" --max-examples 8
)
```

1024 必须与冻结记录和部署一致；test 不佳也如实保留，不改 seed、清单、概率阈值、BFS 或增强后反复重测。测试分析可描述错误和局限，不能据此继续调参或声称获得新独立验证。固定的数据数量、已完成成绩、空标注误检、小目标损失及单种子/未知患者关联限制统一引用[训练 SPEC「本次冻结实验与限制」](../segmentation-service/app/training/SPEC.md)，本文不复制成绩表或编造重测。

## 8. 本地部署、接入与容器边界

### 8.1 前台启动研究包

只在准备好有效包并获得本地运行授权后执行。核对当前 shell 继承的配置，不盲目加载 `.env.example`。以下明确使用 local、回环地址、CPU、冻结 BFS=1024 和新工件目录，不启 reload、不清理其他进程：

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

需要 MPS 时显式设置 DEVICE=mps，并检查实际 device；不可用可能回 CPU，不会像训练的显式 mps 那样必然报错。Ctrl+C 停止自己启动的前台进程；修改配置/包后重启。端口被占用先确认所属者，不使用粗暴进程清理来“修好”冲突。

无权重的纯契约联调用[README 的显式 mock 命令](../segmentation-service/README.md)，不要用随机 Torch 或 mock 输出演示医学精度。有效研究包始终优先于后端环境设置，不能只把 backend 改成 mock 就绕过包校验。

### 8.2 存活、元数据与一次请求的检查层次

服务已启动时，可在另一个终端只读查询：

```bash
curl --fail-with-body http://127.0.0.1:8099/health
curl --fail-with-body http://127.0.0.1:8099/api/v1/models/current
```

1. `/health` 仅读 raw settings；不校验 Torch、包/权重、设备、图像读取或输出可写性，不是 readiness，模型摘要也可能与有效研究模型不同。
2. `/api/v1/models/current` 首次依赖解析会初始化后端/校验包。核对 research、支持模态、weights_loaded、预期 manifest 摘要、尺寸及实际设备；该结果不执行影像推理，也不是性能或临床资格认证。
3. 另行授权的请求验证应使用预先准备的合成/获准科研 ULTRASOUND PNG、虚构身份和新 request_id，完整请求直接采用 [API.md 第 2 节](../segmentation-service/API.md)。本地文件路径必须是**服务端**可读路径；不同机器/容器需明确共享挂载，不是客户端上传。
4. 检查 HTTP 与业务质量状态两个层次。研究结果仍须保留用途、像素单位及 nullable 毫米值；在线空预测需要复核，不表示患者正常。工件保存应在受控目录核查，不对外公开身份或服务端路径。

不要从少量合成请求、门禁 PASS 或生成 PDF 推断 Dice、临床准确率或吞吐量。模型 timing 不等于完整请求耗时；mock 的固定计时也不是实测性能。选项、门禁、工件及计时精确定义均见 [API.md 第 2–3 节](../segmentation-service/API.md)。

### 8.3 启动脚本的副作用与整栈接入

[模块 scripts/run-dev.sh](../segmentation-service/scripts/run-dev.sh) 不是首选安全启动入口：

- 先用已激活 venv，再用模块 `.venv`；不自行创建 venv。
- 用命令模式匹配同 host/port 的旧 Uvicorn reload 进程，先终止，必要时强杀；不是只管理本次启动的子进程。
- 始终检查 fastapi/numpy/Pillow/uvicorn/**Torch**，即使后端为 mock；缺少时执行 `pip install -e '.[unet]'`。
- 没有参数解析或安全帮助分支，**不要执行它的 `--help`**；追加此参数仍可能清理进程、安装依赖并启动服务。

[仓库 scripts/quick-start.sh](../scripts/quick-start.sh) 是另一个整栈入口，会安装/构建并启动多个服务，依赖 Java 21、前端工具链、数据库与本地配置，不是本手册的默认验证命令。它前台监管自己的子进程、Ctrl+C 清理，端口冲突时失败，不做模块脚本的通用旧进程清理；日志在仓库 `.run/logs/`。TCP/日志等待不证明模型 readiness，构建跳过测试也不证明测试通过。

该脚本用外层变量重写 Python 子进程环境：

| 整栈外层变量 | 默认 | 覆盖的子进程变量 |
| --- | --- | --- |
| `SEGMENTATION_ENV` | local | `SEGMENTATION_SERVICE_ENVIRONMENT` |
| `SEGMENTATION_HOST` | 127.0.0.1 | `SEGMENTATION_SERVICE_HOST`，也用于监听参数 |
| `SEGMENTATION_PORT` | 8099 | `SEGMENTATION_SERVICE_PORT`，也用于监听参数 |
| `SEGMENTATION_BACKEND` | torch_unet | `SEGMENTATION_SERVICE_INFERENCE_BACKEND` |

因此只在外部设置同义 `SEGMENTATION_SERVICE_*` 并不保证整栈使用该值；需按外层变量或脚本选项配置。包路径、DEVICE、MIN_REGION_PIXELS 等其他前缀变量仍会继承，包强制契约也仍适用。`--without-segmentation` 仅跳过 Python 启动，不替你关闭 metric 的远程开关。

**`METRIC_SEGMENTATION_SERVICE_ENABLED` 独立，源码默认 false**：仅启动 Python 或选择 Torch 不会启用远程分析。默认 metric 使用本地启发式像素分析，并不代表该分析临床有效；启用远程需在 metric 进程环境中显式设 true 并配置 `METRIC_SEGMENTATION_SERVICE_URL`。调用方还要求身份/模态匹配、completed + PASS、非 mock、权重已加载和非空区域；research 走像素量与非诊断报告路径，不进入旧临床常量映射。失败不得回退 LLM。

Python 默认前缀可配置，但 Kotlin [SegmentationServiceClient](../metric-service/src/main/kotlin/aiagent/tools/SegmentationServiceClient.kt) **硬编码 `/api/v1/segment` 后缀**；只修改 Python API_PREFIX 会导致不兼容。客户端使用 HTTP/1.1，不能据单端可访问就断言整条链路成功。报告生成、前端确认和数据库登记不是同一事务；边界见[架构](../architecture.md)与 [Agent SPEC](../metric-service/src/main/kotlin/aiagent/SPEC.md)。

### 8.4 Docker/Compose 不是一键生产方案

本次未构建或运行容器。现有 [Dockerfile.segmentation](../docker/Dockerfile.segmentation) 与 [docker-compose.yml](../docker-compose.yml) 至少有以下未验收事项，不能直接套用为医院部署：

- 整栈 Kotlin Dockerfile 引用的 `docker/gradle-dists` 构建目录当前缺失；文档/文本检查不等于镜像能成功构建。
- Python 镜像只装基础依赖、默认 mock，不包含 Torch 推理 extra；未设置非 root USER 或 HEALTHCHECK。
- Compose 依赖已存在的外部 `tidb_tidb-network`，不自动提供数据库初始化；不能复制仓库演示凭证作为实际部署凭证。
- 没有真实研究包/权重、输入数据和工件的完整挂载约定，跨容器本地路径不能自动互通；需独立设计访问权限与持久化。
- Compose 的 metric 远程调用默认开启、Python 默认 mock，与源码/quick-start 默认组合不同，不是已经验收的研究包链路；仅 nginx 发布宿主机端口。

鉴权、资源限额、隔离、日志脱敏、模型与数据授权、真实包加载及完整链路需另行验收。本文不提供“直接生产部署”命令或通过声明。

## 9. 排错顺序与当前限制

先核对解释器 → 所选配置/环境 → 包与数据摘要 → 实际设备 → 输入/输出路径 → 调用方开关及前缀。不要在日志中公开患者资料、凭证或完整敏感请求。请求字段、错误体与 HTTP 状态以 [API.md](../segmentation-service/API.md) 为准；不在此维护第二套错误码表。

| 现象 | 排查与处理 |
| --- | --- |
| Python 版本或 Torch/Matplotlib 缺失 | 使用模块 venv 和对应 extra；dev 不包含训练依赖。不要通过 run-dev 的“帮助”触发安装 |
| 训练显式 mps 失败 / 服务实际为 CPU | 按第 2.2 节区分设备解析；训练可显式 cpu，服务查看实际描述符，不把 CPU 数据当 MPS 实测 |
| health 正常但模型查询失败 | health 不加载模型；检查 manifest/所引权重、Torch、严格架构与哈希。错误映射见 [API 第 6–7 节](../segmentation-service/API.md)；不靠改哈希或启随机权重绕过 |
| 配置变更不生效 / BFS 变成 24 或 8 | 查自定义 TOML、导入的 .env.example、test 环境和非空变量，再重启；冻结部署必须显式 1024 |
| prepare 拒绝损坏/冲突 mask | 保留原件，按人工批准的派生清理记录处理；不猜标注、不盲传确认 flag，不覆盖封存清单 |
| train 拒绝输出目录 / 中断后想续训 | 新/空目录，不把清单放进输出；无 resume CLI，last.pt 不提供恢复入口 |
| evaluate 报清单/数据摘要不匹配 | 回查训练封存版本和数据是否改变，不手改 manifest/hash，不将外部队列伪装为同一 test |
| 请求字段或模态失败 | 按 [API 第 2、7 节](../segmentation-service/API.md) 区分 schema 校验与执行错误；research 仅 ULTRASOUND，不凭 CT/MRI 字符串获得 DICOM 能力 |
| return_metrics=false 仍有指标 | 当前关闭行为未落实，精确定义见 [API 第 2.3 节](../segmentation-service/API.md)；不是客户端拼字段能修复 |
| 重复 ID 写工件失败 / 批量中途失败 | 开工件时重复 ID 是 500，不是幂等命中或 409；批量异常中止且已写工件不回滚，可能留下部分目录。使用新 ID，保留现场，见 [API 第 3.6、4、7 节](../segmentation-service/API.md) |
| 工件路径无法在浏览器打开 | 是服务端路径，不是 URL，没有下载路由；不要为排错公开目录。共享/挂载由上层受控管理，见 [API 第 3.6 节](../segmentation-service/API.md) |
| HTTP 200 但 metric 拒绝 | 检查质量状态/身份/模态/用途/区域，不只看 HTTP；门禁失败也可能已写工件，见 [API 第 3.4、7 节](../segmentation-service/API.md) |
| URL 读取超时或处理未取消 | 请求超时设置未落实到推理取消；远程读取异常不保证映射为 504，见 [API 第 2.2、7 节](../segmentation-service/API.md) |
| Python 可调用但 metric 不调用/404 | 核对 metric 独立 enable/URL、整栈外层变量，以及 Kotlin 固定的 /api/v1/segment 后缀 |

当前**没有**原生 DICOM/序列/体数据、文件上传、原生 S3 URI、异步任务队列/查询/回调、readiness、模型热更新或任务恢复入口。原始输入还缺少本地目录/远程 URL 白名单、统一字节/像素/批量/并发资源限额，以及本服务认证/授权；身份字段不是权限凭证。不能直接向不可信调用方开放路径/URL 读取能力。具体输入、错误、安全和质量状态的边界见 [API 第 2、7–8 节](../segmentation-service/API.md)。

## 10. 验证与交接记录

### 10.1 文档静态检查不等于业务验证

本次限定为两个文档的同步：对照 SPEC、政策、API、settings/pyproject、CLI 源码及已有安全 help 输出，静态检查 JSON/shell/Python/TOML 代码块语法、内部链接/标题及示例参数。**不会执行本文的启动、prepare、训练、评测或测试示例。** 无新增评分、性能、医疗效果或生产可用性结论。

如仅需确认 CLI 用法，以下三个模块有安全 argparse 帮助入口；不要把同样做法套到 run-dev。文档同步已使用预先保存的 help 结果，无需为了写文档再执行：

```bash
.venv/bin/python -m app.training.prepare --help
.venv/bin/python -m app.training.train --help
.venv/bin/python -m app.training.evaluate --help
```

### 10.2 后续另行授权的验证应区分证据

- 配置与契约：参考 [test_runtime_settings.py](../segmentation-service/tests/unit/test_runtime_settings.py)、[test_model_bundle_routes.py](../segmentation-service/tests/integration/test_model_bundle_routes.py) 和 [API 的证据索引](../segmentation-service/API.md)。合成回归证明契约，不产生 BUSI 成绩或临床准确率。
- 训练/指标/导出回归：参考 [test_training_smoke.py](../segmentation-service/tests/unit/test_training_smoke.py)、[test_training_metrics.py](../segmentation-service/tests/unit/test_training_metrics.py)。缺少可选依赖导致 skip 不等于通过；这些也不是封存 test 的重评分入口。
- 服务请求/科研 PDF：按第 8 节分层记录实际包摘要、设备、用途、像素单位和输出。可选真实服务测试被跳过不算联调通过；PDF 生成成功也不证明业务数据库登记或前端/鉴权完成。
- 历史真实 BUSI/MPS 训练、冻结内部评测和科研 PDF 的范围、已测成绩及限制仅引用[训练 SPEC](../segmentation-service/app/training/SPEC.md)与[模块 SPEC](../segmentation-service/SPEC.md)，不擅自重测或扩大结论。

交接时分别写明：修改的文档、静态检查结果、实际执行过的验证（没有就写未执行）、模型/数据授权与安全限制，以及尚未验收的环节。权重加载、哈希一致、质量 PASS、结构化输出和报告生成是不同事实，均不能替代患者独立验证或临床资格。
