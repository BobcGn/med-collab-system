---
id: shared
type: module-design
status: draft
title: Kotlin 共享契约与基础工具
parent: med-collab-architecture
---

# Kotlin 共享契约与基础工具

## 职责

为 Kotlin 服务提供可序列化 DTO、领域枚举、异常类型、JWT 辅助和数据库初始化工具。它是库，不是部署服务，也不是单独的业务事实存储。

## 边界

不承载模型推理、报告编排、前端状态或具体服务 repository。数据库初始化工具是现存基础设施能力，不意味着各服务可以绕过所有权任意访问其他业务表。

序列化多态约定与字段变更会影响多个消费者；按 `med-collab-architecture` 的跨层契约规则一起验证。Python 分割 schema 当前是另一份 HTTP 契约，不会因修改 Kotlin DTO 自动同步。

当前 metric 工具 payload、POST 解码和数据库 JSON 使用 kind，普通 GET 使用默认 type，标签为完整类名；这不是 shared 的统一全局设置，GET 不能未经递归适配原样回 POST。日期主要是字符串，null/默认字段输出由各消费者配置决定；具体线协议归 [metric API](../metric-service/API.md)及其他服务 API，而不是声明了 DTO 就自动获得对应路由。

GeneralMetric.confidence 可空：几何量没有校准置信度时保留缺失，而不是填入 0 或固定百分数。具体科研测量与用途的语义由 `segmentation-service` 和 `hospital-ai-boundary` 定义；其他既有模态 DTO 的字段未改变。
