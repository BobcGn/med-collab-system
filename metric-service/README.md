# metric-service：开发与运行

本模块是 Kotlin/Ktor 影像业务服务，负责影像元数据、分析/报告记录、受控分析与 PDF 输出，以及文件式会话历史。Python U-Net 的训练和推理属于独立的 `segmentation-service`，不在业务 JVM 内运行。

入口：[仓库 README](../README.md) · [文档索引](../docs/README.md) · [本服务 API 参考](API.md) · [分割服务](../segmentation-service/README.md)。本文提供开发和运行入口，不重复 API 的完整字段表。

职责以 [模块规格](SPEC.md)、[Agent Harness 规格](src/main/kotlin/aiagent/SPEC.md)、[架构](../architecture.md) 和 [医疗 AI 边界政策](../docs/hospital-ai-boundary-policy.md) 为准。

> **仅用于原型联调与课程科研，不是医院生产可用系统。** 默认分析是本地启发式，并非训练好的 U-Net；普通直连业务接口缺少路由认证，AI 入口也没有医院/患者资源授权。确定性、权重加载、质量门禁通过或 PDF 生成成功，都不证明医学有效性。

## 1. 实际执行路径

- 有图请求：HTTP/WS → `executeControlledMetricReportPipeline` → 分析工具 → 结构化契约校验 → 报告工具 → 报告绑定校验 → 返回结果。失败只返回失败说明和人工复核建议，不让 LLM 补分析或报告。
- 无图请求：按影像/指标关键词划分讨论或不支持范围，**仍是拼接固定文本**，不是已绑定既有报告证据的多轮解释 Agent。
- 路由没有运行 `metricReportStrategy` Koog 图，也没有调用 LLM。图虽然已定义，其“登记正式报告”节点只在事务内打印日志，不写业务记录；实际受控函数不调用该节点。

主要入口：[Application.kt](src/main/kotlin/Application.kt)、[Routing.kt](src/main/kotlin/Routing.kt)、[StrategyGraph.kt](src/main/kotlin/aiagent/strategy/StrategyGraph.kt)、[关键词范围守卫](src/main/kotlin/aiagent/validation/MetricAiConversationGuard.kt)。关键词判断不是资源授权或完整医疗安全策略。

## 2. 本地开发与启动

### 前提与命令

使用 **Java 21**，使 `JAVA_HOME`/`PATH` 指向相应 JDK；模块的 JVM toolchain/target 均为 21。版本清单为 Kotlin 2.2.21、Ktor 3.3.2、Koog 0.5.1，根 Gradle Wrapper 为 9.2.1，编译依赖 `:shared`。来源：[模块构建](build.gradle.kts)、[版本清单](../gradle/libs.versions.toml)、[Wrapper 配置](../gradle/wrapper/gradle-wrapper.properties)。

先准备可连接的 MySQL 协议数据库（MySQL/TiDB）及下表环境变量。单独运行本服务不要求 Node.js、Python 或云 LLM key；启用远程分割时才需另外准备 Python 服务和研究模型包。

以下命令均在**仓库根目录**执行，供后续人工操作；本次文档同步未执行构建、启动或测试。开发运行：

```bash
./gradlew :metric-service:run
```

或生成分发包后运行（二选一，不要同时占用同一端口）：

```bash
./gradlew :metric-service:installDist
metric-service/build/install/metric-service/bin/metric-service
```

默认 HTTP 地址为 `http://localhost:8083`，由 `PORT` 覆盖。`GET /health` 会执行数据库 `SELECT 1`；不是模型就绪、分析能力或授权验收。完整前后端启动另见 [根 README](../README.md) 和 [quick-start.sh](../scripts/quick-start.sh)；脚本构建跳过测试，启动成功不等于测试通过。

### 配置与启动副作用

配置入口：[application.conf](src/main/resources/application.conf)、[MetricAiSupport.kt](src/main/kotlin/MetricAiSupport.kt)。敏感值应通过本地/部署环境安全注入，不依赖仓库开发凭据；本文不复述任何默认账号密码或密钥值。

