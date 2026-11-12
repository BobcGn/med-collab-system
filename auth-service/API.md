# auth-service 当前 API 参考

[模块说明](README.md) · [全局 API 索引](../docs/api-reference.md) · [网关规则](../api-gateway/API.md)

本文按 `b556ebf` 工作基线的当前源码静态核对，覆盖本服务的 36 个 REST 路由和 1 个 WebSocket 路由；未启动服务或数据库，示例不是实测响应。边界以 [SPEC](SPEC.md)、[架构](../architecture.md)和[医院 AI 职责政策](../docs/hospital-ai-boundary-policy.md)为准。本服务不提供影像分析或报告生成。

## 1. 地址、格式与认证

- 默认直连：`http://localhost:8081`；以下 REST 路径均相对此地址。端口可由 `PORT` 覆盖，见[配置](src/main/resources/application.conf)。
- 经默认网关：在路径前加 `/api/auth`，例如 `/login` → `http://localhost:8088/api/auth/login`。下游实际仍接收 `/login`，不是 `/api/auth/login`。
- 通知 WebSocket：直连 `ws://localhost:8081/ws/notifications`（TLS 部署用 `wss`）；网关没有 WebSocket 代理。
- JSON 请求使用 `Content-Type: application/json`。没有统一的 `success/data` 外层：响应可以是 DTO、裸数组、Map，异常多为 `{code,message}`，刷新失败另用 `{error}`。
- [JSON 配置](src/main/kotlin/Serialization.kt)忽略未知字段、启用宽松解析和 `coerceInputValues`，`encodeDefaults=false`。下表列出 DTO 全部字段；有默认值的响应字段可能省略，不能把声明误认为每次都会输出的键。
- 时间字段均为 `String`：数据库日期时间和通知通常由 `LocalDateTime.toString()` 等产生，没有统一时区约定，不能假设是带 `Z` 的 UTC 时间。`updatedAt` 可为 null，仓储更新未主动维护该时间。
- 分页返回裸 `UserInfo[]`，没有 `total/page/size` 外层；未设置页大小上限或稳定排序。未分页目录、搜索、批量接口也没有统一数量上限。

### 实际认证边界

文中 **JWT** 表示受 `authenticate("jwt")` 保护，携带 `Authorization: Bearer <JWT占位>`。[Security](src/main/kotlin/Security.kt)和[JwtUtil](../shared/src/main/kotlin/utils/JwtUtil.kt)验证 HMAC256 签名、issuer、audience、有效期；并不对每次请求检查账号是否冻结/删除，也不验证操作者的医院范围。

**管理员**表示在 JWT 之外，service 按 token subject 查询数据库并要求 `role == "admin"`；此检查本身没有额外拒绝冻结管理员。实际只有专用角色/冻结/解冻接口和医院、科室写操作使用此管理员检查。不要把注释中的“管理员功能”、前端隐藏按钮或完整 JWT 当成资源授权。

当前必须留意的缺口（见 [UserService](src/main/kotlin/service/UserService.kt)、[UserRepository](src/main/kotlin/database/repository/UserRepository.kt)）：

- `/register` 公开且接受 `admin`，没有管理员邀请或审批检查。
- 通用用户更新、`/info`、他人密码重置、单个/批量删除仅要求 JWT，没有“本人或管理员”检查；通用更新还能写 `role/isFrozen`，绕过专用管理接口的检查。
- 用户读取、搜索、统计及批量读取没有操作者医院/科室范围检查；`canAccessUserData` 等辅助函数未接入这些路由。
- 密码实际使用 SHA-256 摘要，不是 BCrypt。通用更新中 `passwordHash` 长度小于 40 才按明文验证强度并摘要，否则直接按已有摘要写入；这不是推荐的凭据接口。

## 2. 请求与响应类型

来源：[UserDto](../shared/src/main/kotlin/dto/UserDto.kt)、[HospitalDto](../shared/src/main/kotlin/dto/HospitalDto.kt)、[DepartmentDto](../shared/src/main/kotlin/dto/DepartmentDto.kt)。`?` 表示可为 null；“默认 null”表示可省略。Map 请求的其他键不会参与业务处理。

