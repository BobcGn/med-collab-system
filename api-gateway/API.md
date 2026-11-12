# api-gateway 当前 API 与转发参考

[模块说明](README.md) · [全局 API 索引](../docs/api-reference.md) · [身份服务 API](../auth-service/API.md) · [患者服务 API](../patient-service/API.md)

本文按 `b556ebf` 工作基线静态核对网关自身路由、16 个方法/前缀转发组合和兜底路由，未启动服务或数据库，未进行转发验收。以 [SPEC](SPEC.md)、[架构](../architecture.md)和[医院 AI 职责政策](../docs/hospital-ai-boundary-policy.md)为边界；网关不拥有患者数据，不执行影像推理、生成报告或实施完整医院资源授权。

## 1. 入口与协议

默认地址 `http://localhost:8088`，监听端口可由 `PORT` 覆盖。地址配置见 [application.conf](src/main/resources/application.conf)、[ServiceConfig](src/main/kotlin/ServiceConfig.kt)，加载顺序见 [Application](src/main/kotlin/Application.kt)。本文的下游路径映射以默认 baseUrl 为准；环境覆盖含路径、跨容器寻址或部署前缀需要单独验证。

- 代理不是统一 DTO API：请求字段、默认值、返回 DTO、业务错误由下游拥有，详见[身份 API](../auth-service/API.md)、[患者 API](../patient-service/API.md)及[全局索引](../docs/api-reference.md)。**没有全局 `success/data` 封装**。
- 代理仅注册 **GET、POST、PUT、DELETE**。工具函数虽有 PATCH body 分支，[CORS](src/main/kotlin/HTTP.kt)也允许 PATCH/OPTIONS，不等于存在 PATCH 业务代理；未匹配且未被 CORS/认证等中间件提前处理的请求进入兜底，不能据 CORS 推断接口支持。
- 没有 WebSocket 代理。通知需直连 auth 的 `/ws/notifications`，AI WebSocket 也需直连 metric；不要使用网关业务前缀构造 WS URL。
- 网关自身 JSON 使用默认 kotlinx JSON [配置](src/main/kotlin/Serialization.kt)。下游响应按字节转发，不用该配置重新序列化业务 DTO。

## 2. 网关自身端点

来源：[Routing](src/main/kotlin/Routing.kt)、[ResponseModels](src/main/kotlin/ResponseModels.kt)、[Monitoring](src/main/kotlin/Monitoring.kt)。这些端点均公开，没有 JWT 要求。

| 方法与路径 | 请求字段 | 源码响应 / 状态码 |
| --- | --- | --- |
| `GET /health` | 无 | 200 `HealthResponse`：`status: String = "UP"`（路由填值）、`service: String = "api-gateway"`（路由填值）、`timestamp: Long`（当前 epoch 毫秒）；不验证任一下游或数据库健康 |
| `GET /test-jwt` | 可带 `Authorization`，无 body/查询参数 | 源码拟返回 200 异构 Map：`hasAuthorization: Boolean`、`authorizationHeader: String`（头部前 50 字符；缺省为字符串 `"null"`）；**源码声明，未运行验收** |
| `GET /metrics` | 无 | 200 `text/plain`，固定文本 `Prometheus metrics endpoint`，不是 Prometheus scrape 数据 |
| 任意方法，未匹配路径 | 任意 | 兜底路由返回 404 `StandardResponse`，success=false、code=404、message=`接口不存在`、data=null；CORS 预检等可能先被插件处理，无统一方法不支持的 405 契约 |

`HealthResponse` 三个字段在 DTO 中均为必填字段，没有 DTO 默认值；上表的 UP/service 是路由传入值。时间戳仅此处为 epoch 毫秒，不代表下游日期时间 String 已有统一时区。

### `/test-jwt` 的凭据风险

该端点**不验证 JWT**，有头部不表示有效认证。它会记录**完整 Authorization 头**到服务日志，并把前 50 字符放入拟响应 Map；这是凭据泄露风险，不能安全用于生产。异构 `Map<String, Any>` 的实际序列化未验收，不能把源码的 200 调用描述为已成功响应，更不能通过真实 token 验证文档示例。

Monitoring 虽安装了 Micrometer 和 Prometheus registry，但 `/metrics` 没有调用 registry.scrape()；固定文本不能用于宣称已有可抓取的 Prometheus 指标端点。

## 3. 实际 JWT 检查

[Security](src/main/kotlin/Security.kt)在患者、metric、report 转发路由的 `authenticate("jwt")` 中验证 HMAC256 签名、issuer、audience、有效期，再创建 JWTPrincipal；没有角色、操作者医院、患者归属或数据库账号状态检查，也未在 validate 中额外要求非空 subject。

