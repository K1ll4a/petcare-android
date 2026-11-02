package com.petcare.app.domain.model

import java.time.Instant
import java.time.ZoneId

enum class SpaceRole { OWNER, MEMBER }

/**
 * Пространство семьи (ТЗ FR-02, FR-21).
 *
 * [timezoneId] — строка, которую прислал сервер (space_timezone).
 * [zone] — разобранный часовой пояс; null, если строка неверная.
 * Неверный пояс НЕ подменяется на UTC: экран показывает ошибку (BR-05, задача #14).
 */
data class Space(
    val id: Long,
    val name: String,
    val timezoneId: String,
    val zone: ZoneId?,
    val role: SpaceRole = SpaceRole.MEMBER,
) {
    val isTimezoneValid: Boolean get() = zone != null

    /** «Europe/Moscow, UTC+03:00» — смещение берём на момент [at] (учитывает переход на летнее время). */
    fun timezoneLabel(at: Instant = Instant.now()): String {
        val z = zone ?: return "неверный часовой пояс «$timezoneId»"
        val offset = z.rules.getOffset(at).id.let { if (it == "Z") "+00:00" else it }
        return "$timezoneId, UTC$offset"
    }
}

/** Участник пространства — кандидат в ответственные за задачу (GET /spaces/{id}/members). */
data class Member(
    val id: Long,
    val name: String,
    val role: SpaceRole,
)

/** Вид животного из справочника GET /pet-types. */
data class PetType(val id: Int, val name: String)
