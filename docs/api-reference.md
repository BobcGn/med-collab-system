# API 参考总入口

本文索引**当前源码已注册的接口**，不是规划清单或医院上线证明。各服务 API 文档记录请求、响应、实际权限检查及已知不一致；没有把客户端 helper、共享 DTO 或设计提案直接当作已实现端点。文档更新不改变路由行为。

## 服务与访问路径

下表端口为本地配置默认值；`PORT` 等环境变量可覆盖。网关地址默认 `http://localhost:8088`，只代理已注册的 HTTP 方法。

| 服务 | 直连默认地址 | 网关映射 | 完整接口文档 |
|---|---|---|---|
| auth | `http://localhost:8081` | `/api/auth` + 直连路径 | [auth-service/API.md](../auth-service/API.md) |
| patient | `http://localhost:8082` | 保留 `/patients` 路径 | [patient-service/API.md](../patient-service/API.md) |
| metric | `http://localhost:8083` | `/metric` + 直连路径 | [metric-service/API.md](../metric-service/API.md) |
| segmentation | `http://127.0.0.1:8099` | Ktor 网关未注册此服务 | [segmentation-service/API.md](../segmentation-service/API.md) |
| gateway | `http://localhost:8088` | 自有健康、调试及转发入口 | [api-gateway/API.md](../api-gateway/API.md) |

例如：auth 的 `/login` 对应 `/api/auth/login`；patient 的 `/patients` 仍是 `/patients`，不是 `/api/patients`；metric 的 `/reports` 对应 `/metric/reports`。独立 `/report/*` 使用 `REPORT_SERVICE_URL`，默认目标端口 `8084`，仓库没有独立 report-service；不能将它无条件当作 metric 的报告别名。Compose 对该地址另有覆盖，不能与本机默认值混淆。

### WebSocket 与自动文档

- 通知：直连 `ws://localhost:8081/ws/notifications`。
- AI：直连 `ws://localhost:8083/ws/ai-agent`。
- 浏览器当前以 `?token=…` 传 JWT；避免在访问日志、截图或共享链接中暴露真实令牌。握手失败可能在升级后以 `1008` 关闭，不等于 REST JSON 401。
- **Ktor 网关没有 WebSocket 代理**。Docker Nginx 的独立 WS location 是另一层配置，不代表该网关已支持 WS。
- Python FastAPI 默认提供根路径 `/docs`、`/redoc`、`/openapi.json`；业务路由默认前缀 `/api/v1`，自动文档路径不跟此前缀变化。
- Kotlin 服务当前没有可依赖的 Swagger/OpenAPI 路由。旧生成器 README 中的插件功能表不代表已注册这些端点。

## 请求与响应不是全局统一契约

### JSON 字段、日期和多态

- Kotlin 业务 DTO 主要使用 `camelCase`；Python 分割使用 `snake_case`，不提供 camelCase 别名。
- 大多数业务接口返回裸对象或数组，不存在覆盖所有服务的 `{success,data}` 包装。网关自己的错误包装也不代表下游错误格式。
- 分页列表通常是裸数组，没有 `total`；共享代码中的 `*PageResponse` 类型不表示路由已经返回它。
- 日期/时间多为字符串：患者生日使用 `YYYY-MM-DD`，业务时间通常为无时区的 `LocalDateTime` 字符串。不能假设全部是 epoch、UTC 或固定秒/小数精度。网关 `/health` 的 `timestamp` 是 epoch 毫秒，属单独约定。
- `MetricDto` 是多态类型。metric 工具 JSON、POST 解码及数据库 JSON 使用 **`kind`**；普通 metric GET 的 Ktor JSON 使用默认 **`type`**。标签是如 `dto.MetricDto.GeneralMetric` 的完整类名。不能将 GET 的 metrics 原样回传并假设两种判别器等价；完整例子见 [metric API](../metric-service/API.md)。
- 不同 JSON 配置对默认值/null 的省略方式不同。科研 `GeneralMetric.confidence` 允许缺失或 null；消费者不得擅自补成 0、95 或医学置信度。

共享字段定义位于 [shared/src/main/kotlin/dto/](../shared/src/main/kotlin/dto/)；各端点实际接受的类型仍以服务 API 文档及接收代码为准，例如 `POST /analyses` 不是接收最简 `AnalysisResultCreate`。

