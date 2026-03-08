package utils

import com.auth0.jwt.JWTVerifier
import io.ktor.server.config.*
import java.util.*

/**
 * JWT 工具类 - 使用 Ktor JWT 实现
 */
class JwtUtil(config: ApplicationConfig) {
    private val secret: String = config.property("jwt.secret").getString()
    private val jwtExpiration: Long = config.property("jwt.expiration").getString().toLong()
    private val audience: String = config.property("jwt.audience").getString()
    private val issuer: String = config.property("jwt.issuer").getString()

    private val verifier: JWTVerifier by lazy {
        val algorithm = com.auth0.jwt.algorithms.Algorithm.HMAC256(secret)
        com.auth0.jwt.JWT.require(algorithm)
            .withIssuer(issuer)
            .withAudience(audience)
            .build()
    }

    /**
     * 为指定用户生成JWT令牌，携带自定义声明
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param customClaims 自定义声明
     * @return 生成的JWT令牌字符串
     */
    fun generateToken(userId: String, username: String, customClaims: Map<String, Any>): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtExpiration * 1000)

        val algorithm = com.auth0.jwt.algorithms.Algorithm.HMAC256(secret)

        val tokenBuilder = com.auth0.jwt.JWT.create()
            .withSubject(userId)
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("username", username)
            .withIssuedAt(now)
            .withExpiresAt(expiryDate)

        // 添加自定义声明
        customClaims.forEach { (key, value) ->
            when (value) {
                is String -> tokenBuilder.withClaim(key, value)
                is Int -> tokenBuilder.withClaim(key, value)
                is Double -> tokenBuilder.withClaim(key, value)
                is Long -> tokenBuilder.withClaim(key, value)
                is Boolean -> tokenBuilder.withClaim(key, value)
                else -> tokenBuilder.withClaim(key, value.toString())
            }
        }

        return tokenBuilder.sign(algorithm)
    }

    /**
     * 验证令牌是否有效
     *
     * @param token 待验证的JWT令牌
     * @return 令牌有效返回true，否则返回false
     */
    fun validateToken(token: String): Boolean {
        return try {
            verifier.verify(token)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 从令牌中提取用户ID
     *
     * @param token JWT令牌
     * @return 用户ID，如果解析失败则返回null
     */
    fun getUserIdFromToken(token: String): String? {
        return try {
            val principal = verifier.verify(token)
            principal.subject
        } catch (e: Exception) {
            println("解析令牌失败: ${e.message}")
            null
        }
    }

    /**
     * 从令牌中提取用户名
     *
     * @param token JWT令牌
     * @return 用户名，如果解析失败则返回null
     */
    fun getUsernameFromToken(token: String): String? {
        return try {
            val principal = verifier.verify(token)
            principal.getClaim("username").asString()
        } catch (e: Exception) {
            println("解析用户名失败: ${e.message}")
            null
        }
    }

    /**
     * 从令牌中获取自定义声明
     *
     * @param token JWT令牌
     * @param key 声明键
     * @return 声明值，如果不存在或解析失败则返回null
     */
    fun <T> getClaimFromToken(token: String, key: String, clazz: Class<T>): T? {
        return try {
            val principal = verifier.verify(token)
            val claim = principal.getClaim(key)
            if (claim.isNull) {
                null
            } else {
                when (clazz) {
                    String::class.java -> claim.asString() as T
                    Int::class.java -> claim.asInt() as T
                    Long::class.java -> claim.asLong() as T
                    Boolean::class.java -> claim.asBoolean() as T
                    Double::class.java -> claim.asDouble() as T
                    else -> claim.asString() as T
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 检查令牌是否已过期
     *
     * @param token JWT令牌
     * @return 如果令牌已过期返回true，否则返回false
     */
    fun isTokenExpired(token: String): Boolean {
        return try {
            val principal = verifier.verify(token)
            principal.expiresAt.before(Date())
        } catch (e: Exception) {
            true
        }
    }

    /**
     * 从令牌中获取过期时间
     *
     * @param token JWT令牌
     * @return 令牌的过期日期
     */
    fun getExpirationDateFromToken(token: String): Date? {
        return try {
            val principal = verifier.verify(token)
            principal.expiresAt
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 从令牌中获取签发时间
     *
     * @param token JWT令牌
     * @return 令牌的签发日期
     */
    fun getIssuedAtDateFromToken(token: String): Date? {
        return try {
            val principal = verifier.verify(token)
            principal.issuedAt
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 刷新令牌
     *
     * @param token 原始令牌
     * @return 新的JWT令牌
     */
    fun refreshToken(token: String): String? {
        return try {
            val principal = verifier.verify(token)
            val userId = principal.subject ?: return null
            val username = principal.getClaim("username").asString() ?: return null

            // 获取自定义声明，排除标准声明
            val customClaims = mutableMapOf<String, Any>()
            val payload = principal.claims
            for (entry in payload) {
                if (entry.key !in listOf("sub", "username", "iat", "exp", "aud", "iss")) {
                    customClaims[entry.key] = entry.value
                }
            }

            generateToken(userId, username, customClaims)
        } catch (e: Exception) {
            println("刷新令牌失败: ${e.message}")
            null
        }
    }
}
