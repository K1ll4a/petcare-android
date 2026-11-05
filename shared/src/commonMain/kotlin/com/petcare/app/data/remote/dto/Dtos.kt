package com.petcare.app.data.remote.dto

/*
 * DTO = ровно то, что присылает сервер. Ключи JSON — по petcare-backend/docs/API.md
 * и app/queries.py (build_task_items); разбор и сборка JSON — в JsonCodec.kt.
 * Имена свойств Kotlin оставлены с недели 1, поменялись только ключи JSON.
 */

/** Элемент ответа GET /task и GET /pets/{pet_id}/tasks. */
data class TaskDto(
    /** JSON "id" — id задачи (у всех дней ежедневной задачи одинаковый). */
    val taskId: Long,
    /** "task_name" */
    val taskName: String,
    /** "pet_id" */
    val petId: Long,
    /** "date_time" — срок, unix-время в секундах (UTC). */
    val dateTime: Long,
    /** "responsible_id" */
    val responsibleId: Long,
    /** "is_daily" */
    val isDaily: Boolean,
    /** "responsible_name" */
    val responsibleName: String? = null,
    /** "status": upcoming / overdue / done / missed / cancelled — считает сервер (BR-02). */
    val status: String? = null,
    /** "occurrence_date" — дата экземпляра ежедневной задачи, YYYY-MM-DD. */
    val occurrenceDate: String? = null,
    /** "is_active" */
    val isActive: Boolean? = null,
    /** "completed_by_id" */
    val completedById: Long? = null,
    /** "completed_by_name" */
    val completedByName: String? = null,
    /** "completed_at" — unix-время в секундах. */
    val completedAt: Long? = null,
    /** "space_id" */
    val spaceId: Long? = null,
    /** "item_id" — уникальный ключ строки списка. */
    val itemId: String? = null,
    /** "occurrence_id" */
    val occurrenceId: Long? = null,
)

/** Профиль питомца: ответы GET/POST /spaces/{id}/pets, GET/PATCH /pet/{id}. */
data class PetDto(
    /** "pet_id" */
    val petId: Long,
    /** "pet_name" */
    val petName: String,
    /** "pet_type_id" */
    val petTypeId: Int,
    /** "space_id" */
    val spaceId: Long? = null,
    /** "pet_type" — сервер не присылает; название берётся из справочника. */
    val petType: String? = null,
    /** "pet_features" */
    val features: String? = null,
    /** "pet_allergies" */
    val allergies: String? = null,
    /** "pet_intolerances" */
    val intolerances: String? = null,
    /** "pet_care_notes" */
    val careNotes: String? = null,
)

/** Элемент справочника GET /pet-types: {"id", "name"}. */
data class PetTypeDto(
    val petTypeId: Int,
    val name: String,
)

/** Пространство: GET /spaces, GET /spaces/{id}. */
data class SpaceDto(
    /** "space_id" */
    val spaceId: Long,
    /** "space_name" */
    val spaceName: String,
    /** "space_timezone" — IANA-идентификатор, например "Europe/Moscow". */
    val timezone: String,
    /** "role": owner / member */
    val role: String? = null,
)

/** Участник: GET /spaces/{id}/members. */
data class MemberDto(
    /** "user_id" */
    val userId: Long,
    /** "user_name" */
    val name: String,
    /** "role" */
    val role: String? = null,
)

/** Тело ошибки (API.md §2). */
data class ErrorDto(
    val code: String,
    val message: String,
    val fields: List<Pair<String, String>> = emptyList(),
)
