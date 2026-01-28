package com.example

import exception.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<AuthException> { call, throwable ->
            when (throwable) {
                is AuthException.UserNotFoundException -> {
                    call.respond(
                        HttpStatusCode.NotFound,
                        JsonObject(mapOf(
                            "code" to JsonPrimitive(throwable.code),
                            "message" to JsonPrimitive(throwable.message)
                        ))
                    )
                }
                is AuthException.UserAlreadyExistsException -> {
                    call.respond(
                        HttpStatusCode.Conflict,
                        JsonObject(mapOf(
                            "code" to JsonPrimitive(throwable.code),
                            "message" to JsonPrimitive(throwable.message)
                        ))
                    )
                }
                is AuthException.CreateUserFailedException -> {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        JsonObject(mapOf(
                            "code" to JsonPrimitive(throwable.code),
                            "message" to JsonPrimitive(throwable.message)
                        ))
                    )
                }
                is AuthException.LoginFailedException -> {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        JsonObject(mapOf(
                            "code" to JsonPrimitive(throwable.code),
                            "message" to JsonPrimitive(throwable.message)
                        ))
                    )
                }
                is AuthException.TokenInvalidException -> {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        JsonObject(mapOf(
                            "code" to JsonPrimitive(throwable.code),
                            "message" to JsonPrimitive(throwable.message)
                        ))
                    )
                }
                is AuthException.TokenExpiredException -> {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        JsonObject(mapOf(
                            "code" to JsonPrimitive(throwable.code),
                            "message" to JsonPrimitive(throwable.message)
                        ))
                    )
                }
                is AuthException.PermissionDeniedException -> {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        JsonObject(mapOf(
                            "code" to JsonPrimitive(throwable.code),
                            "message" to JsonPrimitive(throwable.message)
                        ))
                    )
                }
                is AuthException.CannotDeleteSelfException -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        JsonObject(mapOf(
                            "code" to JsonPrimitive(throwable.code),
                            "message" to JsonPrimitive(throwable.message)
                        ))
                    )
                }
                is AuthException.HospitalIdInvalidException -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        JsonObject(mapOf(
                            "code" to JsonPrimitive(throwable.code),
                            "message" to JsonPrimitive(throwable.message)
                        ))
                    )
                }
                is AuthException.DepartmentIdInvalidException -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        JsonObject(mapOf(
                            "code" to JsonPrimitive(throwable.code),
                            "message" to JsonPrimitive(throwable.message)
                        ))
                    )
                }
                is AuthException.HospitalOrDepartmentIdInvalidException -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        JsonObject(mapOf(
                            "code" to JsonPrimitive(throwable.code),
                            "message" to JsonPrimitive(throwable.message)
                        ))
                    )
                }
                is AuthException.UserIdInvalidException -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        JsonObject(mapOf(
                            "code" to JsonPrimitive(throwable.code),
                            "message" to JsonPrimitive(throwable.message)
                        ))
                    )
                }
                is AuthException.RoleInvalidException -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        JsonObject(mapOf(
                            "code" to JsonPrimitive(throwable.code),
                            "message" to JsonPrimitive(throwable.message)
                        ))
                    )
                }
                is AuthException.PasswordTooWeakException -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        JsonObject(mapOf(
                            "code" to JsonPrimitive(throwable.code),
                            "message" to JsonPrimitive(throwable.message)
                        ))
                    )
                }
                is AuthException.OldPasswordIncorrectException -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        JsonObject(mapOf(
                            "code" to JsonPrimitive(throwable.code),
                            "message" to JsonPrimitive(throwable.message)
                        ))
                    )
                }
                is AuthException.NewPasswordSameAsOldPasswordException -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        JsonObject(mapOf(
                            "code" to JsonPrimitive(throwable.code),
                            "message" to JsonPrimitive(throwable.message)
                        ))
                    )
                }
                is AuthException.AccountDisabledException -> {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        JsonObject(mapOf(
                            "code" to JsonPrimitive(throwable.code),
                            "message" to JsonPrimitive(throwable.message)
                        ))
                    )
                }
                is AuthException.AccountFrozenException -> {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        JsonObject(mapOf(
                            "code" to JsonPrimitive(throwable.code),
                            "message" to JsonPrimitive(throwable.message)
                        ))
                    )
                }
                else -> {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        JsonObject(mapOf(
                            "code" to JsonPrimitive(500),
                            "message" to JsonPrimitive("未知错误")
                        ))
                    )
                }
            }
        }

        // 处理其他常见异常类型
        exception<IllegalArgumentException> { call, throwable ->
            call.respond(
                HttpStatusCode.BadRequest,
                JsonObject(mapOf(
                    "code" to JsonPrimitive(400),
                    "message" to JsonPrimitive(throwable.message ?: "请求参数错误")
                ))
            )
        }

        exception<IllegalStateException> { call, throwable ->
            call.respond(
                HttpStatusCode.InternalServerError,
                JsonObject(mapOf(
                    "code" to JsonPrimitive(500),
                    "message" to JsonPrimitive(throwable.message ?: "服务器内部错误")
                ))
            )
        }

        exception<Throwable> { call, throwable ->
            call.respond(
                HttpStatusCode.InternalServerError,
                JsonObject(mapOf(
                    "code" to JsonPrimitive(500),
                    "message" to JsonPrimitive("服务器内部错误: ${throwable.message}")
                ))
            )
        }
    }
}
