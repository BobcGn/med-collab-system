---
id: metric-service
type: module-design
status: draft
title: 影像业务与结果登记
parent: med-collab-architecture
references: [hospital-ai-boundary]
---

# 影像业务与结果登记

## 职责

拥有影像、分析结果和报告的业务记录与查询/下载入口，以及 HTTP / WebSocket AI 请求和文件式会话历史。分析与 PDF 工具的受控执行边界归 `metric-agent-harness`；医疗职责归 `hospital-ai-boundary`。

## 边界

不在 JVM 内训练或加载 Python UNet，不将 LLM 文本作为分析事实，不自行把前端身份字段视为授权。工具 payload 与数据库 DTO 是不同层次：前者可含区域、图例和解释性字段，后者是持久化契约；二者不能靠自由文本相互替代。

生成、确认与登记的实际关系由 `med-collab-architecture` 记录。本服务提供独立的 analyses / reports 写入接口，不能宣称图节点已经负责可靠登记或两次写入具有原子性。

## 已知限制

请求标准化未将医院与患者绑定到已验证的操作者范围，并会补 unknown 标识；会话历史按医院/会话名存文件不等于医院授权。报告下载有目录范围检查，但这不构成完整的数据资源授权。

用户确认登记和安全需补强；当前规格不承诺任意业务 CRUD 或会话读写已满足医院生产隔离。检验受控链路时，必须同时覆盖成功、结构化分析失败和跨服务字段不一致，而不能只看 PDF 是否存在。
