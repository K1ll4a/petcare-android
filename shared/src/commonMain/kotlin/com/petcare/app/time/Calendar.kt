package com.petcare.app.time

import kotlinx.datetime.*

/** Explicit timezone conversions shared by Android and iOS. */
fun LocalDate.plusDays(days: Long): LocalDate = plus(days.toInt(), DateTimeUnit.DAY)
fun LocalDate.plusDays(days: Int): LocalDate = plus(days, DateTimeUnit.DAY)
fun LocalDate.minusDays(days: Int): LocalDate = minus(days, DateTimeUnit.DAY)
fun LocalTime.withoutSeconds(): LocalTime = LocalTime(hour, minute)
fun LocalTime.displayTime(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
fun LocalDate.displayDate(): String = "${dayOfMonth.toString().padStart(2, '0')}.${monthNumber.toString().padStart(2, '0')}.${year.toString().padStart(4, '0')}"
fun Instant.displayDateTime(zone: TimeZone): String = toLocalDateTime(zone).let { "${it.date.displayDate()} ${it.time.displayTime()}" }
fun TimeZone.offsetLabel(at: Instant): String = offsetAt(at).toString().let { if (it == "Z") "+00:00" else it }
fun LocalDate.russianDayMonth(): String = "$dayOfMonth ${listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")[monthNumber - 1]}"

fun Clock.instant(): Instant = now()
