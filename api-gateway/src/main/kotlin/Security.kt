package com.example

import com.auth0.jwt.interfaces.Payload
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable
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
                // 直接从credential获取payload信息
                val userId = credential.payload.getClaim("userId").asString()
                val username = credential.payload.getClaim("username").asString()

                // 验证令牌有效性
                if (userId != null && jwtUtil.validateToken(credential.payload.subject)) {
                    // 创建包含用户信息的 JWTPrincipal
                    JWTPrincipal(credential.payload)
                } else {
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
