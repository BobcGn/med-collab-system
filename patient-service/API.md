# patient-service 当前 API 参考

[模块说明](README.md) · [全局 API 索引](../docs/api-reference.md) · [网关规则](../api-gateway/API.md) · [身份服务 API](../auth-service/API.md)

本文按 `b556ebf` 工作基线的当前源码静态核对，覆盖 19 个 REST 路由。未启动服务或数据库，示例不是实测响应。职责与安全边界见 [SPEC](SPEC.md)、[架构](../architecture.md)和[医院 AI 职责政策](../docs/hospital-ai-boundary-policy.md)；患者服务不执行影像推理或生成正式报告。

## 1. 地址、格式与实际授权

- 默认直连：`http://localhost:8082`；端口可由 `PORT` 覆盖，见[配置](src/main/resources/application.conf)。
- 默认网关：`http://localhost:8088`，保留患者业务路径，例如直连和网关均为 `/patients/{id}`，不要再拼一层 `/patients`。直连 `/health` 是本服务健康检查；网关 `/health` 属于网关本身，没有通用 `/patients/health` 健康映射。
- 除 `GET /health` 外，全部路由要求 `Authorization: Bearer <JWT占位>`；本服务没有 WebSocket 路由。
- JSON body 使用 `Content-Type: application/json`。没有统一 `success/data` 封装：创建/详情/统计返回 DTO，列表返回裸数组，修改/删除多返回 `{success}` 或 `{successCount}`，业务错误为 `{code,message}`。
- [序列化配置](src/main/kotlin/Serialization.kt)：忽略未知字段、宽松解析、`encodeDefaults=true`、`explicitNulls=false`。默认非 null 值会编码，null 字段通常省略；不能要求响应永远包含所有可空键。
- 业务日期/时间 DTO 使用 `String`，不是 epoch；出生日期由 `LocalDate.parse` 解析（如 `2000-01-01`），就诊时间由 `LocalDateTime.parse` 解析（如 `2025-01-01T09:00:00`）。无统一时区约定，不要默认接受带 `Z`/偏移的时间。修改未主动维护 `updatedAt`。

### JWT 不等于医院授权

[Security](src/main/kotlin/Security.kt)验证 HMAC256 签名、issuer、audience、有效期，不逐请求确认账号实时状态或医院范围。[PatientService](src/main/kotlin/service/PatientService.kt)和[AuthClient](src/main/kotlin/service/AuthClient.kt)的**实际检查**如下，不能按注释中的“主治医生”推断权限：

| 操作 | 路由 / service 实际检查 |
| --- | --- |
| 创建患者 | 路由要求 JWT role 为 `admin/receptionist`；service 再向 auth 读取操作者，要求数据库角色为 `admin/receptionist`；检查目标医生为未删除/未冻结 doctor，且医生医院等于请求 hospitalId |
| 通用更新患者 | 路由要求 JWT role 为 `admin/receptionist`；service 的 `canModifyPatient` 再要求 auth 返回操作者角色为 `admin/receptionist`；若换医生，验证该医生有效 |
| 状态更新、转移 | 路由仅 JWT；service 的 `canModifyPatient` 要求 auth 返回角色为 `admin/receptionist`；**不是主治医生可修改**。转移另验证新医生有效 |
| 详情、所有列表、搜索、最近就诊、统计 | 仅 JWT；未调用资源范围检查 |
| 删除、批量删除、visit、vitals、medical | 仅 JWT，并检查患者记录存在；无额外角色/医院/主治医生授权；visit 改医生也不验证医生有效性 |

创建、通用更新、状态更新、转移均未核对**操作者的医院**，查询操作者角色时也未单独拒绝冻结账号。通用更新/转移未验证新医生与患者同医院或科室；创建只核医生医院，不验证 department 与医生科室匹配。`canModifyPatient` 未使用传入的原主治医生 ID 判断权限。`canAccessPatientData` 虽存在，但未贯穿这些路由；读到指定医院 ID 的数据不表示具备该医院授权。

AuthClient 默认直接访问 `http://localhost:8081`，路由传入原 Bearer token。用户请求失败/解析失败会转为 null：权限判断可能因此失败，医生姓名查询则回退为医生 ID；不是所有 auth 故障都会对外报 502，也不保证附加姓名成功。其批量用户辅助方法发送对象与 auth 的裸数组契约不一致，当前患者路由的姓名补充使用逐用户查询，并非该批量方法。

