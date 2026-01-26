package dto

sealed class UserDto {
    /**
     * 用户完整信息数据类（不包含密码哈希，用于对外展示）
     */
    data class UserInfo(
        val id: String,
        val hospitalId: String,
        val deptCode: String,
        val userSeq: String,
        val username: String?,
        val fullName: String,
        val role: String,
        val createdAt: String,
        val updatedAt: String?
    ) : UserDto()

    /**
     * 用户信息数据类（包含密码哈希，用于内部验证）
     */
    data class UserInfoWithCredentials(
        val id: String,
        val hospitalId: String,
        val deptCode: String,
        val userSeq: String,
        val username: String?,
        val fullName: String,
        val role: String,
        val passwordHash: String,
        val createdAt: String,
        val updatedAt: String?
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
                updatedAt = updatedAt
            )
        }
    }

    /**
     * 用户登录数据类
     */
    data class UserLogin(
        val username: String,
        val password: String
    ) : UserDto()

    /**
     * 用户注册数据类
     */
    data class UserRegister(
        val hospitalId: String,
        val deptCode: String,
        val userSeq: String,
        val username: String?,
        val password: String,
        val fullName: String
    ) : UserDto()

    /**
     * 用户创建数据类
     */
    data class UserCreate(
        val hospitalId: String,
        val deptCode: String,
        val userSeq: String,
        val username: String?,
        val fullName: String,
        val passwordHash: String,
        val role: String = "doctor"
    ) : UserDto()

    /**
     * 用户更新数据类
     */
    data class UserUpdate(
        val id: String,
        val username: String? = null,
        val fullName: String? = null,
        val passwordHash: String? = null,
        val role: String? = null
    ) : UserDto()
}