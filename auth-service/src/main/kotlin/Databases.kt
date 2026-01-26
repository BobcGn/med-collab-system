package com.example

import io.ktor.server.application.*
import io.ktor.server.config.*
import org.jetbrains.exposed.sql.Database

fun Application.configureDatabases() {
    val database = Database.connect(
        url = environment.config.property("database.mysql.url").getString(),
        user = environment.config.property("database.mysql.user").getString(),
        driver = environment.config.property("database.mysql.driver").getString(),
        password = environment.config.property("database.mysql.password").getString(),
    )
}
