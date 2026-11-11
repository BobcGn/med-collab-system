---
id: api-gateway
type: module-design
status: draft
title: 网关转发与边缘认证
parent: med-collab-architecture
---

# 网关转发与边缘认证

## 职责

为浏览器 REST 提供统一转发入口，根据服务配置路由并处理路径映射、转发头和响应。部分业务前缀在边缘要求 JWT，auth 前缀转发后由下游区分公开与受保护操作。

## 边界与限制

网关不拥有业务数据、分割模型或报告生成规则；下游仍须负责资源级授权，网关验签不能代替医院/患者范围检查。真实调用方向由 `med-collab-architecture` 描述。

当前没有 WebSocket 代理；客户端有直连路径，不能把网关视为唯一安全边界。转发实现对 DELETE 请求体的处理、report 服务地址、跨容器路径和部署前缀需集成验证；当前不宣称所有转发方法及 Docker 拓扑已正确验收。