### HTTP 成功不等于分析成功

1. Python 质量门禁 `FAIL` / `LIMITED` 仍可能返回 HTTP 200，业务状态分别是 `failed` / `limited`。
2. Python batch 只有 `items`，无 `overall_status` 或 `partial_failure`；执行异常中止整批，已生成工件不回滚。
3. metric 受控流水线失败可能返回 HTTP 200，外层 `result` / WS `analysisResult` 是人工复核文本，不是成功的 JSON payload。
4. AI 成功结果的内层 payload 当前以 **JSON 字符串**返回；WS 也不是逐 token 或逐工具进度流。
5. 必须分别检查 HTTP 状态、业务状态、质量门禁、用途与模型信息。`weights_loaded`、PASS、PDF 文件存在和 CI 通过都不是医学正确性证明。

错误体、领域码和 HTTP 映射见各服务文档。部分非法输入或反序列化异常当前返回 500，不能依照一套假想的全局错误码处理。

## 科研分割与兼容性

课程模型仅用于 `research` + `ULTRASOUND`：返回像素面积/外接框/覆盖率和未经临床校准的前景概率，生成 `ULTRASOUND_RESEARCH_REPORT` 非诊断报告。毫米为空，不生成血流、良恶性或临床风险判断。Python manifest 哈希用于完整性追踪，不是可信签名、许可认证或临床资格。

**BREAKING CHANGE：** Python `estimated_size_mm`、Kotlin 高亮 `estimatedSizeMm` 和 `GeneralMetric.confidence` 可空；科研成功外层 confidence 不再固定 95。旧数值型客户端必须适配缺失值，不能补 0 mm 或升级到旧临床式映射。区域像素字段、用途和包哈希的具体消费边界见 [分割 API](../segmentation-service/API.md) 与 [metric API](../metric-service/API.md)。

默认启动不是上述科研部署：metric 远程开关默认关闭，旧本地启发式仍可能输出正式样式 PDF；选择 `torch_unet` 后端也不等于加载真实权重。科研部署步骤见 [操作手册](segmentation-service-operation-manual.md)，医学职责以 [医院 AI 边界政策](hospital-ai-boundary-policy.md) 为准。

## 当前实现缺口

以下事实已在对应模块文档标出，**文档同步没有修复它们**：

- JWT 验签和菜单角色不构成完整医院/患者授权。auth 存在公开 admin 注册及部分仅 JWT 的用户修改/重置等入口；metric 普通直连 CRUD 缺少路由认证。
- `/validate`、部分影像创建/返回值、异构 Map 序列化等存在源码已知问题或未运行验证，不能将所有已注册路由称为已验收功能。
- 网关丢弃 DELETE 请求体，批量删除经网关不可声称已支持；文件/压缩转发也未完整验收。
- 客户端声明的部分患者软删、恢复、批量状态、分配医生 helper 没有相应服务端路由。
- Python 原始影像路径/远程 URL、资源上界及服务鉴权不完整；无任务取消、异步回调、模型热更新或通用重试保证。
- PDF 生成、用户确认、分析落库、报告登记是不同步骤；历史保存不是 Agent checkpoint，现有持久化 DTO 不保留完整科研审计字段。

仅对授权的公开/合成科研数据在受控本地环境中验证；实际医院部署、日志脱敏、跨院隔离、负载、登记恢复及临床效度需独立验收。源码中调试日志可能记录身份/请求或认证信息，不应将当前配置直接用于真实病历。

## 文档维护入口

- 端点、路径映射：[网关路由](../api-gateway/src/main/kotlin/Routing.kt)及各服务 `Routing.kt`。
- Python 请求/响应：[schemas](../segmentation-service/app/schemas/)，运行时 `/openapi.json` 从同一 schema 生成。
- 医疗职责与设计理由：[政策](hospital-ai-boundary-policy.md)、[架构规格](../architecture.md)及模块 `SPEC.md`；规格仍为待人工审阅的 draft，不能因文档整理而自动激活。
- 操作及文档导航：[文档索引](README.md)、[根 README](../README.md)。
