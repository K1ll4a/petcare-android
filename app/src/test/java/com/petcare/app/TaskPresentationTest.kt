package com.petcare.app

import com.petcare.app.domain.model.RepeatType
import com.petcare.app.domain.model.Task
import com.petcare.app.domain.model.TaskItem
import com.petcare.app.domain.model.TaskStatus
import com.petcare.app.presentation.home.SpaceTimeFormatter
import com.petcare.app.presentation.home.sortedForHome
import com.petcare.app.presentation.home.toUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone

class TaskPresentationTest {

    private val moscow = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2026, 10, 22)

    private fun item(id: Long, status: TaskStatus, due: String, daily: Boolean = false) = TaskItem(
        task = Task(id, 10, "задача $id", if (daily) RepeatType.DAILY else RepeatType.ONCE, 3, "Гриша"),
        dueAt = Instant.parse(due),
        occurrenceDate = null,
        status = status,
    )

    @Test
    fun sorting_overdueFirst_thenUpcomingByTime_thenDoneNewestFirst() {
        val sorted = listOf(
            item(1, TaskStatus.DONE, "2026-10-22T05:00:00Z"),
            item(2, TaskStatus.UPCOMING, "2026-10-22T18:00:00Z"),
            item(3, TaskStatus.MISSED, "2026-10-21T17:00:00Z"),
            item(4, TaskStatus.OVERDUE, "2026-10-22T09:00:00Z"),
            item(5, TaskStatus.UPCOMING, "2026-10-22T12:00:00Z"),
            item(6, TaskStatus.DONE, "2026-10-22T07:00:00Z"),
            item(7, TaskStatus.CANCELLED, "2026-10-22T07:00:00Z"),
        ).sortedForHome().map { it.task.id }

        assertEquals(listOf(4L, 5L, 2L, 6L, 1L, 3L, 7L), sorted)
    }

    @Test
    fun time_isShownInSpaceZone_notDeviceZone() {
        val saved = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo")) // «устройство» в Токио
            val f = SpaceTimeFormatter(moscow, today)
            // 17:00 UTC = 20:00 МСК (в Токио было бы 02:00 следующего дня)
            assertEquals("сегодня, 20:00", f.format(Instant.parse("2026-10-22T17:00:00Z")))
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    @Test
    fun relativeDays() {
        val f = SpaceTimeFormatter(moscow, today)
        assertEquals("вчера, 08:00", f.format(Instant.parse("2026-10-21T05:00:00Z")))
        assertEquals("завтра, 08:00", f.format(Instant.parse("2026-10-23T05:00:00Z")))
        assertTrue(f.format(Instant.parse("2026-10-24T12:00:00Z")).startsWith("24 "))
    }

    @Test
    fun dayBoundary_dependsOnSpaceZone() {
        // 22:30 UTC 21 октября — это уже 22 октября по Москве.
        val f = SpaceTimeFormatter(moscow, today)
        assertEquals("сегодня, 01:30", f.format(Instant.parse("2026-10-21T22:30:00Z")))
    }

    @Test
    fun ui_fallbacks_and_completedText() {
        val f = SpaceTimeFormatter(moscow, today)
        val noName = item(1, TaskStatus.UPCOMING, "2026-10-22T09:00:00Z").let {
            it.copy(task = it.task.copy(assigneeName = null))
        }.toUi(f)
        assertEquals("Ответственный: участник #3", noName.assigneeText)
        assertNull(noName.completedText)
        assertEquals("Задача 1", noName.title)

        val done = item(2, TaskStatus.DONE, "2026-10-22T05:00:00Z", daily = true).copy(
            completedByName = "Аня", completedAt = Instant.parse("2026-10-22T05:12:00Z"),
        ).toUi(f)
        assertEquals("Выполнил(а): Аня в 08:12", done.completedText)
        assertTrue(done.isDaily)
    }

    @Test
    fun disabledDailySchedule_isFlagged() {
        val f = SpaceTimeFormatter(moscow, today)
        val it = item(1, TaskStatus.CANCELLED, "2026-10-22T07:00:00Z", daily = true)
        val ui = it.copy(task = it.task.copy(isActive = false)).toUi(f)
        assertTrue(ui.isScheduleDisabled)
    }
}
