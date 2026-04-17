# segmentation-service 操作手册

## 1. 文档目的

这份手册面向当前项目中的 `segmentation-service` 模块，目标是帮助你在不熟悉 Python 服务端的情况下，先理解这个模块为什么存在、目录应该怎么用、后续功能应该落在哪一层。

这套结构默认以 `FastAPI` 作为服务框架来理解。

## 2. 模块定位

`segmentation-service` 是一个独立的 Python 推理服务，它不负责整套业务系统，而是专门负责：

- 接收影像分割请求
- 调用 U-Net 模型进行推理
- 做预处理、后处理和质量门禁
- 输出标准化分割结果
- 为 `metric-service` 提供可消费的结构化结果

它和主系统的关系可以理解为：

```text
frontend / metric-service
        -> segmentation-service
        -> 返回 mask / contour / metrics / model metadata
        -> metric-service 继续生成分析结果与 PDF 报表
```

一句话理解：

- `metric-service` 负责业务编排和报表
- `segmentation-service` 负责“看图”和“产出分割结果”

## 3. 基本项目结构

当前目录结构如下：

```text
segmentation-service/
├── app/
│   ├── adapters/
│   │   ├── http/
│   │   └── metric_service/
│   ├── api/
│   │   ├── dependencies/
│   │   └── routes/
│   ├── core/
│   │   ├── config/
│   │   ├── errors/
│   │   └── logging/
│   ├── domain/
│   │   ├── entities/
│   │   └── value_objects/
│   ├── inference/
│   │   ├── backends/
│   │   ├── engines/
│   │   └── pipelines/
│   ├── postprocessing/
│   ├── preprocessing/
│   ├── schemas/
│   │   ├── internal/
│   │   ├── requests/
│   │   └── responses/
│   ├── services/
│   │   ├── orchestration/
│   │   ├── quality/
│   │   └── storage/
│   └── utils/
├── configs/
│   ├── local/
│   └── test/
├── deployments/
│   ├── docker/
│   └── k8s/
├── scripts/
└── tests/
    ├── fixtures/
    ├── integration/
    └── unit/
```

## 4. 各个目录的职责

### 4.1 `app/`

`app/` 是服务的核心代码目录。以后绝大部分 Python 代码都会放在这里。

### 4.1.1 `app/api/`

这是 HTTP API 层，也就是 FastAPI 暴露给外部的接口层。

作用：

- 定义接口路由
- 接收请求
- 返回响应
- 调用内部 service，不直接写模型推理细节

子目录职责：

- `app/api/routes/`
  - 放具体路由
  - 例如：健康检查、分割推理、模型信息查询
- `app/api/dependencies/`
  - 放 FastAPI 依赖注入逻辑
  - 例如：配置对象、鉴权、请求上下文、trace id

你可以把这一层理解成：

- “接口入口层”
- 只负责接请求和分发，不负责真正算模型

### 4.1.2 `app/core/`

这是全局基础设施层。

作用：

- 管理配置
- 管理日志
- 管理错误类型

子目录职责：

- `app/core/config/`
  - 读取环境变量、配置文件
  - 比如模型路径、推理超时、设备类型、输出目录
- `app/core/logging/`
  - 统一日志格式
  - 比如请求日志、推理耗时日志、错误日志
- `app/core/errors/`
  - 自定义异常
  - 比如输入非法、模型不可用、推理超时、后处理失败

这一层的目标是：

- 让全项目的配置、日志、异常有统一规则

### 4.1.3 `app/schemas/`

这是数据结构定义层，主要放 API 请求和响应结构。

作用：

- 定义接口入参
- 定义接口出参
- 定义内部传输结构

子目录职责：

- `app/schemas/requests/`
  - 外部请求结构
  - 例如：分割请求、批量请求、回调请求
- `app/schemas/responses/`
  - 外部响应结构
  - 例如：分割结果、健康状态、错误响应
- `app/schemas/internal/`
  - 内部中间结构
  - 例如：预处理结果、模型输出、后处理结果

这一层是为了避免：

- 路由层直接传裸字典
- 内部处理时字段含义混乱

