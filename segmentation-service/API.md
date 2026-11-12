# segmentation-service 当前 API 参考

本文按基线 `b556ebf` 的实现整理，描述现有接口，不是功能规划。接口字段统一为 **snake_case**。请求和业务响应使用 JSON；没有文件上传、工件下载、模型切换或异步任务查询接口。

相关文档：[项目 API 总览](../docs/api-reference.md) · [模块 README](README.md) · [操作手册](../docs/segmentation-service-operation-manual.md)。行为与医疗边界以 [模块 SPEC](SPEC.md)、[训练 SPEC](app/training/SPEC.md)、[AI 边界策略](../docs/hospital-ai-boundary-policy.md)及下述源码为准；操作材料中的目录示意或未来能力不代表已实现。

> 课程模型包固定用于 `research`，仅支持单张超声栅格图的二值区域分割，不做良恶性分类或诊断。`PASS`、`completed`、`weights_loaded=true`、模型哈希或生成工件均不是临床资格证明。临床有效性、负载能力及完整鉴权链路未验收，不能据此称为生产可用。

## 1. 地址与路由

本地常用基址为 `http://127.0.0.1:8099`；默认 `api_prefix=/api/v1`。端口和前缀可配置，不是协议常量：`SEGMENTATION_SERVICE_PORT`、`SEGMENTATION_SERVICE_API_PREFIX` 可覆盖相应设置，实际监听地址还取决于启动参数。仓库测试 TOML 的端口为 `8092`，不是本地默认端口。

下表列出全部 **4 个业务路由**，路径按默认前缀展示：

| HTTP 方法 | 路径 | 请求体 | 正常返回 |
| --- | --- | --- | --- |
| GET | `/health` | 无 | `HealthResponse`，HTTP 200；仅存活与原始配置摘要 |
| GET | `/api/v1/models/current` | 无 | `ModelInfoResponse`，HTTP 200；当前模型描述 |
| POST | `/api/v1/segment` | `SegmentationRequest` | `SegmentationResponse`，HTTP 200（含质量门禁失败结果） |
| POST | `/api/v1/segment/batch` | `BatchSegmentationRequest` | `BatchSegmentationResponse`，HTTP 200（整批无异常时） |

只有 `/models/current`、`/segment`、`/segment/batch` 随 `api_prefix` 挂载。FastAPI 自动文档位于根路径，**不随该前缀变化**：

