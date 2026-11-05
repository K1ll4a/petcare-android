package com.petcare.app.data.remote

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/** HTTP client for both platforms. Coroutine cancellation cancels the platform request. */
class KtorHttpTransport(
    baseUrl: String,
    private val tokens: TokenProvider,
    private val client: HttpClient = platformHttpClient(),
) : HttpTransport {
    private val base = baseUrl.trimEnd('/')
    override suspend fun send(method: String, path: String, query: Map<String, String>, body: String?): HttpResult {
        val response = client.request(base + path) {
            this.method = HttpMethod.parse(method)
            url { query.forEach { (key, value) -> parameters.append(key, value) } }
            accept(ContentType.Application.Json)
            tokens.token()?.let { bearerAuth(it) }
            if (body != null) { contentType(ContentType.Application.Json); setBody(body) }
            timeout { requestTimeoutMillis = 15_000; connectTimeoutMillis = 15_000; socketTimeoutMillis = 15_000 }
        }
        return HttpResult(response.status.value, response.bodyAsText())
    }
    fun close() = client.close()
}
internal expect fun platformHttpClient(): HttpClient