### 2.1 账号请求

| 类型 | 字段（类型、必填/默认） |
| --- | --- |
| `UserRegister` | `role: String` 必填；`password: String` 必填；`fullName: String` 必填；`hospitalId: String? = null`；`deptCode: String? = null`；`username: String? = null`（接收但不用于自定义用户名） |
| `UserLogin` | `username: String`、`password: String`，均必填 |
| `UserUpdate` | `id: String? = null`（路由用路径 ID 覆盖）；`username/fullName/passwordHash/role: String? = null`；`isFrozen: Boolean? = null` |

注册实际规则：

- `role` 去首尾空白并转小写，只允许 `admin/doctor/nurse/receptionist`；姓名去首尾空白后不得为空。
- 密码至少 8 位，含小写、大写字母及数字。修改/重置密码使用同一强度规则；没有实际执行“新旧密码必须不同”的检查。
- `admin` 不绑定医院或科室，提交的组织字段不用于创建。
- 非管理员必须提供去空白后的非空 `hospitalId`。`doctor` 必须提供有效的激活科室及激活医院；`nurse/receptionist` 科室可缺省，若提供则同样校验医院+科室。**科室缺省时没有单独验证医院存在且激活**。空科室或字符串 `"null"` 视为无科室。
- 用户 ID 为 UUID，`userSeq` 为当前毫秒时间戳的后 6 位，没有唯一性/并发可靠性承诺。用户名由[实体](src/main/kotlin/database/entity/UserEntity.kt)计算：无医院为 `ADMIN-{userSeq}`，有医院为 `{DR|NR|RC|USR}-{hospitalId}[-{deptCode}]-{userSeq}`，不存储请求中的自定义 `username`。
- `UserUpdate` 的 null 字段不更新，不能用于清空字段。仓储实际只写 `fullName/passwordHash/role/isFrozen`，不写 `username`；通用更新不校验角色枚举或姓名非空，也不修改组织归属。

以下仅为请求形状示例，所有占位字符串均非真实凭据，也不保证通过业务校验：

```json
{
  "role": "doctor",
  "hospitalId": "医院ID占位",
  "deptCode": "科室ID占位",
  "password": "密码占位（非真实凭据）",
  "fullName": "姓名占位"
}
```

```json
{
  "username": "账号占位",
  "password": "密码占位（非真实凭据）"
}
```

### 2.2 用户响应

`UserInfo`（对外不含密码或摘要）：

| 字段 | 类型 / 声明默认 |
| --- | --- |
| `id` | String，用户 UUID |
| `hospitalId`、`deptCode` | String? |
| `hospitalName`、`deptName` | String?，默认 null；部分查询在名称缺失时回退到组织 ID |
| `userSeq` | String |
| `username` | String?，实体计算值 |
| `fullName`、`role` | String |
| `createdAt` | String |
| `updatedAt` | String? |
| `isDeleted`、`isFrozen` | Boolean，默认 false，序列化时默认值可省略 |

- `RegisterResponse`：`user: UserInfo`、`message: String`（源码填入“注册成功”），不含 token。
- `LoginResponse`：`user: UserInfo`、`token: String`，不含独立 refresh token。签发 JWT 的 subject 为用户 ID，附有 `username/role` 及标准 `iss/aud/iat/exp`，没有医院授权上下文。
- 大部分用户读取排除 `isDeleted=true`，但不排除冻结用户；批量读取忽略不存在或查询失败的 ID，不返回逐项错误。

### 2.3 组织请求与响应

| 类型 | 全部字段 |
| --- | --- |
| `HospitalCreate` | `id: String`、`name: String`，均必填 |
| `HospitalUpdate` | `name: String? = null`、`isActive: Boolean? = null` |
| `DepartmentCreate` | `id: String`、`hospitalId: String`、`name: String`，均必填 |
| `DepartmentUpdate` | `name: String? = null`、`isActive: Boolean? = null` |
| `HospitalInfo` | `id/name: String`、`isActive: Boolean`、`createdAt: String`、`updatedAt: String?` |
| `DepartmentInfo` | `id/hospitalId/name: String`、`isActive: Boolean`、`createdAt: String`、`updatedAt: String?` |

