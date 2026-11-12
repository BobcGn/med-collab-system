# metric-service 当前 API 参考

本文按基线 `b556ebf` 的源码描述**当前实现**，不是生产可用性、医学有效性或已完成端到端验收的承诺。本次仅静态核对，未运行服务、数据库或训练；下文响应示例均非实测。示例身份、路径和数值仅为虚拟/合成科研数据，不含真实病例或凭据。

相关入口：[仓库 README](../README.md) · [统一 API 参考](../docs/api-reference.md) · [分割服务 API 参考](../segmentation-service/API.md)。职责以 [metric 规格](SPEC.md)、[受控工具链规格](src/main/kotlin/aiagent/SPEC.md)、[架构](../architecture.md) 和 [医疗 AI 边界政策](../docs/hospital-ai-boundary-policy.md) 为准。

> **安全与医学边界**：普通直连 CRUD、会话和文件下载没有路由级 `authenticate`；只有 AI HTTP/WS 手工验 JWT，且没有角色、医院或患者资源授权。确定性、结构化、权重已加载或生成 PDF 均不代表临床有效。research 结果仅可生成明确非诊断的科研报告。失败不得由 LLM 补写分析或报告。

> **BREAKING CHANGE — nullable / confidence**：`GeneralMetric.confidence` 为 `Double? = null`；高亮区域 `estimatedSizeMm` 为 `Double? = null`，并增加可空像素量字段。科研成功的外层 `confidence` 为 null（WS 通常省略），不是旧的固定 95。调用方必须支持 null/缺省，不能补成 `0 mm`、95 或把前景概率当作医学置信度。

## 1. 地址、认证与全部路由

默认直连 HTTP：`http://localhost:8083`；REST 网关：`http://localhost:8088/metric`。网关去掉 `/metric` 前缀再转发，直连 URL **不带** `/metric`。端口/地址可由部署配置改变。

| 方法 | metric 直连路径 | 网关 HTTP 路径 |
| --- | --- | --- |
| GET | `/health` | `/metric/health` |
| GET | `/images` | `/metric/images` |
| POST | `/images` | `/metric/images` |
| GET | `/images/patient/{name}` | `/metric/images/patient/{name}` |
| DELETE | `/images/{id}` | `/metric/images/{id}` |
| GET | `/analyses` | `/metric/analyses` |
| POST | `/analyses` | `/metric/analyses` |
| GET | `/analyses/patient/{name}` | `/metric/analyses/patient/{name}` |
| GET | `/reports` | `/metric/reports` |
| POST | `/reports` | `/metric/reports` |
| GET | `/reports/patient/{name}` | `/metric/reports/patient/{name}` |
| GET | `/reports/{id}/file` | `/metric/reports/{id}/file` |
| GET | `/conversations/{conversationId}` | `/metric/conversations/{conversationId}` |
| PUT | `/conversations/{conversationId}` | `/metric/conversations/{conversationId}` |
| DELETE | `/conversations/{conversationId}` | `/metric/conversations/{conversationId}` |
| POST | `/ai/analyze-and-report` | `/metric/ai/analyze-and-report` |
| WS | `/ws/ai-agent` | **没有 Ktor WebSocket 代理** |

WS 默认直连 `ws://localhost:8083/ws/ai-agent`（HTTPS 部署使用适配的 `wss://` 地址）。不要把 `/metric/ws/ai-agent` 写成已支持的网关 WS 入口；网关只有普通 HTTP 转发。网关自身的 `/health` 不是 metric 的数据库健康检查；独立 `/report` 前缀也不是 `/metric/reports` 的别名。

- 网关 `/metric/...` 外层使用 Ktor JWT 认证，通常携带 `Authorization: Bearer <有效JWT>`；它不会代替资源级授权。
- 直连：`/images`、`/analyses`、`/reports`、`/conversations`、`/health` 均未包在 `authenticate` 中；安装 Authentication 插件不等于这些路由已受保护。
- AI HTTP 和 WS 共用手工 token 提取：**非空 query `token` 优先于 Authorization**；否则读取 header，移除区分大小写的 `Bearer ` 前缀并验证。JWT 校验签名、issuer、audience、有效期，并要求能取出 subject 作为 `userId`。经网关时，仅 query token 不能替代外层认证 header。
- `role` 只是返回的用户上下文；请求 `hospitalId/patientId/patientName` 没有与 JWT 操作者范围绑定。姓名查询也没有医院过滤，不能视为可靠的患者唯一定位或隔离。
- 路径参数使用 URL 编码。没有分页路由，也没有影像/分析/报告的通用 `GET /{id}`、更新或额外删除接口；DTO 中存在某种 Create/Update/Page 类型不代表有对应路由。

来源：[Routing.kt](src/main/kotlin/Routing.kt)、[Security.kt](src/main/kotlin/Security.kt)、[JwtUtil.kt](../shared/src/main/kotlin/utils/JwtUtil.kt)、[网关路由](../api-gateway/src/main/kotlin/Routing.kt)、[网关配置](../api-gateway/src/main/kotlin/ServiceConfig.kt)、[转发实现](../api-gateway/src/main/kotlin/GatewayUtil.kt)。

## 2. JSON、错误和多态兼容性

写入通常使用 `Content-Type: application/json`、`Accept: application/json`；文件下载使用 `Accept: application/pdf`。响应没有统一的 `data/success/code` 包装：列表直接返回数组，登记通常返回 `id`，错误通常返回 `error`。

### 2.1 `kind` 与 `type` 不能混用

| 边界 | JSON 配置 / 指标判别键 |
| --- | --- |
| 工具 payload、AI 成功结果字符串内部 | `toolJson`：`kind`，`encodeDefaults=true`、忽略未知键 |
| `POST /analyses`、`POST /reports` 的自定义解码 | `metricPayloadJson`：`kind`、忽略未知键 |
| 数据库 metrics 文本 | repository 的 `storageJson`：`kind` |
| 普通 GET 的 Ktor JSON 响应 | `Serialization.kt` 仅调用 `json()`，没有改判别键；`MetricDto` 使用默认 `type` |
| AI 请求与 WS envelope | `socketJson`：忽略未知键，默认值不强制输出；可选 null 字段通常省略 |