| 环境变量 | 默认值 / 使用要求 |
| --- | --- |
| `PORT` | `8083` |
| `METRIC_DATABASE_URL` | `jdbc:mysql://localhost:4000/analysis?useInformationSchema=false` |
| `METRIC_DATABASE_USER` | 自行配置数据库账号，不在文档中复制开发账号 |
| `METRIC_DATABASE_PASSWORD` | 自行安全注入数据库密码 |
| `METRIC_DATABASE_DRIVER` | `com.mysql.cj.jdbc.Driver`；构建包含 MySQL Connector/J |
| `JWT_SECRET` | 与 auth、gateway 等使用同一 JWT 的服务共享配置；issuer/audience 也必须一致，不展示密钥值 |

**启动会写数据库结构，不是只读连接，也不是受版本管理的迁移流程。** 对 MySQL JDBC URL，启动先执行 `CREATE DATABASE IF NOT EXISTS`，随后 Exposed 为 `medical_images`、`analysis_results`、`reports` 执行 `createMissingTablesAndColumns`。账号需具备相应建库/建表/补列及业务读写权限；不要用医院数据库尝试启动。JDBC 辅助函数还会应用 `useInformationSchema=false` 的 TiDB 兼容参数。

来源：[Databases.kt](src/main/kotlin/Databases.kt)、[MySqlDatabaseBootstrap.kt](../shared/src/main/kotlin/utils/MySqlDatabaseBootstrap.kt)。

## 3. 分析模式与科研语义

### 默认：本地启发式

`METRIC_SEGMENTATION_SERVICE_ENABLED` 默认 **false**，走本地灰度、边缘和常量规则，不调用 Python U-Net。这条路径可以生成标作“正式分析报告”的 PDF，但 HU-like、毫米范围、血流/组织描述及风险等级等**不是对应医学量的有效测量**，不能供临床使用。未声明 research 的旧远程 mask 分支也复用部分旧规则，启用远程服务本身不会赋予这些指标医学效度。

### 显式启用远程分割

| 环境变量 | 默认值 |
| --- | --- |
| `METRIC_SEGMENTATION_SERVICE_ENABLED` | `false` |
| `METRIC_SEGMENTATION_SERVICE_URL` | `http://127.0.0.1:8099` |
| `METRIC_SEGMENTATION_SERVICE_TIMEOUT_SECONDS` | `30` |

先按 [分割服务 README](../segmentation-service/README.md) 准备已加载真实研究权重的服务，检查模型信息，再显式启用，例如：

```bash
METRIC_SEGMENTATION_SERVICE_ENABLED=true \
METRIC_SEGMENTATION_SERVICE_URL=http://127.0.0.1:8099 \
METRIC_SEGMENTATION_SERVICE_TIMEOUT_SECONDS=30 \
./gradlew :metric-service:run
```

客户端访问 `POST /api/v1/segment`，固定 **HTTP/1.1**，不发送 h2c 升级（兼容性缘由见[架构规格](../architecture.md)）；配置用于连接及请求超时，**没有应用级重试**。身份/模态必须匹配，且结果须为 `completed` + `PASS`、权重已加载、非 mock、区域非空；LIMITED、服务不可用或契约不一致都会阻止报告，不静默退回本地分析或 LLM。

来源：[SegmentationServiceClient.kt](src/main/kotlin/aiagent/tools/SegmentationServiceClient.kt)、[MedicalImageAnalyzerTool.kt](src/main/kotlin/aiagent/tools/MedicalImageAnalyzerTool.kt)。

### 课程 research 模型包

- 模型响应必须明确 `intended_use=research`，支持模态恰为 `["ULTRASOUND"]`，请求也必须是 `ULTRASOUND`，并满足单通道二值分割契约。不能用前端声明或自由文本替代模型用途；用途、包哈希缺失或身份/请求 ID 不匹配会失败。
- 独立科研映射只输出像素面积、外接框像素宽高、覆盖率等可复核量，并校验几何一致性。`estimated_size_mm` / `estimatedSizeMm` 必须为 **null**，无物理标定不能补成 `0 mm` 或换算毫米。旧非科研响应反而要求有效正物理尺寸，不能借 nullable 改动混入该分支。
- 指标使用 `MetricCollection` 内的 `GeneralMetric`，`GeneralMetric.confidence=null`。区域前景概率未经临床校准，不能当医学置信度；颜色只区分区域，不代表风险。AI 外层科研 `confidence` 为 null，WS 通常省略该键；旧非科研分支的固定 95 也不是评测结果。
- 报告保留 `intendedUse=research`、`ULTRASOUND_RESEARCH_REPORT`，标题明确“科研影像分割报告（非诊断）”，不输出血流、良恶性或临床风险结论。
- Kotlin 校验 `bundle_manifest_sha256` 格式及几何一致性，Python 校验模型包/权重完整性；这些只提供契约与完整性线索，**不是可信来源认证、信任签名或临床有效性证明**。

