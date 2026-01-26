package dto

sealed class Response {
    abstract val code: Int
    abstract val message: String
    abstract val data: Any?
    abstract val success: Boolean

    /**
     * 成功响应
     */
    data class Success(
        override val code: Int,
        override val message: String,
        override val data: Any?
    ) : Response() {
        override val success: Boolean = true
    }

    /**
     * 错误响应
     */
    data class Error(
        override val code: Int,
        override val message: String,
        override val data: Any?
    ) : Response() {
        override val success: Boolean = false
    }

    /**
     * 未授权响应
     */
    data class Unauthorized(
        override val code: Int = 401,
        override val message: String = "Unauthorized",
        override val data: Any? = null
    ) : Response() {
        override val success: Boolean = false
    }
}