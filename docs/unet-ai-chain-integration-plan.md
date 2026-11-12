# U-Net 集成：历史设计与当前实现对照

## 1. 文档定位与权威来源

本页保留“历史集成设计 / 现状对照”用途，**不再是待执行的实施计划、配置模板或接口契约**。原始方案可从本文件的 Git 历史查看；其中的生产目标、示例 payload、后端枚举、降级建议及 Phase 0–4 不再作为当前操作依据，也不构成新的交付排期。

以下按本次工作树静态核对，区分已实现能力、已知缺陷和未实现目标；不表示本轮重新训练、评分或完成端到端验收，更不表示医院可用。

| 查阅事项 | 唯一归属 / 当前参考 |
| --- | --- |
| 跨服务边界、集成动机、独立 Python 等技术选型理由 | [架构](../architecture.md)及[分割模块 SPEC](../segmentation-service/SPEC.md)；本文不重复决策理由 |
| 医疗职责、科研用途、LLM 与失败处理硬边界 | [医院 AI 职责政策](hospital-ai-boundary-policy.md) |
| 推理、模型包与训练证据 | [分割模块 SPEC](../segmentation-service/SPEC.md)、[训练 SPEC](../segmentation-service/app/training/SPEC.md)；实验成绩、溯源和局限不在本页复制 |
| 受控执行、Koog 现状与未来 Harness 范围 | [Agent SPEC](../metric-service/src/main/kotlin/aiagent/SPEC.md)；业务记录归[metric SPEC](../metric-service/SPEC.md) |
| 实际接口与操作 | [API 总索引](api-reference.md)、[操作手册](segmentation-service-operation-manual.md)；字段、默认值、配置及命令以对应材料为准 |

## 2. 当前实现矩阵

“已有代码”“已有局部验证”和“医院可用”不是同一状态。

| 环节 | 当前事实与限制 | 核对入口 |
| --- | --- | --- |
| 独立 Python 推理 | FastAPI 服务已存在；包含栅格读取、预处理、仓库内 UNet2D、原尺寸概率恢复、阈值化、四连通 BFS、区域组装、质量门禁及工件写入，不再是“待新增服务” | [Python 入口](../segmentation-service/app/main.py)、[推理流水线](../segmentation-service/app/inference/pipelines/segmentation_pipeline.py) |
| 训练与研究包 | 本地 BUSI 准备、训练、原尺寸评测和最佳模型包自动导出已实现；已有本机 MPS 训练、封存内部评测及真实 Python/Kotlin 工具链生成科研 PDF 的历史证据。无患者独立或临床效度证据，不是本轮重测结论 | [训练 SPEC](../segmentation-service/app/training/SPEC.md)、[训练入口](../segmentation-service/app/training/train.py) |
| HTTP / WebSocket | `POST /ai/analyze-and-report` 与 `/ws/ai-agent` 的图像分支经包装函数直接调用 `executeControlledMetricReportPipeline`，执行分析、校验、报告生成与报告校验 | [Routing.kt](../metric-service/src/main/kotlin/Routing.kt)、[受控函数](../metric-service/src/main/kotlin/aiagent/strategy/StrategyGraph.kt) |
| Koog 策略图 | `metricReportStrategy` 已定义，但实际路由不运行此图；图内“登记正式报告”仅在事务块中打印日志，不写业务记录 | [StrategyGraph.kt](../metric-service/src/main/kotlin/aiagent/strategy/StrategyGraph.kt) |
| 文本分支与 LLM | 范围守卫按有图/关键词分类；文本回复是固定说明拼接，不调用 LLM，也没有绑定真实报告证据的多轮解释。DeepSeek/Koog Provider 配置只是能力预留 | [范围守卫](../metric-service/src/main/kotlin/aiagent/validation/MetricAiConversationGuard.kt)、[Routing.kt](../metric-service/src/main/kotlin/Routing.kt)、[Application.kt](../metric-service/src/main/kotlin/Application.kt) |
| 远程分割开关 | metric 的 `METRIC_SEGMENTATION_SERVICE_ENABLED` 源码默认 `false`；显式启用才经 `SegmentationServiceClient` 调用 Python。只启动 Python 或加载权重不会自动切换 metric | [配置解析](../metric-service/src/main/kotlin/MetricAiSupport.kt)、[分割客户端](../metric-service/src/main/kotlin/aiagent/tools/SegmentationServiceClient.kt)、[操作手册](segmentation-service-operation-manual.md) |
| 默认旧启发式 | 开关关闭时仍走本地灰度/边缘/常量规则，可生成带“正式分析报告”字样的 PDF；HU-like、毫米、血流/组织和风险等派生值并非有效医学测量。旧 `unspecified` 远程 mask 分支也复用部分规则，启用远程不自动修正这些语义 | [分析工具](../metric-service/src/main/kotlin/aiagent/tools/MedicalImageAnalyzerTool.kt)、[Agent SPEC](../metric-service/src/main/kotlin/aiagent/SPEC.md) |
| 显式 research | 课程模型包固定 `research`、仅 `ULTRASOUND` 单张栅格二值分割；Kotlin 走独立像素量映射，不进入旧超声指标推导。报告保留用途并输出 `ULTRASOUND_RESEARCH_REPORT`，标题、结论明确非诊断 | [模型包](../segmentation-service/app/inference/model_bundle.py)、[科研映射](../metric-service/src/main/kotlin/aiagent/tools/ResearchSegmentationPayload.kt)、[报告工具](../metric-service/src/main/kotlin/aiagent/tools/ReportGenerateTool.kt) |
| 报告消费 | 工具 payload、轮廓、边界框、高亮图例和 PDF 渲染已有；报告工具消费结构化分析，不重新分析影像。可反序列化不等于绕过流水线后仍具备全部校验 | [ToolPayloads.kt](../metric-service/src/main/kotlin/aiagent/tools/ToolPayloads.kt)、[ReportGenerateTool.kt](../metric-service/src/main/kotlin/aiagent/tools/ReportGenerateTool.kt)、[PDF 渲染](../metric-service/src/main/kotlin/aiagent/tools/PdfReportRenderer.kt) |

