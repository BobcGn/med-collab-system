package com.example.database.repository

import database.entity.UserEntity
import database.entity.HospitalEntity
import database.entity.DepartmentEntity
import database.table.Users
import database.table.Hospitals
import database.table.Departments
import dto.UserDto
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class UserRepository(
    private val hospitalRepository: HospitalRepository,
    private val departmentRepository: DepartmentRepository
) {
    /**
     * 创建新用户
     */
    suspend fun createUser(user: UserDto.UserCreate): String{
        return try {
            transaction {
                val userId = Users.generateId()  // 生成 UUID
                Users.insert {
                    it[id] = userId
                    it[hospitalId] = user.hospitalId
                    it[deptCode] = user.deptCode
                    it[userSeq] = user.userSeq
                    it[fullName] = user.fullName
                    it[passwordHash] = user.passwordHash
                    it[role] = user.role
                } get Users.id
                userId  // 返回生成的 UUID
            }
        } catch (e: Exception) {
            throw Exception("创建用户失败: ${e.message}", e)
        }
    }


    /**
     * 根据id查找用户（不包含密码哈希）
     * @param id
     * @return UserDto.UserInfo?
     */
    suspend fun findUserById(id: String): UserDto.UserInfo? {
        return try {
            transaction {
                UserEntity.findById(id)?.let { entity ->
                    // 过滤已删除用户
                    if (entity.isDeleted) return@let null

                    // 获取医院名称
                    val hospitalName = if (entity.hospitalId != null) {
                        HospitalEntity.findById(entity.hospitalId!!)?.name ?: entity.hospitalId
                    } else {
                        null
                    }

                    // 获取科室名称
                    val deptName = if (entity.hospitalId != null && entity.deptCode != null) {
                        DepartmentEntity.find {
                            (Departments.id eq entity.deptCode) and
                            (Departments.hospitalId eq entity.hospitalId!!)
                        }.firstOrNull()?.name ?: entity.deptCode
                    } else {
                        null
                    }

                    UserDto.UserInfo(
                        id = entity.id.value,
                        hospitalId = entity.hospitalId,
                        deptCode = entity.deptCode,
                        hospitalName = hospitalName,
                        deptName = deptName,
                        userSeq = entity.userSeq,
                        username = entity.username,
                        fullName = entity.fullName,
                        role = entity.role,
                        createdAt = entity.createdAt.toString(),
                        updatedAt = entity.updatedAt?.toString(),
                        isDeleted = entity.isDeleted,
                        isFrozen = entity.isFrozen
                    )
                }
            }
        } catch (e: Exception) {
            throw Exception("查找用户失败", e)
        }
    }

    /**
     * 根据id查找用户（包含密码哈希，用于内部验证）
     * @param id
     * @return UserDto.UserInfoWithCredentials?
     */
    suspend fun findUserByIdWithCredentials(id: String): UserDto.UserInfoWithCredentials? {
        return try {
            transaction {
                UserEntity.findById(id)?.let { entity ->
                    // 过滤已删除用户
                    if (entity.isDeleted) return@let null

                    UserDto.UserInfoWithCredentials(
                        id = entity.id.value,
                        hospitalId = entity.hospitalId,
                        deptCode = entity.deptCode,
                        userSeq = entity.userSeq,
                        username = entity.username,
                        fullName = entity.fullName,
                        role = entity.role,
                        passwordHash = entity.passwordHash,
                        createdAt = entity.createdAt.toString(),
                        updatedAt = entity.updatedAt?.toString(),
                        isDeleted = entity.isDeleted,
                        isFrozen = entity.isFrozen
                    )
                }
            }
        } catch (e: Exception) {
            throw Exception("查找用户失败", e)
        }
    }

    /**
     * 根据账号查找用户（不包含密码哈希）
     * @param username
     * @return UserDto.UserInfo?
     */
    suspend fun findUserByUsername(username: String): UserDto.UserInfo? {
        return try {
            val parts = username.split("-")

            if (parts.size == 2 && parts[0] == "ADMIN") {
                val userSeq = parts[1]
                return transaction {
                    UserEntity.find {
                        (Users.hospitalId.isNull()) and
                        (Users.deptCode.isNull()) and
                        (Users.userSeq eq userSeq) and
                        (Users.isDeleted eq false)
                    }.firstOrNull()?.let { entity -> buildUserInfo(entity) }
                }
            }

            val entity = if (parts.size == 4) {
                val (_, hospitalId, deptCode, userSeq) = parts
                transaction {
                    UserEntity.find {
                        (Users.hospitalId eq hospitalId) and
                                (Users.deptCode eq deptCode) and
                                (Users.userSeq eq userSeq) and
                                (Users.isDeleted eq false)
                    }.firstOrNull()
                }
            } else if (parts.size == 3) {
                val (_, hospitalId, userSeq) = parts
                transaction {
                    UserEntity.find {
                        (Users.hospitalId eq hospitalId) and
                                (Users.deptCode.isNull()) and
                                (Users.userSeq eq userSeq) and
                                (Users.isDeleted eq false)
                    }.firstOrNull()
                }
            } else {
                return null
            }

            return entity?.let { buildUserInfo(it) }
        } catch (e: Exception) {
            throw Exception("查找用户失败: ${e.message}", e)
        }
    }

    /**
     * 根据账号查找用户（包含密码哈希，用于登录验证）
     * @param username
     * @return UserDto.UserInfoWithCredentials?
     */
    suspend fun findUserByUsernameWithCredentials(username: String): UserDto.UserInfoWithCredentials? {
        return try {
            val parts = username.split("-")

            transaction {
                val entity = if (parts.size == 2 && parts[0] == "ADMIN") {
                    // 管理员账号格式: ADMIN-{userSeq}
                    val userSeq = parts[1]
                    UserEntity.find {
                        (Users.hospitalId.isNull()) and
                        (Users.deptCode.isNull()) and
                        (Users.userSeq eq userSeq) and
                        (Users.isDeleted eq false)
                    }.firstOrNull()
                } else if (parts.size == 4) {
                    // 普通用户账号格式: {rolePrefix}-{hospitalId}-{deptCode}-{userSeq}
                    val (_, hospitalId, deptCode, userSeq) = parts
                    UserEntity.find {
                        (Users.hospitalId eq hospitalId) and
                        (Users.deptCode eq deptCode) and
                        (Users.userSeq eq userSeq) and
                        (Users.isDeleted eq false)
                    }.firstOrNull()
                } else if (parts.size == 3) {
                    // 无科室用户: {rolePrefix}-{hospitalId}-{userSeq}
                    val (_, hospitalId, userSeq) = parts
                    UserEntity.find {
                        (Users.hospitalId eq hospitalId) and
                        (Users.deptCode.isNull()) and
                        (Users.userSeq eq userSeq) and
                        (Users.isDeleted eq false)
                    }.firstOrNull()
                } else {
                    return@transaction null
                }

                entity?.let {
                    // 获取医院名称
                    val hospitalName = it.hospitalId?.let { hospitalId ->
                        HospitalEntity.findById(hospitalId)?.name
                    }

                    // 获取科室名称
                    val deptName = if (it.hospitalId != null && it.deptCode != null) {
                        val tempHospitalId = it.hospitalId
                        val tempDeptCode = it.deptCode
                        DepartmentEntity.find {
                            (Departments.id eq tempDeptCode) and
                                    (Departments.hospitalId eq tempHospitalId!!)
                        }.firstOrNull()?.name
                    } else {
                        null
                    }

                    UserDto.UserInfoWithCredentials(
                        id = it.id.value,
                        hospitalId = it.hospitalId,
                        deptCode = it.deptCode,
                        hospitalName = hospitalName,
                        deptName = deptName,
                        userSeq = it.userSeq,
                        username = it.username,
                        fullName = it.fullName,
                        role = it.role,
                        passwordHash = it.passwordHash,
                        createdAt = it.createdAt.toString(),
                        updatedAt = it.updatedAt?.toString(),
                        isDeleted = it.isDeleted,
                        isFrozen = it.isFrozen
                    )
                }
            }
        } catch (e: Exception) {
            throw Exception("查找用户失败", e)
        }
    }

    /**
     * 更新用户信息
     * @param userUpdate
     * @return Boolean
     */
    suspend fun updateUser(userUpdate: UserDto.UserUpdate): Boolean{
        return try {
            val userId = userUpdate.id ?: throw Exception("缺少用户ID")
            transaction {
                UserEntity.findById(userId)?.let{ user ->
                    userUpdate.fullName?.let{ user.fullName = it}
                    userUpdate.passwordHash?.let { user.passwordHash = it }
                    userUpdate.role?.let { user.role = it }
                    userUpdate.isFrozen?.let { user.isFrozen = it }
                    true
                }?: false
            }
        } catch (e: Exception) {
            throw Exception("更新用户失败", e)
        }
    }

    /**
     * 软删除用户
     */
    suspend fun deleteUser(id: String): Boolean{
        return try {
            transaction {
                UserEntity.findById(id)?.let{ user ->
                    user.isDeleted = true
                    true
                }?: false
            }
        } catch (e: Exception) {
            throw Exception("删除用户失败: ${e.message}", e)
        }
    }

    /**
     * 根据医院和科室查找用户
     * @param hospitalId
     * @param deptCode
     * @return List<UserDto.UserInfo>
     */
    suspend fun findByHospitalAndDept(hospitalId: String, deptCode: String): List<UserDto.UserInfo>{
        return try {
            transaction {
                UserEntity.find{
                    (Users.hospitalId eq hospitalId) and (Users.deptCode eq deptCode) and (Users.isDeleted eq false)
                }.map{ entity ->
                    // 获取医院名称
                    val hospitalName = HospitalEntity.findById(entity.hospitalId!!)?.name ?: entity.hospitalId

                    // 获取科室名称
                    val deptName = DepartmentEntity.find {
                        (Departments.id eq entity.deptCode) and
                        (Departments.hospitalId eq entity.hospitalId!!)
                    }.firstOrNull()?.name ?: entity.deptCode

                    UserDto.UserInfo(
                        id = entity.id.value,
                        hospitalId = entity.hospitalId,
                        deptCode = entity.deptCode,
                        hospitalName = hospitalName,
                        deptName = deptName,
                        userSeq = entity.userSeq,
                        username = entity.username,
                        fullName = entity.fullName,
                        role = entity.role,
                        createdAt = entity.createdAt.toString(),
                        updatedAt = entity.updatedAt?.toString(),
                        isDeleted = entity.isDeleted,
                        isFrozen = entity.isFrozen
                    )
                }
            }
        } catch (e: Exception) {
            throw Exception("查找用户失败", e)
        }
    }

    /**
     * 检查账户是否存在
     * @param username
     * @return Boolean
     */
    suspend fun existsByUsername(username: String): Boolean{
        return try {
            val parts = username.split("-")

            transaction {
                val result = if (parts.size == 2 && parts[0] == "ADMIN") {
                    val userSeq = parts[1]
                    UserEntity.find {
                        (Users.hospitalId.isNull()) and
                        (Users.deptCode.isNull()) and
                        (Users.userSeq eq userSeq) and
                        (Users.isDeleted eq false)
                    }
                } else if (parts.size == 4) {
                    val (_, hospitalId, deptCode, userSeq) = parts
                    UserEntity.find {
                        (Users.hospitalId eq hospitalId) and
                        (Users.deptCode eq deptCode) and
                        (Users.userSeq eq userSeq) and
                        (Users.isDeleted eq false)
                    }
                } else if (parts.size == 3) {
                    val (_, hospitalId, userSeq) = parts
                    UserEntity.find {
                        (Users.hospitalId eq hospitalId) and
                        (Users.deptCode.isNull()) and
                        (Users.userSeq eq userSeq) and
                        (Users.isDeleted eq false)
                    }
                } else {
                    return@transaction false
                }

                !result.empty()
            }
        } catch (e: Exception) {
            throw Exception("检查用户失败", e)
        }
    }



    /**
     * 检查医院和科室是否存在
     * @param hospitalId
     * @param deptCode
     * @return Boolean
     */
    suspend fun existsByHospitalAndDept(hospitalId: String, deptCode: String): Boolean {
        return try {
            // 检查医院是否存在且激活
            val hospitalExists = hospitalRepository.existsActiveById(hospitalId)
            if (!hospitalExists) return false

            // 检查科室是否存在且激活
            departmentRepository.existsActiveByHospitalAndId(hospitalId, deptCode)
        } catch (e: Exception) {
            throw Exception("检查医院和科室失败: ${e.message}", e)
        }
    }

    /**
     * 检查医院是否存在
     * @param hospitalId
     * @return Boolean
     */
    suspend fun existsByHospital(hospitalId: String): Boolean {
        return try {
            hospitalRepository.existsActiveById(hospitalId)
        } catch (e: Exception) {
            throw Exception("检查医院失败: ${e.message}", e)
        }
    }

    /**
     * 分页查询所有用户
     * @param page 页码，从0开始
     * @param size 每页大小
     * @param role 角色过滤（可选）
     * @return List<UserDto.UserInfo>
     */
    suspend fun findAllUsers(page: Int, size: Int, role: String? = null): List<UserDto.UserInfo> {
        // 验证输入参数
        if (page < 0 || size <= 0) {
            return emptyList()
        }

        return try {
            transaction {
                val query = if (role != null) {
                    UserEntity.find { (Users.role eq role) and (Users.isDeleted eq false) }
                } else {
                    UserEntity.find { Users.isDeleted eq false }
                }

                query
                    .limit(size)
                    .offset(page.toLong() * size)
                    .map { entity ->
                        // 获取医院名称
                        val hospitalName = if (entity.hospitalId != null) {
                            HospitalEntity.findById(entity.hospitalId!!)?.name ?: entity.hospitalId
                        } else {
                            null
                        }

                        // 获取科室名称
                        val deptName = if (entity.hospitalId != null && entity.deptCode != null) {
                            DepartmentEntity.find {
                                (Departments.id eq entity.deptCode) and
                                (Departments.hospitalId eq entity.hospitalId!!)
                            }.firstOrNull()?.name ?: entity.deptCode
                        } else {
                            null
                        }

                        UserDto.UserInfo(
                            id = entity.id.value,
                            hospitalId = entity.hospitalId,
                            deptCode = entity.deptCode,
                            hospitalName = hospitalName,
                            deptName = deptName,
                            userSeq = entity.userSeq,
                            username = entity.username,
                            fullName = entity.fullName,
                            role = entity.role,
                            createdAt = entity.createdAt.toString(),
                            updatedAt = entity.updatedAt?.toString(),
                            isDeleted = entity.isDeleted,
                            isFrozen = entity.isFrozen
                        )
                    }
            }
        } catch (e: Exception) {
            throw Exception("分页查询用户失败", e)
        }
    }

    /**
     * 搜索用户
     * @param keyword 搜索关键词（用户名或姓名）
     * @return List<UserDto.UserInfo>
     */
    suspend fun searchUsers(keyword: String): List<UserDto.UserInfo> {
        return try {
            transaction {
                UserEntity.find {
                    ((Users.fullName like "%$keyword%") or
                    (Users.hospitalId like "%$keyword%") or
                    (Users.deptCode like "%$keyword%") or
                    (Users.userSeq like "%$keyword%")) and
                    (Users.isDeleted eq false)
                }
                .map { entity ->
                    // 获取医院名称
                    val hospitalName = if (entity.hospitalId != null) {
                        HospitalEntity.findById(entity.hospitalId!!)?.name ?: entity.hospitalId
                    } else {
                        null
                    }

                    // 获取科室名称
                    val deptName = if (entity.hospitalId != null && entity.deptCode != null) {
                        DepartmentEntity.find {
                            (Departments.id eq entity.deptCode) and
                            (Departments.hospitalId eq entity.hospitalId!!)
                        }.firstOrNull()?.name ?: entity.deptCode
                    } else {
                        null
                    }

                    UserDto.UserInfo(
                        id = entity.id.value,
                        hospitalId = entity.hospitalId,
                        deptCode = entity.deptCode,
                        hospitalName = hospitalName,
                        deptName = deptName,
                        userSeq = entity.userSeq,
                        username = entity.username,
                        fullName = entity.fullName,
                        role = entity.role,
                        createdAt = entity.createdAt.toString(),
                        updatedAt = entity.updatedAt?.toString(),
                        isDeleted = entity.isDeleted,
                        isFrozen = entity.isFrozen
                    )
                }
            }
        } catch (e: Exception) {
            throw Exception("搜索用户失败", e)
        }
    }

    /**
     * 根据医院ID查询用户
     * @param hospitalId 医院ID
     * @return List<UserDto.UserInfo>
     */
    suspend fun findUsersByHospitalId(hospitalId: String): List<UserDto.UserInfo> {
        return try {
            transaction {
                UserEntity.find {
                    (Users.hospitalId eq hospitalId) and (Users.isDeleted eq false)
                }
                .map { entity ->
                    // 获取医院名称
                    val hospitalName = HospitalEntity.findById(entity.hospitalId!!)?.name ?: entity.hospitalId

                    // 获取科室名称
                    val deptName = if (entity.deptCode != null) {
                        DepartmentEntity.find {
                            (Departments.id eq entity.deptCode) and
                            (Departments.hospitalId eq entity.hospitalId!!)
                        }.firstOrNull()?.name ?: entity.deptCode
                    } else {
                        null
                    }

                    UserDto.UserInfo(
                        id = entity.id.value,
                        hospitalId = entity.hospitalId,
                        deptCode = entity.deptCode,
                        hospitalName = hospitalName,
                        deptName = deptName,
                        userSeq = entity.userSeq,
                        username = entity.username,
                        fullName = entity.fullName,
                        role = entity.role,
                        createdAt = entity.createdAt.toString(),
                        updatedAt = entity.updatedAt?.toString(),
                        isDeleted = entity.isDeleted,
                        isFrozen = entity.isFrozen
                    )
                }
            }
        } catch (e: Exception) {
            throw Exception("根据医院ID查询用户失败", e)
        }
    }

    /**
     * 获取用户统计信息
     * @param hospitalId 医院ID（可选）
     * @return Map<String, Int> 统计信息
     */
    suspend fun getUserStatistics(hospitalId: String? = null): Map<String, Any> {  // 修改返回类型
        return try {
            transaction {
                val query = if (hospitalId != null) {
                    UserEntity.find { (Users.hospitalId eq hospitalId) and (Users.isDeleted eq false) }
                } else {
                    UserEntity.find { Users.isDeleted eq false }
                }

                val total = query.count().toInt()
                val byRole = query.groupBy { it.role }.mapValues { it.value.size }
                val deleted = query.filter { it.isDeleted }.count().toInt()

                mapOf(
                    "total" to total,
                    "deleted" to deleted,
                    "byRole" to byRole
                )
            }
        } catch (e: Exception) {
            throw Exception("获取用户统计信息失败", e)
        }
    }

    // ==================== 辅助方法 ====================
    private fun buildUserInfo(entity: UserEntity): UserDto.UserInfo {
        val hospitalName = if (entity.hospitalId != null) {
            HospitalEntity.findById(entity.hospitalId!!)?.name ?: entity.hospitalId
        } else {
            null
        }

        val deptName = if (entity.hospitalId != null && entity.deptCode != null) {
            DepartmentEntity.find {
                (Departments.id eq entity.deptCode) and
                (Departments.hospitalId eq entity.hospitalId!!)
            }.firstOrNull()?.name ?: entity.deptCode
        } else {
            null
        }

        return UserDto.UserInfo(
            id = entity.id.value,
            hospitalId = entity.hospitalId,
            deptCode = entity.deptCode,
            hospitalName = hospitalName,
            deptName = deptName,
            userSeq = entity.userSeq,
            username = entity.username,
            fullName = entity.fullName,
            role = entity.role,
            createdAt = entity.createdAt.toString(),
            updatedAt = entity.updatedAt?.toString(),
            isDeleted = entity.isDeleted,
            isFrozen = entity.isFrozen
        )
    }
}