## 2. 请求 DTO 与字段

来源：[PatientDto](../shared/src/main/kotlin/dto/PatientDto.kt)、[枚举](../shared/src/main/kotlin/enums/PatientEnums.kt)、[Routing 内附属 DTO](src/main/kotlin/Routing.kt)。`?` 表示可为 null；除明确必填字段外，默认 null 的字段可省略。

### 2.1 枚举与 ID

| 类型 | 对外 JSON 值（大小写敏感） |
| --- | --- |
| `Gender` | `"M"`、`"F"` |
| `BloodType` | `"A"`、`"B"`、`"O"`、`"AB"`、`"Unknown"` |
| `Status` | `"Active"`、`"Discharged"`、`"Deceased"` |

路径 `{id}` 是患者记录 UUID；`{patientId}` / body.patientId 是医院病历号，二者不可混用。状态在数据库中以小写保存，对外 DTO 则转换为上述枚举名；统计 `byStatus` 键沿用数据库值，通常是小写。

### 2.2 创建 `CreatePatient`

| 字段 | 类型 | 必填 / 默认与行为 |
| --- | --- | --- |
| `hospitalId` | String | 必填；须等于目标医生医院 |
| `patientId` | String | 必填，医院病历号；重复检查包含已软删除记录 |
| `name` | String | 必填 |
| `gender` | Gender | 必填 |
| `birthDate` | String? | 默认 null，日期 |
| `phone`、`idCard` | String? | 默认 null |
| `department` | String | 必填，不会自动从医生科室推导 |
| `attendingDoctorId` | String | 必填，验证未删除/未冻结 doctor |
| `allergies`、`medicalHistory`、`familyHistory`、`chiefComplaint` | String? | 默认 null |
| `heightCm` | Int? | 默认 null |
| `weightKg` | Float? | 默认 null |
| `bloodType` | BloodType | 默认 `Unknown` |
| `status` | Status | 默认 `Active` |
| `firstVisitDate` | String? | 默认 null；创建时同时用它设置 firstVisitDate 和 lastVisitDate，缺省则均为 null，不自动设为当前时间 |

请求形状示例（虚构占位，未运行）：

```json
{
  "hospitalId": "医院ID占位",
  "patientId": "病历号占位",
  "name": "患者姓名占位",
  "gender": "F",
  "department": "科室占位",
  "attendingDoctorId": "医生UUID占位",
  "bloodType": "Unknown",
  "status": "Active"
}
```

### 2.3 通用更新 `UpdatePatient`

**body.id 仍为必填 String**，即使路径已有 ID，反序列化仍先要求该字段。service 随后用路径 ID 覆盖它；省略 body.id 不能被视为合法请求。

| 字段 | 类型 / 默认 |
| --- | --- |
| `id` | String，必填，随后被路径 ID 覆盖 |
| `name`、`phone`、`idCard`、`department`、`attendingDoctorId` | String? = null |
| `allergies`、`medicalHistory`、`familyHistory`、`chiefComplaint` | String? = null |
| `heightCm` | Int? = null |
| `weightKg` | Float? = null |
| `bloodType` | BloodType? = null |
| `status` | Status? = null |
| `lastVisitDate` | String? = null，按 LocalDateTime 解析 |

没有 hospitalId、patientId、gender、birthDate、firstVisitDate 更新字段。所有修改只写非 null 值，null/省略都表示保留，不能用 null 清空。空字符串不等于 null，未建立通用非空或医学范围验证。

```json
{
  "id": "患者UUID占位",
  "phone": "电话占位",
  "status": "Active"
}
```

### 2.4 附属修改与批量请求

| 路由使用的类型 | 全部字段 |
| --- | --- |
| 状态 Map | `status: String` 必填，调用 `Status.valueOf`；必须精确使用上述枚举名 |
| `PatientVisitUpdate` | `chiefComplaint/department/attendingDoctorId: String? = null`；无 id 字段 |
| `PatientVitalsUpdate` | `heightCm: Int? = null`、`weightKg: Float? = null`、`bloodType: BloodType? = null` |
| `PatientMedicalUpdate` | `allergies/medicalHistory/familyHistory: String? = null` |
| `PatientTransferRequest` | `newDepartment: String`、`newAttendingDoctorId: String`，均必填 |
| 批量删除 | 裸 `String[]`，例如 `["患者UUID占位"]`，不是包在对象内 |