组织更新仅写非 null 字段；不能用更新修改 ID 或科室所属医院。创建默认激活，删除只是设 `isActive=false`，不级联清理用户或科室。科室表以 `id` 为主键，不是 `(hospitalId,id)` 联合主键；不要假设不同医院可以重复使用同一个科室 ID。名称、ID 的长度限制来自[医院表](src/main/kotlin/database/table/Hospitals.kt)和[科室表](src/main/kotlin/database/table/Departments.kt)（ID 20、名称 100 字符），不代表路由有完整参数校验。

## 3. REST 路由

本节状态码是源码正常分支或明确业务失败分支；所有 JWT 路由另可能在中间件阶段返回 401，具体业务异常映射见第 5 节。Map 响应写法是字段声明，不是已成功请求的证据。

### 3.1 健康、注册、登录与刷新（公开）

来源：[Routing](src/main/kotlin/Routing.kt)、[UserService](src/main/kotlin/service/UserService.kt)。

| 方法与路径 | 输入 | 源码响应与检查 |
| --- | --- | --- |
| `GET /health` | 无 | 200，`{status: "ok"}`；不检查数据库或其他服务 |
| `POST /register` | `UserRegister` | 201 `RegisterResponse`；规则见 2.1。角色/组织/密码错误 400，空姓名 400；公开允许注册 admin |
| `POST /login` | `UserLogin` | 200 `LoginResponse`；不存在/已删除 404，冻结 403，密码错误 401；用输入账号的分段信息查找记录并验证密码 |
| `POST /refresh` | 无需 JSON body；读取 `Authorization: Bearer <现有JWT占位>` | 200 `{token: String}`；无效/过期 token 或用户查询失败在 try 内返回 401 `{error: "刷新token失败: 具体原因"}`；完全缺少 Authorization 在 try 外抛异常，按 500 处理 |

刷新路由虽然不在 JWT 中间件内，`getUserIdFromToken` 仍使用完整 verifier：**过期 token 不能刷新**。头部处理只是去掉区分大小写的 `Bearer ` 前缀后验证剩余字符串，并未强制该前缀存在。不存在独立 refresh token、撤销或 token 轮换记录。刷新读取当前未删除用户并重新签发，不拒绝 `isFrozen=true`；旧 token 不会因刷新自动失效。

### 3.2 公开组织目录

来源：[Routing](src/main/kotlin/Routing.kt)、[HospitalRepository](src/main/kotlin/database/repository/HospitalRepository.kt)、[DepartmentRepository](src/main/kotlin/database/repository/DepartmentRepository.kt)。

| 方法与路径 | 查询参数 | 源码响应 |
| --- | --- | --- |
| `GET /hospitals` | `includeInactive: Boolean = false`，按字符串 `toBoolean()` 解析 | 200 `HospitalInfo[]`；默认仅激活，true 时包含停用 |
| `GET /hospitals/{id}` | 无 | 200 `HospitalInfo`，可返回停用医院；不存在 404 `{error: "医院不存在"}` |
| `GET /hospitals/{hospitalId}/departments` | `includeInactive: Boolean = false` | 200 `DepartmentInfo[]`；仅按科室所属医院和可选激活状态筛选，不先验证医院是否存在/激活 |
| `GET /departments/{id}` | 无 | 200 `DepartmentInfo`，可返回停用科室；不存在 404 `{error: "部门不存在"}` |

这些读取没有 JWT 要求；停用不等于目录数据不可公开读取。

### 3.3 用户读取（均为 JWT，无资源范围检查）

