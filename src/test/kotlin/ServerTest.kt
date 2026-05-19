package com.example

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class ServerTest {

    @Test
    fun `test root endpoint`() = testApplication {
        application {
            configureSerialization()
            configureRouting()
        }
        assertEquals(HttpStatusCode.OK, client.get("/").status)
    }

}
