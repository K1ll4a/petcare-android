package com.petcare.app.data.mapper

import com.petcare.app.data.remote.dto.JsonCodec
import com.petcare.app.data.remote.dto.MemberDto
import com.petcare.app.data.remote.dto.PetDto
import com.petcare.app.data.remote.dto.PetTypeDto
import com.petcare.app.data.remote.dto.SpaceDto
import com.petcare.app.data.remote.dto.TaskDto
import com.petcare.app.domain.model.Member
import com.petcare.app.domain.model.Pet
import com.petcare.app.domain.model.PetDraft
import com.petcare.app.domain.model.PetType
import com.petcare.app.domain.model.RepeatType
import com.petcare.app.domain.model.Space
import com.petcare.app.domain.model.SpaceRole
import com.petcare.app.domain.model.Task
import com.petcare.app.domain.model.TaskDraft
import com.petcare.app.domain.model.TaskItem
import com.petcare.app.domain.model.TaskStatus
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/*
 * Единственное место, где формат сервера превращается в модели приложения и обратно.
 * Если контракт поменяется — правим DTO, JsonCodec и этот файл, экраны не трогаем.
 */

fun TaskDto.toDomain(): TaskItem = TaskItem(
    task = Task(
        id = taskId,
        petId = petId,
        title = taskName.trim(),
        repeat = if (isDaily) RepeatType.DAILY else RepeatType.ONCE,
        assigneeId = responsibleId,
        assigneeName = responsibleName?.takeIf { it.isNotBlank() },
        isActive = isActive ?: true,
    ),
    dueAt = Instant.ofEpochSecond(dateTime),
    occurrenceDate = if (isDaily) parseDateOrNull(occurrenceDate) else null,
    // Статус НЕ вычисляем по времени на клиенте (BR-02): нет поля — UNKNOWN.
    status = TaskStatus.fromServer(status),
    completedById = completedById,
    completedByName = completedByName?.takeIf { it.isNotBlank() },
    completedAt = completedAt?.let(Instant::ofEpochSecond),
    spaceId = spaceId,
    serverItemId = itemId?.takeIf { it.isNotBlank() },
)

/**
 * @param petTypes справочник видов (id -> название): сервер присылает только pet_type_id.
 */
fun PetDto.toDomain(petTypes: Map<Int, String> = emptyMap()): Pet = Pet(
    id = petId,
    spaceId = spaceId,
    name = petName.trim(),
    typeId = petTypeId,
    typeName = petType?.takeIf { it.isNotBlank() } ?: petTypes[petTypeId],
    features = features.blankToNull(),
    allergies = allergies.blankToNull(),
    intolerances = intolerances.blankToNull(),
    careNotes = careNotes.blankToNull(),
)

fun PetTypeDto.toDomain() = PetType(petTypeId, name)

/**
 * Неверный часовой пояс НЕ подменяется на UTC (задача #14, BR-05):
 * zone = null, а экран сообщает об ошибке.
 */
fun SpaceDto.toDomain(): Space = Space(
    id = spaceId,
    name = spaceName,
    timezoneId = timezone,
    zone = parseZoneOrNull(timezone),
    role = parseRole(role),
)

fun MemberDto.toDomain() = Member(userId, name.trim(), parseRole(role))

/** Тело POST/PATCH питомца. Пустые необязательные поля отправляются как null — сервер их очищает. */
fun PetDraft.toRequestBody(): String = JsonCodec.petBody(
    name = name.trim(),
    typeId = requireNotNull(typeId) { "Вид не выбран — форму нужно проверить validate()" },
    features = features.blankToNull(),
    allergies = allergies.blankToNull(),
    intolerances = intolerances.blankToNull(),
    careNotes = careNotes.blankToNull(),
)

/**
 * Тело создания/изменения задачи. Дата и время формы — местные для ПРОСТРАНСТВА:
 * момент срока считается в [zone], а не в поясе телефона (BR-05).
 */
fun TaskDraft.toRequestBody(zone: ZoneId): String {
    val d = requireNotNull(date) { "Дата не указана" }
    val t = requireNotNull(time) { "Время не указано" }
    val daily = repeat == RepeatType.DAILY
    return JsonCodec.taskBody(
        petId = requireNotNull(petId) { "Питомец не выбран" },
        name = title.trim(),
        responsibleId = requireNotNull(assigneeId) { "Ответственный не выбран" },
        isDaily = daily,
        dateTime = LocalDateTime.of(d, t).atZone(zone).toEpochSecond(),
        startDate = if (daily) d.toString() else null,
        localTime = if (daily) t.format(HH_MM) else null,
    )
}

/** Заполнить форму задачей из списка: дата и время — в поясе пространства. */
fun TaskItem.toDraft(zone: ZoneId): TaskDraft {
    val local = dueAt.atZone(zone)
    return TaskDraft(
        petId = task.petId,
        title = task.title,
        assigneeId = task.assigneeId,
        repeat = task.repeat,
        date = local.toLocalDate(),
        time = local.toLocalTime().withSecond(0).withNano(0),
    )
}

private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun parseZoneOrNull(raw: String?): ZoneId? {
    if (raw.isNullOrBlank()) return null
    return try {
        ZoneId.of(raw.trim())
    } catch (e: DateTimeException) {
        null
    }
}

private fun parseRole(raw: String?) =
    if (raw.equals("owner", ignoreCase = true)) SpaceRole.OWNER else SpaceRole.MEMBER

private fun parseDateOrNull(raw: String?): LocalDate? = try {
    raw?.let(LocalDate::parse)
} catch (e: DateTimeParseException) {
    null
}

private fun String?.blankToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
