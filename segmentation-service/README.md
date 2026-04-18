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

配置读取顺序如下：

1. `configs/<environment>/app.toml`
2. `SEGMENTATION_SERVICE_CONFIG_FILE` 指向的 TOML 文件
3. 环境变量覆盖

默认环境为 `local`。

## 后端说明

- `mock`
  - 用于联调和契约测试，不依赖真实模型权重
- `torch_unet`
  - 使用 PyTorch 2D U-Net 执行真实推理
  - 本地默认配置即使用该后端
  - 需要安装 `.[unet]` 依赖
  - 建议配置 `SEGMENTATION_SERVICE_MODEL_WEIGHTS_PATH` 以加载训练权重
  - 若未配置权重且 `allow_random_weights=true`，仍会执行真实 U-Net 前向，但输出仅适合联调

## 运行验证

启动后可检查：

```bash
curl http://localhost:8099/health
curl http://localhost:8099/api/v1/models/current
```
