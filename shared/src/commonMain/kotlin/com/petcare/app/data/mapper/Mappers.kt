package com.petcare.app.data.mapper

import kotlinx.datetime.*
import com.petcare.app.time.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import com.petcare.app.data.remote.PetRequests
import com.petcare.app.data.remote.PetWire
import com.petcare.app.data.remote.TaskRequests
import com.petcare.app.data.remote.TaskWire
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
    dueAt = Instant.fromEpochSeconds(dateTime),
    occurrenceDate = if (isDaily) parseDateOrNull(occurrenceDate) else null,
    // Статус НЕ вычисляем по времени на клиенте (BR-02): нет поля — UNKNOWN.
    status = TaskStatus.fromServer(status),
    completedById = completedById,
    completedByName = completedByName?.takeIf { it.isNotBlank() },
    completedAt = completedAt?.let(Instant::fromEpochSeconds),
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

/** Питомец формы в полях сервера. Пустые необязательные поля — null. */
fun PetDraft.toWire(): PetWire = PetWire(
    name = name.trim(),
    typeId = requireNotNull(typeId) { "Вид не выбран — форму нужно проверить validate()" },
    features = features.blankToNull(),
    allergies = allergies.blankToNull(),
    intolerances = intolerances.blankToNull(),
    careNotes = careNotes.blankToNull(),
)

/** Тело POST /spaces/{space_id}/pets. */
fun PetDraft.toRequestBody(): String = PetRequests.create(toWire())

/** Тело PATCH /pet/{pet_id}: только поля, изменённые относительно [original]. */
fun PetDraft.toPatchBody(original: PetDraft): String = PetRequests.patch(original.toWire(), toWire())

/**
 * Задача формы в полях сервера. Дата и время формы — местные для ПРОСТРАНСТВА (BR-05):
 * у разовой срок переводится в момент по [zone] и отправляется как ISO 8601 с `Z`;
 * у ежедневной отправляются дата начала и местное время, срок считает сервер.
 */
fun TaskDraft.toWire(zone: TimeZone): TaskWire {
    val d = requireNotNull(date) { "Дата не указана" }
    val t = requireNotNull(time) { "Время не указано" }
    val daily = repeat == RepeatType.DAILY
    return TaskWire(
        petId = requireNotNull(petId) { "Питомец не выбран" },
        title = title.trim(),
        assigneeId = requireNotNull(assigneeId) { "Ответственный не выбран" },
        daily = daily,
        scheduledAt = if (daily) null else LocalDateTime(d, t).toInstant(zone).toString(),
        startDate = if (daily) d.toString() else null,
        localTime = if (daily) t.displayTime() else null,
    )
}

/** Тело POST /spaces/{space_id}/tasks. */
fun TaskDraft.toRequestBody(zone: TimeZone): String = TaskRequests.create(toWire(zone))

/** Тело PATCH /tasks/{task_id}: только поля, изменённые относительно [original]. */
fun TaskDraft.toPatchBody(original: TaskDraft, zone: TimeZone): String =
    TaskRequests.patch(original.toWire(zone), toWire(zone))

/** Заполнить форму задачей из списка: дата и время — в поясе пространства. */
fun TaskItem.toDraft(zone: TimeZone): TaskDraft {
    val local = dueAt.toLocalDateTime(zone)
    return TaskDraft(
        petId = task.petId,
        title = task.title,
        assigneeId = task.assigneeId,
        repeat = task.repeat,
        date = local.date,
        time = local.time.withoutSeconds(),
    )
}


fun parseZoneOrNull(raw: String?): TimeZone? {
    if (raw.isNullOrBlank()) return null
    return try {
        TimeZone.of(raw.trim())
    } catch (e: IllegalArgumentException) {
        null
    }
}

private fun parseRole(raw: String?) =
    if (raw.equals("owner", ignoreCase = true)) SpaceRole.OWNER else SpaceRole.MEMBER

private fun parseDateOrNull(raw: String?): LocalDate? = try {
    raw?.let(LocalDate::parse)
} catch (e: IllegalArgumentException) {
    null
}

private fun String?.blankToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
