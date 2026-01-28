package com.example.service

import com.example.database.repository.UserRepository
import dto.UserDto
import exception.AuthException
import utils.JwtUtil
import java.security.MessageDigest

class UserService(
    private val userRepository: UserRepository,
    private val jwtUtil: JwtUtil
){
// =======================业务方法========================
    /**
     * 用户注册（便捷方法）
     *
     * @param user
     * @return UserDto.RegisterResponse 包含用户信息和消息
     * @throws
     */
    suspend fun registerUser(user: UserDto.UserRegister): UserDto.RegisterResponse {
        // 检查密码强度
        if (!isPasswordStrong(user.password)) {
            throw AuthException.PasswordTooWeakException()
        }

        // 加密密码
        val encryptedPassword = hashPassword(user.password)

        // 根据角色处理不同的注册逻辑
        if (user.role == "admin") {
            // 管理员注册
            // 生成临时用户ID（实际会被数据库生成的ID替换）
            val tempUserId = System.currentTimeMillis().toString().takeLast(6)
            // 生成管理员账号：admin-用户id
            val adminUsername = "admin-$tempUserId"

            // 创建 UserCreate 对象
            val userCreate = UserDto.UserCreate(
                hospitalId = null,
                deptCode = null,
                userSeq = tempUserId,
                username = adminUsername,
                fullName = user.fullName,
                passwordHash = encryptedPassword,
                role = "admin"
            )

            val userId = userRepository.createUser(userCreate)
            val createdUser = userRepository.findUserById(userId)
                ?: throw Exception("用户创建失败")

            return UserDto.RegisterResponse(
                user = createdUser,
                message = "注册成功"
            )
        } else {
            // 普通用户注册
            // 验证医院和科室是否有效
            val hospitalId = user.hospitalId ?: throw AuthException.HospitalOrDepartmentIdInvalidException()
            val deptCode = user.deptCode ?: throw AuthException.HospitalOrDepartmentIdInvalidException()

            if (!userRepository.existsByHospitalAndDept(hospitalId, deptCode)) {
                throw AuthException.HospitalOrDepartmentIdInvalidException()
            }

            // 生成临时用户ID（实际会被数据库生成的ID替换）
            val tempUserId = System.currentTimeMillis().toString().takeLast(6)
            // 生成用户账号：医院id-部门id-用户id
            val userUsername = "$hospitalId-$deptCode-$tempUserId"

            // 创建 UserCreate 对象
            val userCreate = UserDto.UserCreate(
                hospitalId = hospitalId,
                deptCode = deptCode,
                userSeq = tempUserId,
                username = userUsername,
                fullName = user.fullName,
                passwordHash = encryptedPassword,
                role = "doctor" // 默认角色
            )

            val userId = userRepository.createUser(userCreate)
            val createdUser = userRepository.findUserById(userId)
                ?: throw Exception("用户创建失败")

            return UserDto.RegisterResponse(
                user = createdUser,
                message = "注册成功"
            )
        }
    }

    /**
     * 用户注册（完整方法）
     *
     * 业务规则：
     * 1. 检查用户名是否已存在
     * 2. 验证医院和科室是否有效
     * 3. 密码强度检查
     * 4. 密码加密
     * 5. 创建用户
     */
    suspend fun registerUser(user: UserDto.UserCreate): String{
        if (userRepository.existsByUsername(user.username!!)){
            throw AuthException.UserAlreadyExistsException()
        }
        // 只有非管理员用户才需要验证医院和科室
        if (user.role != "admin") {
            if (!userRepository.existsByHospitalAndDept(user.hospitalId!!, user.deptCode!!)){
                throw AuthException.HospitalOrDepartmentIdInvalidException()
            }
        }

        // 检查密码强度并加密
        val password = user.passwordHash
        if (isPlainPassword(password)) {
            // 如果是明文密码，检查强度
            if (!isPasswordStrong(password)) {
                throw AuthException.PasswordTooWeakException()
            }
            // 加密密码
            val encryptedPassword = hashPassword(password)
            // 创建新的用户对象，使用加密后的密码
            val userWithEncryptedPassword = user.copy(passwordHash = encryptedPassword)
            return userRepository.createUser(userWithEncryptedPassword)
        } else {
            // 如果已经是加密密码，直接使用
            return userRepository.createUser(user)
        }
    }

    /**
     * 用户登录
     *
     * 业务规则：
     * 1. 验证用户是否存在
     * 2. 验证密码是否正确
     * 3. 检查用户是否被冻结或删除
     * @param userLogin
     * @return UserDto.LoginResponse
     */
    suspend fun loginUser(userLogin: UserDto.UserLogin): UserDto.LoginResponse {
        // 验证用户是否存在（获取包含密码哈希的用户信息）
        val userWithCredentials = userRepository.findUserByUsernameWithCredentials(userLogin.username)
            ?: throw AuthException.UserNotFoundException()

        // 检查用户是否被冻结
        if (userWithCredentials.isFrozen) {
            throw AuthException.AccountFrozenException()
        }

        // 检查用户是否被删除
        if (userWithCredentials.isDeleted) {
            throw AuthException.UserNotFoundException()
        }

        // 验证密码是否正确 - 这里就是关键的密码验证逻辑
        if (!verifyPassword(userLogin.password, userWithCredentials.passwordHash)) {
            throw AuthException.LoginFailedException()
        }

        // 转换为不包含密码的用户信息
        val user = userWithCredentials.toUserInfo()

        // 生成JWT token
        val token = jwtUtil.generateToken(user.id, user.username?:"", mapOf("role" to user.role))

        // 返回包含用户信息和token的结果
        return UserDto.LoginResponse(
            user = user,
            token = token
        )
    }

    /**
     * 获取用户信息
     * @param userId
     * @return UserDto.UserInfo
     */
    suspend fun getUserInfo(userId: String): UserDto.UserInfo {
        return userRepository.findUserById(userId)
            ?: throw AuthException.UserNotFoundException()
    }

    /**
     * 批量获取用户信息
     * @param userIds 用户ID列表
     * @return List<UserDto.UserInfo>
     */
    suspend fun getUserInfos(userIds: List<String>): List<UserDto.UserInfo> {
        return userIds.mapNotNull { userId ->
            try {
                userRepository.findUserById(userId)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 获取所有用户（分页）
     * @param page 页码，从0开始
     * @param size 每页大小
     * @param role 角色过滤（可选）
     * @return List<UserDto.UserInfo>
     */
    suspend fun getAllUsers(page: Int = 0, size: Int = 20, role: String? = null): List<UserDto.UserInfo> {
        return userRepository.findAllUsers(page, size, role)
    }

    /**
     * 搜索用户
     * @param keyword 搜索关键词（用户名或姓名）
     * @return List<UserDto.UserInfo>
     */
    suspend fun searchUsers(keyword: String): List<UserDto.UserInfo> {
        return userRepository.searchUsers(keyword)
    }

    /**
     * 获取科室医生列表
     * @param hospitalId
     * @param deptCode
     * @return List<UserDto.UserInfo>
     */
    suspend fun getDepartmentDoctors(hospitalId: String, deptCode: String): List<UserDto.UserInfo> {
        // 验证医院和科室是否有效
        if (!validateHospital(hospitalId) || !validateDepartment(hospitalId, deptCode)) {
            throw AuthException.HospitalOrDepartmentIdInvalidException()
        }

        return userRepository.findByHospitalAndDept(hospitalId, deptCode).filter { it.role == "doctor" }
    }

    /**
     * 获取科室护士列表
     * @param hospitalId
     * @param deptCode
     * @return List<UserDto.UserInfo>
     */
    suspend fun getDepartmentNurses(hospitalId: String, deptCode: String): List<UserDto.UserInfo> {
        // 验证医院和科室是否有效
        if (!validateHospital(hospitalId) || !validateDepartment(hospitalId, deptCode)) {
            throw AuthException.HospitalOrDepartmentIdInvalidException()
        }

        return userRepository.findByHospitalAndDept(hospitalId, deptCode).filter { it.role == "nurse" }
    }

    /**
     * 获取医院所有用户
     * @param hospitalId
     * @return List<UserDto.UserInfo>
     */
    suspend fun getHospitalUsers(hospitalId: String): List<UserDto.UserInfo> {
        // 验证医院是否有效
        if (!validateHospital(hospitalId)) {
            throw AuthException.HospitalIdInvalidException()
        }

        return userRepository.findUsersByHospitalId(hospitalId)
    }

    /**
     * 更新用户信息
     * @param userUpdate
     * @return Boolean
     */
    suspend fun updateUser(userUpdate: UserDto.UserUpdate): Boolean {
        val userId = userUpdate.id ?: throw Exception("缺少用户ID")
        // 验证用户是否存在
        val existingUser = userRepository.findUserById(userId)
            ?: throw AuthException.UserNotFoundException()

        // 如果更新密码，需要进行密码强度检查和加密
        val updatedUser = userUpdate.copy(
            passwordHash = userUpdate.passwordHash?.let {
                if (isPlainPassword(it)) {
                    // 如果是明文密码，检查强度并加密
                    if (!isPasswordStrong(it)) {
                        throw AuthException.PasswordTooWeakException()
                    }
                    hashPassword(it)
                } else {
                    // 已经是加密密码，直接使用
                    it
                }
            }
        )

        return userRepository.updateUser(updatedUser)
    }

    /**
     * 冻结用户
     * @param userId 用户ID
     * @param operatorId 操作者ID（用于权限验证）
     * @return Boolean
     */
    suspend fun freezeUser(userId: String, operatorId: String): Boolean {
        // 验证操作者是否为管理员
        if (!isAdmin(operatorId)) {
            throw AuthException.PermissionDeniedException()
        }

        // 验证目标用户是否存在
        val existingUser = userRepository.findUserById(userId)
            ?: throw AuthException.UserNotFoundException()

        // 不能冻结自己
        if (userId == operatorId) {
            throw AuthException.CannotDeleteSelfException()
        }

        // 更新冻结状态
        return userRepository.updateUser(
            UserDto.UserUpdate(
                id = userId,
                isFrozen = true
            )
        )
    }

    /**
     * 解冻用户
     * @param userId 用户ID
     * @param operatorId 操作者ID（用于权限验证）
     * @return Boolean
     */
    suspend fun unfreezeUser(userId: String, operatorId: String): Boolean {
        // 验证操作者是否为管理员
        if (!isAdmin(operatorId)) {
            throw AuthException.PermissionDeniedException()
        }

        // 验证目标用户是否存在
        userRepository.findUserById(userId)
            ?: throw AuthException.UserNotFoundException()

        // 更新冻结状态
        return userRepository.updateUser(
            UserDto.UserUpdate(
                id = userId,
                isFrozen = false
            )
        )
    }

    /**
     * 修改密码
     * @param userId 用户ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return Boolean
     */
    suspend fun changePassword(userId: String, oldPassword: String, newPassword: String): Boolean {
        // 验证用户是否存在（获取包含密码哈希的用户信息）
        val userWithCredentials = userRepository.findUserByIdWithCredentials(userId)
            ?: throw AuthException.UserNotFoundException()

        // 验证旧密码
        if (!verifyPassword(oldPassword, userWithCredentials.passwordHash)) {
            throw AuthException.OldPasswordIncorrectException()
        }

        // 检查新密码强度
        if (!isPasswordStrong(newPassword)) {
            throw AuthException.PasswordTooWeakException()
        }

        // 加密新密码
        val encryptedNewPassword = hashPassword(newPassword)

        // 更新密码
        return userRepository.updateUser(
            UserDto.UserUpdate(
                id = userId,
                passwordHash = encryptedNewPassword
            )
        )
    }

    /**
     * 重置密码（管理员功能）
     * @param userId 用户ID
     * @param newPassword 新密码
     * @return Boolean
     */
    suspend fun resetPassword(userId: String, newPassword: String): Boolean {
        // 验证用户是否存在
        userRepository.findUserById(userId)
            ?: throw AuthException.UserNotFoundException()

        // 检查新密码强度
        if (!isPasswordStrong(newPassword)) {
            throw AuthException.PasswordTooWeakException()
        }

        // 加密新密码
        val encryptedNewPassword = hashPassword(newPassword)

        // 更新密码
        return userRepository.updateUser(
            UserDto.UserUpdate(
                id = userId,
                passwordHash = encryptedNewPassword
            )
        )
    }

    /**
     * 修改用户名
     * @param userId 用户ID
     * @param newUsername 新用户名
     * @return Boolean
     */
    suspend fun changeUsername(userId: String, newUsername: String): Boolean {
        // 验证用户是否存在
        val user = userRepository.findUserById(userId)
            ?: throw AuthException.UserNotFoundException()

        // 检查新用户名是否已被占用
        if (userRepository.existsByUsername(newUsername)) {
            throw AuthException.UserAlreadyExistsException()
        }

        // 更新用户名
        return userRepository.updateUser(
            UserDto.UserUpdate(
                id = userId,
                username = newUsername
            )
        )
    }

    /**
     * 更新用户角色
     * @param userId 用户ID
     * @param newRole 新角色
     * @param operatorId 操作者ID（用于权限验证）
     * @return Boolean
     */
    suspend fun changeRole(userId: String, newRole: String, operatorId: String): Boolean {
        // 验证操作者是否为管理员
        if (!isAdmin(operatorId)) {
            throw AuthException.PermissionDeniedException()
        }

        // 验证目标用户是否存在
        userRepository.findUserById(userId)
            ?: throw AuthException.UserNotFoundException()

        // 验证角色是否有效
        val validRoles = listOf("admin", "doctor", "nurse", "receptionist")
        if (newRole !in validRoles) {
            throw AuthException.RoleInvalidException()
        }

        // 更新角色
        return userRepository.updateUser(
            UserDto.UserUpdate(
                id = userId,
                role = newRole
            )
        )
    }

    /**
     * 删除用户
     * @param userId
     * @return Boolean
     */
    suspend fun deleteUser(userId: String): Boolean {
        // 验证用户是否存在
        val existingUser = userRepository.findUserById(userId)
            ?: throw AuthException.UserNotFoundException()

        return userRepository.deleteUser(userId)
    }

    /**
     * 批量删除用户
     * @param userIds 用户ID列表
     * @return 删除成功的数量
     */
    suspend fun batchDeleteUsers(userIds: List<String>): Int {
        var successCount = 0
        for (userId in userIds) {
            try {
                if (deleteUser(userId)) {
                    successCount++
                }
            } catch (e: Exception) {
                // 记录日志，但不中断批量操作
                continue
            }
        }
        return successCount
    }

    /**
     * 更新用户基本信息（不包括密码）
     * @param userId 用户ID
     * @param fullName 姓名（可选）
     * @param role 角色（可选）
     * @return Boolean
     */
    suspend fun updateUserInfo(
        userId: String,
        fullName: String? = null,
        role: String? = null
    ): Boolean {
        // 验证用户是否存在
        userRepository.findUserById(userId)
            ?: throw AuthException.UserNotFoundException()

        return userRepository.updateUser(
            UserDto.UserUpdate(
                id = userId,
                fullName = fullName,
                role = role
            )
        )
    }

// ==================== 私有辅助方法 ====================
    /**
     * 验证医院是否有效
     * 通过查询数据库中的医院表来验证
     *
     * @param hospitalId 医院编码
     * @return Boolean
     */
    private suspend fun validateHospital(hospitalId: String): Boolean {
        return userRepository.existsByHospital(hospitalId)
    }

    /**
     * 验证科室是否有效
     * 通过查询数据库中的科室表来验证
     * 同时验证科室是否属于指定的医院
     *
     * @param hospitalId 医院编码
     * @param deptCode 科室编码
     * @return Boolean
     */
    private suspend fun validateDepartment(hospitalId: String, deptCode: String): Boolean {
        return userRepository.existsByHospitalAndDept(hospitalId, deptCode)
    }

    /**
     * 密码强度检查
     * @param password
     * @return Boolean
     */
    private fun isPasswordStrong(password: String): Boolean{
        // 空值检查
        if (password.isBlank()) {
            return false
        }

        // 长度检查
        if (password.length < 8) {
            return false
        }

        // 正则表达式检查：
        // (?=.*[a-z]) 至少一个小写字母
        // (?=.*[A-Z]) 至少一个大写字母
        // (?=.*\d) 至少一个数字
        // [a-zA-Z\d\S] 允许字母、数字和特殊字符
        // {8,} 至少8位
        val passwordRegex = Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$")

        return password.matches(passwordRegex)
    }
    /**
     * 判断密码是否为明文（未加密）
     *
     * @param password
     * @return Boolean
     */
    private fun isPlainPassword(password: String): Boolean {
        return password.length < 40
    }

    /**
     * 密码加密（生产环境使用 BCrypt）
     * @param password
     * @return 加密后的密码
     */
    private fun hashPassword(password: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val hashBytes = md.digest(password.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            throw Exception("密码加密失败: ${e.message}")
        }
    }

    /**
     * 密码验证
     * @param password
     * @return Boolean
     */
    private fun verifyPassword(plainPassword: String, hashedPassword: String): Boolean {
        return try {
            val hashedInput = hashPassword(plainPassword)
            // 使用常量时间比较，防止时序攻击
            constantTimeCompare(hashedInput, hashedPassword)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 常量时间比较
     * 用于密码验证，防止时序攻击
     *
     * @param a
     * @param b
     * @return Boolean
     */
    private fun constantTimeCompare(a: String, b: String): Boolean {
        if (a.length != b.length) {
            return false
        }

        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }

        return result == 0
    }

    /**
     * 权限检查
     * @param userId
     * @param permission
     * @return Boolean
     */
    suspend fun hasPermission(userId: String, permission: String): Boolean {
        // 获取用户信息
        val user = userRepository.findUserById(userId)
            ?: return false

        // 管理员拥有所有权限
        if (user.role == "admin") {
            return true
        }

        // 基于角色的权限控制
        val rolePermissions = mapOf(
            "doctor" to listOf("view_patient", "create_report", "edit_own_report", "prescribe_medication"),
            "admin" to listOf("*")  // 所有权限
        )

        val permissions = rolePermissions[user.role] ?: return false

        return permissions.contains("*") || permissions.contains(permission)
    }

    /**
     * 检查用户是否为管理员
     *
     * @param userId
     * @return Boolean
     */
    suspend fun isAdmin(userId: String): Boolean {
        val user = userRepository.findUserById(userId)
            ?: return false

        return user.role == "admin"
    }

    /**
     * 检查用户是否可以访问指定用户的数据
     * 规则：
     * 1. 管理员可以访问所有用户数据
     * 2. 用户可以访问自己的数据
     * 3. 同科室医生可以访问基本信息
     *
     * @param requesterId 请求者ID
     * @param targetId 目标用户ID
     * @return Boolean
     */
    suspend fun canAccessUserData(requesterId: String, targetId: String): Boolean {
        // 管理员可以访问所有用户数据
        if (isAdmin(requesterId)) {
            return true
        }

        // 用户可以访问自己的数据
        if (requesterId == targetId) {
            return true
        }

         // 同科室医生可以访问基本信息
         val requester = userRepository.findUserById(requesterId)
         val target = userRepository.findUserById(targetId)
         return requester?.hospitalId == target?.hospitalId &&
                requester?.deptCode == target?.deptCode
    }

    /**
     * 检查用户是否可以执行特定操作
     * @param userId 用户ID
     * @param action 操作类型
     * @param resourceType 资源类型
     * @return Boolean
     */
    suspend fun canPerformAction(userId: String, action: String, resourceType: String): Boolean {
        val user = userRepository.findUserById(userId)
            ?: return false

        // 管理员可以执行所有操作
        if (user.role == "admin") {
            return true
        }

        // 根据角色和操作类型进行权限检查
        return when (user.role) {
            "doctor" -> {
                when (resourceType) {
                    "patient" -> listOf("view", "create", "update").contains(action)
                    "report" -> listOf("view", "create", "update").contains(action)
                    "prescription" -> listOf("view", "create").contains(action)
                    else -> false
                }
            }
            "nurse" -> {
                when (resourceType) {
                    "patient" -> listOf("view", "update_vitals").contains(action)
                    "vital_signs" -> listOf("view", "create", "update").contains(action)
                    else -> false
                }
            }
            "receptionist" -> {
                when (resourceType) {
                    "patient" -> listOf("view", "create").contains(action)
                    "appointment" -> listOf("view", "create", "update", "cancel").contains(action)
                    else -> false
                }
            }
            else -> false
        }
    }

    /**
     * 刷新 Token
     * @param userId 用户ID
     * @return String 新的 JWT token
     */
    suspend fun refreshToken(userId: String): String {
        val user = userRepository.findUserById(userId)
            ?: throw Exception("用户不存在")

        return jwtUtil.generateToken(user.id, user.username?:"", mapOf("role" to user.role))
    }

    /**
     * 验证 Token 并获取用户信息
     * @param token JWT token
     * @return UserDto.UserInfo
     */
    suspend fun validateTokenAndGetUser(token: String): UserDto.UserInfo {
        val userId = jwtUtil.getUserIdFromToken(token)?:throw Exception("Token中缺少用户ID")

        return userRepository.findUserById(userId)
            ?: throw Exception("用户不存在")
    }

    /**
     * 获取用户统计信息
     * @param hospitalId 医院ID（可选）
     * @return Map<String, Int> 统计信息
     */
    suspend fun getUserStatistics(hospitalId: String? = null): Map<String, Any> {  // 修改返回类型以匹配实际实现
        return userRepository.getUserStatistics(hospitalId)
    }

    /**
     * 验证用户状态
     * @param userId 用户ID
     * @return Boolean 用户是否处于正常状态（未被删除等）
     */
    suspend fun validateUserStatus(userId: String): Boolean {
        val user = userRepository.findUserById(userId)
            ?: return false

        // 检查用户是否被删除（软删除）
        return !user.isDeleted
    }

}