---
id: med-collab-architecture
type: architecture-design
status: draft
title: 医工创新平台架构与模块边界
parent: med-collab-goals
references: [hospital-ai-boundary]
tags: [architecture, contracts]
---

# 医工创新平台架构与模块边界

## 运行拓扑与真实依赖

Kotlin 2.2.21 / Ktor 3.3.2 服务使用 Java 21、Gradle、Exposed 与 MySQL 协议数据库；React 19 / Vite 8 和 Python 3.11+ / FastAPI / PyTorch 各自独立构建。Koog 当前依赖为 0.5.1。

- 浏览器 REST 通常经 `api-gateway` 转发至认证、患者或影像服务；客户端存在 metric 直连回退。通知与 AI WebSocket 直连 auth / metric，网关当前没有 WebSocket 代理，不能宣称所有访问都经过网关。
- `auth-service` 拥有身份、组织及通知；`patient-service` 拥有患者业务，通过 HTTP 向 auth 获取用户信息。
- `metric-service` 拥有影像业务、分析/报告记录与会话历史；通过 HTTP 调用 `segmentation-service`，其内部 Agent 边界由 `metric-agent-harness` 定义。
- 四个 Kotlin 服务编译依赖 `shared`。Python 不依赖 Kotlin 运行时；跨语言接口由两侧契约镜像表达，而不是直接共享 Kotlin DTO。
- Python 负责预处理、模型运行、后处理、质量门禁与分割工件；metric 负责业务接入、跨服务校验及 PDF 消费。独立 Python 推理边界符合训练生态与运行依赖隔离；业务 JVM 不承载训练依赖。

这是实际调用方向，不表示每个依赖都经过完整安全验证。

## 跨服务契约

`POST /api/v1/segment` 使用 snake_case JSON，传递请求 ID、医院/患者身份、模态、影像引用与输出选项；返回身份、模型描述、耗时、质量门禁、区域和工件位置。Kotlin 由 SegmentationServiceClient 镜像该契约。

该客户端显式采用 HTTP/1.1 访问 FastAPI/Uvicorn。JDK 默认的明文 h2c 升级曾在真实 Uvicorn 联调中导致空 body / HTTP 422，而原 JDK 模拟服务器未暴露此行为；因此禁用该升级，并保留请求头回归与真实服务集成验证。这不改变 JSON 契约或其他服务的传输策略。

调用方当前要求身份/模态匹配、completed + PASS、weights_loaded、非 mock 且区域非空；这只是运行接收条件，不是模型准确率或临床适用性证明。更严格的模型与测量语义见 `segmentation-service`。课程模型的 research 用途须贯穿 Python、Kotlin payload 和报告，不得因丢失标记而进入旧临床指标映射；其职责边界归 `hospital-ai-boundary`。

修改接口时必须一起检查 Python schema、Kotlin 客户端、工具 payload、相关 shared DTO 和前端消费者；边界原则以 `hospital-ai-boundary` 为准。当前工具/写入和普通 GET 的多态序列化并不统一，不能假设 DTO 名称相同就能跨边界原样回传，具体约定归 `shared`。不要让前端重新拼造模型结论，也不要把 GPU/Python 依赖直接塞入业务 JVM。

## 工件与登记不是同一事务

Python 将 mask、overlay 和结果快照写入服务端目录；metric 的报告工具先生成 PDF 并返回 payload。浏览器随后通过确认分析和登记报告分别调用持久化 API。Koog 图中的“登记”节点目前仅在事务内打印，不写业务记录；实际路由也不执行该节点。

因此“生成成功”“用户已确认”“数据库已登记”是不同事实；没有全链路事务、幂等执行或可靠恢复保证。工具 payload、shared 持久化 DTO 和前端转换保留的信息不同，不构成完整科研审计链；具体缺口由对应模块说明。现有会话 JSON 也不是 Agent checkpoint。共享路径只在显式挂载/同机约定下可跨服务使用，工件路径不等于可公开下载的 URL。

## 安全与验证边界

代码包含 JWT、部分角色检查、路径检查和质量门禁，但不能声称已实现完整医院隔离。用户已确认身份范围校验、审计与登记可靠性需补强；metric 请求仍接受客户端身份并填充 unknown 默认值，Python 原始影像路径、远程 URL 和访问身份仍缺少完整受控范围约束；工件命名与覆盖约束已由分割模块收紧，但不构成服务鉴权。

现有启发式指标与真实训练模型必须可区分；“确定性”只说明执行方式，不自动证明医学含义。当前源码中的默认本地启发式结果仍可生成标为正式的 PDF，这一事实不构成对其临床使用的认可。

默认端口及启动操作以 README 和 scripts/quick-start.sh 为入口；启动脚本构建时跳过测试，不能用启动成功替代验证。Docker/跨容器路径、医院部署与真实数据授权尚未验收。现有 Dockerfile 构建输入缺失、非 root/健康检查未显式配置，Compose 仍含明文口令；名为 Docker Build 的 CI job 仅文本检查且漏匹配 Dockerfile.*，不构成镜像构建/运行证据。

## 交付门禁

PR 流水线将 shared 与四个 Kotlin 服务的测试失败作为阻塞，而不是允许失败后继续报绿；共享 DTO 变化同时触发前端构建，门禁配置自身变化也触发相关检查。

自动 PR 评论任务在各自 job 申请 `pull-requests: write`；需要检出代码的任务另申请 `contents: read`。权限不提升到全流水线，保留 `pull_request` 事件，不使用 PAT 或 `pull_request_target`，避免修复评论 403 时扩大其他作业的写权限；fork PR 仍受 GitHub 令牌策略限制。

Python 门禁使用 CPU PyTorch 和训练依赖运行合成测试，并检查 JUnit 中核心训练、指标与模型包测试确实执行且没有跳过或失败，避免可选依赖缺失产生假绿。公共 CI 不需要原始数据、研究权重或医院数据库；真实 MPS/模型联调属于单独的显式验收证据，不能由构建成功替代。

## 文档权威关系

`hospital-ai-boundary` 是从现有政策原地采纳的节点。规格承载边界和设计理由；[文档索引](docs/README.md)、[API 索引](docs/api-reference.md)、模块 README 与[操作手册](docs/segmentation-service-operation-manual.md)按路由、schema、构建清单和脚本维护操作事实，不建立第二套医疗规则。[集成计划](docs/unet-ai-chain-integration-plan.md)是历史方案与实现对照，不是运行契约；已否决的 LLM/mock 失败替代、旧协议及尚未实现能力不得重新作为现状。发生冲突时显式核对源码与所属规格，不以模板功能表或前端 helper 推定端点存在；文档同步不自动激活 draft。
