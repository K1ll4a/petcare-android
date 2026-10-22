package com.petcare.app.data.mapper

import com.petcare.app.data.remote.dto.PetDto
import com.petcare.app.data.remote.dto.SpaceDto
import com.petcare.app.data.remote.dto.TaskDto
import com.petcare.app.domain.model.Pet
import com.petcare.app.domain.model.RepeatType
import com.petcare.app.domain.model.Space
import com.petcare.app.domain.model.Task
import com.petcare.app.domain.model.TaskItem
import com.petcare.app.domain.model.TaskStatus
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/*
 * Единственное место, где формат Backend превращается в модели приложения.
 * Если контракт поменяется — правим DTO и этот файл, экраны не трогаем.
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
)

/**
 * @param petTypes справочник видов (pet_type_id -> название). Нужен, пока Backend
 * присылает только pet_type_id без названия.
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

/** Некорректный часовой пояс не должен ронять приложение: падаем обратно на UTC. */
fun SpaceDto.toDomain(): Space = Space(
    id = spaceId,
    name = spaceName,
    timezone = try {
        ZoneId.of(timezone)
    } catch (e: DateTimeException) {
        ZoneOffset.UTC
    },
)

private fun parseDateOrNull(raw: String?): LocalDate? = try {
    raw?.let(LocalDate::parse)
} catch (e: DateTimeParseException) {
    null
}

private fun String?.blankToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