| 方法与路径 | 输入 / 默认 | 源码响应与查询语义 |
| --- | --- | --- |
| `GET /user` | token subject | 200 `UserInfo`；当前用户不存在/已删除 404 |
| `GET /users/{id}` | 路径用户 ID | 200 `UserInfo`；不存在/已删除 404 |
| `POST /users/batch` | body 为裸 `String[]`，例如 `["用户ID占位"]`，不是带 userIds 键的对象 | 200 `UserInfo[]`；逐 ID 查询，遗漏失败项 |
| `GET /users` | `page: Int = 0`、`size: Int = 20`、`role: String?` | 200 `UserInfo[]`；role 精确匹配，page 从 0 开始；负 page 或 size≤0 返回空数组；不可解析整数回退默认值 |
| `GET /users/search` | `keyword: String` 必须出现，可为空 | 200 `UserInfo[]`；对 `fullName/hospitalId/deptCode/userSeq` 做包含式 SQL LIKE，不是对计算用户名列搜索；无分页；缺 keyword 为普通异常，500 |
| `GET /users/doctors/{hospitalId}/{deptCode}` | 路径医院/科室 | 200 `UserInfo[]`；验证激活医院+科室，再精确筛 `role=doctor`；组织无效 400/4003 |
| `GET /users/nurses/{hospitalId}/{deptCode}` | 同上 | 200 `UserInfo[]`；筛 `role=nurse`；组织无效 400/4003 |
| `GET /users/receptionists/{hospitalId}/{deptCode}` | 同上 | 200 `UserInfo[]`；筛 `role=receptionist`；组织无效 400/4003 |
| `GET /users/hospital/{hospitalId}` | 路径医院 ID | 200 `UserInfo[]`；要求医院激活，否则 400/4001 |
| `GET /statistics` | `hospitalId: String?`，省略为所有未删除用户 | 200，异构 `Map<String, Any>`：`total: Int`、`deleted: Int`、`byRole: Map<String, Int>`；**源码声明，未运行验收** |

`/statistics` 先过滤掉已删除用户再计算 `deleted`，所以源码逻辑下该值为 0，不是已删除用户总数；`byRole` 只包含实际出现的角色键。异构 Map 的 JSON 序列化未做运行验收，不把该表视为可用性证明。

### 3.4 用户修改与删除

无 body 的行不要发送业务字段；其余 Map 字段均按字符串读取，`/info` 允许字符串或 null。

| 方法与路径 | 实际鉴权 | 请求 body | 源码正常响应 / 额外行为 |
| --- | --- | --- | --- |
| `PUT /users/{id}` | 仅 JWT | `UserUpdate` | 200 `{success: Boolean}`；路径覆盖 body.id；不存在 404。可写角色、冻结、摘要等，但 username 不落库 |
| `PUT /password` | JWT，目标是 token subject | `oldPassword: String`、`newPassword: String` 必填 | 200 `{success: Boolean}`；旧密码错误 400/5002，弱新密码 400/5001 |
| `PUT /users/{id}/password` | **仅 JWT，不检查管理员/本人/旧密码** | `newPassword: String` 必填 | 200 `{success: Boolean}`；目标不存在 404，新密码弱 400/5001 |
| `PUT /username` | JWT，目标是 token subject | `newUsername: String` 必填 | 200 `{success: Boolean}`；按账号分段检查占用，已存在 409；**仓储未写 username，即使 true 也不能据此认定改名成功** |
| `PUT /users/{id}/role` | JWT + 数据库管理员检查 | `newRole: String` 必填 | 200 `{success: Boolean}`；必须精确为 `admin/doctor/nurse/receptionist`，不做大小写归一化；无权限 403，角色无效 400 |
| `PUT /users/{id}/freeze` | JWT + 数据库管理员检查 | 无 | 200 `{success: Boolean}`；不能冻结自己，400/3002（复用“不能删除自己”异常）；目标不存在 404 |
| `PUT /users/{id}/unfreeze` | JWT + 数据库管理员检查 | 无 | 200 `{success: Boolean}`；目标不存在 404 |
| `PUT /users/{id}/info` | **仅 JWT** | `fullName: String?`、`role: String?`，省略均按 null，不更新 | 200 `{success: Boolean}`；不验证角色枚举、操作者范围或姓名非空；目标不存在 404 |
| `DELETE /users/{id}` | **仅 JWT** | 无 | 200 `{success: Boolean}`；软删除；不存在/已删除 404；没有禁止删除自身的检查 |
| `DELETE /users/batch` | **仅 JWT** | 裸 `String[]` | 200 `{successCount: Int}`；逐项软删除，捕获并跳过单项异常，不返回失败明细 |
| `POST /validate` | JWT（请求头），另收 body | `token: String` 必填 | 对已认证且正常解析的请求，service **恒抛异常，返回 500/code=500**；没有可用的用户成功响应 |