### 4.1.4 `app/domain/`

这是领域模型层，放“业务概念”本身，而不是接口格式。

作用：

- 表达这个模块里的核心概念
- 保持内部模型稳定，不被 API 格式绑死

子目录职责：

- `app/domain/entities/`
  - 放核心实体
  - 例如：SegmentationTask、SegmentationResult、ModelInfo
- `app/domain/value_objects/`
  - 放值对象
  - 例如：BoundingBox、ContourPoint、ConfidenceScore

这一层的意义是：

- API 怎么变都可以
- 但内部的“分割结果是什么”应该保持稳定

### 4.1.5 `app/preprocessing/`

这是预处理层。

作用：

- 读取原始影像
- 转换输入格式
- 归一化
- resize / pad / channel 处理
- 为模型推理准备输入 tensor

典型职责：

- 文件路径读取
- base64 图像解码
- DICOM 转换
- 窗宽窗位处理
- 图像尺寸对齐

这一层很重要，因为医学影像模型通常对输入格式很敏感。

### 4.1.6 `app/inference/`

这是模型推理层，是整个模块最核心的部分。

子目录职责：

- `app/inference/backends/`
  - 放不同推理后端适配
  - 例如：本地 PyTorch、ONNX Runtime、TensorRT
- `app/inference/engines/`
  - 放模型加载和真正的执行逻辑
  - 例如：加载 U-Net 权重、执行 forward
- `app/inference/pipelines/`
  - 放完整推理流水线
  - 即：预处理输入 -> 调模型 -> 生成原始输出

这一层负责的事情是：

- 真正调用 U-Net
- 返回 logits / mask / class map 等模型原始结果

### 4.1.7 `app/postprocessing/`

这是后处理层。

作用：

- 把模型原始输出变成业务可消费结果

典型职责：

- 阈值二值化
- mask 平滑
- 连通域分析
- 提取轮廓
- 计算边界框
- 计算面积、大小、覆盖率
- 转换成 `metric-service` 可消费的字段

这一层的输出，应该已经非常接近最终 API 返回格式。

### 4.1.8 `app/services/`

这是服务编排层，用来把多个步骤串起来。

子目录职责：

- `app/services/orchestration/`
  - 串联一次完整分割任务
  - 即：接收任务 -> 预处理 -> 推理 -> 后处理 -> 返回结果
- `app/services/quality/`
  - 放质量门禁逻辑
  - 例如：空 mask、低置信度、异常面积、推理失败判定
- `app/services/storage/`
  - 放结果文件和工件管理
  - 例如：保存 mask、overlay、临时文件、结果快照

如果说：

- `api` 是入口
- `inference` 是算子

那么 `services` 就是把这些部分按业务流程串起来的“调度层”。

### 4.1.9 `app/adapters/`

这是外部系统适配层。

子目录职责：

- `app/adapters/http/`
  - 放通用 HTTP 客户端适配
  - 比如访问对象存储、通知别的服务
- `app/adapters/metric_service/`
  - 放和 `metric-service` 的专用对接逻辑
  - 例如：回调、结果上报、契约转换

这一层的目的是：

- 不把外部系统调用写到业务逻辑里

### 4.1.10 `app/utils/`

这是工具函数层。

作用：

- 放零散但通用的辅助函数

例如：

- 时间格式化
- 文件名清洗
- 哈希计算
- 百分比换算

注意：

- 不要把核心业务逻辑塞进 `utils`
- `utils` 只放真正通用的辅助函数

### 4.2 `configs/`

这是配置文件目录。

子目录职责：

- `configs/local/`
  - 本地开发配置
- `configs/test/`
  - 测试环境配置

建议未来放的内容：

- 本地模型路径
- GPU/CPU 切换配置
- 推理阈值
- 日志级别
- 文件输出目录

### 4.3 `deployments/`

这是部署相关目录。

子目录职责：

- `deployments/docker/`
  - Dockerfile、镜像构建相关文件
- `deployments/k8s/`
  - Kubernetes 部署配置

