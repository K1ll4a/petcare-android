package com.petcare.app.data.remote

import java.io.IOException

/** Ответ сервера: код и тело как есть. */
data class HttpResult(val code: Int, val body: String?)

/**
 * Отправка HTTP-запроса. В приложении — OkHttpTransport, в тестах — подставной.
 * Нет связи / тайм-аут / обрыв — бросает [IOException]; любой HTTP-код возвращает как результат.
 */
interface HttpTransport {
    suspend fun send(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        body: String? = null,
    ): HttpResult
}

/** Чтение токена серверной сессии; HTTP-клиент сам не выполняет вход. */
fun interface TokenProvider {
    fun token(): String?
}