嵌套 `MetricDto` 的标签是完整类名，如 `dto.MetricDto.GeneralMetric`，**不是** `GeneralMetric`、`CT` 或 `ULTRASOUND`。集合内部的每个指标也需要正确判别键。具体 DTO 解码不要求在分析/报告根对象手工添加密封父类标签。

以下两个片段表示同一个虚拟指标；左侧供 POST/工具使用，右侧表示普通 GET 的指标形式，均不是完整分析请求：

```json
{
  "kind": "dto.MetricDto.GeneralMetric",
  "name": "合成图像像素计数",
  "value": 64.0,
  "unit": "px²",
  "referenceRange": "仅演示，无临床参考范围",
  "confidence": null
}
```

```json
{
  "type": "dto.MetricDto.GeneralMetric",
  "name": "合成图像像素计数",
  "value": 64.0,
  "unit": "px²",
  "referenceRange": "仅演示，无临床参考范围",
  "confidence": null
}
```

**普通 GET 返回值不能原样回 POST**：需要按写入契约递归转换 metrics 的判别键，不能只替换最外层，也不能把所有名为 `type` 的业务字段全局替换。完整类名标签不是稳定的跨语言短枚举。

表中的“默认”表示 Kotlin DTO 或路由逻辑默认，不表示所有编码路径都会显式输出该字段。日期多为字符串而非统一时区时间戳；图像列表时间为 `yyyy-MM-dd HH:mm:ss`，工具/分析/报告/会话多为 `LocalDateTime.toString()`。

### 2.2 HTTP 状态不等于业务成功

- 普通路由几乎都以 `catch (Exception)` 返回 **500** 和 `{"error": ...}`；解码错误、缺必填字段、数据库错误不应被文档美化成统一 400/422。`error` 通常是异常消息，也可能为 null。
- AI 的无效/缺失 JWT 返回 **401**，源码声明错误体为 `{"error":"未授权访问"}`。请求 JSON 错误或消息与图片同时为空通常走 **500**。
- **已进入受控流水线后的分析/报告失败被转换为 HTTP 200**，或 WS `ai_response`：`confidence=0`，`result/analysisResult` 是人工复核说明文本，不是报告 JSON。不能只看 `response.ok`、`type=ai_response` 或非空 `result` 判断成功。
- 文件路由另有明确的 404/403，见第 5 节。会话不存在不是 404；影像删除目标不存在也不保证返回 false。
- 网关可能另外产生 JWT 拒绝、404 或转发失败 502；网关 502 使用其 `StandardResponse`，不等同于下游 `error` 格式。

**响应验收限制**：健康错误响应、AI HTTP 响应等由 Map 构造；特别是 AI 的字符串/整数/null 异构 Map，以及影像创建的 `Map<String, Any>`，下文只描述**源码声明**，未运行验证其 Ktor 实际序列化。不要将示例当作线上抓包或宣称所有响应编码已经验收。

来源：[Serialization.kt](src/main/kotlin/Serialization.kt)、[ToolPayloads.kt](src/main/kotlin/aiagent/tools/ToolPayloads.kt)、[Routing.kt](src/main/kotlin/Routing.kt)、[AnalysisRepository.kt](src/main/kotlin/database/repository/AnalysisRepository.kt)。

## 3. 影像 `/images`

### GET `/images`；GET `/images/patient/{name}`

- 全量：200，`List<MedicalImageDto.MedicalImageDetail>`，无分页、无医院条件、未定义排序。
- 姓名：精确匹配，但 repository 只取 `firstOrNull()` 再包装为列表，**不是该姓名的全部影像**。源码返回类型是 `List<MedicalImageDetail?>`；未命中可能是 `[null]`，不是约定的 `[]` 或 404。
- 查询没有统一的 `isDeleted` 过滤；全量映射没有赋实体 `isDeleted`，使用 DTO 默认 false，姓名查询则映射该字段。

`MedicalImageDetail` 的全部字段：

| 字段 | 类型 | 默认 / 含义 |
| --- | --- | --- |
| `id` | String | 影像 ID |
| `hospitalId` | String | 医院标识，不代表已授权 |
| `patientId` | String | 患者标识 |
| `patientName` | String? | 默认 null |
| `imageType` | String | 存储的类型字符串 |
| `filePath` | String | 服务端路径记录，不是下载 URL |
| `fileSize` | Long? | 默认 null，字节 |
| `status` | String | 存储状态 |
| `uploadTime` | String | `yyyy-MM-dd HH:mm:ss` |
| `isDeleted` | Boolean | 默认 false；见上述映射差异 |
| `analysisResults` | List<String>? | 默认 null；当前查询不填关联列表 |

### POST `/images`

接收具体 `MedicalImageDto.MedicalImageCreate`，这是**登记元数据**，不是 multipart 上传、图像分析或文件存在性验证接口。

| 请求字段 | 类型 | 必填 / 默认 |
| --- | --- | --- |
| `hospitalId` | String | 必填 |
| `patientId` | String | 必填 |
| `patientName` | String | 必填，但当前 INSERT 漏写此列 |
| `imageType` | String | 必填；普通 CRUD 不做 AI 模态标准化 |
| `filePath` | String | 必填 |
| `fileSize` | Long? | 可省略，默认 null，字节 |

```json
{
  "hospitalId": "demo-hospital",
  "patientId": "demo-patient-001",
  "patientName": "虚拟科研对象A",
  "imageType": "ULTRASOUND",
  "filePath": "/example-public-data/synthetic-ultrasound.png",
  "fileSize": null
}
```