存储限制见[Patients 表](src/main/kotlin/database/table/Patients.kt)：hospitalId/patientId/department 为 50 字符，name 为 100，phone 为 20，idCard 为 30，医生 ID 为 36；heightCm 会转换成 Short，weightKg 写 decimal(5,2)。这些不是已实现的请求校验或安全医学取值范围；越界、日期错误等可能在仓储阶段转为业务失败。

## 3. 响应 DTO

来源：[PatientDto](../shared/src/main/kotlin/dto/PatientDto.kt)、[PatientEntity](src/main/kotlin/database/entity/PatientEntity.kt)。以下为完整声明，不是响应采样；null 字段可被序列化省略。

### `PatientInfo`（创建及详情）

| 字段 | 类型 / 默认 |
| --- | --- |
| `id`、`hospitalId`、`patientId`、`name` | String |
| `gender` | Gender |
| `birthDate`、`phone`、`idCard` | String? = null |
| `department`、`attendingDoctorId` | String |
| `attendingDoctorName` | String? = null |
| `allergies`、`medicalHistory`、`familyHistory`、`chiefComplaint` | String? = null |
| `heightCm` | Int? = null |
| `weightKg` | Float? = null |
| `bloodType` | BloodType = Unknown |
| `status` | Status = Active |
| `firstVisitDate`、`lastVisitDate` | String? = null |
| `createdAt` | String |
| `updatedAt` | String? = null |
| `isDeleted` | Boolean = false |

创建返回仓储 DTO，未补 `attendingDoctorName`；详情读取会尝试从 auth 补姓名，失败时回退医生 ID。

### `PatientListItem`（所有患者列表）

| 字段 | 类型 / 默认 |
| --- | --- |
| `id`、`hospitalId`、`patientId`、`name` | String |
| `gender` | Gender |
| `age` | Int? = null，由出生日期和服务器当前日期计算 |
| `department`、`attendingDoctorId` | String |
| `attendingDoctorName` | String? = null，service 尝试补姓名，失败回退医生 ID |
| `bloodType` | BloodType |
| `status` | Status |
| `lastVisitDate` | String? = null |
| `createdAt` | String |

列表不含完整出生日期、电话、身份证或病史。`PatientPageResponse`、`PatientSearchParams` 虽存在于 shared，但**当前路由没有使用**：不能据此要求列表返回 total，或推断通用查询支持其中全部字段。

### `PatientStatistics`

- `total: Int`：所选范围的未删除患者数。
- `byStatus/byDepartment/byBloodType: Map<String,Int>`：按数据库状态、科室、血型分组，仅包含出现的键；血型 null 分组为 `Unknown`，状态键通常为 `active/discharged/deceased`。
- `recentPatients: PatientListItem[]`：同一范围内按 lastVisitDate 降序最多 10 条，**没有最近 7 天的时间过滤**；医生名同样尝试补充。null 日期的排序由数据库决定，不保证相同日期记录的次序。

其他写响应为同类型值 Map：`{success: Boolean}` 或 `{successCount: Int}`，不是新患者 DTO。

## 4. 全部路由、查询与检查

来源：[Routing](src/main/kotlin/Routing.kt)、[PatientService](src/main/kotlin/service/PatientService.kt)、[PatientRepository](src/main/kotlin/database/repository/PatientRepository.kt)、[AuthClient](src/main/kotlin/service/AuthClient.kt)。本节的成功状态码是源码分支，不代表运行验收。除健康外，均另有 JWT 401 和第 5 节通用错误的可能。

### 4.1 健康检查（公开）

`GET /health` 无 body/查询参数。执行数据库事务，统计全部患者实体（包括软删除）：

| 分支 | HTTP | `PatientHealthResponse` 字段 |
| --- | --- | --- |
| 数据库读取成功 | 200 | `status="ok"`、`service="patient-service"`、`database="connected"`、`patientCount: Long` |
| 数据库异常 | 500 | `status="error"`、`service="patient-service"`、`database="disconnected"`、`error: String?`（异常消息） |

