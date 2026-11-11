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