Map 请求缺少上述必填键时路由一般抛普通 `Exception`，按 500 处理，不是统一 400。通用更新/`/info` 不得被当作经过角色或医院授权的管理接口。`/validate` 的声明返回类型虽是 `UserInfo`，实现明确拒绝直接验证 token，不能作为 token introspection 使用。

**网关限制：**`DELETE /users/batch` 依赖 DELETE body，当前网关不转发该 body，经网关调用不可靠；见[转发规则](../api-gateway/API.md)。

### 3.5 医院和科室写操作（均为 JWT + 数据库管理员检查）

来源：[HospitalService](src/main/kotlin/service/HospitalService.kt)及上述组织仓储。无权限为 403/3001。

| 方法与路径 | body | 源码正常响应 / 明确失败 |
| --- | --- | --- |
| `POST /hospitals` | `HospitalCreate` | 201 `{hospitalId: String}`；ID 已存在（包括停用）400/4001 |
| `PUT /hospitals/{id}` | `HospitalUpdate` | 200 `{success: true}`；不存在 404 `{error: "医院不存在"}` |
| `DELETE /hospitals/{id}` | 无 | 200 `{success: true}`，停用；不存在同上 404 |
| `POST /departments` | `DepartmentCreate` | 201 `{departmentId: String}`；医院不存在/未激活 400/4003；同医院同 ID 已存在 400/4002；其他主键冲突可能进入通用 500 |
| `PUT /departments/{id}` | `DepartmentUpdate` | 200 `{success: true}`；不存在 404 `{error: "部门不存在"}` |
| `DELETE /departments/{id}` | 无 | 200 `{success: true}`，停用；不存在同上 404 |

### 3.6 定向通知 REST（仅 JWT）

`POST /notifications/send`

- body 为 `Map<String,String>`：`userId/title/content` 必须出现；`type` 可省略，默认 `"system"`。title/content/type 会 trim，但空字符串未被拒绝，type 传空不会回退默认值。
- 不检查管理员、目标用户是否存在或双方医院。senderId 来自 token subject，senderName 取 token 的 username，缺失则用 senderId；通知 priority 固定 `"normal"`，ID 为 UUID，createdAt 为本机当前日期时间字符串。
- 200 `{success: true}` 只表示执行了进程内发送尝试：**目标离线、无连接或发送失败清理连接也可返回 success**，没有送达确认、离线队列或持久化。此定向通知不加入公告历史。
- 接收方得到 `NotificationSocketEnvelope`，`type` 为请求 type，`message` 为 content，`notification` 为新通知；类型见下一节。缺少必填键按普通异常 500 处理。

## 4. 通知 WebSocket

来源：[Routing](src/main/kotlin/Routing.kt)、[SystemNotificationHub](src/main/kotlin/SystemNotificationHub.kt)、[WebSocket DTO](../shared/src/main/kotlin/dto/WebSocketDto.kt)、[WebSockets 配置](src/main/kotlin/WebSockets.kt)。

### 4.1 连接与实际权限

`WS /ws/notifications?token=<JWT占位>` 支持 query token，也支持 Authorization Bearer；非空 query token 优先于请求头。query token 可能进入 URL 日志，应视为敏感凭据，本文不提供真实 token。

连接进入处理器后，缺少/无效/过期 token 以关闭码 **1008**、原因 `invalid token` 关闭；subject 缺失则 1008 `invalid user`，不是业务 REST 错误 DTO。取用户信息失败时仍回退到 token 的角色和用户名，不会因此硬性拒绝连接；未统一拒绝冻结/删除账号。连接角色在建连时确定，后续 publish 不重新查询当前角色。

注册连接后立即发 `snapshot`。同一用户可有多个连接。公告对**所有连接广播，不按医院分区**；history 是当前进程内最近 100 条公告（新在前），不持久化、不跨进程共享；重启丢失。定向 REST 通知不进入该历史。

