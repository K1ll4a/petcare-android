package com.petcare.app.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ссылка на задачу из Telegram (API.md 1.2 §7.1): `petcare://tasks/{task_id}?occurrence_id={occurrence_id}`.
 * [occurrenceId] есть только у экземпляра ежедневной задачи.
 */
data class TaskLink(val taskId: Long, val occurrenceId: Long? = null)

object TaskLinkParser {
    /** null — ссылка не наша или повреждена: такие ссылки игнорируются, ничего не открывается. */
    fun parse(url: String): TaskLink? {
        val trimmed = url.trim()
        val prefix = "petcare://tasks/"
        if (!trimmed.startsWith(prefix, ignoreCase = true)) return null
        val rest = trimmed.substring(prefix.length).substringBefore('#')
        val path = rest.substringBefore('?').trimEnd('/')
        if (path.isEmpty() || path.contains('/')) return null
        val taskId = path.toLongOrNull()?.takeIf { it > 0 } ?: return null
        val query = rest.substringAfter('?', missingDelimiterValue = "")
        val params = query.split('&').filter { it.isNotEmpty() }.associate {
            it.substringBefore('=') to it.substringAfter('=', missingDelimiterValue = "")
        }
        val rawOccurrence = params["occurrence_id"]
        val occurrenceId = when {
            rawOccurrence.isNullOrEmpty() || rawOccurrence == "null" || rawOccurrence == "None" -> null
            else -> rawOccurrence.toLongOrNull()?.takeIf { it > 0 } ?: return null
        }
        return TaskLink(taskId, occurrenceId)
    }
}

/**
 * Ссылка, пришедшая от платформы (Android Intent, iOS onOpenURL), ждёт здесь, пока пользователь
 * не войдёт. Хранится только в памяти процесса: после перезапуска её нужно открыть снова.
 */
object PendingDeepLinks {
    private val mutable = MutableStateFlow<TaskLink?>(null)
    val link: StateFlow<TaskLink?> = mutable.asStateFlow()

    /** true — ссылка принята. Новая ссылка заменяет ещё не открытую. */
    fun offer(url: String?): Boolean {
        val parsed = url?.let(TaskLinkParser::parse) ?: return false
        mutable.value = parsed
        return true
    }

    /** Забрать ссылку для обработки; повторно она не откроется. */
    fun take(): TaskLink? {
        val current = mutable.value
        mutable.value = null
        return current
    }
}
