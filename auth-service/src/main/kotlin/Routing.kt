package com.example

import com.example.database.repository.HospitalRepository
import com.example.database.repository.DepartmentRepository
import com.example.database.repository.UserRepository
import dto.DepartmentDto
import dto.HospitalDto
import dto.UserDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import utils.JwtUtil

fun Application.configureRouting() {
    // 创建JwtUtil实例
    val jwtUtil = JwtUtil(environment.config)

    // 创建Repository实例
    val hospitalRepository = HospitalRepository()
    val departmentRepository = DepartmentRepository()
    val userRepository = UserRepository(hospitalRepository, departmentRepository)

    // 创建UserService实例
    val userService = com.example.service.UserService(userRepository, jwtUtil)
    
    // 路由配置
    routing {
        // 健康检查
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }
        
        // API前缀
        route("/api/auth") {
            // 公开路由 - 不需要认证
            post("/register") {
                val userRegister = call.receive<UserDto.UserRegister>()
                val userId = userService.registerUser(userRegister)
                call.respond(HttpStatusCode.Created, mapOf("userId" to userId))
            }
            
            post("/login") {
                val userLogin = call.receive<UserDto.UserLogin>()
                val result = userService.loginUser(userLogin)
                call.respond(HttpStatusCode.OK, result)
            }
            
            // 需要认证的路由
            authenticate("jwt") {
                // 获取当前用户信息
                get("/user") {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.subject ?: throw Exception("Token中缺少用户ID")
                    val user = userService.getUserInfo(userId)
                    call.respond(HttpStatusCode.OK, user)
                }
                
                // 刷新token
                post("/refresh") {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.subject ?: throw Exception("Token中缺少用户ID")
                    val newToken = userService.refreshToken(userId)
                    call.respond(HttpStatusCode.OK, mapOf("token" to newToken))
                }
                
                // 获取指定用户信息
                get("/users/{id}") {
                    val userId = call.parameters["id"] ?: throw Exception("缺少用户ID")
                    val user = userService.getUserInfo(userId)
                    call.respond(HttpStatusCode.OK, user)
                }
                
                // 批量获取用户信息
                post("/users/batch") {
                    val userIds = call.receive<List<String>>()
                    val users = userService.getUserInfos(userIds)
                    call.respond(HttpStatusCode.OK, users)
                }
                
                // 获取所有用户（分页）
                get("/users") {
                    val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
                    val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20
                    val role = call.request.queryParameters["role"]
                    val users = userService.getAllUsers(page, size, role)
                    call.respond(HttpStatusCode.OK, users)
                }
                
                // 搜索用户
                get("/users/search") {
                    val keyword = call.request.queryParameters["keyword"] ?: throw Exception("缺少搜索关键词")
                    val users = userService.searchUsers(keyword)
                    call.respond(HttpStatusCode.OK, users)
                }
                
                // 获取科室医生列表
                get("/users/doctors/{hospitalId}/{deptCode}") {
                    val hospitalId = call.parameters["hospitalId"] ?: throw Exception("缺少医院ID")
                    val deptCode = call.parameters["deptCode"] ?: throw Exception("缺少科室代码")
                    val doctors = userService.getDepartmentDoctors(hospitalId, deptCode)
                    call.respond(HttpStatusCode.OK, doctors)
                }
                
                // 获取科室护士列表
                get("/users/nurses/{hospitalId}/{deptCode}") {
                    val hospitalId = call.parameters["hospitalId"] ?: throw Exception("缺少医院ID")
                    val deptCode = call.parameters["deptCode"] ?: throw Exception("缺少科室代码")
                    val nurses = userService.getDepartmentNurses(hospitalId, deptCode)
                    call.respond(HttpStatusCode.OK, nurses)
                }
                
                // 获取医院所有用户
                get("/users/hospital/{hospitalId}") {
                    val hospitalId = call.parameters["hospitalId"] ?: throw Exception("缺少医院ID")
                    val users = userService.getHospitalUsers(hospitalId)
                    call.respond(HttpStatusCode.OK, users)
                }
                
                // 更新用户信息
                put("/users/{id}") {
                    val userId = call.parameters["id"] ?: throw Exception("缺少用户ID")
                    val userUpdate = call.receive<UserDto.UserUpdate>()
                    // 确保用户ID一致
                    val updatedUserUpdate = userUpdate.copy(id = userId)
                    val success = userService.updateUser(updatedUserUpdate)
                    call.respond(HttpStatusCode.OK, mapOf("success" to success))
                }
                
                // 修改密码
                put("/password") {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.subject ?: throw Exception("Token中缺少用户ID")
                    val request = call.receive<Map<String, String>>()
                    val oldPassword = request["oldPassword"] ?: throw Exception("缺少旧密码")
                    val newPassword = request["newPassword"] ?: throw Exception("缺少新密码")
                    val success = userService.changePassword(userId, oldPassword, newPassword)
                    call.respond(HttpStatusCode.OK, mapOf("success" to success))
                }
                
                // 重置密码（管理员功能）
                put("/users/{id}/password") {
                    val userId = call.parameters["id"] ?: throw Exception("缺少用户ID")
                    val request = call.receive<Map<String, String>>()
                    val newPassword = request["newPassword"] ?: throw Exception("缺少新密码")
                    val success = userService.resetPassword(userId, newPassword)
                    call.respond(HttpStatusCode.OK, mapOf("success" to success))
                }
                
                // 修改用户名
                put("/username") {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.subject ?: throw Exception("Token中缺少用户ID")
                    val request = call.receive<Map<String, String>>()
                    val newUsername = request["newUsername"] ?: throw Exception("缺少新用户名")
                    val success = userService.changeUsername(userId, newUsername)
                    call.respond(HttpStatusCode.OK, mapOf("success" to success))
                }
                
                // 更新用户角色
                put("/users/{id}/role") {
                    val principal = call.principal<JWTPrincipal>()
                    val operatorId = principal?.payload?.subject ?: throw Exception("Token中缺少操作者ID")
                    val userId = call.parameters["id"] ?: throw Exception("缺少用户ID")
                    val request = call.receive<Map<String, String>>()
                    val newRole = request["newRole"] ?: throw Exception("缺少新角色")
                    val success = userService.changeRole(userId, newRole, operatorId)
                    call.respond(HttpStatusCode.OK, mapOf("success" to success))
                }
                
                // 删除用户
                delete("/users/{id}") {
                    val userId = call.parameters["id"] ?: throw Exception("缺少用户ID")
                    val success = userService.deleteUser(userId)
                    call.respond(HttpStatusCode.OK, mapOf("success" to success))
                }
                
                // 批量删除用户
                delete("/users/batch") {
                    val userIds = call.receive<List<String>>()
                    val successCount = userService.batchDeleteUsers(userIds)
                    call.respond(HttpStatusCode.OK, mapOf("successCount" to successCount))
                }
                
                // 更新用户基本信息（不包括密码）
                put("/users/{id}/info") {
                    val userId = call.parameters["id"] ?: throw Exception("缺少用户ID")
                    val request = call.receive<Map<String, String?>>()
                    val fullName = request["fullName"]
                    val role = request["role"]
                    val success = userService.updateUserInfo(userId, fullName, role)
                    call.respond(HttpStatusCode.OK, mapOf("success" to success))
                }
                
                // 获取用户统计信息
                get("/statistics") {
                    val hospitalId = call.request.queryParameters["hospitalId"]
                    val statistics = userService.getUserStatistics(hospitalId)
                    call.respond(HttpStatusCode.OK, statistics)
                }
                
                // 验证Token并获取用户信息
                post("/validate") {
                    val request = call.receive<Map<String, String>>()
                    val token = request["token"] ?: throw Exception("缺少Token")
                    val user = userService.validateTokenAndGetUser(token)
                    call.respond(HttpStatusCode.OK, user)
                }

                // ============ 医院相关 API ============
                // 获取所有医院
                get("/hospitals") {
                    val includeInactive = call.request.queryParameters["includeInactive"]?.toBoolean() ?: false
                    val hospitals = if (includeInactive) {
                        hospitalRepository.findAllHospitals()
                    } else {
                        hospitalRepository.findAllActiveHospitals()
                    }
                    call.respond(HttpStatusCode.OK, hospitals)
                }

                // 获取指定医院信息
                get("/hospitals/{id}") {
                    val id = call.parameters["id"] ?: throw Exception("缺少医院ID")
                    val hospital = hospitalRepository.findHospitalById(id)
                        ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "医院不存在"))
                    call.respond(HttpStatusCode.OK, hospital)
                }

                // 创建医院
                post("/hospitals") {
                    val hospital = call.receive<HospitalDto.HospitalCreate>()
                    val hospitalId = hospitalRepository.createHospital(hospital)
                    call.respond(HttpStatusCode.Created, mapOf("hospitalId" to hospitalId))
                }

                // 更新医院信息
                put("/hospitals/{id}") {
                    val id = call.parameters["id"] ?: throw Exception("缺少医院ID")
                    val hospitalUpdate = call.receive<HospitalDto.HospitalUpdate>()
                    val updatedUpdate = hospitalUpdate.copy(id = id)
                    val success = hospitalRepository.updateHospital(updatedUpdate)
                    if (success) {
                        call.respond(HttpStatusCode.OK, mapOf("success" to true))
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "医院不存在"))
                    }
                }

                // 删除医院（软删除）
                delete("/hospitals/{id}") {
                    val id = call.parameters["id"] ?: throw Exception("缺少医院ID")
                    val success = hospitalRepository.deleteHospital(id)
                    if (success) {
                        call.respond(HttpStatusCode.OK, mapOf("success" to true))
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "医院不存在"))
                    }
                }

                // ============ 部门相关 API ============
                // 获取指定医院的所有部门
                get("/hospitals/{hospitalId}/departments") {
                    val hospitalId = call.parameters["hospitalId"] ?: throw Exception("缺少医院ID")
                    val includeInactive = call.request.queryParameters["includeInactive"]?.toBoolean() ?: false
                    val departments = if (includeInactive) {
                        departmentRepository.findAllDepartmentsByHospitalId(hospitalId)
                    } else {
                        departmentRepository.findActiveDepartmentsByHospitalId(hospitalId)
                    }
                    call.respond(HttpStatusCode.OK, departments)
                }

                // 获取指定部门信息
                get("/departments/{id}") {
                    val id = call.parameters["id"] ?: throw Exception("缺少部门ID")
                    val department = departmentRepository.findDepartmentById(id)
                        ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "部门不存在"))
                    call.respond(HttpStatusCode.OK, department)
                }

                // 创建部门
                post("/departments") {
                    val department = call.receive<DepartmentDto.DepartmentCreate>()
                    val departmentId = departmentRepository.createDepartment(department)
                    call.respond(HttpStatusCode.Created, mapOf("departmentId" to departmentId))
                }

                // 更新部门信息
                put("/departments/{id}") {
                    val id = call.parameters["id"] ?: throw Exception("缺少部门ID")
                    val departmentUpdate = call.receive<DepartmentDto.DepartmentUpdate>()
                    val updatedUpdate = departmentUpdate.copy(id = id)
                    val success = departmentRepository.updateDepartment(updatedUpdate)
                    if (success) {
                        call.respond(HttpStatusCode.OK, mapOf("success" to true))
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "部门不存在"))
                    }
                }

                // 删除部门（软删除）
                delete("/departments/{id}") {
                    val id = call.parameters["id"] ?: throw Exception("缺少部门ID")
                    val success = departmentRepository.deleteDepartment(id)
                    if (success) {
                        call.respond(HttpStatusCode.OK, mapOf("success" to true))
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "部门不存在"))
                    }
                }
            }
        }
    }
}