传输层设置 pingPeriod=20 秒、timeout=20 秒、maxFrameSize=Long.MAX_VALUE；应用 JSON `ping` 是另一个命令。非文本 frame 被业务循环忽略。

### 4.2 消息字段

| 类型 | 全部字段 / 默认 |
| --- | --- |
| `NotificationSocketCommand` | `type: String` 必填；`title/content/priority: String? = null` |
| `SocketUserContext` | `userId: String`；`username/displayName/role: String? = null` |
| `SystemNotificationMessage` | `id/title/content/createdAt/senderId/senderName: String`；`priority: String = "normal"` |
| `NotificationSocketEnvelope` | `type: String`；`message: String? = null`；`notification: SystemNotificationMessage? = null`；`notifications: SystemNotificationMessage[] = []`；`user: SocketUserContext? = null` |

WS 使用独立的 `Json { ignoreUnknownKeys=true }`，默认值字段可能省略。

| 客户端命令 / 条件 | 服务端消息 |
| --- | --- |
| 建连成功 | `type="snapshot"`，message 为 `system notification channel ready`，带 `notifications` 和 `user`；空的默认列表可能省略 |
| `type="ping"`（type 比较前转小写，不 trim） | `type="pong"`，message 为 `ok` |
| `type="publish"` 且建连角色为 admin | title/content trim 后必须非空；priority trim、转小写，缺省/空为 normal，不限制为枚举；创建通知并广播 `type="announcement"`、带 notification，无独立发布 ack |
| 非 admin 发布 | `type="error"`，message 为 `only admin can publish notifications` |
| 发布标题或内容为空 | `type="error"`，message 为 `title and content are required` |
| JSON 无法解析 | `type="error"`，message 为 `invalid payload`；连接继续 |
| 其他 type | `type="error"`，message 为 `unsupported command type` |

命令形状示例（非实测）：

```json
{
  "type": "publish",
  "title": "通知标题占位",
  "content": "通知内容占位",
  "priority": "normal"
}
```

## 5. 状态码与错误字段

来源：[AuthException](../shared/src/main/kotlin/exception/AuthException.kt)、[ExceptionHandling](src/main/kotlin/ExceptionHandling.kt)。业务异常通常输出 `code: Int`、`message: String`，没有 `success/data`。下表列的是已声明的映射，不意味着每个异常都被上述路由实际触发。

| HTTP | code | 默认含义 |
| --- | --- | --- |
| 404 | 1001 | 用户不存在 |
| 409 | 1002 | 用户已存在 |
| 500 | 1003 | 创建用户失败（部分仓储实际抛普通 Exception，走 code=500） |
| 401 | 2001 / 2002 / 2003 | 登录失败 / Token 无效 / Token 过期 |
| 403 | 3001 | 无权限 |
| 400 | 3002 | 不能删除自己（冻结自身也复用） |
| 400 | 4001 / 4002 / 4003 | 医院 ID / 科室 ID / 医院或科室数据无效 |
| 400 | 4003 | `UserIdInvalidException` 实际默认也是 4003，虽源码注释写 4004；不要据注释实现 4004 契约 |
| 400 | 4005 | 角色无效 |
| 400 | 5001 / 5002 / 5003 | 密码强度不足 / 旧密码错误 / 新旧密码相同（最后一项未接入当前密码修改流程） |
| 403 | 6001 / 6002 | 账号禁用 / 冻结（登录明确检查冻结） |
| 400 | 400 | `IllegalArgumentException`，message 为具体参数原因 |
| 500 | 500 | 普通异常/非法状态/未知错误，message 可能带底层异常信息 |

例如业务错误的 JSON **形状**为：

```json
{
  "code": 1001,
  "message": "用户不存在"
}
```

不要混淆三种情况：JWT 中间件的 401 没有本模块自定义统一 JSON challenge；刷新失败和组织单项不存在用 `{error}`；body 缺键、反序列化错误等不保证都走 400，未单独匹配时可能落入通用 500。经网关还可能收到其自己的 404/502，见[网关 API](../api-gateway/API.md)。