**当前缺陷，不承诺正常创建**：表的 `patientName` 非空且无默认值，repository 却未写入；repository 返回的是 Exposed `InsertStatement<Number>`，不是生成的 ID 字符串。路由试图以 201 返回 `mapOf<String, Any>("id" to result)`，存在数据库/序列化失败风险。**不能将其写成已实测可用的 `201 {"id":"..."}` 契约**。仓库异常通常变为 500，错误消息为“创建医学影像失败”；没有保证回滚所有可能已完成的副作用。

### DELETE `/images/{id}`

200，源码声明 `{"success":true}`。repository 调用实体 `delete()` 后无条件返回 true，**不存在的 ID 也可能 true**；这是删除数据库记录，不是软删除，不删除原始图像/PDF，也没有级联清理保证。异常走 500。

来源：[MedicalImageDto.kt](../shared/src/main/kotlin/dto/MedicalImageDto.kt)、[MetricService.kt](src/main/kotlin/service/MetricService.kt)、[MedicalImageRepository.kt](src/main/kotlin/database/repository/MedicalImageRepository.kt)、[MedicalImages.kt](src/main/kotlin/database/table/MedicalImages.kt)。

## 4. 分析结果 `/analyses`

### GET `/analyses`；GET `/analyses/patient/{name}`

200，直接返回 `List<AnalysisResultDto.AnalysisResultComplete>`。过滤 `isDeleted=false`，按 `completedAt ?: createdAt` 降序；姓名接口精确匹配姓名，不过滤医院。无记录返回 `[]`。数据库 `success` 状态读出为 `completed`。

### POST `/analyses`

只做结果登记/覆盖，不运行分析。解码依次尝试以下完整对象：

1. `GeneratedReportPayload` → 提取 `.analysis`；
2. `MedicalImageAnalysisPayload` → 提取 `.analysis`；
3. 裸 `AnalysisResultDto.AnalysisResultComplete`。

前两项是**两种完整工具 payload**，不是只要包一层 `{"analysis": ...}` 即可。`AnalysisResultCreate` 不在此路由的接收契约内。成功源码声明 201、`{"id":"持久化后的分析ID"}`；即使按同 ID 更新也是 201。

`AnalysisResultComplete` 全部字段（POST 必填与 GET DTO 相同）：

| 字段 | 类型 | 必填 / 默认 / 行为 |
| --- | --- | --- |
| `id` | String | 必填；持久化会标准化，见下文 |
| `hospitalId` | String | 必填 |
| `imageId` | String | 必填；同样标准化 |
| `patientId` | String | 必填 |
| `patientName` | String | 必填 |
| `metrics` | MetricDto | 必填；POST 用 `kind`，普通 GET 用 `type` |
| `status` | String | 必填；不是受控枚举，写入会归一化 |
| `createdAt` | String | DTO 必填，但 repository 不采用客户端值，新增由 DB 默认生成，更新保留原值 |
| `completedAt` | String? | 默认 null；有效本地日期时间才保存 |
| `errorMessage` | String? | 默认 null；非空白时强制存 failed |

`MetricDto` 的全部变体；表中标签对 POST 为 `kind` 的值，对普通 GET 为 `type` 的值：

| 完整标签 | 全部数据字段 |
| --- | --- |
| `dto.MetricDto.CTMetric` | `density: Double`、`size: Double`、`location: String`、`severity: String`、`confidence: Double`，均必填 |
| `dto.MetricDto.MRIMetric` | `signalIntensity: String`、`size: Double`、`location: String`、`tissueCharacteristics: String`、`confidence: Double`，均必填 |
| `dto.MetricDto.XRayMetric` | `opacity: String`、`size: Double`、`location: String`、`boneStructure: String`、`confidence: Double`，均必填 |
| `dto.MetricDto.UltrasoundMetric` | `echogenicity: String`、`size: Double`、`location: String`、`bloodFlow: String`、`confidence: Double`，均必填 |
| `dto.MetricDto.GeneralMetric` | 必填 `name: String`、`value: Double`、`unit: String`、`referenceRange: String`；`confidence: Double? = null` |
| `dto.MetricDto.MetricCollection` | 必填 `metrics: List<MetricDto>`；元素递归使用同一判别键；集合本身没有 confidence 字段 |

旧模态指标中的 `size` 标作 mm、confidence 多为 0–1，但默认本地实现会从灰度/边缘/常量规则派生 HU-like、毫米、血流/组织和风险描述，**没有对应医学测量或校准的保证**。`intendedUse=unspecified` 的旧远程 mask 分支也仍复用部分本地规则，不是启用远程分割就获得这些指标的医学效度。不要用字段名称证明临床含义。

以下是可表达完整写入结构的**虚拟 DTO 示例**，不是 research 流水线完整输出，更不是已验证医学结果：

```json
{
  "id": "demo-analysis-001",
  "hospitalId": "demo-hospital",
  "imageId": "demo-image-001",
  "patientId": "demo-patient-001",
  "patientName": "虚拟科研对象A",
  "metrics": {
    "kind": "dto.MetricDto.MetricCollection",
    "metrics": [
      {
        "kind": "dto.MetricDto.GeneralMetric",
        "name": "合成图像像素计数",
        "value": 64.0,
        "unit": "px²",
        "referenceRange": "仅演示，无临床参考范围",
        "confidence": null
      }
    ]
  },
  "status": "completed",
  "createdAt": "2026-01-01T10:00:00",
  "completedAt": "2026-01-01T10:00:00",
  "errorMessage": null
}
```

登记行为：

- ID 去首尾空白；若含 UUID 子串则取该 UUID（如 `ANL-<UUID>` / `IMG-<UUID>` 去掉前缀）；否则长度不超过 36 保留，超过 36 改为随机 UUID。以响应 `id` 为准，不假定与工具 ID 完全相同。
- 同 ID 更新现有记录并清除删除标记；不是追加一条已签署分析，也不能由此推断完整流水线幂等。
- `errorMessage` 非空白 → failed；否则 `pending/running/failed` 保留，其余状态均存 success，读取映射 completed（包括客户端提交的 limited）。
- `completedAt` 用 `LocalDateTime.parse`；空值/解析失败保存 null，而不是统一报错。不要假定带 `Z`/时区偏移的时间会被正确保存。
- 此 CRUD 不重跑流水线的身份、像素、用途或质量门禁校验；接受 Complete 不是已经证明结果可信。

