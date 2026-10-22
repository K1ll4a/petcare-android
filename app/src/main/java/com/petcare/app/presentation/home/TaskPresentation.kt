package com.petcare.app.presentation.home

import com.petcare.app.domain.model.RepeatType
import com.petcare.app.domain.model.TaskItem
import com.petcare.app.domain.model.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Чистая логика отображения задач — без Android, поэтому покрыта unit-тестами.
 */

/** Порядок групп на главном экране: сначала то, что требует действия. */
private val STATUS_ORDER = listOf(
    TaskStatus.OVERDUE,
    TaskStatus.UPCOMING,
    TaskStatus.UNKNOWN,
    TaskStatus.DONE,
    TaskStatus.MISSED,
    TaskStatus.CANCELLED,
)

/**
 * Сортировка списка: по группе статуса, внутри группы — по сроку.
 * Выполненные и пропущенные — от новых к старым, остальные — от ближайших.
 */
fun List<TaskItem>.sortedForHome(): List<TaskItem> = sortedWith(
    compareBy<TaskItem> { STATUS_ORDER.indexOf(it.status) }
        .thenComparator { a, b ->
            val newestFirst = a.status == TaskStatus.DONE || a.status == TaskStatus.MISSED
            if (newestFirst) b.dueAt.compareTo(a.dueAt) else a.dueAt.compareTo(b.dueAt)
        }
        .thenBy { it.task.title },
)

fun TaskStatus.label(): String = when (this) {
    TaskStatus.UPCOMING -> "Предстоит"
    TaskStatus.OVERDUE -> "Просрочено"
    TaskStatus.DONE -> "Выполнено"
    TaskStatus.MISSED -> "Пропущено"
    TaskStatus.CANCELLED -> "Отменено"
    TaskStatus.UNKNOWN -> "Статус неизвестен"
}

/**
 * Форматирует время в часовом поясе ПРОСТРАНСТВА (BR-05), а не устройства.
 * [today] — сегодняшняя дата в том же поясе.
 */
class SpaceTimeFormatter(
    private val zone: ZoneId,
    private val today: LocalDate,
    locale: Locale = Locale.forLanguageTag("ru"),
) {
    private val time = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", locale)

    fun format(instant: Instant): String {
        val local = instant.atZone(zone)
        val day = when (local.toLocalDate()) {
            today -> "сегодня"
            today.minusDays(1) -> "вчера"
            today.plusDays(1) -> "завтра"
            else -> local.format(dayMonth)
        }
        return "$day, ${local.format(time)}"
    }

    fun formatTime(instant: Instant): String = instant.atZone(zone).format(time)
}

/** Готовая к показу строка списка — Compose ничего не вычисляет сам. */
data class TaskItemUi(
    val key: String,
    val title: String,
    val dueText: String,
    val assigneeText: String,
    val status: TaskStatus,
    val statusText: String,
    val isDaily: Boolean,
    val isScheduleDisabled: Boolean,
    val completedText: String?,
)

fun TaskItem.toUi(formatter: SpaceTimeFormatter): TaskItemUi = TaskItemUi(
    key = key,
    title = task.title.replaceFirstChar { it.titlecase() },
    dueText = formatter.format(dueAt),
    assigneeText = "Ответственный: " + (task.assigneeName ?: "участник #${task.assigneeId}"),
    status = status,
    statusText = status.label(),
    isDaily = task.repeat == RepeatType.DAILY,
    isScheduleDisabled = task.repeat == RepeatType.DAILY && !task.isActive,
    completedText = if (status == TaskStatus.DONE) completedText(formatter) else null,
)

private fun TaskItem.completedText(formatter: SpaceTimeFormatter): String {
    val who = completedByName ?: completedById?.let { "участник #$it" } ?: "неизвестно кто"
    val whenText = completedAt?.let { " в ${formatter.formatTime(it)}" }.orEmpty()
    return "Выполнил(а): $who$whenText"
}