| HTTP 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/docs` | Swagger UI |
| GET | `/redoc` | ReDoc |
| GET | `/openapi.json` | OpenAPI JSON |

源码：[app/main.py](app/main.py)、[app/api/router.py](app/api/router.py)、[health 路由](app/api/routes/health.py)、[models 路由](app/api/routes/models.py)、[segmentation 路由](app/api/routes/segmentation.py)。OpenAPI 的 POST 响应声明只列出 200/422，自定义异常处理器的错误码还需按本文第 7 节处理。

跨服务注意：Kotlin [SegmentationServiceClient.kt](../metric-service/src/main/kotlin/aiagent/tools/SegmentationServiceClient.kt) 的 `endpointUri()` 固定使用 `/api/v1/segment` 后缀，并未读取 Python 的 `api_prefix`；只更改 Python 前缀不能保证调用方同步适配。

## 2. 单张请求：`POST /api/v1/segment`

以 `Content-Type: application/json` 发送对象。以下是完整请求示例，身份均为虚构的合成样例；文件必须由调用方预先准备在**服务端可读的文件系统**中，不是浏览器或调用方机器上的路径。

```json
{
  "request_id": "demo-synthetic-001",
  "hospital_id": "DEMO-HOSPITAL",
  "patient_id": "SYNTHETIC-ONLY",
  "patient_name": "合成样例（非患者）",
  "image_type": "ULTRASOUND",
  "image": {
    "source_type": "LOCAL_FILE",
    "value": "/path/to/synthetic-ultrasound.png",
    "mime_type": "image/png"
  },
  "options": {
    "return_mask": true,
    "return_contour": true,
    "return_metrics": true
  }
}
```

### 2.1 顶层字段（`SegmentationRequest`）

“必填”与“允许 null”不同；省略 `options` 使用默认对象，显式传 `options: null` 不合法。未声明的额外字段按 Pydantic 默认行为忽略，不提供 camelCase 别名；拼错可选字段不会自动变成所需选项。

| 字段 | JSON 类型 | 必填 / 默认 | 当前约束与语义 |
| --- | --- | --- | --- |
| `request_id` | string 或 null | 否；默认 null | 非 null 时完整匹配 `[A-Za-z0-9][A-Za-z0-9._-]{0,127}`，即 1–128 个字符的安全单目录组件；不做 trim。空串、路径分隔符、`..` 等校验失败为 422。省略或 null 时生成 `seg-<UUIDv4>` |
| `hospital_id` | string | 是 | `min_length=1`，strip 后也不得为空；无医院存在性或权限校验 |
| `patient_id` | string | 是 | `min_length=1`，strip 后不得为空；无患者登记或归属校验 |
| `patient_name` | string | 是 | `min_length=1`，strip 后不得为空；仅作为结果身份字段 |
| `image_type` | string | 是 | `min_length=1`，strip 后不得为空，执行时转为大写。旧模式无模态枚举校验；研究包只允许 `ULTRASOUND`，其他值执行时返回 400 `INVALID_IMAGE`，不是 422 |
| `image` | object | 是 | `ImageReferenceRequest`，见下表；不可为 null |
| `options` | object | 否；默认三个选项均为 true | `SegmentationOptionsRequest`，见 2.3；不可为 null |

除 `request_id` 外，上述文本字段没有应用定义的最大长度。模型、阈值、物理间距和工件目录不是请求选项，不能通过额外 JSON 字段逐次指定。

源码：[请求 schema](app/schemas/requests/segmentation.py)、[request_id 校验](app/domain/value_objects/request_id.py)、[请求规范化](app/preprocessing/normalizer.py)、[ID 生成](app/utils/ids.py)。

### 2.2 图像引用（`ImageReferenceRequest`）

| 字段 | JSON 类型 | 必填 / 默认 | 当前约束与语义 |
| --- | --- | --- | --- |
| `source_type` | string | 是 | 严格区分大小写的四选一：`LOCAL_FILE`、`DATA_URL`、`OBJECT_STORAGE`、`INLINE_REFERENCE`；其他值为 schema 校验 422 |
| `value` | string | 是 | `min_length=1`，strip 后不得为空；解释方式取决于 `source_type` |
| `mime_type` | string 或 null | 否；默认 null | 可选提示，仅传递到内部图像对象；没有 MIME 白名单、格式真实性或与字节一致性校验 |

| `source_type` | `value` 的真实解释 | 限制 / 失败行为 |
| --- | --- | --- |
| `LOCAL_FILE` | 用 `Path(value).expanduser()` 读取本机文件字节；相对路径相对于服务进程工作目录 | 必须存在且是文件，否则 400 `INVALID_IMAGE`；不是上传或文件共享协议。没有本地目录白名单；权限等未包装的 I/O 异常可返回 500 |
| `DATA_URL` | 按首个逗号拆分头和载荷；头含 `;base64`（不区分大小写）时严格 Base64 解码，否则 URL 百分号解码 | 空载荷、无效 Base64 或 Pillow 无法识别图像时为 400 `INVALID_IMAGE`。实现没有严格校验 `data:` 头或其 MIME 声明，不能把 schema 接受当作格式验证成功 |
| `OBJECT_STORAGE` | **仅**接受以 `http://` 或 `https://` 开头的 URL，由 `urllib.request.urlopen(..., timeout=10)` 读取字节 | 名称不代表原生 S3：不接受 `s3://`，没有 bucket/key、云凭证或签名生成接口。10 秒是此远程读取调用的超时设置，不是端到端推理期限。非 HTTP(S) 值返回 400 `UNSUPPORTED_IMAGE_SOURCE`；网络/读取超时等未专门映射，落入 500 `INTERNAL_SERVER_ERROR`，不是 504 |
| `INLINE_REFERENCE` | schema 接受的引用字符串，但不含像素字节 | 进入加载器后固定返回 400 `UNSUPPORTED_IMAGE_SOURCE`，消息为 `Inline reference does not include raster bytes`；不能仅凭引用或自由文本推理 |

前三种来源最终统一由 Pillow 解码为**单张二维栅格图**，转换 RGB 预览和灰度模型输入。没有原生 DICOM、序列/体数据、多帧遍历、HU 窗宽窗位或像素物理间距/方向解析；传入 `image_type=CT/MRI` 不会增加这些能力。没有 multipart、二进制直传或自动对象存储适配。

源码：[app/preprocessing/image_loader.py](app/preprocessing/image_loader.py)、[模态校验](app/core/config/settings.py)、[流水线](app/inference/pipelines/segmentation_pipeline.py)。

### 2.3 输出选项（`SegmentationOptionsRequest`）

三个字段均为 boolean、可省略、默认 `true`、不允许 null：