来源：[AnalysisResultDto.kt](../shared/src/main/kotlin/dto/AnalysisResultDto.kt)、[MetricDto.kt](../shared/src/main/kotlin/dto/MetricDto.kt)、[解码与路由](src/main/kotlin/Routing.kt)、[AnalysisRepository.kt](src/main/kotlin/database/repository/AnalysisRepository.kt)。

## 5. 报告 `/reports` 与 PDF

### GET `/reports`；GET `/reports/patient/{name}`

200，`List<ReportDto.ReportComplete>`，排除已删除记录，按 `generatedAt ?: createdAt` 降序；姓名精确匹配，无医院过滤，无记录为 `[]`。

### POST `/reports`

登记报告，不执行 `ReportGenerateTool`。按顺序尝试：

| 输入 | 行为 |
| --- | --- |
| 完整 `GeneratedReportPayload` | 提取 `.report` 后登记 |
| 裸 `ReportDto.ReportComplete` | 登记已有报告元数据 |
| `ReportDto.ReportCreate` | 路由生成 UUID、本地 createdAt，设 `status=generating`，文件信息/generatedAt/errorMessage 为 null 后登记；**不生成 PDF，也没有此接口自动排队生成的保证** |

成功源码声明 201、`{"id":"持久化后的报告ID"}`，同 ID 更新也是 201。不能把简写 `{"report": ...}` 当作完整工具 payload。由于逐级试解码并忽略未知键，缺少 Complete 字段但仍满足 Create 的对象可能退为 generating 记录，不能仅凭 201 判断已登记完整文件信息。

`ReportCreate` 的全部字段均必填：

| 字段 | 类型 |
| --- | --- |
| `hospitalId` | String |
| `patientId` | String |
| `patientName` | String |
| `analysisIds` | List<String> |
| `reportType` | String |

```json
{
  "hospitalId": "demo-hospital",
  "patientId": "demo-patient-001",
  "patientName": "虚拟科研对象A",
  "analysisIds": ["demo-analysis-001"],
  "reportType": "ULTRASOUND_RESEARCH_REPORT"
}
```

这个示例即使返回 201，也只表示 generating 记录，不表示科研 PDF 已生成。

`ReportComplete` 的全部字段：

| 字段 | 类型 | 必填 / 默认 / 行为 |
| --- | --- | --- |
| `id` | String | 必填；同分析 ID 规则标准化，`REP-<UUID>` 通常变为 UUID |
| `hospitalId` | String | 必填 |
| `patientId` | String | 必填 |
| `patientName` | String | 必填 |
| `analysisIds` | List<String> | 必填；每个元素也标准化 |
| `reportType` | String | 必填；受控像素报告旧分支为 `<模态>_AI_REPORT`，科研为 `ULTRASOUND_RESEARCH_REPORT`；CRUD 本身不校验临床用途 |
| `filePath` | String? | 默认 null，已有服务端文件路径，不是 URL |
| `fileSize` | Long? | 默认 null，字节 |
| `status` | String | 必填；工具输出 generated，持久化后通常读为 completed |
| `createdAt` | String | DTO 必填；repository 不采用客户端值，新增由 DB 默认生成，更新保留原值 |
| `generatedAt` | String? | 默认 null；解析规则同 completedAt，无效变 null |
| `errorMessage` | String? | 默认 null；非空白强制 failed |

状态归一化：非空白错误 → failed；否则 `failed/error` → failed，`completed/generated/success` → completed，其余 → generating。同 ID 可覆盖已有元数据、清除删除标记；不证明关联分析存在、医生已确认、文件真实存在或已通过受控流水线。两个登记 API 也没有共同事务。

### GET `/reports/{id}/file`

`id` 是数据库报告 ID，通常用登记响应中的 ID，而不是未经标准化的 `REP-...`。query `disposition` 经 lowercase 后仅 `attachment` 触发下载，其余（含缺省、未知值）均为 inline。

| 状态 | 源码声明的响应 |
| --- | --- |
| 200 | 文件字节，`Content-Type: application/pdf`；`Content-Disposition: inline` 或 `attachment`，filename 取实际文件名 |
| 404 | `{"error":"报表不存在"}`：记录未找到/已删除 |
| 404 | `{"error":"报表文件不存在"}`：路径空或文件不存在 |
| 403 | `{"error":"无权访问该报表文件"}`：路径不在项目 `reports` 目录下 |
| 500 | 路径解析、读取等异常，`{"error": ...}` |

检查是 `toAbsolutePath().normalize()` 加 `startsWith(projectReportsDirectory)` 的**词法目录检查**，不是 `realpath/toRealPath`，不能宣称防住符号链接逃逸。相对 filePath 按进程工作目录解析；项目 reports 目录由项目根标记向上定位。该接口没有医院/患者授权，也没有强制校验报告状态、PDF 内容或签署资格。文件路径不等于可以跨主机直接下载的 URL。

来源：[ReportDto.kt](../shared/src/main/kotlin/dto/ReportDto.kt)、[ReportRepository.kt](src/main/kotlin/database/repository/ReportRepository.kt)、[Routing.kt](src/main/kotlin/Routing.kt)、[LocalFiles.kt](src/main/kotlin/LocalFiles.kt)、[PDF 写入实现](src/main/kotlin/aiagent/tools/PdfReportRenderer.kt)。

## 6. 会话 `/conversations/{conversationId}`

此处 `/conversations/{conversationId}` 即常写的 `/conversations/{id}`，源码参数名为 `conversationId`。存储是项目 `chat-histories` 下的 JSON 文件，不是数据库审计记录或可信 Agent checkpoint。

