package exception

sealed class AuthException: Exception() {

    abstract val code: Int
    abstract override val message: String

    /**
     * 用户不存在异常
     * 错误码: 1001
     */
    data class UserNotFoundException(
        override val code: Int = 1001,
        override val message: String = "用户不存在"
    ) : AuthException()

    /**
     * 用户已存在异常
     * 错误码: 1002
     */
    data class UserAlreadyExistsException(
        override val code: Int = 1002,
        override val message: String = "用户已存在"
    ) : AuthException()

    /**
     * 创建用户失败异常
     * 错误码: 1003
     */
    data class CreateUserFailedException(
        override val code: Int = 1003,
        override val message: String = "创建用户失败"
    ) : AuthException()

// ===============================认证相关异常=======================================
    /**
     * 登录失败异常
     * 错误码: 2001
     */
    data class LoginFailedException(
        override val code: Int = 2001,
        override val message: String = "登录失败"
    ) : AuthException()

    /**
     * Token无效异常
     * 错误码: 2002
     */
    data class TokenInvalidException(
        override val code: Int = 2002,
        override val message: String = "Token无效"
    ) : AuthException()

    /**
     * Token 过期异常
     * 错误码: 2003
     */
    data class TokenExpiredException(
        override val code: Int = 2003,
        override val message: String = "Token 过期"
    ) : AuthException()

// ===============================权限相关异常=======================================
    /**
     * 无权限异常
     * 错误码: 3001
     */
    data class PermissionDeniedException(
        override val code: Int = 3001,
        override val message: String = "无权限"
    ) : AuthException()

    /**
     * 不能删除自己异常
     * 错误码: 3002
     */
    data class CannotDeleteSelfException(
        override val code: Int = 3002,
        override val message: String = "不能删除自己"
    ) : AuthException()

// ==================== 数据验证异常 ====================
    /**
     * 医院ID无效异常
     * 错误码: 4001
     */
    data class HospitalIdInvalidException(
        override val code: Int = 4001,
        override val message: String = "医院ID无效"
    ) : AuthException()

    /**
     * 科室ID无效异常
     * 错误码: 4002
     */
    data class DepartmentIdInvalidException(
        override val code: Int = 4002,
        override val message: String = "科室ID无效"
    ) : AuthException()

    /**
     * 医院或科室数据无效异常
     * 错误码: 4003
     */
    data class HospitalOrDepartmentIdInvalidException(
        override val code: Int = 4003,
        override val message: String = "医院或科室数据无效"
    ) : AuthException()

    /**
     * 用户数据无效异常
     * 错误码: 4004
     */
    data class UserIdInvalidException(
        override val code: Int = 4003,
        override val message: String = "用户数据无效"
    ) : AuthException()

    /**
     * 角色无效异常
     * 错误码: 4005
     */
    data class RoleInvalidException(
        override val code: Int = 4005,
        override val message: String = "角色无效"
    ) : AuthException()
// ==================== 密码相关异常 ====================
    /**
     * 密码强度不足异常
     * 错误码: 5001
     */
    data class PasswordTooWeakException(
        override val code: Int = 5001,
        override val message: String = "密码强度不足"
    ) : AuthException()

    /**
     * 旧密码错误异常
     * 错误码: 5002
     */
    data class OldPasswordIncorrectException(
        override val code: Int = 5002,
        override val message: String = "旧密码错误"
    ) : AuthException()

    /**
     * 新密码与旧密码相同异常
     */
    data class NewPasswordSameAsOldPasswordException(
        override val code: Int = 5003,
        override val message: String = "新密码与旧密码相同"
    ) : AuthException()

// ==================== 账户状态异常 ====================
    /**
     * 账户已被禁用异常
     * 错误码: 6001
     */
    data class AccountDisabledException(
        override val code: Int = 6001,
        override val message: String = "账户已被禁用"
    ): AuthException()

    /**
     * 账户被冻结异常
     * 错误码: 6002
     */
    data class AccountFrozenException(
        override val code: Int = 6002,
        override val message: String = "账户被冻结"
    ) : AuthException()
}