| 字段 | 当前实际作用 |
| --- | --- |
| `return_mask` | 当服务级 `return_artifacts=true` 时控制是否写 `mask.png`。设为 false 后 `artifacts.mask_path=null`，**仍计算分割与区域，仍写 overlay 和 metadata** |
| `return_contour` | false 时各区域 `contour=[]`；不影响边界框、区域、mask 或其他测量 |
| `return_metrics` | schema 接受并传入内部请求，但当前**未落实关闭测量的行为**；false 仍返回置信度、覆盖率、像素宽高等字段，不能当作“不计算/不返回指标”的保证 |

服务级 `return_artifacts=false` 时三个工件路径全为 null，且不创建本次工件目录；请求中的 `return_mask=true` 不能覆盖此设置。`return_contour` 仍独立生效。

源码：[内部请求字段](app/schemas/internal/segmentation.py)、[区域组装](app/postprocessing/assembler.py)、[工件存储](app/services/storage/artifacts.py)。

## 3. 单张响应：`SegmentationResponse`

无执行或存储异常时返回 HTTP 200。以下表格覆盖全部响应字段；`string 或 null` 等表示允许 JSON null，未如此标注的字段不可为 null。当前序列化会输出所有列出的字段，包括默认值、null 和空数组；响应 schema 的兼容默认值不是请求可选配置。

### 3.1 顶层字段

| 字段 | JSON 类型 | 语义 |
| --- | --- | --- |
| `request_id` | string | 校验后的调用方 ID 或自动生成 ID；关联区域与服务端工件目录 |
| `status` | string | 门禁 `PASS` → `completed`、`LIMITED` → `limited`、`FAIL` → `failed`；不是 `success` 布尔字段，也不等于 HTTP 状态 |
| `message` | string | 执行说明，包含门禁原因；存在 `model_note` 时追加模型说明。研究包说明用途、概率非校准及当前 BFS 阈值；不是诊断结论或稳定错误码 |
| `hospital_id` | string | strip 后的请求值 |
| `patient_id` | string | strip 后的请求值 |
| `patient_name` | string | strip 后的请求值 |
| `image_type` | string | strip 并转大写后的请求值 |
| `model` | object | `ModelDescriptorResponse`，见 3.2 |
| `timing` | object | `TimingResponse`，见 3.3；不是总耗时 |
| `quality_gate` | object | `QualityGateResponse`，见 3.4；只做启发式输出门禁 |
| `artifacts` | object | `ArtifactPathsResponse`，见 3.6；即使不写工件仍返回对象 |
| `regions` | array of object | `SegmentationRegionResponse` 列表；可为空数组，不是 null。按区域平均前景概率降序排列，无 `classes` 数组 |

源码：[响应 schema](app/schemas/responses/segmentation.py)、[领域结果](app/domain/entities/segmentation.py)、[编排与响应转换](app/services/orchestration/segmentation.py)。

### 3.2 模型描述（`ModelDescriptorResponse`）

| `model` 字段 | JSON 类型 | 当前语义 / schema 默认 |
| --- | --- | --- |
| `name` | string | 模型名称；研究包派生为 `unet-coursework-research` |
| `version` | string | 模型版本；研究包为 `research-` 加 manifest SHA-256 的前 16 位，不是临床版本批准号 |
| `backend` | string | 后端标识；研究包固定 `torch_unet`。旧模式可为 mock 或 Torch 配置标识，schema 不将此字段限定为枚举 |
| `device` | string | Torch 实际解析出的运行设备（如 `cpu`、`mps`、`cuda:0`）；不可用的请求设备可能回退 CPU。mock 仅回显其设置 |
| `input_height` | integer | 模型输入高度，单位像素；不是原图高度 |
| `input_width` | integer | 模型输入宽度，单位像素；不是原图宽度 |
| `in_channels` | integer | 模型输入通道数；研究包固定 1。旧多通道预处理是灰度复制，不是真 RGB 模型输入 |
| `output_classes` | integer | 网络输出通道数；研究包固定 1。旧多输出仅 softmax 后取配置的一个 lesion 通道，不代表返回多类别区域体系 |
| `weights_path` | string 或 null | 服务端权重文件路径，默认 null；不是下载 URL。路径存在或非空不等于加载成功 |
| `weights_loaded` | boolean | 是否成功加载权重；随机权重与 mock 为 false。true 只证明加载，不证明有训练、来源合规、精度或临床适用性 |
| `intended_use` | string | schema 默认 `unspecified`，旧模式包括已加载裸权重仍为此值；研究包固定 `research`，消费方不得丢弃或升级用途 |
| `bundle_manifest_sha256` | string 或 null | 默认 null；研究包为 **manifest 完整文件原始字节的 SHA-256 十六进制摘要**，不是整个目录/所有文件的聚合哈希、权重哈希或数字签名，不能认证来源许可或临床效度 |
| `supported_image_types` | array of string | schema 默认 `[]`；研究包固定 `["ULTRASOUND"]`。旧模式空列表不构成“支持全部医学模态”的验证证据 |