| 方法 | 参数 / 请求 | 成功行为 |
| --- | --- | --- |
| GET | query `hospitalId` 缺省/空白为 `unknown-hospital`；`patientName` 缺省为空字符串 | 200 返回历史；不存在则返回该会话 ID、默认/请求医院、请求姓名、空 messages 和当前 updatedAt，不返回 404。已有文件时 patientName query 不覆盖历史 |
| PUT | 完整 `ConversationHistoryDocument` JSON | 200 返回保存后的文档；路径 ID 覆盖 body ID，服务端当前时间覆盖 updatedAt，整个文件覆盖写入，不是消息追加/合并 |
| DELETE | query `hospitalId` 缺省/空白为 `unknown-hospital` | 200 `{"deleted":true}` 或 `{"deleted":false}`，取决于文件是否删除；不删除分析、报告或 PDF |

`ConversationHistoryDocument` 全部字段：

| 字段 | 类型 | PUT 必填 / 默认 |
| --- | --- | --- |
| `conversationId` | String | DTO 必填，即使随后被路径覆盖 |
| `patientName` | String | 必填；保存时 trim |
| `hospitalId` | String | 必填；保存时 trim，空串变 unknown-hospital |
| `confirmedResultMessageId` | String? | 默认 null；不验证是否确有对应消息或确认资格 |
| `messages` | List<JsonElement> | 默认 `[]`；元素可以是任意合法 JSON，服务端不验证其医学事实/消息结构 |
| `updatedAt` | String | DTO 必填，即使随后被服务端当前时间覆盖 |

```json
{
  "conversationId": "demo-conversation-001",
  "patientName": "虚拟科研对象A",
  "hospitalId": "demo-hospital",
  "confirmedResultMessageId": null,
  "messages": [],
  "updatedAt": "2026-01-01T10:00:00"
}
```

文件名由医院和会话 ID 清洗后拼为 `<医院>_<会话>.json`：仅保留 ASCII 字母、数字、下划线、短横线，其余替换为下划线并处理空值。清洗会产生碰撞，不构成身份授权；覆盖文件也没有原子并发/版本冲突或运行恢复保证。异常通常 500。

来源：[ConversationHistoryStore.kt](src/main/kotlin/ConversationHistoryStore.kt)、[LocalFiles.kt](src/main/kotlin/LocalFiles.kt)、[会话路由](src/main/kotlin/Routing.kt)。

## 7. 健康检查 GET `/health`

执行数据库 `SELECT 1`，不只是进程存活检查，也不检查分割模型、报告生成或授权能力。

200 的源码声明：

```json
{
  "status": "ok",
  "service": "metric-service",
  "database": "connected"
}
```

数据库异常时 500；源码声明含 `status="error"`、`service="metric-service"`、`database="disconnected"`、`error: String?`。经网关访问 `/metric/health` 还需要网关 JWT。来源：[Routing.kt](src/main/kotlin/Routing.kt)。

## 8. AI HTTP 与 WS 共用请求

HTTP `POST /ai/analyze-and-report` 与 WS 文本帧均解码 `MetricAiSocketRequest`，**不是自由格式提示词或 JSON 字符串字面量**。HTTP body 是 JSON 对象；WS 每个文本帧也是一个完整 JSON 对象。

| 全部请求字段 | 类型 | 缺省与标准化 |
| --- | --- | --- |
| `type` | String | 默认 `chat`；trim 后空串也变 chat。目前不按它选择工具或控制运行 |
| `requestId` | String? | 默认 null；null 时生成 `req-<当前毫秒>`，显式空串不因此补值；不是幂等键 |
| `message` | String? | 默认 null；trim。只作为补充说明/范围判断，不授权改写分析事实 |
| `imageData` | String? | 默认 null；trim；非空则优先走影像分支 |
| `imageType` | String? | 默认 null；trim、转大写、去 `-`/`_`，识别 XRAY、CT、MRI、ULTRASOUND、PATHOLOGY，其余为 OTHER |
| `patientId` | String? | 默认 null；空白/缺省补 unknown-patient |
| `patientName` | String? | 默认 null；空白/缺省补 未知患者 |
| `hospitalId` | String? | 默认 null；空白/缺省补 unknown-hospital |

消息与图片 trim 后不能同时为空，否则 HTTP 通常 500 / WS error。虽然身份有默认值，调用方仍应明确提供虚拟/已授权的身份；默认值不是已验证患者归属。

`imageData` 原样作为工具 `imagePath`：支持本地文件路径、`file://`、可解码的图像 DATA_URL。**不是裸 base64，也不是 multipart**；`inline-image://` 只有元数据，不能通过像素门禁；本地入口不将任意 HTTP(S) URL 当远程下载处理。可读栅格依赖 Java ImageIO，不能承诺原生 DICOM、多帧或体数据解码。远程分割启用后，metric 仍先要求本地能读到像素，路径还需满足同机/挂载约定。

完整字段示例；路径仅为虚拟占位，必须自行提供有权读取的合成/公开科研栅格文件才能执行：

```json
{
  "type": "chat",
  "requestId": "demo-request-001",
  "message": "请对公开科研示例执行分割，不作诊断。",
  "imageData": "/example-public-data/synthetic-ultrasound.png",
  "imageType": "ULTRASOUND",
  "patientId": "demo-patient-001",
  "patientName": "虚拟科研对象A",
  "hospitalId": "demo-hospital"
}
```

请求没有 `intendedUse`、模型选择、物理间距、取消或恢复字段；科研用途来自受控模型响应，不能靠前端声明覆盖。未知 JSON 键会被 AI 解码器忽略。

### 8.1 范围和实际执行路径

