package com.petcare.app.data.remote

import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/*
 * Тела запросов создания и изменения — строго по petcare-backend/docs/API.md 1.2
 * и docs/task20_contract.md. Здесь нет дат и часовых поясов: значения уже переведены
 * в строки контракта (см. Mappers.kt), поэтому правила полей проверяются отдельно.
 */

/** Питомец в полях сервера. Пустые необязательные поля — null. */
data class PetWire(
    val name: String,
    val typeId: Int,
    val features: String?,
    val allergies: String?,
    val intolerances: String?,
    val careNotes: String?,
)

/**
 * Задача в полях сервера.
 * Разовая: [scheduledAt] — ISO 8601 с `Z` (сервер отклоняет время без пояса, 422).
 * Ежедневная: [startDate] (YYYY-MM-DD) и [localTime] (HH:MM) в поясе пространства;
 * срок считает сервер.
 */
data class TaskWire(
    val petId: Long,
    val title: String,
    val assigneeId: Long,
    val daily: Boolean,
    val scheduledAt: String?,
    val startDate: String?,
    val localTime: String?,
) {
    init {
        if (daily) {
            require(startDate != null && localTime != null) { "Для ежедневной задачи нужны start_date и local_time" }
        } else {
            require(scheduledAt != null) { "Для разовой задачи нужен scheduled_at" }
        }
    }
}

/** Попытка изменить то, что сервер менять запрещает (pet_id, space_id задачи). */
class ForbiddenChangeException(message: String) : IllegalArgumentException(message)

object PetRequests {
    /** POST /spaces/{space_id}/pets */
    fun create(w: PetWire): String = buildJsonObject {
        put("pet_name", w.name)
        put("pet_type_id", w.typeId)
        putIfPresent("pet_features", w.features)
        putIfPresent("pet_allergies", w.allergies)
        putIfPresent("pet_intolerances", w.intolerances)
        putIfPresent("pet_care_notes", w.careNotes)
    }.toString()

    /**
     * PATCH /pet/{pet_id} — только изменённые поля (API.md §6).
     * Очищенное необязательное поле отправляется как null: так сервер его стирает.
     */
    fun patch(old: PetWire, new: PetWire): String = buildJsonObject {
        if (new.name != old.name) put("pet_name", new.name)
        if (new.typeId != old.typeId) put("pet_type_id", new.typeId)
        if (new.features != old.features) put("pet_features", new.features)
        if (new.allergies != old.allergies) put("pet_allergies", new.allergies)
        if (new.intolerances != old.intolerances) put("pet_intolerances", new.intolerances)
        if (new.careNotes != old.careNotes) put("pet_care_notes", new.careNotes)
    }.toString()

    private fun JsonObjectBuilder.putIfPresent(key: String, value: String?) {
        if (value != null) put(key, value)
    }
}

object TaskRequests {
    /** POST /spaces/{space_id}/tasks */
    fun create(w: TaskWire): String = buildJsonObject {
        put("task_title", w.title)
        put("pet_id", w.petId)
        put("assignee_id", w.assigneeId)
        put("recurrence", if (w.daily) "daily" else "once")
        if (w.daily) {
            put("start_date", w.startDate)
            put("local_time", w.localTime)
        } else {
            put("scheduled_at", w.scheduledAt)
        }
    }.toString()

    /**
     * PATCH /tasks/{task_id} — только изменённые поля (task20_contract.md §5).
     * pet_id и space_id не отправляются никогда: сервер отвечает на них 400.
     * Смена повторения отправляет полный набор полей нового типа, как требует сервер.
     */
    fun patch(old: TaskWire, new: TaskWire): String {
        if (new.petId != old.petId) {
            throw ForbiddenChangeException("Питомца задачи изменить нельзя — создайте новую задачу")
        }
        return buildJsonObject {
            if (new.title != old.title) put("task_title", new.title)
            if (new.assigneeId != old.assigneeId) put("assignee_id", new.assigneeId)
            when {
                new.daily != old.daily -> {
                    put("recurrence", if (new.daily) "daily" else "once")
                    if (new.daily) {
                        put("start_date", new.startDate)
                        put("local_time", new.localTime)
                    } else {
                        put("scheduled_at", new.scheduledAt)
                    }
                }
                new.daily -> {
                    if (new.startDate != old.startDate) put("start_date", new.startDate)
                    if (new.localTime != old.localTime) put("local_time", new.localTime)
                }
                new.scheduledAt != old.scheduledAt -> put("scheduled_at", new.scheduledAt)
            }
        }.toString()
    }
}