实现：[ResearchSegmentationPayload.kt](src/main/kotlin/aiagent/tools/ResearchSegmentationPayload.kt)、[ToolPayloads.kt](src/main/kotlin/aiagent/tools/ToolPayloads.kt)、[ReportGenerateTool.kt](src/main/kotlin/aiagent/tools/ReportGenerateTool.kt)；接收字段与限制见 [API.md](API.md)。

### DeepSeek 配置不等于调用

`DEEPSEEK_API_KEY`、JVM `deepseek.apiKey` 等仅用于预留的非诊断文本 Provider 配置。未配置时可禁用 Provider，不阻止本地受控工具链；即使配置，也不会使现有路由自动运行 Koog 图或调用 LLM 解释报告。**课程科研不需要为此开通云 key**，不得用 LLM 顶替失败的分析。

## 4. 接口索引与访问边界

直连 HTTP 基址为 `http://localhost:8083`，路径**不带 `/metric`**。网关 REST 基址为 `http://localhost:8088/metric`，去掉前缀后转发；网关外层要求 JWT。AI WebSocket 默认直连 `ws://localhost:8083/ws/ai-agent`，网关目前没有 WS 代理。

| 主要入口（metric 直连） | 用途 |
| --- | --- |
| `GET /health` | 数据库连通性检查 |
| `/images` | 影像元数据列表、登记、姓名查询及按 ID 删除；不是 multipart 上传 |
| `/analyses` | 分析列表、姓名查询和结果登记；POST 不运行分析 |
| `/reports` | 报告列表、姓名查询、登记；`GET /reports/{id}/file` 读取已有 PDF |
| `/conversations/{conversationId}` | GET/PUT/DELETE 文件式会话历史 |
| `POST /ai/analyze-and-report` | AI HTTP 受控分析/固定文本入口 |
| `WS /ws/ai-agent` | 直连、逐请求返回完整结果，不是逐 token 流 |

完整方法、字段、状态、序列化差异和示例统一见 **[API.md](API.md)**；跨服务入口见 [API 总索引](../docs/api-reference.md)。不要据 DTO 名称推测存在通用更新、分页或额外 CRUD 路由。

- **直连认证缺口**：普通 `/images`、`/analyses`、`/reports`、会话和 PDF 下载没有路由级 `authenticate`；安装 Authentication 插件不等于接口受保护。
- **AI 仅手验 JWT**：HTTP/WS 校验签名、issuer、audience、有效期和 subject，没有角色或医院/患者授权。可用 Authorization header 或 query `token`，非空 query 优先；经网关仍需外层认证 header。客户端身份未绑定操作者范围，缺失时还会补 unknown 标识。不能保证仅医院用户访问，更不能视为医院隔离已完成。
- `imageData` 是本地路径、`file://` 或可解码图像 DATA_URL，不是裸 base64；仅元数据引用不能进入报告。栅格解码依赖 Java ImageIO，不能承诺原生 DICOM、多帧或体数据支持；本地入口不会把任意 HTTP(S) URL 当作下载源。
- HTTP 成功 `result`、WS 成功 `analysisResult` 是 **GeneratedReportPayload 的内层 JSON 字符串**，不是嵌套对象。先解析外层，再校验/解析成功 payload；文本讨论和失败内容不是该 JSON。受控流水线失败仍可返回 **HTTP 200 / WS `ai_response`** 和人工复核文本（confidence=0），不能只看 HTTP 状态或字段非空判断成功。
- 工具/POST 指标判别键是 `kind`，普通 GET 使用默认 `type`，不能不经转换将 GET 原样回 POST。影像创建还存在漏写非空 `patientName`、返回 `InsertStatement` 而非 ID 的问题；影像创建、AI 等异构 Map 的真实 HTTP 序列化未在本次运行验收，不能宣称全功能已成功。