配置研究包后，会校验 manifest 的 schema/架构/用途/预处理声明、权重文件 SHA-256 及严格 state_dict 结构，强制 Torch、禁用随机权重，尺寸/通道/概率阈值以包为准。损坏或不兼容时拒绝，不自动退回 mock/随机模型。裸权重与模型包均使用受限 `weights_only=True` 加载；并非任意网络、TorchScript 或 ONNX 自动适配服务。

源码：[模型实体](app/domain/entities/model.py)、[模型包校验](app/inference/model_bundle.py)、[有效模型配置](app/core/config/settings.py)、[Torch 引擎](app/inference/engines/torch_unet_engine.py)、[mock 描述符](app/inference/backends/mock_backend.py)。

### 3.3 计时（`TimingResponse`）

| `timing` 字段 | JSON 类型 / 单位 | 真实计时范围 |
| --- | --- | --- |
| `preprocess_ms` | integer，毫秒 | 图像已经加载/解码后，准备模型 tensor 的耗时；不含本地文件/远程读取和解码 |
| `inference_ms` | integer，毫秒 | Torch 前向及概率激活的计时，支持的加速设备前后同步；不包含模型初始化、输入搬运及返回 CPU 概率数组等完整开销。mock 固定为 5，不是实测推理性能 |
| `postprocess_ms` | integer，毫秒 | 浮点概率恢复原尺寸、阈值化及四连通 BFS 清理的耗时；不含后续区域组装、质量门禁及工件写入 |

实测值经 `int(秒数 × 1000)` 截断，可为 0；三个字段之和**不是请求端到端耗时**，无 `total_ms` 或推理取消保证。

源码：[图像预处理](app/preprocessing/image_preprocessor.py)、[Torch 引擎](app/inference/engines/torch_unet_engine.py)、[流水线](app/inference/pipelines/segmentation_pipeline.py)。

### 3.4 质量门禁（`QualityGateResponse`）

| `quality_gate` 字段 | JSON 类型 | 语义 |
| --- | --- | --- |
| `status` | string | 当前运行值为 `PASS`、`LIMITED`、`FAIL`（schema 本身仅声明 string） |
| `reason` | string | 第一个命中条件的原因，见下表；不是医疗风险评估 |

按下列顺序判定，命中即返回：

| 条件 | 门禁 | 顶层 `status` | 当前 `reason` |
| --- | --- | --- | --- |
| BFS 清理后的 mask 无前景 | `FAIL` | `failed` | `No foreground mask was produced` |
| mask 非空但区域列表为空 | `FAIL` | `failed` | `All detected regions were filtered out` |
| 区域列表中的**最高** `confidence` 小于 `min_confidence`（默认 0.55） | `LIMITED` | `limited` | `Segmentation confidence is below the configured production threshold` |
| 区域列表中的**最大单区域** `coverage_percent` 大于 80.0（不是总覆盖率） | `LIMITED` | `limited` | `Foreground coverage is unusually large and should be reviewed manually` |
| 其余 | `PASS` | `completed` | `Segmentation passed the bootstrap quality gate` |

最高置信度和最大覆盖率取自已组装的区域字段。门禁不核验真实标注、训练来源、模型准确性或临床资格，mock/随机前向也可能 PASS；原因中的 `production threshold` 只是现有代码文字，不表示已经完成生产验收。在线空预测仍需复核，不表示患者正常。

`FAIL`、`LIMITED` **仍返回 HTTP 200**，并仍可能写工件。如下仅是说明 HTTP 200 质量失败的**字段片段，不是完整响应或医疗结果**：

```json
{
  "status": "failed",
  "quality_gate": {
    "status": "FAIL",
    "reason": "No foreground mask was produced"
  },
  "regions": []
}
```

源码：[质量门禁](app/services/quality/gate.py)、[状态和消息映射](app/services/orchestration/segmentation.py)。

### 3.5 区域与几何（`SegmentationRegionResponse`）

几何均来自**原图尺寸**的二维栅格：灰度输入双线性拉伸至模型尺寸并归一化为 `[-1,1]`；前景概率以 float32 恢复原尺寸（不经 8-bit 图像量化），按 `probability >= probability_threshold` 二值化，再做上下左右四连通 BFS 小区域过滤。概率阈值旧配置默认 0.5，研究包取 manifest 中的阈值；它不是 HTTP 请求字段。

