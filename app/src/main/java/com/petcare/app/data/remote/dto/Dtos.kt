package com.petcare.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * DTO = ровно то, что присылает Backend. Имена полей — как в контракте (snake_case).
 *
 * Поля из черновика Backend (контракты_api.txt) обязательные.
 * Поля, которые мы попросили добавить, nullable со значением по умолчанию:
 * пока Backend их не присылает, разбор JSON не падает (ignoreUnknownKeys + default = null).
 */

/** Элемент ответа GET /task. */
@Serializable
data class TaskDto(
    // --- есть в контракте Backend ---
    @SerialName("task_id") val taskId: Long,
    @SerialName("task_name") val taskName: String,
    @SerialName("pet_id") val petId: Long,
    /** Unix-время в секундах (UTC). */
    @SerialName("date_time") val dateTime: Long,
    @SerialName("responsible_id") val responsibleId: Long,
    @SerialName("is_daily") val isDaily: Boolean,

    // --- запрошено у Backend, пока может отсутствовать ---
    @SerialName("responsible_name") val responsibleName: String? = null,
    /** upcoming / overdue / done / missed / cancelled — считает сервер (BR-02). */
    @SerialName("status") val status: String? = null,
    /** Дата экземпляра ежедневной задачи, формат YYYY-MM-DD. */
    @SerialName("occurrence_date") val occurrenceDate: String? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
    @SerialName("completed_by_id") val completedById: Long? = null,
    @SerialName("completed_by_name") val completedByName: String? = null,
    /** Unix-время в секундах (UTC). */
    @SerialName("completed_at") val completedAt: Long? = null,
)

/** Ответ GET /pet/{pet_id}. */
@Serializable
data class PetDto(
    // --- есть в контракте Backend ---
    @SerialName("pet_id") val petId: Long,
    @SerialName("pet_name") val petName: String,
    @SerialName("pet_type_id") val petTypeId: Int,

    // --- запрошено у Backend (FR-04), пока может отсутствовать ---
    @SerialName("space_id") val spaceId: Long? = null,
    @SerialName("pet_type") val petType: String? = null,
    @SerialName("features") val features: String? = null,
    @SerialName("allergies") val allergies: String? = null,
    @SerialName("intolerances") val intolerances: String? = null,
    @SerialName("care_notes") val careNotes: String? = null,
)

/** Элемент справочника видов (предложенный GET /pet-types). */
@Serializable
data class PetTypeDto(
    @SerialName("pet_type_id") val petTypeId: Int,
    @SerialName("name") val name: String,
)

/** Пространство (предложенный GET /spaces/{id}); нужен ради часового пояса. */
@Serializable
data class SpaceDto(
    @SerialName("space_id") val spaceId: Long,
    @SerialName("space_name") val spaceName: String,
    /** IANA-идентификатор, например "Europe/Moscow". */
    @SerialName("timezone") val timezone: String,
)
