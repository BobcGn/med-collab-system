package com.example

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import utils.JwtUtil


/**
 * 安全配置
 * 配置JWT认证
 */
fun Application.configureSecurity() {
    val jwtUtil = JwtUtil(environment.config)
    val jwtConfig = environment.config.config("jwt")

    authentication {
        jwt("jwt") {
            realm = jwtConfig.property("realm").getString()

            // JWT 验证器 - 使用 JwtUtil 类提供的验证逻辑
            validate { credential ->
                try {
                    // 从凭证中提取用户信息
                    val userId = credential.payload.getClaim("userId").asString()
                    val username = credential.payload.getClaim("username").asString()

                    // 在validate函数外部无法直接访问call，所以我们直接验证JWT凭证的有效性
                    if (userId != null && userId.isNotEmpty()) {
                        JWTPrincipal(credential.payload)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }

            // 认证失败的处理
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    StandardResponse(
                        success = false,
                        code = 401,
                        message = "未授权或令牌已过期",
                        data = null
                    )
                )
            }
        }
    }
}