1. 有图片 → `IMAGE_ANALYSIS`，直接执行 `executeControlledMetricReportPipeline` → 分析工具 → 契约校验 → 报告工具 → 报告校验。
2. 无图片但命中影像/指标关键词或英文模式 → `METRIC_DISCUSSION`，拼接固定范围说明，并不是 LLM 对话或对既有报告的真实证据绑定解释。
3. 其余 → `UNSUPPORTED`，固定提示仅支持受控影像分析和既有结果/报告解读，不提供通用问答。

文本分支固定边界包括：“不会执行分割、不会生成诊断性影像结论、不会改写正式结构化分析结果”，并提示上传影像走受控流水线。关键词守卫只是范围提示，不是授权或诊断安全证明。

**路由没有运行 `metricReportStrategy` Koog 图，也没有调用 LLM 生成这些回复。** `Application.kt` 的 DeepSeek/Koog 配置仅为后续非诊断文本能力预留。默认 `METRIC_SEGMENTATION_SERVICE_ENABLED=false`，使用本地启发式像素分析，不应称默认已使用训练 U-Net。显式启用远程分割后才调用 Python `POST /api/v1/segment`（默认地址 127.0.0.1:8099、超时 30 秒）。

来源：[Routing.kt](src/main/kotlin/Routing.kt)、[MetricAiSupport.kt](src/main/kotlin/MetricAiSupport.kt)、[Application.kt](src/main/kotlin/Application.kt)、[StrategyGraph.kt](src/main/kotlin/aiagent/strategy/StrategyGraph.kt)、[MetricAiConversationGuard.kt](src/main/kotlin/aiagent/validation/MetricAiConversationGuard.kt)、[MedicalImageAnalyzerTool.kt](src/main/kotlin/aiagent/tools/MedicalImageAnalyzerTool.kt)。

### 8.2 HTTP 响应：结果是字符串

已通过 JWT 且请求处理正常返回时，源码声明 HTTP 200、以下三个 Map 字段（异构 Map 的实际序列化尚未实测）：

| 字段 | 类型 | 语义 |
| --- | --- | --- |
| `result` | String | `response.analysisResult ?: response.message`；影像成功为 **GeneratedReportPayload 的 JSON 字符串**，失败/文本请求为普通说明文本 |
| `message` | String | 科研成功“科研影像分割与非诊断报告已完成”；旧分支成功“结构化影像分析与正式报告已完成”；失败或文本分支为对应说明 |
| `confidence` | Int? | research 成功 null；旧非 research 成功固定 95；受控流水线失败 0；纯文本通常 null |

**成功 `result` 不是嵌套对象**。先解析 HTTP body，再对成功的 `result` 做 JSON 解析及 payload 校验；不能无条件把文本回复/失败文本当 JSON，也不能仅检查 confidence。旧值 95 是硬编码返回值，不是医学评测、模型校准概率或医生置信度。

下面是**仅为说明双层编码的字段投影片段**：内层除 intendedUse 外的字段全部省略，不能当作完整成功响应或正式 payload 回 POST。

```json
{
  "result": "{\"intendedUse\":\"research\"}",
  "message": "科研影像分割与非诊断报告已完成",
  "confidence": null
}
```

流水线失败时不生成替代报告，`message` 固定为：“结构化影像分析未完成，未启用 LLM 代替医学分析。请复核输入影像或联系人工复核。” `result` 则包含分析状态、患者/模态、可选补充说明、未成功声明和人工复核建议，可能附异常详细原因。**此分支仍为 200、confidence=0，result 不是 GeneratedReportPayload。** JSON 解码或外围异常才通常为 500、`{"error": ...}`。

### 8.3 WebSocket `/ws/ai-agent`

连接可用 query `token` 或 Authorization header（浏览器客户端使用 query）；非空 query token 优先，即使 header 有有效 token 也不会替换无效 query token。无效/缺失 token 在连接处理阶段以 **1008（VIOLATED_POLICY）、reason=`invalid token`** 关闭，不是正常 error envelope。

服务端文本帧为 `MetricAiSocketEnvelope`：

| 全部字段 | 类型 | 默认 / 含义 |
| --- | --- | --- |
| `type` | String | 必填：connected / processing / ai_response / error |
| `requestId` | String? | 默认 null；用于关联，不保证去重 |
| `message` | String? | 默认 null |
| `analysisResult` | String? | 默认 null；成功为内层 GeneratedReportPayload JSON **字符串**，流水线失败为复核文本 |
| `confidence` | Int? | 默认 null；语义同 HTTP，research null 通常不输出该键 |
| `createdAt` | String | 必填；服务端本地时间字符串，不是 token 流时间戳 |
| `user` | SocketUserContext? | 默认 null；由 JWT 提取 |

`SocketUserContext` 全部字段：`userId: String` 必填，`username: String? = null`、`displayName: String? = null`、`role: String? = null`；路由 displayName 取 username 或 userId，无医院字段或资源授权含义。

| 事件/行为 | 具体语义 |
| --- | --- |
| `connected` | 验 JWT 后发送一次，message 为 `ai-agent websocket connected` |
| `processing` | 每个可解码请求先发送，message 为 `ai-agent is processing your request`；requestId 使用原始请求值 |
| `ai_response` | 一次完整文本/分析报告结果；requestId 通常为标准化后的值。流水线失败也走此事件且 confidence=0 |
| `error` | JSON 无法解码时 message 为 `invalid websocket payload` 并继续接收；外围执行异常也会发 error，可能包含原始 requestId |
| 非文本帧 | 业务循环忽略，不当作上传图片 |

connected 的虚拟 wire 形式示例（未设置的可选字段省略）：

```json
{
  "type": "connected",
  "message": "ai-agent websocket connected",
  "createdAt": "2026-01-01T10:00:00",
  "user": {
    "userId": "demo-user",
    "displayName": "demo-user"
  }
}
```

同一连接逐帧处理业务请求，不是逐 token 文本流；没有 cancel/resume、断线重放、checkpoint 或可靠恢复协议。`type="cancel"` 不会取消运行，仍按图片/消息判断。插件 ping/timeout 均为 20 秒不等于受控任务取消保证。