依据：[Routing.kt](src/main/kotlin/Routing.kt)、[Security.kt](src/main/kotlin/Security.kt)、[影像仓库](src/main/kotlin/database/repository/MedicalImageRepository.kt)、[网关路由](../api-gateway/src/main/kotlin/Routing.kt)。前端 REST 还存在直连回退，见 [api.js](../frontend/src/lib/api.js)；WS 和地址配置分别见 [ws.js](../frontend/src/lib/ws.js)、[runtimeConfig.js](../frontend/src/lib/runtimeConfig.js)，不能声称所有访问都经网关。

## 5. PDF、确认、登记与本地文件

实际时序如下，**不是一次原子“分析并登记”操作**：

1. AI 受控函数先生成 PDF，返回工具 payload，此时没有保存业务分析/报告记录。
2. 浏览器“确认分析”调用 `POST /analyses`，采用返回的持久化分析 ID；这不等于已经验证资质的医生签署。
3. 浏览器“生成报表”将**已有 PDF** 的报告元数据、文件路径和保存后的分析 ID 发给 `POST /reports`，不是重新生成 PDF。只提交 `ReportCreate` 也仅得到 generating 记录，不会自动生成文件。
4. 登记后用数据库报告 ID 访问 `GET /reports/{id}/file`。

PDF、Python 工件、会话文件及两次数据库登记没有共同事务、全链路幂等或可靠恢复；中途失败可留下孤立 PDF，重试可产生新分析 ID/多个 PDF。Koog 的日志节点不是登记保障，现有会话 JSON 也不是 Agent checkpoint。

工具 payload 与数据库 DTO 不等价：登记接口即使接收完整 envelope，也只提取 analysis/report；数据库没有完整用途、区域、模型或工件审计字段。科研仅将部分模型/包哈希/门禁信息映射到 keyIndicators，并不保留 Python 完整响应。当前 [App.jsx](../frontend/src/App.jsx) 转换还丢弃顶层 `intendedUse` 和区域 `pixelCount/widthPx/heightPx`；保留 PDF 文本或报告类型不能证明前端/数据库完整保留科研审计链。

### 文件位置与限制

- PDF 写入项目根 `reports/`，会话写入 `chat-histories/`。路径从进程工作目录向上找根标记；找不到时回落到工作目录。运行分发包时保持明确、稳定的工作目录和写权限，不能假定换目录/换机器仍能读到旧文件。实现见 [LocalFiles.kt](src/main/kotlin/LocalFiles.kt)、[ConversationHistoryStore.kt](src/main/kotlin/ConversationHistoryStore.kt)。
- 根 [.gitignore](../.gitignore) 忽略 `reports/`、`chat-histories/`、`.run/`、`.data/` 和构建输出；Python 的 [忽略规则](../segmentation-service/.gitignore) 覆盖其 `var/` 产物目录。公开/合成样例之外的数据、权重、报告、日志、token 和凭据不要提交；Git 忽略不是加密、授权或备份机制。
- PDF 由 Java AWT 将整页渲染为图像再封装，**没有可搜索/复制的 PDF 文本层**；中文显示依赖主机字体。图像解码、页面图像和 DATA_URL 会占用内存，尚无完整大图资源限额、任务队列与取消控制；WS 帧上限为 `Long.MAX_VALUE`。来源：[PdfReportRenderer.kt](src/main/kotlin/aiagent/tools/PdfReportRenderer.kt)、[WebSockets.kt](src/main/kotlin/WebSockets.kt)。
- 下载只有 `reports` 下的词法路径范围检查，不是 realpath/符号链接防护或医院资源授权。会话文件名清洗可能碰撞，覆盖写入没有原子并发/版本恢复保证。
- 远程分割启用后，metric 仍先读取输入像素；本地文件路径需两侧同机或显式共享挂载，Python 工件路径也不是公开 URL。跨主机/容器路径、访问权限、挂载与备份恢复需单独设计和验收，不能把服务端路径直接交给浏览器当下载地址。

