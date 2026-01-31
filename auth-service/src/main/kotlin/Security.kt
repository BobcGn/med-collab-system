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
    var realm = jwtConfig.property("realm").getString()

    install(Authentication) {
        jwt("jwt") {
            this@configureSecurity.environment.log.info("配置JWT认证")

            realm = this@configureSecurity.environment.config.config("jwt").property("realm").getString()

            // 配置验证器
            verifier {
                val algorithm = com.auth0.jwt.algorithms.Algorithm.HMAC256(secret)
                com.auth0.jwt.JWT.require(algorithm)
                    .withIssuer(issuer)
                    .withAudience(audience)
                    .build()
            }

            // 验证函数：检查令牌的 audience 是否匹配
            validate { credential ->
                val aud = credential.payload.audience
                if (aud.contains(audience)) {
                    JWTPrincipal(credential.payload)
                } else {
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
