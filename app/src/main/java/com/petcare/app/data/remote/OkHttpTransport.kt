package com.petcare.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * HTTP через OkHttp. Отмена корутины отменяет запрос — так поздний ответ
 * для старого пространства или питомца даже не дочитывается.
 */
class OkHttpTransport(
    baseUrl: String,
    private val tokens: TokenProvider,
    timeoutSeconds: Long = 15,
) : HttpTransport {

    private val base = baseUrl.trimEnd('/')

    private val client = OkHttpClient.Builder()
        .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .build()

    override suspend fun send(
        method: String,
        path: String,
        query: Map<String, String>,
        body: String?,
    ): HttpResult {
        val url = (base + path).toHttpUrl().newBuilder().apply {
            query.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()

        val requestBody = when {
            body != null -> body.toRequestBody(JSON)
            method == "POST" || method == "PATCH" || method == "PUT" -> "".toRequestBody(JSON)
            else -> null
        }
        val request = Request.Builder()
            .url(url)
            .method(method, requestBody)
            .header("Accept", "application/json")
            .apply { tokens.token()?.let { header("Authorization", "Bearer $it") } }
            .build()

        return withContext(Dispatchers.IO) {
            val call = client.newCall(request)
            suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (cont.isActive) cont.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val result = try {
                            response.use { HttpResult(it.code, it.body?.string()) }
                        } catch (e: IOException) {
                            if (cont.isActive) cont.resumeWithException(e)
                            return
                        }
                        if (cont.isActive) cont.resume(result)
                    }
                })
            }
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