| `regions[]` 字段 | JSON 类型 | 单位与真实语义 |
| --- | --- | --- |
| `region_id` | string | `<request_id>-region-<序号>`；序号按栅格扫描发现并保留区域的次序分配，随后列表按平均概率降序排序，序号不代表排名或医学分级 |
| `class_name` | string | 研究包固定 `target_region`；旧 Torch/mock 为 `lesion`，不代表良恶性分类 |
| `label` | string | 研究包为 `ultrasound-target-region`；旧 Torch 为 `<小写 image_type>-lesion`，mock 为 `<小写 image_type>-candidate`；只是后端标签 |
| `confidence` | number | 该区域原图前景概率的像素均值，0–1、无单位，四舍五入 4 位小数；不是患病概率、经过临床校准的置信度或精度分数 |
| `location` | string | 区域像素质心的**图像坐标**描述；研究包按 x/y 是否小于 50% 分为 `upper-left image quadrant`、`upper-right image quadrant`、`lower-left image quadrant`、`lower-right image quadrant`，等于 50% 归右/下。不是解剖位置 |
| `severity` | string | 研究包固定 `not_assessed`。旧模式依平均概率/覆盖率规则输出 `high`、`medium`、`low`，不是临床风险或病灶恶性程度 |
| `coverage_percent` | number | `pixel_count / (原图高 × 原图宽) × 100`，单位百分比 0–100，四舍五入 4 位；不是区域框面积占比 |
| `estimated_size_mm` | number 或 null | schema 默认 null。研究包始终 null（强制取消物理间距）；旧模式间距非 null 时为 `max(width_px, height_px) × pixel_spacing_mm`，单位毫米，四舍五入 2 位；它是包围框长边的规则估值，不是直径或患者特异测量 |
| `pixel_count` | integer | 保留连通区域的前景像素数，单位原图像素；schema 兼容默认 0，当前流水线填实测计数 |
| `width_px` | integer | 区域包围框宽度 `right-left+1`，单位原图像素；schema 兼容默认 0，当前非空区域至少 1 |
| `height_px` | integer | 区域包围框高度 `bottom-top+1`，单位原图像素；schema 兼容默认 0，当前非空区域至少 1 |
| `bounding_box` | object | `BoundingBoxResponse`，下表四个百分比字段；不可为 null |
| `contour` | array of object | `ContourPointResponse` 点列；`return_contour=false` 时为空数组，不是 null。不是完整边界/拓扑或闭合多边形保证 |

`bounding_box` 与 `contour[]` 的字段均为非 null 的 number，百分比按 `值 / 原图对应尺寸 × 100` 计算并四舍五入 4 位；坐标原点在左上，x 向右、y 向下，分母是宽/高，**不是宽减一/高减一**：

| 对象 | 字段 | 计算 / 单位 |
| --- | --- | --- |
| `bounding_box` | `left_percent` | 最左像素 x / 原图宽 × 100，% |
| `bounding_box` | `top_percent` | 最上像素 y / 原图高 × 100，% |
| `bounding_box` | `width_percent` | `width_px` / 原图宽 × 100，% |
| `bounding_box` | `height_percent` | `height_px` / 原图高 × 100，% |
| `contour[]` | `x_percent` | 边界采样像素 x / 原图宽 × 100，% |
| `contour[]` | `y_percent` | 边界采样像素 y / 原图高 × 100，% |

轮廓由边界像素按相对质心的极角排序、再下采样；最多 `max_contour_points` 个（Settings/本地默认 48，测试 TOML 为 24），未证明能保留孔洞、完整拓扑或精确解剖边界。

旧模式的 `location` 以 x/y 的 38%/62% 为分区阈值（小于 38 为 left/upper，大于 62 为 right/lower，中间为 central/mid），组合为 `central region`、`upper/lower central region`、`left/right middle region` 或象限字符串。旧 `severity` 的规则为：平均概率 ≥0.85 或覆盖率 ≥15% → high；否则概率 ≥0.65 或覆盖率 ≥6% → medium；否则 low。旧默认 `pixel_spacing_mm=0.7` 只是全局配置，不来自图像物理标定；研究包即使收到环境间距覆盖也保持毫米值为 null。

**BFS 最小区域阈值不是一个统一默认：**

| 场景 | `min_region_pixels`（原图像素） |
| --- | --- |
| 研究包，无显式覆盖 | 16 |
| 旧 Settings / 仓库 local TOML | 24 |
| 仓库 test TOML（显式配置） | 8 |
| 本次冻结 BUSI 实验与对应部署 | 必须显式设为 1024；这是验证集选定值，不是服务通用默认 |