patientCount/error 的 DTO 默认均为 null，空值可能省略。此路由不验证 auth 可用性或跨院权限。

### 4.2 基本记录写操作

| 方法与路径 | body | 源码正常响应 / 实际检查 |
| --- | --- | --- |
| `POST /patients` | `CreatePatient` | 201 `PatientInfo`；JWT role + auth 操作者角色均须 admin/receptionist；医院+病历号已存在 409；医生无效 400/1202，医生医院不符 400/1201，无权限 403/1301 |
| `PUT /patients/{id}` | `UpdatePatient`，body.id 必填 | 200 `{success: Boolean}`；JWT role + auth 操作者角色均须 admin/receptionist；患者不存在 404；新医生无效 400/1202；不核操作者医院或新医生医院 |
| `DELETE /patients/{id}` | 无 | 200 `{success: Boolean}`，软删除；仅 JWT；记录不存在 404，无角色或操作者范围检查 |
| `DELETE /patients/batch` | 裸 `String[]` | 200 `{successCount: Int}`；仅 JWT，逐项软删除，吞掉单项异常，不返回失败列表；无事务级全成全败保证 |

软删除行为并不一致：医院+病历号详情、列表、搜索、统计排除已删除记录，但按 UUID 的 `findPatientById` **不排除已删除记录**，所以详情仍可返回 `isDeleted=true`，后续修改或重复删除仍可能执行；创建的重复检查也包含软删除记录。没有对外恢复接口。

批量 DELETE 依赖 body，**当前网关丢弃 DELETE body，经网关批量删除不可靠**；不能把失败归因于这里接收对象格式。

### 4.3 详情与列表（仅 JWT，无资源范围授权）

分页参数统一按 `toIntOrNull()` 读取：缺省或不可解析时 `page=0`、`size=20`，从第 0 页开始。没有统一页大小上限、非负校验或稳定排序；负值等可能触发仓储错误，而不是约定返回空页。除明确说明的 recent/统计排序外，不能依赖返回顺序。所有列表都是裸 `PatientListItem[]`，没有 total。

| 方法与路径 | 查询参数 | 源码响应 / 真实查询行为 |
| --- | --- | --- |
| `GET /patients/{id}` | 无 | 200 `PatientInfo`，按记录 UUID；不存在 404；可能读到软删除记录 |
| `GET /patients/hospital/{hospitalId}/{patientId}` | 无 | 200 `PatientInfo`，医院+病历号精确匹配且未删除；不存在 404 |
| `GET /patients` | `hospitalId/department/attendingDoctorId: String?`；`status: String?`；page/size | 200 `PatientListItem[]`；实际过滤优先级见下文，不是多条件 AND |
| `GET /patients/hospital/{hospitalId}` | page/size | 200 `PatientListItem[]`；精确医院 ID，不验证医院存在或操作者归属 |
| `GET /patients/department/{department}` | page/size | 200 `PatientListItem[]`；精确科室字符串，**不附加医院筛选** |
| `GET /patients/doctor/{doctorId}` | page/size | 200 `PatientListItem[]`；精确主治医生 ID，不验证该医生存在 |
| `GET /patients/search` | `keyword: String` 必须出现；page/size | 200 `PatientListItem[]`；对 name/patientId/hospitalId 做包含式 SQL LIKE；不附加医院范围；缺关键词 400/1204，空字符串未拒绝 |
| `GET /patients/recent` | `days: Int = 7`、`limit: Int = 20`，不可解析回退默认 | 200 `PatientListItem[]`；lastVisitDate≥服务器 now−days，未删除，按 lastVisitDate 降序，limit 条；没有医院筛选或统一上限 |
| `GET /patients/statistics` | `hospitalId: String?`，省略为全局 | 200 `PatientStatistics`；不校验操作者医院，统计定义见第 3 节 |

`GET /patients` 实现只选择一个分支：

1. 提供 `attendingDoctorId` → 仅按医生筛选；忽略 department/hospitalId。
2. 否则提供 `department` → 仅按科室筛选；忽略 hospitalId。
3. 否则提供 `hospitalId` → 仅按医院筛选。
4. 均未提供 → 所有未删除患者。

上述“提供”是非 null，不是非空。`status` 在路由被 `Status.valueOf` 解析，却在 service 中**未使用**：合法值不产生过滤效果，非法值仍会触发 400/code=400。通用列表没有读取 keyword/bloodType 参数；不能由 shared 搜索 DTO 推断支持它们。

