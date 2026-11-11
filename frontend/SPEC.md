---
id: frontend
type: module-design
status: draft
title: 协作客户端与人工确认边界
parent: med-collab-architecture
---

# 协作客户端与人工确认边界

## 职责

React SPA 提供账号、组织、患者与影像工作入口，展示结构化分析/报告、维护会话消息，并发起确认分析和登记报告。真实工具链以本模块 package.json 为准，不使用 Vue。

## 边界

展示服务端事实，不在浏览器推断分割结论、重算医学指标或将自由文本伪装成正式结果。业务访问仍由服务端授权，隐藏菜单或携带 hospitalId 不构成数据权限证明。通信拓扑由 `med-collab-architecture` 统一描述。

当前确认分析调用 analyses API；报告登记是另一次请求；PDF 可能在确认前已经生成。“已确认”是现有业务状态，不表示有已核实资质的医生完成签署。会话保存并不等同于 Agent 运行恢复。

客户端当前存在 token 本地存储和 WebSocket query token 用法，不能宣称凭据治理已完善。尚无专门前端测试框架；涉及消费者契约改动至少执行本模块 build，构建成功仍不能证明业务授权或医学正确性。
