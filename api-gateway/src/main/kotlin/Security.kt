package com.example

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

/**
 * 配置 JWT 认证
 *
 * 此函数配置 JWT 验证器，用于验证传入的 JWT 令牌
 */
fun Application.configureSecurity() {
    val jwtConfig = environment.config.config("jwt")
    val secret = jwtConfig.property("secret").getString()
    val issuer = jwtConfig.property("issuer").getString()
    val audience = jwtConfig.property("audience").getString()

    install(Authentication) {
        jwt("jwt") {
            this@configureSecurity.environment.log.info("配置JWT认证: issuer=$issuer, audience=$audience")

            realm = this@configureSecurity.environment.config.config("jwt").property("realm").getString()

            // 配置验证器
            verifier {
                this@configureSecurity.environment.log.info("初始化JWT验证器")
                val algorithm = com.auth0.jwt.algorithms.Algorithm.HMAC256(secret)
                com.auth0.jwt.JWT.require(algorithm)
                    .withIssuer(issuer)
                    .withAudience(audience)
                    .build()
            }

            // 验证函数：检查令牌的 audience 是否匹配
            validate { credential ->
                this@configureSecurity.environment.log.info("验证JWT令牌: subject=${credential.payload.subject}")
                val aud = credential.payload.audience
                this@configureSecurity.environment.log.info("令牌audience: $aud, 配置audience: $audience")
                if (aud.contains(audience)) {
                    this@configureSecurity.environment.log.info("JWT验证通过")
                    JWTPrincipal(credential.payload)
                } else {
                    this@configureSecurity.environment.log.warn("JWT验证失败: audience不匹配")
                    null
                }
            }

            // 令牌过期时拒绝
            skipWhen { call ->
                // 对于公开端点，可以通过路由配置跳过验证
                false
            }
        }
    }
}
