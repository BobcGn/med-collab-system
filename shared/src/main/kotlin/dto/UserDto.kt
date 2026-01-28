package dto

import kotlinx.serialization.Serializable

@Serializable
sealed class UserDto {
    /**
     * 用户隶属医院和部门的数据类
     */
    @Serializable
    data class UserAffiliation(
        val hospitalId: String,
        val deptCode: String
    ): UserDto()

    /**
     * 用户完整信息数据类（不包含密码哈希，用于对外展示）
     */
    @Serializable
    data class UserInfo(
        val id: String,
        val hospitalId: String?,
        val deptCode: String?,
        val userSeq: String,
        val username: String?,
        val fullName: String,
        val role: String,
        val createdAt: String,
        val updatedAt: String?,
        val isDeleted: Boolean = false,
        val isFrozen: Boolean = false
    ) : UserDto()

    /**
     * 用户信息数据类（包含密码哈希，用于内部验证）
     */
    @Serializable
    data class UserInfoWithCredentials(
        val id: String,
        val hospitalId: String?,
        val deptCode: String?,
        val userSeq: String,
        val username: String?,
        val fullName: String,
        val role: String,
        val passwordHash: String,
        val createdAt: String,
        val updatedAt: String?,
        val isDeleted: Boolean = false,
        val isFrozen: Boolean = false
    ) : UserDto() {
        /**
         * 转换为不包含密码的 UserInfo
         */
        fun toUserInfo(): UserInfo {
            return UserInfo(
                id = id,
                hospitalId = hospitalId,
                deptCode = deptCode,
                userSeq = userSeq,
                username = username,
                fullName = fullName,
                role = role,
                createdAt = createdAt,
                updatedAt = updatedAt,
                isDeleted = isDeleted,
                isFrozen = isFrozen
            )
        }
    }

    /**
     * 用户登录数据类
     */
    @Serializable
    data class UserLogin(
        val username: String,
        val password: String
    ) : UserDto()

    /**
     * 用户注册数据类
     */
    @Serializable
    data class UserRegister(
        val role: String,
        val hospitalId: String? = null,
        val deptCode: String? = null,
        val username: String? = null,
        val password: String,
        val fullName: String
    ) : UserDto()

    /**
     * 用户创建数据类
     */
    @Serializable
    data class UserCreate(
        val hospitalId: String? = null,
        val deptCode: String? = null,
        val userSeq: String,
        val username: String?,
        val fullName: String,
        val passwordHash: String,
        val role: String = "doctor"
    ) : UserDto()

    /**
     * 用户更新数据类
     */
    @Serializable
    data class UserUpdate(
        val id: String? = null,
        val username: String? = null,
        val fullName: String? = null,
        val passwordHash: String? = null,
        val role: String? = null,
        val isFrozen: Boolean? = null
    ) : UserDto()

    /**
     * 用户登录响应数据类
     */
    @Serializable
    data class LoginResponse(
        val user: UserInfo,
        val token: String
    ) : UserDto()

    /**
     * 用户注册响应数据类
     */
    @Serializable
    data class RegisterResponse(
        val user: UserInfo,
        val message: String
    ) : UserDto()
}