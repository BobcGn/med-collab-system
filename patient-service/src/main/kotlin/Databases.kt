package com.example

import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database

fun Application.configureDatabases() {
    val databaseUrl = environment.config.property("database.mysql.url").getString()
    val databaseUser = environment.config.property("database.mysql.user").getString()
    val databasePassword = environment.config.property("database.mysql.password").getString()
    val databaseDriver = environment.config.property("database.mysql.driver").getString()

    // 连接数据库
    val database = Database.connect(
        url = databaseUrl,
        user = databaseUser,
        password = databasePassword,
        driver = databaseDriver
    )
}