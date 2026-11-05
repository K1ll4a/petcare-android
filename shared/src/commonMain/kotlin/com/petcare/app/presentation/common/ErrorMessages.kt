package com.petcare.app.presentation.common

import com.petcare.app.data.remote.dto.JsonCodec
import com.petcare.app.domain.repository.ApiException
import com.petcare.app.domain.repository.InvalidServerDataException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException

/** Понятный текст ошибки для экрана. Никогда не роняет приложение. */
fun Throwable.toUserMessage(fallback: String): String = when (this) {
    is IOException -> "Нет соединения с сервером. Проверьте интернет и повторите."
    is ApiException -> when {
        isUnauthorized -> "Сессия истекла. Войдите снова."
        else -> message
    }
    is JsonCodec.FormatException -> "Сервер прислал данные в неожиданном формате: $message"
    is InvalidServerDataException -> message ?: fallback
    else -> fallback
}

/**
 * Выполнить и вернуть результат или ошибку. Отмену корутины НЕ глотает:
 * отменённый запрос старого пространства не должен превратиться в «ошибку» на экране.
 */
suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

fun invalidTimezoneMessage(timezoneId: String) =
    "Сервер прислал неверный часовой пояс пространства: «$timezoneId». " +
        "Сроки задач не показаны, чтобы не показать неверное время. Сообщите Backend."
