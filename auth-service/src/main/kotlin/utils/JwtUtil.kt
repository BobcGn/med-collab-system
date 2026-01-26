package utils

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.time.Instant
import java.time.temporal.ChronoUnit

class JwtUtil(
    private val secret: String,
    private val issuer: String,
    private val audience: String,
    private val expirationDays: Long = 1
) {
    
    /**
     * 生成JWT token
     * @param userId 用户ID
     * @param username 用户名
     * @param role 用户角色
     * @return JWT token字符串
     */
    fun generateToken(userId: String, username: String, role: String): String {
        val algorithm = Algorithm.HMAC256(secret)
        val expiration = Instant.now().plus(expirationDays, ChronoUnit.DAYS)
        
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId)
            .withClaim("username", username)
            .withClaim("role", role)
            .withExpiresAt(expiration)
            .withIssuedAt(Instant.now())
            .sign(algorithm)
    }
    
    /**
     * 验证JWT token
     * @param token JWT token字符串
     * @return 是否有效
     */
    fun validateToken(token: String): Boolean {
        return try {
            val algorithm = Algorithm.HMAC256(secret)
            val verifier = JWT.require(algorithm)
                .withIssuer(issuer)
                .withAudience(audience)
                .build()
            
            verifier.verify(token)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getUserIdFromToken(token: String): String? {
        return try {
            val decodedJWT = JWT.decode(token)
            decodedJWT.claims["userId"]?.asString()
        } catch (e: Exception) {
            println("解析令牌失败: ${e.message}")
            null
        }
    }
}