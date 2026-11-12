---
id: metric-agent-harness
type: submodule-design
status: draft
title: 受控分析工具与 Koog Agent Harness
parent: metric-service
references: [hospital-ai-boundary]
tags: [koog, agent, medical-safety]
---

# 受控分析工具与 Koog Agent Harness

## 职责

拥有结构化分析与 PDF 工具、工具 payload、请求范围守卫、Koog 策略图以及受控流水线执行函数。Harness 指工具权限、上下文、运行状态、失败语义和可追踪性围成的执行环境，不等同于仅安装 Koog Provider。

医疗职责以 `hospital-ai-boundary` 为唯一权威；业务记录与访问归属 `metric-service`，跨服务及生成/登记关系归 `med-collab-architecture`。

## 当前实际执行

HTTP / WebSocket 根据有图或关键词分为 IMAGE_ANALYSIS、METRIC_DISCUSSION、UNSUPPORTED。图像分支直接执行 executeControlledMetricReportPipeline；metricReportStrategy 虽定义了受理、分析和报告子图，但没有被实际路由作为 Agent 图运行。文本分支只拼接固定说明；配置 DeepSeek 并不会让它自动成为多轮解释 Agent。

分析工具将远程分割映射为 MedicalImageAnalysisPayload；报告工具消费此 JSON，确定性生成正文与 PDF。流水线校验身份、模态、像素可读性、摘要/findings，以及报告身份和分析绑定，但不直接证明模型效度。

当前默认关闭远程分割，使用本地启发式像素提取并派生模态指标。这条路径也能进入正式 PDF 生成；包括 HU-like、毫米范围、血流/组织描述及风险等级在内的部分值源自灰度、边缘与常量规则，不是对应医学量的有效测量。未声明科研用途的旧远程 mask 路径仍复用部分本地规则。用户已确认此限制，不能把“确定性”或“结构化”作为指标有效性的证明。

课程 research 结果走独立像素量映射：验证模型包用途/哈希/支持模态、请求身份和像素面积/外接框/覆盖率的一致性，生成无校准置信度的 GeneralMetric 集合；不进入旧超声回声、血流、毫米或风险推导。区域颜色只标识区域，不表示临床优先级。报告保留 research 用途和 _RESEARCH_REPORT 类型，标题及结论明确非诊断；响应不再附带固定 95 置信度。

科研用途或报告类型不得在工具链中丢失。旧分割响应仍要求有效正物理尺寸；nullable 毫米仅为明确科研分支服务，缺失用途/尺寸必须拒绝，不能补成 0 mm。该约束修复了跨层兼容改动可能将像素结果误送入临床指标映射的问题；模型包审计字段不能在 unspecified 模式下被静默消费。

## 用户确认的 Agent 能力边界

以下是确认的目标，尚不能当作已实现能力：

- 解释可追溯的真实结构化结果或正式报告，支持相关多轮问题；不从无证据的自由文本补写分析事实。
- 受控编排请求、必要信息补齐和允许的工具调用；模型分析与报告输出仍由受约束的工具链产生，不交给 LLM 自由生成。
- 完善执行可靠性与可追踪性，包括运行状态、超时/取消、受限重试、工具权限、事件记录和回归评测。

现有会话存储接受客户端消息 JSON，不应被当作已验证分析事实或可信 Agent checkpoint。如何从后端记录绑定解释上下文、权限颗粒度、重试/恢复与事件保留方式尚未设计确认；本规格不宣称这些机制已经存在。

## 失败与副作用

正式分析失败只返回失败/人工复核指导，不启动 LLM 顶替分析，也不将解释文本作为 GeneratedReportPayload。模型说明、错误文本和客户端消息不能越权修改身份、质量门禁或正式结果。

执行、报告文件写入与业务登记当前缺少统一幂等/恢复保证；重试可能产生多个分析 ID 和 PDF。受限重试目标不等于允许盲目重放副作用步骤，取消/恢复设计必须处理这些差异，不能以重新生成医学结果掩盖中途失败。

ReportGenerateTool 目前以反序列化区别正式 payload 与旧 DTO，并不自行重复流水线的完整校验；绕过流水线调用工具不能被认定已通过全部边界检查。关键词守卫只是路由范围提示，不是诊断安全策略或资源授权。

## 已有验证与空缺

边界测试覆盖未知模态、仅元数据输入、合成栅格 PDF；工具测试含远程 LIMITED、未加载权重、mock 拒绝与旧 DTO 拒绝。研究契约回归还覆盖像素量/空置信度、非诊断 PDF、用途与哈希缺失、错误模态、身份不匹配，以及旧响应显式 null / 省略物理尺寸的失败。Python/Kotlin 回归已运行；显式启用的本地集成测试还验证真实 BUSI 权重经 Python HTTP 服务、受控工具链到非诊断 PDF，默认无环境配置时该测试跳过。此验证不含业务数据库登记或完整前端/鉴权流程，也不证明 Koog 图实际执行、多轮证据绑定、权限隔离、超时/取消、幂等恢复或真实 LLM 输出边界有效。