**默认旧启发式的保留是已知现实，不是推荐 fallback。** 显式 mock 仅用于契约联调，不用于展示医学效果，更不能在真实推理失败时替代分析。

## 3. 接收、科研测量与失败边界

- 远程分割接收要求身份/模态匹配、`completed + PASS`、权重已加载、非 mock、区域非空；research 还校验用途、包哈希格式、支持模态、请求关联及像素几何一致性。**`LIMITED` 也拒绝进入报告阶段**，不是原提案中的“带限制继续出报告”。
- 显式 research 只陈述像素几何量与未校准前景概率；无物理标定，毫米值为 null；`GeneralMetric.confidence` 与科研成功外层 confidence 保持 null/相应编码下缺省。区域概率不等于医学置信度，颜色不表示临床风险。不得补成 `0 mm`、固定 95、血流、良恶性或风险判断。精确字段和不同层次的 null/省略规则见[metric API](../metric-service/API.md)与[分割 API](../segmentation-service/API.md)。
- 科研包用途或关键契约缺失不能静默落入旧临床指标映射；非科研旧响应仍要求有效正物理尺寸，nullable 毫米不是所有路径通用的放宽。科研用途来自受控模型包/响应，不是前端随意添加一个请求选项即可取得。
- 分割不可用、门禁不合格、契约不一致或报告失败，只返回失败说明/人工复核提示；**不回退到 LLM、mock 或旧启发式补造分析或报告**。LLM 的未来解释权限也不包括生成、改写或补全正式分析结论，详见[医院政策](hospital-ai-boundary-policy.md)。
- HTTP 200、WS `ai_response`、非空结果文本或工件存在均不等于可报告成功；Python 门禁失败仍可能 HTTP 200 并写工件，metric 受控失败也会转为复核文本。成功结果字符串、错误体及业务状态的解析只按对应 API 文档处理。

## 4. 当前接口只查 API，不复制历史 payload

