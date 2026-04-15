package com.example

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import utils.JwtUtil

/**
 * 配置 JWT 认证
 *
 * 此函数配置 JWT 验证器，用于验证传入的 JWT 令牌
 */
fun Application.configureSecurity() {
    val jwtUtil = JwtUtil(environment.config)

    install(Authentication) {
        jwt("jwt") {
            this@configureSecurity.environment.log.info("配置JWT认证")
            realm = jwtUtil.getRealm()
            verifier(jwtUtil.getVerifier())

            // 验证函数：检查令牌的 audience 是否匹配
            validate { credential ->
                val aud = credential.payload.audience
                if (aud.contains(jwtUtil.getAudience())) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }
}
