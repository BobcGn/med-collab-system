package com.example

import database.table.Patients
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import utils.ensureMySqlDatabaseExists
import utils.isMySqlJdbcUrl
import utils.toTiDbCompatibleJdbcUrl

fun Application.configureDatabases() {
    val configuredDatabaseUrl = environment.config.property("database.mysql.url").getString()
    val databaseUrl = toTiDbCompatibleJdbcUrl(configuredDatabaseUrl)
    val databaseUser = environment.config.property("database.mysql.user").getString()
    val databasePassword = environment.config.property("database.mysql.password").getString()
    val databaseDriver = environment.config.property("database.mysql.driver").getString()

    if (databaseUrl != configuredDatabaseUrl) {
        log.info("Patient service applied TiDB JDBC compatibility parameters")
    }

    if (isMySqlJdbcUrl(databaseUrl)) {
        val databaseName = ensureMySqlDatabaseExists(
            databaseUrl = databaseUrl,
            databaseUser = databaseUser,
            databasePassword = databasePassword,
            databaseDriver = databaseDriver,
        )
        log.info("Patient service database ready: {}", databaseName)
    }

    val database = Database.connect(
        url = databaseUrl,
        user = databaseUser,
        password = databasePassword,
        driver = databaseDriver
    )

    transaction(database) {
        SchemaUtils.createMissingTablesAndColumns(Patients)
    }

    log.info("Patient service database initialized with {}", databaseDriver)
}
