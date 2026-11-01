package com.petcare.app.data.remote

import com.petcare.app.data.remote.dto.JsonCodec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * ВРЕМЕННО, только для разработки: пока нет экрана входа Mobile №1 (задача #13),
 * входит тестовым пользователем из seed сервера (POST /auth/login) и подставляет токен.
 * Когда появится вход — заменить на хранилище токена из #13.
 */
class DevLoginTransport(
    innerFactory: (TokenProvider) -> HttpTransport,
    private val email: String,
    private val password: String,
) : HttpTransport {

    @Volatile
    private var token: String? = null
    private val lock = Mutex()
    private val inner: HttpTransport = innerFactory(TokenProvider { token })

    override suspend fun send(method: String, path: String, query: Map<String, String>, body: String?): HttpResult {
        ensureLoggedIn()
        val first = inner.send(method, path, query, body)
        if (first.code != 401) return first
        // Токен отозван или истёк — один раз входим заново.
        token = null
        ensureLoggedIn()
        return inner.send(method, path, query, body)
    }

    private suspend fun ensureLoggedIn() = lock.withLock {
        if (token != null) return@withLock
        val body = buildJsonObject {
            put("user_email", email)
            put("password", password)
        }.toString()
        val r = inner.send("POST", "/auth/login", body = body)
        if (r.code == 200) {
            token = (JsonCodec.obj(r.body.orEmpty())["token"] as? JsonPrimitive)?.contentOrNull
        }
        // Неудачный вход: запросы уйдут без токена и вернут 401 — экран покажет ошибку.
    }
}