### 4.4 状态、就诊、体征、医疗信息及转移

| 方法与路径 | body | 实际授权与行为 | 源码正常响应 |
| --- | --- | --- | --- |
| `PUT /patients/{id}/status` | 状态 Map | JWT + auth 数据库角色 admin/receptionist；不核操作者医院；患者不存在 404，缺 status 400/1204，非法枚举 400/code=400 | 200 `{success: Boolean}` |
| `PUT /patients/{id}/visit` | `PatientVisitUpdate` | **仅 JWT**；患者存在即可；可直接修改科室/医生 ID，不验证目标医生；即使 body 为 `{}` 也更新 lastVisitDate 为本机当前时间 | 200 `{success: Boolean}` |
| `PUT /patients/{id}/vitals` | `PatientVitalsUpdate` | **仅 JWT**；患者存在即可；非 null 字段更新，不作护士/医生角色或范围检查 | 200 `{success: Boolean}` |
| `PUT /patients/{id}/medical` | `PatientMedicalUpdate` | **仅 JWT**；患者存在即可；非 null 字段更新，无附加资源授权 | 200 `{success: Boolean}` |
| `PUT /patients/{id}/transfer` | `PatientTransferRequest` | JWT + auth 数据库角色 admin/receptionist；先验证新医生有效，再检查操作者角色；不核操作者医院、新医生医院或科室归属；患者不存在 404，医生无效 400/1202 | 200 `{success: Boolean}` |

`status`/`transfer` 的 service 注释写“主治医生或管理员”，实际 AuthClient 允许的是 **admin 或 receptionist**，doctor 不因负责此患者获得写权限。另一方面，visit/vitals/medical 仅 JWT 的缺口也不能理解成被认可的授权矩阵。

当前[服务端 Routing](src/main/kotlin/Routing.kt)没有客户端可能声明的以下独立接口：`DELETE /patients/{id}/soft`、`PUT /patients/{id}/restore`、`PUT /patients/batch/status`、`PUT /patients/{id}/doctor`。不要把前端声明、shared DTO 或仓储函数视为已注册 HTTP API；实际删除、状态、医生变更分别以本节列出的路径为准。

## 5. 错误与状态码

来源：[PatientException](../shared/src/main/kotlin/exception/PatientException.kt)、[ExceptionHandling](src/main/kotlin/ExceptionHandling.kt)。业务错误字段为 `code: Int` 和 `message: String`；例如以下仅为形状示例：

```json
{
  "code": 1101,
  "message": "患者不存在"
}
```

| HTTP | code | 默认含义 / 注意 |
| --- | --- | --- |
| 404 | 1101 | 患者不存在；部分仓储查询异常也被统一转换为此异常，不一定真是记录不存在 |
| 409 | 1102 | 患者已存在 |
| 500 | 1103 | 创建患者失败，仓储可能附加具体原因（如日期/存储失败） |
| 500 | 1104 / 1105 | 更新 / 删除患者失败 |
| 400 | 1201 / 1202 / 1203 / 1204 | 医院 ID / 医生 ID / 科室 / 患者数据无效 |
| 403 | 1301 | 无权限访问患者数据 |
| 500 | 1500 | 未单独匹配的 PatientException；声明了 1302“不能修改他人负责的患者”，但当前流程未使用，若抛出也会进入这个兜底，并非已有 403/1302 契约 |
| 400 | 400 | `IllegalArgumentException`，如查询或状态 Map 的 `Status.valueOf` 失败 |
| 500 | 500 | `IllegalStateException`、普通 Exception 或其他 Throwable；message 可能含底层异常信息 |

JWT 拒绝通常是中间件 401，本模块未定义统一 JSON challenge。缺字段、类型错误、无效枚举等反序列化失败没有独立的完整错误规范，不能一律宣称为 400；未命中特定处理时可能进入通用 500。经网关还可能收到其 404/502，自有字段见[网关 API](../api-gateway/API.md)。

本页不包含已验收的异构 Map 响应；统计为可序列化 DTO，内部各分组为同类型 Map。包括这些 DTO 在内，所有运行行为均只作源码描述，不代表本轮完成服务、数据库、跨院隔离或网关端到端验收。
