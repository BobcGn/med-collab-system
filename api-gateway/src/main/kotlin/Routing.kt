package com.example

import com.example.GatewayUtil.forwardRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable



/**
 * 路由配置
 * 定义网关的所有路由规则
 */
fun Application.configureRouting() {
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

        // 测试JWT端点（用于调试）
        get("/test-jwt") {
            val authHeader = call.request.headers["Authorization"]
            call.application.environment.log.info("测试JWT: Authorization=$authHeader")
            call.respond(
                mapOf(
                    "hasAuthorization" to (authHeader != null),
                    "authorizationHeader" to (authHeader?.take(50) ?: "null")
                )
            )
        }

        // 指标端点 (用于 Prometheus 监控)
        get("/metrics") {
            call.respondText("Prometheus metrics endpoint", ContentType.Text.Plain)
        }
        // 服务路由 - 认证服务(公开访问)
        route("/api/auth") {
            get("/{...}") {
                forwardToService(call, "auth", requireAuth = false)
            }
            post("/{...}") {
                forwardToService(call, "auth", requireAuth = false)
            }
            put("/{...}") {
                forwardToService(call, "auth", requireAuth = false)
            }
            delete("/{...}") {
                forwardToService(call, "auth", requireAuth = false)
            }
        }

        // 服务路由 - 患者服务(需要认证)
        route("/patients") {
            authenticate("jwt") {
                get("/{...}") {
                    call.application.environment.log.info("患者服务GET请求: ${call.request.path()}")
                    forwardToService(call, "patient", requireAuth = true)
                }
                post("/{...}") {
                    call.application.environment.log.info("患者服务POST请求: ${call.request.path()}")
                    forwardToService(call, "patient", requireAuth = true)
                }
                put("/{...}") {
                    call.application.environment.log.info("患者服务PUT请求: ${call.request.path()}")
                    forwardToService(call, "patient", requireAuth = true)
                }
                delete("/{...}") {
                    call.application.environment.log.info("患者服务DELETE请求: ${call.request.path()}")
                    forwardToService(call, "patient", requireAuth = true)
                }
            }
        }

        // 服务路由 - 指标服务(需要认证)
        route("/metric") {
            authenticate("jwt") {
                get("/{...}") {
                    forwardToService(call, "metric", requireAuth = true)
                }
                post("/{...}") {
                    forwardToService(call, "metric", requireAuth = true)
                }
                put("/{...}") {
                    forwardToService(call, "metric", requireAuth = true)
                }
                delete("/{...}") {
                    forwardToService(call, "metric", requireAuth = true)
                }
            }
        }

        // 服务路由 - 报告服务(需要认证)
        route("/report") {
            authenticate("jwt") {
                get("/{...}") {
                    forwardToService(call, "report", requireAuth = true)
                }
                post("/{...}") {
                    forwardToService(call, "report", requireAuth = true)
                }
                put("/{...}") {
                    forwardToService(call, "report", requireAuth = true)
                }
                delete("/{...}") {
                    forwardToService(call, "report", requireAuth = true)
                }
            }
        }

        // 404处理 - 支持所有HTTP方法
        route("{...}") {
            handle {
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
}

/**
 * 转发请求到指定服务
 */
private suspend fun forwardToService(
    call: ApplicationCall,
    serviceName: String,
    requireAuth: Boolean
) {
    val service = ServiceConfig.services[serviceName]
        ?: run {
            call.application.environment.log.error("服务未注册: $serviceName")
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

    // 修复路径拼接问题，避免双斜杠
    // 特别处理：当pathWithoutPrefix为空时，确保目标路径正确
    val targetPath = if (pathWithoutPrefix.isEmpty()) {
        // 当路径前缀与原始路径完全匹配时，保持服务的基本路径结构
        service.baseUrl
    } else if (pathWithoutPrefix.startsWith("/") || service.baseUrl.endsWith("/")) {
        "${service.baseUrl}$pathWithoutPrefix"
    } else {
        "${service.baseUrl}/$pathWithoutPrefix"
    }

    // 对于患者服务，确保请求转发到正确的路径
    val normalizedPath = if (serviceName == "patient") {
        if (targetPath == service.baseUrl) {
            "${service.baseUrl}/patients"
        } else if (!targetPath.contains("/patients")) {
            "${service.baseUrl}/patients${pathWithoutPrefix}"
        } else {
            targetPath
        }
    } else {
        targetPath
    }
    val targetUrl = "$normalizedPath${if (!call.request.queryString().isNullOrEmpty()) "?${call.request.queryString()}" else ""}"

    call.application.environment.log.info("转发请求: $serviceName, 原始路径: $originalPath, 目标URL: $targetUrl")

    // 转发请求
    call.forwardRequest(targetUrl, call.request.headers)
}