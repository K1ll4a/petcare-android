package com.petcare.app.domain.model

/**
 * Профиль питомца (ТЗ FR-04).
 *
 * Обязательные поля — [name] и вид ([typeId] или [typeName]).
 * Остальное необязательно: Backend пока присылает только pet_id, pet_name, pet_type_id,
 * поэтому новые поля nullable и заполнятся, когда их добавят в контракт.
 */
data class Pet(
    val id: Long,
    val spaceId: Long?,
    val name: String,
    /** Идентификатор вида из справочника Backend (pet_type_id). */
    val typeId: Int?,
    /** Название вида ("кот", "собака"). Если Backend его не прислал — берём из справочника. */
    val typeName: String?,
    val features: String? = null,
    val allergies: String? = null,
    val intolerances: String? = null,
    val careNotes: String? = null,
) {
    /** Есть ли что показать в блоке «Особенности» (аллергии, непереносимости и т.д.). */
    val hasSpecialNotes: Boolean
        get() = listOf(features, allergies, intolerances, careNotes).any { !it.isNullOrBlank() }
}
