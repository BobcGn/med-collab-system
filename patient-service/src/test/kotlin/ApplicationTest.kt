package com.example

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {

    @Test
    fun testHealth() = testApplication {
        environment {
            config = MapApplicationConfig(
                "jwt.audience" to "test-audience",
                "jwt.realm" to "test-realm",
                "jwt.secret" to "test-secret",
                "jwt.expiration" to "3600",
                "jwt.issuer" to "test-issuer",
                "database.mysql.url" to "jdbc:h2:mem:patient-service-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                "database.mysql.user" to "sa",
                "database.mysql.password" to "",
                "database.mysql.driver" to "org.h2.Driver",
            )
        }
        application {
            module()
        }
        client.get("/health").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }
}