本页不维护第二套请求/响应表，也不提供可直接运行的配置或大段 JSON 示例。

| 边界 | 应查文档及易混点 |
| --- | --- |
| metric HTTP / WS、登记与 PDF 下载 | [metric API](../metric-service/API.md)：实际 camelCase 请求/工具 payload、双层结果字符串、错误、nullable/缺省，以及工具/POST 的 `kind` 与普通 GET 的 `type` 差异；不能将 GET 原样回 POST |
| Python 单张/顺序批量推理 | [segmentation API](../segmentation-service/API.md)：实际 snake_case 字段、来源、输出选项、质量状态、错误体及工件语义；含 `return_metrics` 尚未落实关闭行为等限制 |
| 认证与患者业务 | [auth API](../auth-service/API.md)、[patient API](../patient-service/API.md)：身份及患者记录接口不构成已完成的医院资源授权，不代替推理或报告接口 |
| 网关与直连 | [gateway API](../api-gateway/API.md)：REST 转发前缀、认证及透传限制；没有 WS 代理，`/report` 也不是 metric 报告接口的别名 |
| 配置、模型包与操作前提 | [操作手册](segmentation-service-operation-manual.md)及[分割 README](../segmentation-service/README.md)：实际环境变量、配置优先级、模型加载、BFS 与部署限制；不要按本页历史名称操作 |

## 5. 原提案退役对照

以下旧示例及相关未实现设想仅供对照，**不是当前可用配置或待办承诺**。不应从旧 Phase 列表推断哪些阶段“全部完成”。

| 旧示例 / 设想 | 当前处理 |
| --- | --- |
| camelCase Python 推理示例：`requestId`、`image.path`、`success`、`classes`、S3 工件 URL | 旧示例已撤下。Python 实际采用 snake_case、`image.value`、状态/门禁及 `regions`；工件是服务端路径，不是 URL。`OBJECT_STORAGE` 只读取 HTTP(S)，没有原生 `s3://` 适配；metric 的 camelCase DTO 不是 Python 协议 |
| `aiInference`、`HEURISTIC / UNET_REMOTE / UNET_LOCAL`、`AI_INFERENCE_BACKEND`、`UNET_INFERENCE_*`、`UNET_MODEL_*` | 旧后端枚举与配置示例未落地，不能照抄；当前 metric 使用独立 segmentation 开关/地址/超时设置，Python 有自己的配置体系，见操作手册 |
| 推理 API key、`retryCount`、失败重试一次 | 当前分割客户端没有这套鉴权头或自动重试机制；DeepSeek 自身的密钥配置不是推理服务鉴权。客户端 HTTP 超时不保证取消 Python 执行，前端已有重试也不是全链路幂等恢复 |
| “本地失败再进 Koog/DeepSeek”、mock 或 LLM 兜底结论、`LIMITED` 继续出报告 | 已退役且不能恢复为推荐操作；当前受控失败拒绝替代分析，实际 Koog 图不运行。旧启发式默认仍在，不等于它是远程失败回退 |
| 独立服务、分割适配、高亮 PDF 都待开发；报告工具基本无需改动 | 已有具体实现，且已增加科研用途、像素量、可空毫米/置信度与非诊断报告处理；不能再按旧改造清单判断源码状态 |
| 异步任务/回调、按医院/科室灰度、多模型路由、多类别体系、DICOM/序列/体数据 | 尚未实现这些承诺。现有批量接口逐项同步，旧多输出只取一个目标通道；模态字符串不是多模型或原生 DICOM 能力 |
| 分割专用表、完整工件治理、图节点可靠登记与审计 | 未形成原设想的完整链路；文件、业务记录与会话分离，元数据保留有损，见下节 |
| Phase 0–4 及后续 Agent 多轮实施路线 | 原排期退役，不在本页另设阶段、验收承诺或时间表。用户确认但未实现的解释、受控编排、可靠性与可追踪目标只查 [Agent SPEC](../metric-service/src/main/kotlin/aiagent/SPEC.md) |