少于阈值的连通区域删除，等于阈值的保留。显式 Settings 参数、自定义 TOML（通过 `SEGMENTATION_SERVICE_CONFIG_FILE`）或 `SEGMENTATION_SERVICE_MIN_REGION_PIXELS` 可覆盖研究默认；随仓库 local TOML 的旧值 24 不被视为研究包的显式覆盖。离线评测与部署必须一致，BFS 也可能删除真实小目标。

源码：[共享变换](app/preprocessing/transforms.py)、[原尺寸阈值与清理](app/inference/pipelines/segmentation_pipeline.py)、[BFS/几何/轮廓](app/postprocessing/mask_ops.py)、[区域组装](app/postprocessing/assembler.py)、[几何值对象](app/domain/value_objects/geometry.py)、[配置与研究默认派生](app/core/config/settings.py)、[冻结实验记录](app/training/SPEC.md)。上述范围是计算语义，响应 schema 并未为所有 number/integer 添加范围约束。

### 3.6 工件（`ArtifactPathsResponse`）

| `artifacts` 字段 | JSON 类型 / 默认 | 实际内容 |
| --- | --- | --- |
| `mask_path` | string 或 null；默认 null | `<artifacts_dir>/<request_id>/mask.png`；原图尺寸的 BFS 清理 mask，背景 0、前景 255；不开工件或 `return_mask=false` 时 null |
| `overlay_path` | string 或 null；默认 null | 同目录 `overlay.png`；原图 RGB 与清理后前景的红色高亮混合图；开启工件时即使不返回 mask 文件仍写入 |
| `metadata_path` | string 或 null；默认 null | 同目录 `result.json`；记录身份、状态/消息、model、timing、quality_gate、regions，**不包含响应的 `artifacts` 路径对象** |

这些是**服务端文件系统路径，不是 URL**；本服务没有下载或静态文件路由。默认本地 `artifacts_dir=var/artifacts`，相对配置按 `segmentation-service/` 根目录解析；test TOML 默认关闭工件并使用 `var/test-artifacts`。文件内容可能包含身份信息，不能直接公开目录或真实患者工件。

工件目录使用 `mkdir(exist_ok=False)` 原子预留，禁止覆盖已有结果。开启工件时重复 `request_id` 会在执行至写入阶段返回 **500 `ARTIFACT_STORAGE_ERROR`**，不是 409、缓存命中或幂等重试。即使 `return_mask=false` 也会预留目录；关闭全部工件时则无此目录占用/去重行为。写入异常可留下已预留目录或部分文件；没有回滚，失败后也不能假定重用同一 ID 安全。

质量 FAIL/LIMITED 本身不会阻止工件写入；最终 HTTP 200 也以本次执行没有存储异常为前提。

源码：[app/services/storage/artifacts.py](app/services/storage/artifacts.py)、[编排顺序](app/services/orchestration/segmentation.py)、[路径解析](app/core/config/settings.py)。

## 4. 批量：`POST /api/v1/segment/batch`

`BatchSegmentationRequest` 只有必填的 `items: array<SegmentationRequest>`，`min_length=1`，无应用定义的最大项数；空数组/null/无效条目按 schema 校验返回 422。以下为完整的单项批量请求示例，不是响应或医疗结果：

```json
{
  "items": [
    {
      "hospital_id": "DEMO-HOSPITAL",
      "patient_id": "SYNTHETIC-ONLY",
      "patient_name": "合成样例（非患者）",
      "image_type": "ULTRASOUND",
      "image": {
        "source_type": "LOCAL_FILE",
        "value": "/path/to/synthetic-ultrasound.png"
      }
    }
  ]
}
```

`BatchSegmentationResponse` 也只有 `items: array<SegmentationResponse>`，按输入顺序**逐项同步执行**并返回；每项字段与第 3 节完全一致。没有 `overall`、`partial_failure`、批次状态、任务 ID 或每项异常包装。

- 单项门禁 `failed`/`limited` 不是异常：保留该项响应并继续下一项；整批可 HTTP 200 且包含不同业务状态。
- 任一项发生加载、推理、工件等异常，立即中止，后续项不执行；HTTP 响应为该异常的错误体，不返回已完成项列表。
- 已完成项及失败项已写入的工件**不回滚**。批内重复 ID 也可能在后续项触发工件冲突 500，不是批量幂等机制。

源码：[批量 schema](app/schemas/requests/segmentation.py)、[批量响应 schema](app/schemas/responses/segmentation.py)、[顺序列表执行](app/services/orchestration/segmentation.py)。

## 5. 存活：`GET /health`

无请求参数。`HealthResponse` 全部字段为非 null 的 string，无 schema 默认值：