- `/api/auth` 整个前缀**不做网关 JWT 验证**，包括刷新、组织目录和用户管理路径；由 auth 下游区分公开与受保护端点，不是整个 auth 服务无需认证。
- `/patients`、`/metric`、`/report` 前缀全部要求网关 JWT，即使某个下游端点自身公开也不自动例外。下游仍可能再验 JWT 或执行业务检查。
- JWT 拒绝通常为中间件 401，未自定义统一 JSON challenge；不要要求它输出 StandardResponse 或下游 `{code,message}`。
- `ServiceConfig.isPublicPath` 中的 login/register 白名单不用于当前 Routing；`forwardToService` 的 `requireAuth` 参数也没有在函数内部实施验证，实际保护来自外层 authenticate 块。

**完整 JWT 不等于医院授权**：网关既不把 token 与 hospitalId/patientId 绑定，也不修复下游授权缺口。当前有直连路径，不应把网关验签理解为全系统唯一安全边界。

## 4. 服务注册与路径映射

来源：[ServiceConfig](src/main/kotlin/ServiceConfig.kt)、[配置](src/main/resources/application.conf)、[Routing 的 forwardToService](src/main/kotlin/Routing.kt)。默认注册为：

| 服务键 | 网关前缀 | 默认 baseUrl / 环境覆盖 | 实际映射 |
| --- | --- | --- | --- |
| `auth` | `/api/auth` | `http://localhost:8081` / `AUTH_SERVICE_URL` | 剥 `/api/auth`，剩余路径原样交给 auth；如 `/api/auth/users` → `/users` |
| `patient` | `/patients` | `http://localhost:8082` / `PATIENT_SERVICE_URL` | 先剥前缀再用特殊逻辑保留/补回 `/patients`；如 `/patients/{id}` → `/patients/{id}` |
| `metric` | `/metric` | `http://localhost:8083` / `METRIC_SERVICE_URL` | 剥 `/metric`，如 `/metric/health` → `/health` |
| `report` | `/report` | `http://localhost:8084` / `REPORT_SERVICE_URL` | 剥 `/report`，独立转向 8084；不是 metric 别名 |

`/report` 是当前注册的占位转发目标；本仓库没有由该映射自动变成可用的独立 report 服务。metric 内的报告接口应按 metric 自身契约经 `/metric` 访问，不能把 `/report` 视为它的同义前缀。8084 是否有另行部署的服务，本文未验收。

路径转换只基于字符串 substring/拼接，不做下游业务路由发现。默认 `/patients` 根转发至下游 `/patients`；其他前缀根转发至对应 baseUrl 根。查询字符串以原 queryString 追加，不重命名参数、不新增默认值、不做医院筛选。

举例（仅映射推导，不是已成功请求）：

| 网关请求 | 默认目标 |
| --- | --- |
| `POST /api/auth/login` | `POST http://localhost:8081/login` |
| `GET /api/auth/hospitals?includeInactive=true` | `GET http://localhost:8081/hospitals?includeInactive=true` |
| `GET /patients?hospitalId=HOSPITAL_PLACEHOLDER&page=0&size=20` | `GET http://localhost:8082/patients?hospitalId=HOSPITAL_PLACEHOLDER&page=0&size=20` |
| `GET /patients/statistics` | `GET http://localhost:8082/patients/statistics` |
| `GET /metric/health` | `GET http://localhost:8083/health`（仍须网关 JWT） |
| `GET /report/health` | `GET http://localhost:8084/health`（仅注册映射，未证明目标服务存在） |

Routing 的四个入口前缀是写死的，配置的 pathPrefix 则用于截取长度；修改配置并不会自动重建路由，不能假设任意前缀覆盖均正确。baseUrl 带路径、结尾斜杠和患者特殊字符串逻辑的组合也未作端到端验证。

### 完整代理方法清单

`{...}` 为剩余路径 tailcard，不表示下游一定有该业务接口；业务请求字段/默认/DTO/状态码均由上表链接的下游文档定义。网关正常分支保留下游 HTTP 状态码，转发异常为 502；JWT 保护列为“是”时另可能在转发前被 401 拒绝。

