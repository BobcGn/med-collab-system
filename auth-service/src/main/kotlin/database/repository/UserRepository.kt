package com.example.database.repository


import database.entity.UserEntity
import database.table.Users
import dto.UserDto
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

class UserRepository {
    /**
     * 创建新用户
     */
    suspend fun createUser(user: UserDto.UserCreate): String{
        return try {
            transaction {
                val id = Users.insert {
                    it[hospitalId] = user.hospitalId
                    it[deptCode] = user.deptCode
                    it[userSeq] = user.userSeq
                    it[username] = user.username
                    it[fullName] = user.fullName
                    it[passwordHash] = user.passwordHash
                    it[role] = user.role
                } get Users.id
                id.value
            }
        } catch (e: Exception) {
            throw Exception("创建用户失败")
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
                    UserDto.UserInfo(
                        id = entity.id.value,
                        hospitalId = entity.hospitalId,
                        deptCode = entity.deptCode,
                        userSeq = entity.userSeq,
                        username = entity.username,
                        fullName = entity.fullName,
                        role = entity.role,
                        createdAt = entity.createdAt.toString(),
                        updatedAt = entity.updatedAt?.toString()
                    )
                }
            }
        }catch (e: Exception){
            throw Exception("查找用户失败")
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
                        updatedAt = entity.updatedAt?.toString()
                    )
                }
            }
        }catch (e: Exception){
            throw Exception("查找用户失败")
        }
    }

    /**
     * 根据账号查找用户（不包含密码哈希）
     * @param username
     * @return UserDto.UserInfo?
     */
    suspend fun findUserByUsername(username: String): UserDto.UserInfo? {
        return try {
            transaction {
                UserEntity.find{ Users.username eq username  }
                    .firstOrNull()
                    ?.let{entity ->
                        UserDto.UserInfo(
                            id = entity.id.value,
                            hospitalId = entity.hospitalId,
                            deptCode = entity.deptCode,
                            userSeq = entity.userSeq,
                            username = entity.username,
                            fullName = entity.fullName,
                            role = entity.role,
                            createdAt = entity.createdAt.toString(),
                            updatedAt = entity.updatedAt?.toString()
                        )
                    }
            }
        }catch (e: Exception){
            throw Exception("查找用户失败")
        }
    }

    /**
     * 根据账号查找用户（包含密码哈希，用于登录验证）
     * @param username
     * @return UserDto.UserInfoWithCredentials?
     */
    suspend fun findUserByUsernameWithCredentials(username: String): UserDto.UserInfoWithCredentials? {
        return try {
            transaction {
                UserEntity.find{ Users.username eq username  }
                    .firstOrNull()
                    ?.let{entity ->
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
                            updatedAt = entity.updatedAt?.toString()
                        )
                    }
            }
        }catch (e: Exception){
            throw Exception("查找用户失败")
        }
    }

    /**
     * 更新用户信息
     * @param userUpdate
     * @return Boolean
     */
    suspend fun updateUser(userUpdate: UserDto.UserUpdate): Boolean{
        return try {
            transaction {
                UserEntity.findById(userUpdate.id)?.let{ user ->
                    userUpdate.fullName?.let{ user.fullName = it}
                    userUpdate.passwordHash?.let { user.passwordHash = it }
                    userUpdate.role?.let { user.role = it }
                    true
                }?: false
            }
        }catch (e: Exception){
            throw Exception("更新用户失败")
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
        }catch (e: Exception){
            throw Exception("删除用户失败")
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
                    (Users.hospitalId eq hospitalId) and (Users.deptCode eq deptCode)
                }.map{
                    entity ->
                    UserDto.UserInfo(
                        id = entity.id.value,
                        hospitalId = entity.hospitalId,
                        deptCode = entity.deptCode,
                        userSeq = entity.userSeq,
                        username = entity.username,
                        fullName = entity.fullName,
                        role = entity.role,
                        createdAt = entity.createdAt.toString(),
                        updatedAt = entity.updatedAt?.toString()
                    )
                }
            }
        }catch (e: Exception){
            throw Exception("查找用户失败")
        }
    }

    /**
     * 检查账户是否存在
     * @param username
     * @return Boolean
     */
    suspend fun existsByUsername(username: String): Boolean{
        return try {
            transaction {
                !UserEntity.find { Users.username eq username }.empty()
            }
        }catch (e: Exception){
            throw Exception("检查用户失败")
        }
    }



    /**
     * 检查医院和科室是否存在
     * @param hospitalId
     * @param deptCode
     * @return Boolean
     */
    suspend fun existsByHospitalAndDept(hospitalId: String, deptCode: String): Boolean{
        return try {
            transaction {
                !UserEntity.find { (Users.hospitalId eq hospitalId) and (Users.deptCode eq deptCode) }.empty()
            }
        }catch (e: Exception){
            throw Exception("检查医院和科室失败")
        }
    }

    /**
     * 检查医院是否存在
     * @param hospitalId
     * @return Boolean
     */
    suspend fun existsByHospital(hospitalId: String): Boolean {
        return try {
            transaction {
                !UserEntity.find { Users.hospitalId eq hospitalId }.empty()
            }
        }catch (e: Exception){
            throw Exception("检查医院失败")
        }
    }
}