来源：[WebSocketDto.kt](../shared/src/main/kotlin/dto/WebSocketDto.kt)、[WS/HTTP 路由](src/main/kotlin/Routing.kt)、[WebSockets.kt](src/main/kotlin/WebSockets.kt)、[浏览器连接](../frontend/src/lib/ws.js)。

## 9. AI 成功结果内部 payload

字段来自 [ToolPayloads.kt](src/main/kotlin/aiagent/tools/ToolPayloads.kt)。`MedicalImageAnalysisPayload` 是分析工具输出；`GeneratedReportPayload` 是报告工具输出，也是 HTTP `result` / WS `analysisResult` 成功字符串解析后的对象。它们不是单纯 shared DTO，不能把解释文本伪装成这两种类型。

下表列出两种 payload 的**全部顶层字段**；“必填”指反序列化契约，而不是所有调用路径都完整复验医学意义。

| 字段 | 类型 | MedicalImageAnalysisPayload | GeneratedReportPayload |
| --- | --- | --- | --- |
| `analysis` | AnalysisResultComplete | 必填 | 必填 |
| `report` | ReportComplete | 无 | 必填 |
| `analysisId` | String | 无 | 必填，绑定当前分析 |
| `imageType` | String | 必填 | 必填 |
| `imagePath` | String | 必填 | 无 |
| `analysisMode` | String（当前 PIXEL / METADATA_ONLY） | 必填；后者不能通过受控报告门禁 | 必填；受控入口成功要求 PIXEL |
| `source` | ImageSourceSnapshot | 必填 | 无独立字段（报告正文可含来源信息） |
| `summary` | String | 必填 | 必填 |
| `keyIndicators` | List<IndicatorItem> | 必填 | 必填 |
| `findings` | List<String> | 必填 | 必填 |
| `conclusion` | String | 无 | 必填 |
| `recommendations` | List<String> | 必填 | 必填 |
| `highlightRegions` | List<HighlightRegion> | 默认 `[]` | 默认 `[]` |
| `highlightLegend` | List<HighlightLegendItem> | 默认 `[]` | 默认 `[]` |
| `limitations` | List<String> | 默认 `[]` | 默认 `[]` |
| `reportContent` | String | 无 | 必填，确定性渲染的报告正文；PDF 字节不内联 |
| `intendedUse` | String | 默认 unspecified | 默认 unspecified；科研必须保留 research |

嵌套类型全部字段：

| 类型 | 字段（未写默认的均必填） |
| --- | --- |
| `IndicatorItem` | `name: String`、`value: String`、`unit: String? = null`、`interpretation: String? = null`；value 即使表示数值也为字符串 |
| `HighlightBoundingBox` | `leftPercent: Double`、`topPercent: Double`、`widthPercent: Double`、`heightPercent: Double`，百分比坐标 |
| `HighlightContourPoint` | `xPercent: Double`、`yPercent: Double` |
| `HighlightLegendItem` | `colorName: String`、`colorHex: String`、`meaning: String` |
| `ImageSourceSnapshot` | `reference: String`、`resolvedPath: String? = null`、`sourceType: String`（当前 LOCAL_FILE / DATA_URL / INLINE_REFERENCE）、`format: String? = null`、`mimeType: String? = null`、`fileSizeBytes: Long? = null`、`width: Int? = null`、`height: Int? = null`、`readable: Boolean`、`rasterDataAvailable: Boolean` |
| `ToolErrorResponse` | `errorCode: String`、`message: String`、`detail: String? = null`；内部工具错误，不是 HTTP 成功报告或统一 HTTP 错误 DTO |

`HighlightRegion` 全部字段：

| 字段 | 类型 / 默认 | 语义 |
| --- | --- | --- |
| `id`, `label` | String，必填 | 区域标识与展示标签 |
| `colorName`, `colorHex` | String，必填 | 展示颜色；research 不表示风险 |
| `priority` | Int，必填 | 展示顺序；research 按像素面积排序，不是临床优先级 |
| `annotationTitle`, `annotationMeaning` | String，必填 | 标注说明 |
| `location`, `severity` | String，必填 | research location 为图像坐标描述，severity 置空，不推断解剖/风险 |
| `confidence` | Double，必填 | research 为 0–1 前景平均概率，未经临床校准；不同于外层 Int? confidence |
| `coveragePercent` | Double，必填 | 覆盖率百分数 |
| `estimatedSizeMm` | Double?，默认 null | research 必须 null；旧非科研分割要求有效正物理尺寸 |
| `shape` | String，必填 | 如 BLOB、ELLIPSE、PILL；research 使用 BLOB |
| `rotationDegrees` | Double，必填 | 展示旋转角 |
| `boundingBox` | HighlightBoundingBox，必填 | 百分比边界框 |
| `contour` | List<HighlightContourPoint>，默认 `[]` | 百分比轮廓；不承诺完整拓扑 |
| `note` | String，必填 | 区域说明 |
| `pixelCount` | Int?，默认 null | 像素面积，research 必须有 |
| `widthPx`, `heightPx` | Int?，默认 null | 原图外接框像素尺寸，research 必须有 |

内部工具错误码包括 `ANALYSIS_FAILED`、`REPORT_GENERATION_FAILED`。流水线会识别这些错误并阻止继续生成/输出成功报告；HTTP/WS 外层再按第 8 节转换为复核文本。报告工具可反序列化 payload **不等于自行重复了整条流水线所有校验**；旧裸分析 DTO 不能直接作为正式报告工具输入。

## 10. research 与接收门禁

远程分割的共同接收条件：医院、患者 ID/姓名和模态匹配；`status=completed`、`quality_gate.status=PASS`；权重已加载、非 mock 后端、区域非空。**LIMITED、未加载权重、mock、空区域不得进入报告阶段**；不能因 Python HTTP 200 就视为可报告。流水线还要求 `analysisMode=PIXEL`、像素可读、summary/findings 非空、分析无 errorMessage，并校验报告状态 generated、正文非空、身份和分析绑定。