这一层主要服务于上线，而不是开发本身。

### 4.4 `scripts/`

这是脚本目录。

建议放：

- 启动脚本
- 本地开发脚本
- 模型下载脚本
- 数据准备脚本
- 结果清理脚本

它的目标是减少手工命令，而不是承载业务逻辑。

### 4.5 `tests/`

这是测试目录。

子目录职责：

- `tests/unit/`
  - 单元测试
  - 测每个小模块，比如后处理函数、阈值逻辑
- `tests/integration/`
  - 集成测试
  - 测一整条分割链路
- `tests/fixtures/`
  - 测试用样例数据
  - 例如：小图像、假 mask、模拟响应

## 5. 后续建议补齐的关键文件

当前你只创建了目录，还没有创建 Python 项目的关键入口文件。后续这个模块通常还需要这些文件：

- `segmentation-service/pyproject.toml`
  - Python 项目依赖定义
- `segmentation-service/README.md`
  - 模块说明
- `segmentation-service/.env.example`
  - 环境变量示例
- `segmentation-service/app/main.py`
  - FastAPI 入口
- `segmentation-service/app/api/routes/health.py`
  - 健康检查接口
- `segmentation-service/app/api/routes/segmentation.py`
  - 分割主接口

这些文件现在还没创建，但这是后面真正把服务跑起来时最先要补的部分。

## 6. 这个模块要实现的所有功能

下面这部分是这个模块的完整功能边界。你可以把它理解为“功能清单”。

### 6.1 对外 API 功能

这个模块至少要提供以下接口能力：

- 健康检查
- 读取当前模型信息
- 单次影像分割
- 批量影像分割
- 返回标准化错误响应

建议最小 API 集：

- `GET /health`
  - 服务是否存活
- `GET /models/current`
  - 当前使用的模型名称、版本、设备信息
- `POST /segment`
  - 单次分割主接口
- `POST /segment/batch`
  - 批量分割接口，后续可选

### 6.2 输入处理功能

这个模块要能接收并处理以下类型的输入：

- 本地文件路径
- 上传文件
- base64 图片
- 对象存储 URI
- 后续可扩展为 DICOM 文件或序列

要完成的处理包括：

- 输入合法性校验
- 文件存在性检查
- 格式识别
- 图像解码
- 模态识别或模态字段校验
- 文件大小限制
- 请求参数完整性检查

### 6.3 预处理功能

模型推理前需要做的工作包括：

- 图像 resize
- normalize
- 灰度/通道处理
- tensor 化
- 批次维度补齐
- DICOM 转普通图像或 numpy
- 窗宽窗位处理
- 尺寸和坐标系记录

为什么要单列这一层：

- 后处理要把模型输出映射回原图坐标
- 所以预处理必须记录变换信息

### 6.4 模型推理功能

这个模块的核心功能是执行 U-Net 模型推理，包括：

- 加载模型权重
- 模型初始化
- CPU/GPU 设备选择
- 推理执行
- 推理超时控制
- 模型热加载或重启后恢复

如果以后模型有多个版本，还需要支持：

- 指定模型版本
- 默认模型切换
- 模型版本查询

### 6.5 后处理功能

模型输出后，这个模块要把原始结果整理成业务结果。

需要实现的能力包括：

- logits 转 mask
- 阈值处理
- 小噪点过滤
- 连通域分离
- 主病灶筛选
- 轮廓提取
- bounding box 计算
- 面积/直径/覆盖率计算
- 百分比坐标转换
- 颜色图例映射

这一步的结果最终要能被 `metric-service` 用于生成：

- `highlightRegions`
- `highlightLegend`
- 结构化指标

### 6.6 质量门禁功能

这个模块不能只要模型有输出就算成功，还要做质量控制。

至少要实现：

- 空结果检测
- 低置信度检测
- 面积异常检测
- 多病灶异常检测
- 非法坐标检测
- 推理超时检测
- 结果状态判断

建议把结果状态分成：

- `PASS`
- `LIMITED`
- `FAIL`

这样 `metric-service` 后续才能根据状态决定：

