package utils

import io.jsonwebtoken.*
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.SecurityException
import io.ktor.server.config.ApplicationConfig
import javax.crypto.SecretKey
import java.util.*

class JwtUtil(config: ApplicationConfig) {
    private val secret: String = config.property("jwt.secret").getString()
    private val jwtExpiration: Long = config.property("jwt.expiration").getString().toLong()
    private val audience: String = config.property("jwt.audience").getString()
    private val issuer: String = config.property("jwt.issuer").getString()

    private val secretKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(secret.toByteArray())
    }

    /**
     * 为指定用户生成JWT令牌
     *
     * @param userId 用户ID
     * @param username 用户名
     * @return 生成的JWT令牌字符串
     */
    fun generateToken(userId: String, username: String): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtExpiration * 1000)

        return Jwts.builder()
            .setSubject(userId)
            .claim("username", username)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .setIssuer(issuer)
            .setAudience(audience)
            .signWith(secretKey, SignatureAlgorithm.HS512)
            .compact()
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

        return Jwts.builder()
            .setSubject(userId)
            .claim("username", username)
            .addClaims(customClaims)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .setIssuer(issuer)
            .setAudience(audience)
            .signWith(secretKey, SignatureAlgorithm.HS512)
            .compact()
    }

    /**
     * 验证令牌是否有效
     *
     * @param token 待验证的JWT令牌
     * @return 令牌有效返回true，否则返回false
     */
    fun validateToken(token: String): Boolean {
        try {
            parseToken(token)
            return true
        } catch (e: Exception) {
            return false
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
            parseToken(token).subject
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
            parseToken(token).get("username", String::class.java)
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
            parseToken(token).get(key, clazz)
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
            parseToken(token).expiration.before(Date())
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
            parseToken(token).expiration
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
            parseToken(token).issuedAt
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解析令牌中的声明信息
     *
     * @param token JWT令牌
     * @return 令牌中的声明信息
     * @throws ExpiredJwtException 令牌已过期
     * @throws UnsupportedJwtException 不支持的令牌
     * @throws MalformedJwtException 令牌格式错误
     * @throws SecurityException 签名验证失败
     * @throws IllegalArgumentException 令牌为空
     */
    private fun parseToken(token: String): Claims {
        return Jwts.parserBuilder()
            .setSigningKey(secretKey)
            .requireAudience(audience)
            .requireIssuer(issuer)
            .build()
            .parseClaimsJws(token)
            .body
    }

    /**
     * 刷新令牌
     *
     * @param token 原始令牌
     * @return 新的JWT令牌
     */
    fun refreshToken(token: String): String? {
        return try {
            val claims = parseToken(token)
            val userId = claims.subject
            val username = claims.get("username", String::class.java)
            
            val customClaims = claims.entries
                .filter { it.key !in listOf("sub", "username", "iat", "exp", "iss", "aud") }
                .associate { it.key to it.value }
            
            generateToken(userId, username, customClaims)
        } catch (e: Exception) {
            println("刷新令牌失败: ${e.message}")
            null
        }
    }
}