research 使用独立的 [ResearchSegmentationPayload.kt](src/main/kotlin/aiagent/tools/ResearchSegmentationPayload.kt) 映射，不进入旧超声回声、血流、毫米或风险推导：

- 要求模型支持模态恰为 `["ULTRASOUND"]`、本次模态 ULTRASOUND、单输入通道/单输出类别、明确 research；响应 requestId 匹配。
- `bundle_manifest_sha256` 必须符合 64 位小写十六进制格式；区域 ID 不重复，像素面积/外接框大小/总面积有效，覆盖率与原图像素量、百分比框与像素宽高一致，坐标与概率有限且在范围内。
- `estimated_size_mm` 必须 null；非科研旧分割则必须提供有效正物理尺寸，不能把省略/null 变成 0 mm。带模型包哈希却丢失 research 用途时拒绝，不能静默落入临床指标映射。
- Kotlin 的用途、哈希格式和几何一致性检查，加上 Python 模型包/权重哈希完整性检查，只是契约/完整性检查，**不是临床签名、可信来源认证、临床校准或模型有效性证明**。

科研分析 `metrics` 为 `MetricCollection`，恰含六项 `GeneralMetric`，每项 confidence 均为 null、referenceRange 为“科研测量，无临床参考范围”：

| 指标名 | 单位 | 含义 |
| --- | --- | --- |
| 分割区域数 | count | 区域数量 |
| 前景像素面积 | px² | 各区域 pixelCount 之和 |
| 前景覆盖率 | % | 总前景像素 / 原图像素 × 100 |
| 主区域宽度 | px | 最大像素面积区域的外接框宽度 |
| 主区域高度 | px | 最大像素面积区域的外接框高度 |
| 前景平均概率（未校准） | % | 各区域前景概率按像素数加权后 × 100；不是医学置信度 |

报告 `intendedUse=research`、`reportType=ULTRASOUND_RESEARCH_REPORT`，标题为“科研影像分割报告（非诊断）”，结论明确不得用于诊断；区域颜色只标识区域。不得把未标定像素量解释为毫米、血流、良恶性或临床风险。外层 confidence null、GeneralMetric confidence null 与区域概率非空是三个不同层次。

**审计保留限制**：payload 没有完整 `model`、`artifacts` 对象。科研仅将模型用途、包哈希、模型名/版本及质量门禁等选定信息映射为 keyIndicators；旧分支也主要保留模型摘要、耗时和工件存在类别，不完整保留工件路径/全部模型审计字段。不要编造下载链接或声称保留了 Python 完整响应。详见 [分割客户端](src/main/kotlin/aiagent/tools/SegmentationServiceClient.kt)、[分析工具](src/main/kotlin/aiagent/tools/MedicalImageAnalyzerTool.kt)、[报告工具](src/main/kotlin/aiagent/tools/ReportGenerateTool.kt) 与 [分割服务规格](../segmentation-service/SPEC.md)。

## 11. 实际生成、确认、登记与客户端限制

当前实际时序，不是一次“生成并原子登记”：

1. AI HTTP/WS 直接执行受控函数，分析后由报告工具**先写 PDF** 到项目 `reports`，返回 GeneratedReportPayload 字符串；此时尚未登记业务分析/报告。
2. 浏览器“确认分析”调用 `POST /analyses`，采用返回的持久化分析 ID；确认状态主要由前端/会话维护，不等于已核实资质的医生签署。
3. 浏览器“生成报表”实际将**已生成报告**的元数据、文件路径和保存后的分析 ID 发送到 `POST /reports`；此步骤不是重新生成 PDF。
4. 登记成功后可用报告数据库 ID 请求 `/reports/{id}/file`。

`metricReportStrategy` 的“登记”节点只打印日志，实际路由也不运行它。PDF、Python 工件、会话文件、分析登记和报告登记之间没有统一事务、全链路幂等或可靠恢复；中途失败可能留下孤立 PDF，重试可能产生新分析 ID/多个 PDF。不能盲目重放并把它称作恢复原运行。

现有前端还存在以下兼容限制，不应写成已经修复：

- [App.jsx](../frontend/src/App.jsx) 的结构化结果转换未保留顶层 `intendedUse`，高亮转换未保留 `pixelCount/widthPx/heightPx`；保留的指标/报告类型或 PDF 文本不等于完整科研审计链。
- shared 的 `AnalysisResultComplete` / `ReportComplete` 没有完整用途、区域、模型或工件审计字段。只 POST 这些 DTO，数据库不会自动保存完整工具 payload；即使 POST 完整 envelope，路由也只是提取 analysis/report。
- [api.js](../frontend/src/lib/api.js) 默认经 `/metric`，遇 403 有直连 metric 回退，502/503 会重试一次，401 可刷新 token 后重试；这些行为不是可靠幂等策略。其成功响应 JSON 解析失败还可能返回 `[]`，不能以 UI 未报错证明服务端契约验收。
- [ws.js](../frontend/src/lib/ws.js) 使用直连 WS 和 query token；[runtimeConfig.js](../frontend/src/lib/runtimeConfig.js) 的 HTTP/WS 地址可分别配置。日志、会话和工件可能携带身份/影像信息，不能公开真实病例、token 或服务端路径。

## 12. 核对范围与未验收项

已按源码列出 16 个 HTTP 方法/路径组合与 1 个 WS 路径，字段以实际 DTO、解码器、service/repository、序列化配置和客户端为依据；没有添加未实现的分页、上传、签署、授权、流式 token、取消或恢复接口。

仍需另行在隔离测试环境验收：异构 Map/InsertStatement 的实际 HTTP 编码、影像创建缺陷的数据库表现、完整浏览器确认/两次登记/文件下载、跨主机路径与部署前缀、医院隔离与资源授权、并发/重试/失败恢复。本次没有运行这些验收，不能用本文替代测试证据。
