package com.petcare.app

import com.petcare.app.data.remote.*
import com.petcare.app.domain.repository.ApiException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class HttpTransportContractTest {
    @Test fun authAndQueryAreSentByThePortableTransport() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("Bearer demo-token", request.headers[HttpHeaders.Authorization])
            assertEquals("/task", request.url.encodedPath)
            assertEquals("2", request.url.parameters["space_id"])
            assertEquals("20", request.url.parameters["pet_id"])
            respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(HttpTimeout) }
        val transport = KtorHttpTransport("https://example.invalid", TokenProvider { "demo-token" }, client)
        try {
            assertEquals(200, transport.send("GET", "/task", mapOf("space_id" to "2", "pet_id" to "20")).code)
        } finally { transport.close() }
    }
    @Test fun unauthorizedIsNotHiddenByTheTransport() = runTest {
        val client = HttpClient(MockEngine {
            respond("""{"code":"unauthorized","message":"Войдите снова","fields":[]}""", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(HttpTimeout) }
        val transport = KtorHttpTransport("https://example.invalid", TokenProvider { null }, client)
        try {
            val error = assertFailsWith<ApiException> { PetCareApi(transport).getSpaces() }
            assertEquals(401, error.httpCode)
        } finally { transport.close() }
    }
}