- 是否继续生成正式报告
- 是否需要提示人工复核

### 6.7 结构化结果输出功能

这个模块最终不能只返回一张 mask 图，它必须输出结构化结果。

至少应返回：

- 请求 ID
- 患者信息
- 影像类型
- 模型名称
- 模型版本
- 推理耗时
- 分割类别
- 置信度
- 轮廓点
- 边界框
- 面积/大小等派生指标
- mask 文件位置
- overlay 文件位置
- 质量状态

这一点非常关键，因为后续 `metric-service` 依赖的是结构化结果，不是原始模型 tensor。

### 6.8 工件存储功能

这个模块通常还需要保存推理相关工件。

包括：

- 原始输入快照
- mask 图
- overlay 图
- 中间结果文件
- 调试输出文件

这些工件的用途包括：

- 报表生成
- 问题排查
- 模型复盘
- 人工复核

### 6.9 与 `metric-service` 的集成功能

这个模块必须和 `metric-service` 配合工作。

至少要支持：

- 接收 `metric-service` 的调用
- 返回固定契约的 JSON 响应
- 与 `metric-service` 统一错误码
- 必要时支持回调或异步任务状态查询

如果未来要做异步任务，还应支持：

- 创建任务
- 查询任务状态
- 查询任务结果

### 6.10 运维与可观测功能

一个生产服务不仅要能跑，还要能观察。

建议这个模块实现：

- 健康检查
- 就绪检查
- 结构化日志
- 请求耗时统计
- 推理耗时统计
- 错误码统计
- 模型版本曝光

如果以后接监控系统，还可以继续接：

- Prometheus 指标
- Trace
- GPU 利用率监控

### 6.11 测试功能

这个模块还要具备完整测试能力。

至少包括：

- 单元测试
- 集成测试
- 假模型或 mock 推理测试
- 样例影像回归测试

你可以理解为：

- 没有测试，模型服务后面接业务系统会很难控风险

## 7. 一次典型请求在项目中的流转路径

为了更直观，这里给出一次请求从进入到返回的大致流转。

```text
1. app/api/routes/
   接收 /segment 请求

2. app/schemas/requests/
   解析请求参数

3. app/services/orchestration/
   开始组织一次完整分割任务

4. app/preprocessing/
   读取图像并做预处理

5. app/inference/engines/
   调用 U-Net 推理

6. app/postprocessing/
   生成 mask、contour、bounding box、指标

7. app/services/quality/
   做质量门禁判断

8. app/services/storage/
   保存 mask / overlay 等工件

9. app/schemas/responses/
   组装响应

10. app/api/routes/
    返回给 metric-service 或调用方
```

## 8. 你后续最常编辑的目录

如果你准备继续推进这个模块，最常碰到的目录会是：

- `app/api/routes/`
  - 写接口
- `app/schemas/`
  - 定义请求和响应结构
- `app/preprocessing/`
  - 处理影像输入
- `app/inference/`
  - 接模型
- `app/postprocessing/`
  - 做分割结果整理
- `app/services/orchestration/`
  - 串起完整流程
- `tests/`
  - 写测试

## 9. 建议的实现优先级

如果按最稳妥的顺序推进，建议这样做：

1. 先补 Python 项目入口文件和依赖文件
2. 先做 `GET /health`
3. 再定义 `/segment` 的请求和响应结构
4. 再接一个假模型或 mock 推理流程
5. 再接真实 U-Net
6. 最后补工件存储、质量门禁和批量能力

这个顺序的好处是：

- 可以很快跑通服务骨架
- 不会一开始就被真实模型和部署问题卡住

## 10. 最后总结

`segmentation-service` 不是一个通用业务后台，而是一个专门的影像分割推理服务。

它的核心职责只有三件事：

- 把输入影像变成模型可用的数据
- 调用 U-Net 得到分割结果
- 把模型结果变成 `metric-service` 可以稳定消费的结构化输出

如果后续你只记住一条原则，那就是：

- API 层不要写模型逻辑
- 模型层不要直接依赖外部业务系统
- 最终对外只暴露稳定的结构化结果
