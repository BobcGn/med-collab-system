# ============================================================================
# PR Review Checklist Template (人工审查用)
# 来源: skill-central / pr-review-checklist — 所有 7 个维度
# ============================================================================

## PR Review: <!-- PR 标题 -->

### 总体评估

- [ ] ✅ Approve — 代码质量优秀，建议合并
- [ ] ⚠️ Request Changes — 存在必须修复的问题
- [ ] 💬 Comment — 仅建议性改进，可选择采纳

---

### 审查清单

#### 1. 架构与设计 [必须]

> 参考: skill-central/architectural-mindset, api-design-review

- [ ] 变更遵循项目现有架构模式（分层约定、模块边界）
- [ ] 新增模块/类符合项目分层约定
- [ ] 公开 API 设计清晰、易用、向前兼容
- [ ] 无过度设计

#### 2. 正确性 [必须]

> 参考: skill-central/error-handling-patterns

- [ ] 边界条件处理完整（null、空集合、零值）
- [ ] 错误路径处理正确（异常捕获、回滚、降级）
- [ ] 并发场景安全
- [ ] 外部依赖调用有超时和重试策略

#### 3. 安全性 [必须]

> 参考: skill-central/codeql-security-scanner

- [ ] 用户输入经过验证和转义
- [ ] 无注入风险（SQL/命令/XSS/路径穿越）
- [ ] 认证/授权检查完整
- [ ] 敏感信息未硬编码

#### 4. 性能 [重要]

> 参考: skill-central/backend-code-review, python-code-review

- [ ] 无 N+1 查询
- [ ] 循环中无不必要的 I/O
- [ ] 大集合操作高效

#### 5. 测试 [重要]

> 参考: skill-central/test-codecov-assessor, testing-best-practices

- [ ] 新增功能有测试用例
- [ ] 关键路径和异常分支被覆盖
- [ ] 测试用例独立、可重复

#### 6. 可维护性

> 参考: skill-central/linting-code-style-inspector

- [ ] 命名清晰表达意图
- [ ] 函数/方法职责单一
- [ ] 有必要注释解释「为什么」

#### 7. 兼容性

> 参考: skill-central/changeset-changelog-generator

- [ ] 数据库 schema 变更向后兼容
- [ ] API 变更标注 BREAKING CHANGE
- [ ] 配置项增删改不影响已有部署

#### 8. 基础设施

> 参考: skill-central/container-infra

- [ ] Docker 镜像非 root 运行
- [ ] 容器有 HEALTHCHECK
- [ ] 密钥通过 secrets 管理，非硬编码

---

### 发现的问题

| # | 严重度 | 维度 | 文件:行号 | 问题描述 | 修复建议 |
|---|--------|------|-----------|---------|---------|
| 1 | 🔴 Critical | 安全性 | `AuthService.kt:42` | 密码明文存储 | 使用 BCrypt 哈希 |
|   |        |      |           |         |         |

### 亮点

<!-- 值得肯定的设计和实现 -->

- 

### 建议（非阻塞性）

<!-- 可在后续 PR 中改进的点 -->

-