| 字段 | 当前含义 |
| --- | --- |
| `status` | 固定 `ok` |
| `service` | 原始 settings 的 `app_name` |
| `version` | 原始 settings 的 `app_version`，不是模型版本 |
| `environment` | 原始 settings 的环境标识 |
| `backend` | 原始 settings 的 `inference_backend` |
| `model_name` | 原始 settings 的模型名称 |
| `model_version` | 原始 settings 的模型版本 |

它只读取已解析的 **raw settings（未经模型包派生）**，不解析分割编排器，不验证模型包/权重、Torch、设备、图像读取或输出目录可写性。`status=ok` 不是 readiness；设置了研究包时，此处的 backend/name/version 甚至可能与 `/models/current` 的有效描述不同。

源码：[health 路由](app/api/routes/health.py)、[HealthResponse](app/schemas/responses/health.py)、[get_runtime_settings](app/api/dependencies/services.py)。

## 6. 当前模型：`GET /api/v1/models/current`

无请求参数。`ModelInfoResponse` 为一个对象，不包装在 `model` 键下：

| 字段 | JSON 类型 | 含义 / 默认 |
| --- | --- | --- |
| `service_name` | string | 编排器有效设置的 `app_name`；注意健康接口使用 `service` |
| `environment` | string | 编排器的环境标识 |
| `backend` | string | 模型描述符的后端，语义同 3.2 |
| `model_name` | string | 模型描述符的 `name`，此处不是 `name` 键 |
| `model_version` | string | 模型描述符的 `version`，此处不是 `version` 键 |
| `device` | string | 描述符设备，同 3.2 |
| `input_height` | integer | 模型输入高度，像素 |
| `input_width` | integer | 模型输入宽度，像素 |
| `in_channels` | integer | 模型输入通道数 |
| `output_classes` | integer | 网络输出通道数 |
| `weights_path` | string 或 null | 服务端权重路径，schema 默认 null |
| `weights_loaded` | boolean | 权重是否已加载，不是模型质量认证 |
| `intended_use` | string | schema 默认 `unspecified`；研究包为 `research` |
| `bundle_manifest_sha256` | string 或 null | schema 默认 null；完整 manifest 字节的 SHA-256，非签名，见 3.2 |
| `supported_image_types` | array of string | schema 默认 `[]`；研究包为 `["ULTRASOUND"]` |

此路由依赖 `get_segmentation_orchestrator()`，**首次解析依赖会触发模型包校验、后端构建及模型/权重加载**，可以返回第 7 节的配置、依赖或推理错误。设置与编排器在进程内缓存；该路由不是每次重新读取权重、执行推理或验证临床资格的检查。

源码：[models 路由](app/api/routes/models.py)、[ModelInfoResponse](app/schemas/responses/models.py)、[依赖构建与缓存](app/api/dependencies/services.py)。

## 7. HTTP 错误与质量状态的区别

### 7.1 服务异常体

可预期服务异常及未处理异常使用以下结构；所有键都会返回，`detail` 可为 null：

| 字段 | JSON 类型 | 含义 |
| --- | --- | --- |
| `error_code` | string | 服务错误码，见下表 |
| `message` | string | 失败说明 |
| `detail` | string 或 null | 具体原因/异常字符串，可能含服务端路径或输入引用，不宜直接公开 |

完整错误体示例（引用为虚构值）：

```json
{
  "error_code": "UNSUPPORTED_IMAGE_SOURCE",
  "message": "Inline reference does not include raster bytes",
  "detail": "synthetic-reference-only"
}
```

| `error_code` | HTTP | 当前触发示例 |
| --- | --- | --- |
| `CONFIGURATION_ERROR` | 500 | 不支持的后端配置 |
| `UNSUPPORTED_IMAGE_SOURCE` | 400 | 执行 `INLINE_REFERENCE`，或 OBJECT_STORAGE 不是 HTTP(S) URL |
| `INFERENCE_UNAVAILABLE` | 503 | 必需权重缺失/不可加载；研究包缺失、损坏、不兼容、后端描述不匹配；研究包缺少 Torch 也按此处理，无随机/mock 回退 |
| `DEPENDENCY_MISSING` | 500 | 旧模式的 Torch 后端缺少 Torch 依赖 |
| `INVALID_IMAGE` | 400 | 本地文件不存在/不是文件、无效 Data URL 载荷、Pillow 无法识别图像；研究包不支持请求模态 |
| `ARTIFACT_STORAGE_ERROR` | 500 | 工件目录冲突、无法创建/写入 mask、overlay 或 metadata |
| `INTERNAL_SERVER_ERROR` | 500 | 未处理异常；消息固定 `Unexpected server error`，detail 为异常字符串。包括未专门包装的远程网络/超时或文件 I/O 错误等 |

