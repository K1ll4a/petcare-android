package com.petcare.app.domain.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * Данные формы питомца перед отправкой на сервер (FR-04).
 * Аллергии и непереносимости — отдельные поля.
 */
data class PetDraft(
    val name: String = "",
    val typeId: Int? = null,
    val features: String = "",
    val allergies: String = "",
    val intolerances: String = "",
    val careNotes: String = "",
) {
    /** Ключ — имя поля формы, значение — текст ошибки. Пусто — можно отправлять. */
    fun validate(): Map<String, String> = buildMap {
        if (name.isBlank()) put(FIELD_NAME, "Укажите имя питомца")
        if (name.trim().length > 100) put(FIELD_NAME, "Имя длиннее 100 символов")
        if (typeId == null) put(FIELD_TYPE, "Выберите вид животного")
        listOf(
            FIELD_FEATURES to features,
            FIELD_ALLERGIES to allergies,
            FIELD_INTOLERANCES to intolerances,
            FIELD_CARE_NOTES to careNotes,
        ).forEach { (field, value) ->
            if (value.trim().length > 2000) put(field, "Не больше 2000 символов")
        }
    }

    companion object {
        const val FIELD_NAME = "pet_name"
        const val FIELD_TYPE = "pet_type_id"
        const val FIELD_FEATURES = "pet_features"
        const val FIELD_ALLERGIES = "pet_allergies"
        const val FIELD_INTOLERANCES = "pet_intolerances"
        const val FIELD_CARE_NOTES = "pet_care_notes"

        fun from(pet: Pet) = PetDraft(
            name = pet.name,
            typeId = pet.typeId,
            features = pet.features.orEmpty(),
            allergies = pet.allergies.orEmpty(),
            intolerances = pet.intolerances.orEmpty(),
            careNotes = pet.careNotes.orEmpty(),
        )
    }
}

/**
 * Данные формы задачи (FR-06, FR-10).
 * [date] и [time] — местные дата и время ПРОСТРАНСТВА, не устройства (BR-05).
 * Для ежедневной задачи [date] — дата начала, [time] — местное время выполнения.
 */
data class TaskDraft(
    val petId: Long? = null,
    val title: String = "",
    val assigneeId: Long? = null,
    val repeat: RepeatType = RepeatType.ONCE,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
) {
    fun validate(): Map<String, String> = buildMap {
        if (petId == null) put(FIELD_PET, "Выберите питомца")
        if (title.isBlank()) put(FIELD_TITLE, "Укажите название задачи")
        if (title.trim().length > 200) put(FIELD_TITLE, "Название длиннее 200 символов")
        if (assigneeId == null) put(FIELD_ASSIGNEE, "Выберите ответственного")
        if (date == null) put(FIELD_DATE, "Укажите дату в формате ДД.ММ.ГГГГ")
        if (time == null) put(FIELD_TIME, "Укажите время в формате ЧЧ:ММ")
    }

    companion object {
        const val FIELD_PET = "pet_id"
        const val FIELD_TITLE = "task_name"
        const val FIELD_ASSIGNEE = "responsible_id"
        const val FIELD_DATE = "date"
        const val FIELD_TIME = "time"
    }
}
