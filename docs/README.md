# 项目文档导航

当前仓库用于医疗协作原型及科研模型验证，不是已完成医院生产验收的系统。下列文档区分**运行现状、操作步骤、设计约束与历史提案**，避免把计划当实现。

## 从哪里开始

1. [根 README](../README.md)：模块、前提、本地启动及当前能力边界。
2. [API 参考总入口](api-reference.md)：五个服务的真实路由、字段、状态、WS 和已知接口缺口。
3. [分割服务操作手册](segmentation-service-operation-manual.md)：环境准备、模型包部署、训练/评测及排错；基于当前实现维护，不再是待创建文件清单。
4. [医院 AI 职责边界](hospital-ai-boundary-policy.md)：不可越过的模型、报告与 LLM 职责约束，不是医院上线证书。

## 模块入口

| 模块 | 运行/开发说明 | API |
|---|---|---|
| auth-service | [README](../auth-service/README.md) | [账号、组织、通知](../auth-service/API.md) |
| patient-service | [README](../patient-service/README.md) | [患者及统计](../patient-service/API.md) |
| metric-service | [README](../metric-service/README.md) | [影像、分析、报告、会话与 AI](../metric-service/API.md) |
| api-gateway | [README](../api-gateway/README.md) | [映射与转发限制](../api-gateway/API.md) |
| segmentation-service | [README](../segmentation-service/README.md) | [分割、质量门禁及模型信息](../segmentation-service/API.md) |
| frontend | [React 客户端 README](../frontend/README.md) | 使用上述接口，不是另一个 API 服务 |
| shared | [共享契约 README](../shared/README.md) | DTO/枚举/工具库，无运行端口 |

## 规格与实验

- [产品目标](../goal-and-requirements.md) → [总体架构](../architecture.md) → 各模块 `SPEC.md`。
- [受控工具与 Agent 边界](../metric-service/src/main/kotlin/aiagent/SPEC.md)：当前走受控函数，Koog 图未实际执行，文本回复不是已上线 LLM Agent。
- [UNet 训练与封存实验](../segmentation-service/app/training/SPEC.md)：数据来源、划分、模型选择、内部结果与限制。测试集已封存，不能据已见结果继续调参。
- 现有12个耐久规格节点仍为 `draft`，等待人工审阅；本次文档同步没有激活规格、改变医疗政策或授予临床资格。

规格承载边界和设计理由；README/API/手册提供对应实现的使用事实，不建立第二套相互矛盾的规则。若发现源码与规格冲突，应显式核对，而不是照抄旧文档扩大能力。

## 历史设计与未实现项

[U-Net 集成设计对照](unet-ai-chain-integration-plan.md)保留设计意图并逐项标明现状。历史字段、配置名、策略图、LLM/mock 兜底和 LIMITED 出报告提案不得当作当前契约；实际接口以 API 参考及源码为准。尚未实现的多轮 Harness、可靠取消/恢复、医院授权和临床验证不能写成已交付能力。

## 不属于可手工同步的文档

`node_modules/` 中的第三方 README/CHANGELOG，即便历史上被跟踪，也不是本项目文档；`generated-reports/`、`reports/`、`build/`、`var/` 和其他运行目录中的报告/数据/权重属于历史产物或本地证据，不为“更新所有文档”而改写。原始数据、权重、曲线、预测图和科研 PDF 不提交到 Git；清理工作区前应另行备份。