错误映射只针对进入应用异常处理器的情况；启动阶段配置解析失败等不保证能够返回 HTTP。不存在专门的 `504` 推理超时错误契约。`request_timeout_seconds` 虽有配置（旧 Settings/local 默认 30 秒，test TOML 为 10 秒），当前没有落实推理执行取消或整批截止时间，也不能替代 OBJECT_STORAGE 的固定 `timeout=10`。

源码：[异常类型与 HTTP 映射](app/core/errors/exceptions.py)、[异常处理器](app/core/errors/handlers.py)、[错误 schema](app/schemas/responses/common.py)、[后端选择](app/inference/backends/factory.py)、[Torch 加载](app/inference/engines/torch_unet_engine.py)。

### 7.2 FastAPI 默认 422 / 404（不是上述错误体）

缺字段、类型/枚举错误、空白必填文本、不安全的 `request_id` 或空批量等请求校验失败为 HTTP **422**，使用 `HTTPValidationError`：顶层 `detail` 是校验项数组，不含服务的 `error_code`/`message`。

| `detail[]` 字段 | JSON 类型 | 含义 |
| --- | --- | --- |
| `loc` | array of string 或 integer 元素 | 字段路径，可含 `body`、`items`、批量下标和字段名 |
| `msg` | string | 校验说明 |
| `type` | string | 校验错误类别 |
| `input` | 任意 JSON 值；可省略 | 相关输入，可能包含敏感内容 |
| `ctx` | object；可省略 | 额外校验上下文，取决于错误类型 |

下例只展示一个校验项的结构，实际响应可能包含多项；不是分割结果：

```json
{
  "detail": [
    {
      "type": "missing",
      "loc": ["body", "hospital_id"],
      "msg": "Field required",
      "input": {}
    }
  ]
}
```

不存在的路由默认返回 HTTP **404**：

```json
{
  "detail": "Not Found"
}
```

HTTP 200 的 `quality_gate=FAIL/LIMITED`、服务异常错误体、默认 422 校验体和默认 404 体是不同层次的契约，调用方不能只解析其中一种或把 HTTP 200 一律当作可用于报告的成功结果。

## 8. 安全与接入边界

- 本服务没有实现认证、授权、API key、医院/患者资源归属校验；请求身份只是被回传/记录的字段，不是访问控制凭证。不要把根路径文档或业务路由当作已有鉴权保护。
- 缺少统一的输入字节数、像素数、批量项数、并发/速率等应用资源限额，缺少本地目录与远程 URL 白名单。远程 URL 和本机文件读取能力只应放在受控环境，不应直接对不可信用户开放。
- 工件路径不是可访问链接，且 metadata 记录身份信息；需由上层另行控制共享、存储与访问，不能据路径存在声称有下载能力。
- 本模块不拥有业务账号、患者档案、报告定稿或自由问答，不提供患者数据库写入、报告 API 或 LLM 兜底分析。失败或不合格结果不能由 LLM 补成诊断。
- 旧模式允许随机前向与显式 mock 联调；研究包严格限制 `research` / `ULTRASOUND`，毫米值为 null、风险未评估，前景概率非临床校准概率。调用方必须保留用途与单位，不能把研究区域静默映射成临床指标。

实现边界：[模块 SPEC](SPEC.md)、[医疗边界策略](../docs/hospital-ai-boundary-policy.md)。已有测试提供的是契约证据，不是本文重新执行或临床验收的声明：

- [tests/integration/test_app_routes.py](tests/integration/test_app_routes.py)：mock 健康与单张结构化结果。
- [tests/integration/test_model_bundle_routes.py](tests/integration/test_model_bundle_routes.py)：合成研究包的离线/API 几何一致、用途/模态拒绝、损坏包拒绝、nullable 毫米值、mask 开关、ID 校验与工件碰撞。
- [tests/unit/test_runtime_settings.py](tests/unit/test_runtime_settings.py)：配置优先级、研究默认与显式 BFS 覆盖的区分。
- [tests/unit/test_shared_postprocessing.py](tests/unit/test_shared_postprocessing.py)、[tests/unit/test_mask_ops.py](tests/unit/test_mask_ops.py)：四连通 BFS、像素单位、浮点恢复与旧响应兼容默认。
- [tests/unit/test_model_bundle.py](tests/unit/test_model_bundle.py)、[tests/unit/test_torch_weight_loading.py](tests/unit/test_torch_weight_loading.py)：模型包哈希/预处理校验、受限权重加载与设备计时同步。