## 6. 生成、确认、登记不是同一事务

实际顺序见[架构](../architecture.md)及[metric API](../metric-service/API.md)：

1. 受控函数执行分析；若启用 Python，分割工件可能已先写入。metric 报告工具随后**先生成 PDF**，再返回工具 payload。
2. 浏览器“确认结果”调用 `POST /analyses` 登记分析；以返回的持久化 ID 为准。确认状态不是已核实资质医生的签署。
3. 浏览器“生成报表”实际调用 `POST /reports` 登记**已经生成的报告**及关联分析 ID，不是在此时首次生成 PDF。
4. 登记后的报告数据库 ID 才用于文件下载接口。图内日志不能替代以上业务写入。

PDF、Python 工件、分析/报告登记及会话文件没有跨步骤原子事务、全链路幂等或可靠恢复保证。中途失败可留下孤立/部分文件，重试可生成新 ID 与多个 PDF；重复工件 ID 拒绝覆盖也不是缓存命中或恢复原运行。

## 7. 仍未实现或未验收的边界

| 边界 | 不能从现有实现推导出的能力 |
| --- | --- |
| 模型医学效度 | 确定性、结构化、`weights_loaded=true`、质量 PASS、哈希一致及 PDF 成功都不是临床资格。BUSI 是受数据划分、单种子、未知患者关联及误检/小目标损失限制的封存内部实验；不据此宣布医院可用，详情归[训练 SPEC](../segmentation-service/app/training/SPEC.md) |
| 端到端科研元数据 | Python 有模型/工件信息，工具 payload 只选择性映射，不完整保留路径及全部审计字段；[前端转换](../frontend/src/App.jsx)未保留顶层 `intendedUse` 和区域像素量。登记路由只提取 shared 分析/报告 DTO，数据库不自动保存完整工具 envelope；保留指标、报告类型或 PDF 文本不等于完整科研审计链 |
| 完整性与来源可信 | 权重哈希和 manifest 摘要用于完整性/一致性核对，不是数字签名、来源许可认证、可信医院签署或医学效度证明；Kotlin 校验哈希格式也不是独立认证模型来源 |
| 路径与部署 | mask/overlay/metadata/PDF 路径不是公开 URL；Python 没有工件下载路由。本地影像须由实际执行进程读到，跨主机/容器依赖显式受控挂载，不自动共享；Docker/医院部署未据此验收 |
| 医院授权与资源上界 | JWT、身份回传及目录检查不构成完整医院隔离；metric 接受客户端身份并补 unknown，普通直连 CRUD/会话/文件缺少路由认证，Python 无服务鉴权。输入路径/远程 URL 白名单及统一字节、像素、批量、并发等应用资源限额仍缺，不能直接向不可信调用方开放 |
| Harness 与运行可靠性 | 没有已交付的证据绑定多轮解释、任务取消/恢复、可信 checkpoint 或完整运行事件审计；会话 JSON 接受客户端消息，不是分析事实或 Agent checkpoint。未来范围与尚待设计部分仅由 [Agent SPEC](../metric-service/src/main/kotlin/aiagent/SPEC.md)记录 |
| HTTP 与影像登记缺陷 | AI HTTP 等异构 Map 的真实编码尚未运行验收；影像登记漏写非空 `patientName`、返回 InsertStatement 而非 ID 等现有缺陷仍在。具体源码声明及风险见[metric API](../metric-service/API.md)，本次文档改写没有修复或验收这些问题 |

## 8. 本次核对范围

完整阅读原计划、架构/医院政策、分割与训练 SPEC、Agent SPEC、五服务 API 和操作手册，并定点核对实际路由、受控函数、分析/科研映射、报告、Python 编排、训练导出、工件与前端登记入口。

本次仅重写本文件并做文档静态自查；未启动服务或数据库，未运行训练、测试或 test split 评分，未新增模型效果、登记可靠性或安全验收结论。其余文档/SPEC/源码/配置不由本任务修改；全部文档同步后的 PR 仍由用户手动合并，本页不宣告“全部完成”。
