package com.example

import com.example.GatewayUtil.forwardRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import utils.JwtUtil



/**
 * 路由配置
 * 定义网关的所有路由规则
 */
fun Application.configureRouting() {
    val jwtUtil = JwtUtil(environment.config)
    routing{
        // 健康检查端点
        get("/health") {
            call.respond(
                HealthResponse(
                    status = "UP",
                    service = "api-gateway",
                    timestamp = System.currentTimeMillis()
                )
            )
        }

        // 指标端点 (用于 Prometheus 监控)
        get("/metrics") {
            call.respondText("Prometheus metrics endpoint", ContentType.Text.Plain)
        }
        // 服务路由 - 认证服务(公开访问)
        route("/auth") {
            get("/{...}") {
                forwardToService(call, "auth", jwtUtil, requireAuth = false)
            }
            post("/{...}") {
                forwardToService(call, "auth", jwtUtil, requireAuth = false)
            }
            put("/{...}") {
                forwardToService(call, "auth", jwtUtil, requireAuth = false)
            }
            delete("/{...}") {
                forwardToService(call, "auth", jwtUtil, requireAuth = false)
            }
        }

        // 服务路由 - 患者服务(需要认证)
        route("/patient") {
            authenticate("jwt") {
                get("/{...}") {
                    forwardToService(call, "patient", jwtUtil, requireAuth = true)
                }
                post("/{...}") {
                    forwardToService(call, "patient", jwtUtil, requireAuth = true)
                }
                put("/{...}") {
                    forwardToService(call, "patient", jwtUtil, requireAuth = true)
                }
                delete("/{...}") {
                    forwardToService(call, "patient", jwtUtil, requireAuth = true)
                }
            }
        }

        // 服务路由 - 指标服务(需要认证)
        route("/metric") {
            authenticate("jwt") {
                get("/{...}") {
                    forwardToService(call, "metric", jwtUtil, requireAuth = true)
                }
                post("/{...}") {
                    forwardToService(call, "metric", jwtUtil, requireAuth = true)
                }
                put("/{...}") {
                    forwardToService(call, "metric", jwtUtil, requireAuth = true)
                }
                delete("/{...}") {
                    forwardToService(call, "metric", jwtUtil, requireAuth = true)
                }
            }
        }

        // 服务路由 - 报告服务(需要认证)
        route("/report") {
            authenticate("jwt") {
                get("/{...}") {
                    forwardToService(call, "report", jwtUtil, requireAuth = true)
                }
                post("/{...}") {
                    forwardToService(call, "report", jwtUtil, requireAuth = true)
                }
                put("/{...}") {
                    forwardToService(call, "report", jwtUtil, requireAuth = true)
                }
                delete("/{...}") {
                    forwardToService(call, "report", jwtUtil, requireAuth = true)
                }
            }
        }

        // 404处理
        get("{...}") {
            call.respond(
                HttpStatusCode.NotFound,
                StandardResponse(
                    success = false,
                    code = 404,
                    message = "接口不存在",
                    data = null
                )
            )
        }
    }
}

/**
 * 转发请求到指定服务
 */
private suspend fun forwardToService(
    call: ApplicationCall,
    serviceName: String,
    jwtUtil: JwtUtil,
    requireAuth: Boolean
) {
    val service = ServiceConfig.services[serviceName]
        ?: run {
            call.respond(
                HttpStatusCode.InternalServerError,
                StandardResponse(
                    success = false,
                    code = 500,
                    message = "服务未注册",
                    data = null
                )
            )
            return
        }

    // 构建目标URL
    val originalPath = call.request.path()
    val pathWithoutPrefix = originalPath.substring(service.pathPrefix.length)
    val targetUrl = "${service.baseUrl}$pathWithoutPrefix${if (call.request.queryString().isNotEmpty()) "?${call.request.queryString()}" else ""}"

    // 转发请求
    call.forwardRequest(targetUrl, call.request.headers)
}