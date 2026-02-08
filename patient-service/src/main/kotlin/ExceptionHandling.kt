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
        exception<PatientException> { call, throwable ->
            when (throwable) {
                is PatientException.PatientNotFoundException -> {
                    call.respond(
                        HttpStatusCode.NotFound,
                        JsonObject(mapOf(
                            "code" to JsonPrimitive(throwable.code),
                            "message" to JsonPrimitive(throwable.message)
                        ))
                    )
                }
                is PatientException.PatientAlreadyExistsException -> {
                    call.respond(
                        HttpStatusCode.Conflict,
                        JsonObject(mapOf(
                            "code" to JsonPrimitive(throwable.code),
                            "message" to JsonPrimitive(throwable.message)
                        ))
                    )
                }
                is PatientException.PatientDataInvalidException -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        JsonObject(mapOf(
                            "code" to JsonPrimitive(throwable.code),
                            "message" to JsonPrimitive(throwable.message)
                        ))
                    )
                }
                is PatientException.DoctorIdInvalidException -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        JsonObject(mapOf(
                            "code" to JsonPrimitive(throwable.code),
                            "message" to JsonPrimitive(throwable.message)
                        ))
                    )
                }
                is PatientException.HospitalIdInvalidException -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        JsonObject(mapOf(
                            "code" to JsonPrimitive(throwable.code),
                            "message" to JsonPrimitive(throwable.message)
                        ))
                    )
                }
                is PatientException.DepartmentInvalidException -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        JsonObject(mapOf(
                            "code" to JsonPrimitive(throwable.code),
                            "message" to JsonPrimitive(throwable.message)
                        ))
                    )
                }
                is PatientException.PermissionDeniedException -> {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        JsonObject(mapOf(
                            "code" to JsonPrimitive(throwable.code),
                            "message" to JsonPrimitive(throwable.message)
                        ))
                    )
                }
                is PatientException.CreatePatientFailedException -> {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        JsonObject(mapOf(
                            "code" to JsonPrimitive(throwable.code),
                            "message" to JsonPrimitive(throwable.message)
                        ))
                    )
                }
                is PatientException.UpdatePatientFailedException -> {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        JsonObject(mapOf(
                            "code" to JsonPrimitive(throwable.code),
                            "message" to JsonPrimitive(throwable.message)
                        ))
                    )
                }
                is PatientException.DeletePatientFailedException -> {
                    call.respond(
                        HttpStatusCode.InternalServerError,
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
                            "code" to JsonPrimitive(1500),
                            "message" to JsonPrimitive("未知错误")
                        ))
                    )
                }
            }
        }

        // 处理其他常见异常类型
        exception<IllegalArgumentException> { call, throwable ->
            call.application.environment.log.error("参数验证失败: ${throwable.message}", throwable)
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
            throwable.printStackTrace()
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