| 方法与网关路径 | 网关 JWT | body 处理 |
| --- | --- | --- |
| `GET /api/auth/{...}` | 否，下游决定 | 不转发 body |
| `POST /api/auth/{...}` | 否，下游决定 | 转发 body |
| `PUT /api/auth/{...}` | 否，下游决定 | 转发 body |
| `DELETE /api/auth/{...}` | 否，下游决定 | **丢弃 body** |
| `GET /patients/{...}` | 是 | 不转发 body |
| `POST /patients/{...}` | 是 | 转发 body |
| `PUT /patients/{...}` | 是 | 转发 body |
| `DELETE /patients/{...}` | 是 | **丢弃 body** |
| `GET /metric/{...}` | 是 | 不转发 body |
| `POST /metric/{...}` | 是 | 转发 body |
| `PUT /metric/{...}` | 是 | 转发 body |
| `DELETE /metric/{...}` | 是 | **丢弃 body** |
| `GET /report/{...}` | 是 | 不转发 body |
| `POST /report/{...}` | 是 | 转发 body |
| `PUT /report/{...}` | 是 | 转发 body |
| `DELETE /report/{...}` | 是 | **丢弃 body** |

## 5. 转发头、请求体与响应处理

来源：[GatewayUtil](src/main/kotlin/GatewayUtil.kt)。

### 请求

- 复制原 HTTP 方法和请求头，跳过 `Host/Content-Length/Connection/Keep-Alive`（大小写不敏感）；Authorization、Content-Type 等通常保留。
- 追加 `X-Gateway: med-collab-gateway`，但未据此建立可信身份，未统一生成 X-Forwarded-For 等链路上下文。
- 实际已注册的代理中，只有 POST/PUT 将 `receiveChannel()` 设置为下游 body；**DELETE 请求体完全丢弃**。因此 auth 的 `DELETE /users/batch`（网关 `/api/auth/users/batch`）和 patient 的 `DELETE /patients/batch` 所需裸 ID 数组无法可靠透传；下游可能因缺 body 失败，不代表批量删除已被支持或验收。
- 网关不校验业务 DTO，也不新增分页上限、排序或 total。合法 JSON 仅表示语法成立，不表示下游鉴权或业务成功。

### 响应

- CIO client 设置 `expectSuccess=false`，下游 4xx/5xx 本身不会被自动改写成网关异常；正常转发复制下游状态、允许的响应头及响应体。
- 跳过响应 `Content-Length/Connection/Keep-Alive/Transfer-Encoding` 和一组 `Access-Control-*` 头，CORS 由网关处理。其他头会尝试复制，包括内容相关头；没有统一的业务响应包装。
- 先 `readBytes()` 将整个响应读入内存，再 `respondBytes`，不是已证明的流式大文件代理。
- 只要存在 Content-Encoding 头就按 **gzip** 解压，不区分实际编码值；原 Content-Encoding 头又未被过滤。编码不是 gzip、解压与头部不一致、文件下载/大响应等均可能有兼容问题；**压缩响应与文件下载仅有源码实现，未运行验收，不宣称已可正确透传**。

[HTTP 配置](src/main/kotlin/HTTP.kt)当前为 anyHost + allowCredentials，允许 Authorization、Content-Type 等常见请求头；这只是开发期 CORS 行为，不提供医院隔离，也不等于生产来源策略已验收。

## 6. 网关自有错误 DTO 与验证范围

`StandardResponse<T>` 声明字段：`success: Boolean`、`code: Int`、`message: String`、`data: T?`，均无 DTO 默认值。目前网关自有错误传 `data=null`：

| HTTP | success / code / message | 触发位置 |
| --- | --- | --- |
| 404 | false / 404 / `接口不存在` | 未匹配任何网关路由（不等同下游业务 404） |
| 500 | false / 500 / `服务未注册` | forwardToService 查不到服务键；当前四个固定键已有注册，是防御性分支 |
| 502 | false / 502 / `服务暂时不可用` | 请求/读取响应/解压等转发过程抛出异常；已开始写响应后的失败行为仍需运行验证 |

形状示例（不是实测响应）：

```json
{
  "success": false,
  "code": 502,
  "message": "服务暂时不可用",
  "data": null
}
```

这不是整个系统的错误协议：JWT 401 未用此 DTO，auth/patient 的业务错误或正常 DTO 不会被统一套壳，`/test-jwt` 的异构 Map 也不是 StandardResponse。请求未找到、认证拒绝、下游业务失败和下游不可连接必须分别处理。

本页只验证静态路由、配置与类型之间的对应关系及示例 JSON 语法；没有构建、启动、数据库检查、WS 代理、跨院授权、跨容器地址、压缩或文件下载的运行验收。下游日期时间、裸数组分页、过滤优先级及授权缺口请以各模块 API 为单一事实来源，网关不会替其补齐。