登记实现：[AnalysisRepository.kt](src/main/kotlin/database/repository/AnalysisRepository.kt)、[ReportRepository.kt](src/main/kotlin/database/repository/ReportRepository.kt)。

## 6. 测试入口（仅说明，未执行）

### 本地单元与工具链回归

在仓库根目录执行：

```bash
./gradlew :metric-service:test
```

现有回归不启动完整业务应用或数据库，但部分测试会启动本地 JDK HTTP 桩、创建合成栅格和临时 PDF；不要将其称为无文件/无网络副作用的纯单元测试。未设置 live 环境变量时，真实服务集成测试默认跳过。

| 源码 | 主要断言范围 |
| --- | --- |
| [MetricAiSupportTest](src/test/kotlin/com/example/MetricAiSupportTest.kt)、[MetricAiConversationGuardTest](src/test/kotlin/aiagent/validation/MetricAiConversationGuardTest.kt) | 配置解析、DATA_URL 保留和关键词范围 |
| [StrategyGraphBoundaryTest](src/test/kotlin/aiagent/strategy/StrategyGraphBoundaryTest.kt) | 受控函数拒绝未知模态/仅元数据、合成栅格到 PDF；不是执行 Koog 图的证据 |
| [MedicalImageToolChainTest](src/test/kotlin/aiagent/tools/MedicalImageToolChainTest.kt) | 本地/桩服务工具链、PDF 文件、LIMITED/未加载权重/mock/旧裸 DTO 拒绝 |
| [ResearchSegmentationToolChainTest](src/test/kotlin/aiagent/tools/ResearchSegmentationToolChainTest.kt) | 科研像素量/空置信度、非诊断报告、用途/哈希/几何/身份校验、旧响应 null/省略尺寸拒绝及 HTTP/1.1 请求 |

这些是源码中的断言范围，不是本次测试通过记录，也不覆盖普通 CRUD 的数据库/HTTP 编码或完整浏览器流程。

### 显式 opt-in live 集成

已核对类名为 **`aiagent.tools.ResearchSegmentationLiveIntegrationTest`**，参数来自 [测试源码](src/test/kotlin/aiagent/tools/ResearchSegmentationLiveIntegrationTest.kt)：

| 环境变量 | 语义 |
| --- | --- |
| `METRIC_RESEARCH_TEST_URL` | 必需：显式指定已准备好的本地 Python 研究分割服务地址 |
| `METRIC_RESEARCH_TEST_IMAGE` | 必需：可读取、已获授权的公开/合成科研栅格路径；两侧满足共享路径约定 |
| `METRIC_RESEARCH_TEST_BUNDLE_SHA256` | 可选：与返回 keyIndicators 中包哈希精确比较的预期值 |
| `METRIC_RESEARCH_TEST_OUTPUT` | 可选：保存 payload JSON 的路径；设置后保留该 PDF，未设置则清理测试 PDF |

URL 或 IMAGE 未设置/为空时使用 JUnit Assume **跳过**，不能把 skip 当联调成功。该测试直接调用受控函数，不要求先启动 metric 业务进程/DB；但会请求真实 Python 服务并生成 PDF，不测试数据库登记、浏览器或鉴权。

仅在隔离环境准备完成后人工选择运行；以下影像是假路径，必须换为自己有权使用的公开/合成科研数据，不含真实病例或凭据：

```bash
METRIC_RESEARCH_TEST_URL=http://127.0.0.1:8099 \
METRIC_RESEARCH_TEST_IMAGE=/example-public-data/synthetic-ultrasound.png \
METRIC_RESEARCH_TEST_OUTPUT="$PWD/.run/metric-research/live-payload.json" \
./gradlew :metric-service:test \
  --tests 'aiagent.tools.ResearchSegmentationLiveIntegrationTest' --rerun-tasks
```

## 7. 本次核对与待验收

本文仅静态对照规格、配置、构建、路由、工具、持久化、前端消费者和测试源码；没有运行服务、数据库、构建、测试或训练。仍需后续独立验收：影像创建及异构 Map 实际序列化、浏览器确认/两次登记/下载、医院与患者授权、大图资源控制、并发重试/幂等恢复、跨机部署及文件共享。更多接口缺陷与兼容边界见 [API.md](API.md)，本说明不将这些未验收项表述为已交付能力。
