package com.petcare.app.domain.model

import java.time.Instant
import java.time.LocalDate

/** Тип повторения задачи (ТЗ FR-06, FR-09). */
enum class RepeatType { ONCE, DAILY }

/**
 * Статус задачи на главном экране (ТЗ FR-07).
 *
 * Статус всегда приходит с сервера (BR-02) — клиент его НЕ вычисляет.
 * [UNKNOWN] используется, пока Backend не добавил поле status в ответ GET /task.
 */
enum class TaskStatus {
    UPCOMING,
    OVERDUE,
    DONE,
    MISSED,
    CANCELLED,
    UNKNOWN;

    companion object {
        fun fromServer(raw: String?): TaskStatus =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

/** Шаблон задачи: что, для кого и кто отвечает (ТЗ FR-06, FR-10). */
data class Task(
    val id: Long,
    val petId: Long,
    val title: String,
    val repeat: RepeatType,
    val assigneeId: Long,
    /** Имя ответственного. null — Backend прислал только responsible_id. */
    val assigneeName: String?,
    /** false — расписание ежедневной задачи отключено (FR-10). */
    val isActive: Boolean = true,
)

/**
 * То, что показывается в списке на главном экране: разовая задача
 * или конкретный дневной экземпляр ежедневной задачи (ТЗ FR-07, FR-09).
 */
data class TaskItem(
    val task: Task,
    /** Срок выполнения (момент времени). Показывается в часовом поясе пространства (BR-05). */
    val dueAt: Instant,
    /** Дата экземпляра для ежедневной задачи; null для разовой. */
    val occurrenceDate: LocalDate?,
    val status: TaskStatus,
    val completedById: Long? = null,
    val completedByName: String? = null,
    val completedAt: Instant? = null,
    /** Пространство задачи (space_id). null — сервер не прислал. */
    val spaceId: Long? = null,
    /** Ключ строки от сервера (item_id, например "7:2026-10-22" или "7:once"). */
    val serverItemId: String? = null,
) {
    /** Уникальный ключ строки списка: у ежедневной задачи один id на много дней. */
    val key: String
        get() = serverItemId ?: occurrenceDate?.let { "${task.id}_$it" } ?: task.id.toString()
}
