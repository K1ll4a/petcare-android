package com.petcare.app.presentation.edit

import com.petcare.app.domain.repository.ApiException
import com.petcare.app.presentation.common.toUserMessage
import java.io.IOException

/**
 * Состояние сохранения формы (FR-20, задача #14 шаг 4).
 * «Сохранено» показывается ТОЛЬКО после ответа сервера.
 */
sealed interface SaveState {
    data object Idle : SaveState

    /** Запрос отправлен, ждём сервер. Кнопка «Сохранить» недоступна. */
    data object Saving : SaveState

    /** Сервер подтвердил. */
    data class Saved(val message: String = "Сохранено") : SaveState

    /** Сервер ответил ошибкой — точно не сохранено. */
    data class Failed(val message: String) : SaveState

    /** Ответ не пришёл — неизвестно, сохранилось ли. Данные перечитываются с сервера. */
    data class Unconfirmed(val message: String) : SaveState
}

const val UNCONFIRMED_MESSAGE =
    "Ответ сервера не получен — неизвестно, сохранились ли изменения. " +
        "Данные перезагружены с сервера: проверьте результат перед повтором."

/** Разбор ошибки сохранения: ошибки полей (422) отдельно от общего сообщения. */
internal data class SaveFailure(val state: SaveState, val fieldErrors: Map<String, String>)

internal fun Throwable.toSaveFailure(mapField: (String) -> String? = { it }): SaveFailure = when {
    this is IOException -> SaveFailure(SaveState.Unconfirmed(UNCONFIRMED_MESSAGE), emptyMap())
    this is ApiException && isValidation -> {
        val fields = fields.mapNotNull { (k, v) -> mapField(k)?.let { it to v } }.toMap()
        SaveFailure(SaveState.Failed(if (fields.isEmpty()) message else "Проверьте отмеченные поля"), fields)
    }
    else -> SaveFailure(SaveState.Failed(toUserMessage("Не удалось сохранить")), emptyMap())
}
