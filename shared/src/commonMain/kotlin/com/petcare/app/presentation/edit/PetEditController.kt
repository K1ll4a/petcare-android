package com.petcare.app.presentation.edit

import com.petcare.app.domain.model.Pet
import com.petcare.app.domain.model.PetDraft
import com.petcare.app.domain.model.PetType
import com.petcare.app.domain.repository.PetRepository
import com.petcare.app.presentation.common.attempt
import com.petcare.app.presentation.common.toUserMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PetEditState(
    val isNew: Boolean,
    val draft: PetDraft,
    val types: List<PetType> = emptyList(),
    val typesError: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val save: SaveState = SaveState.Idle,
)

/**
 * Создание и изменение профиля питомца в выбранном пространстве (FR-04).
 * Аллергии и непереносимости — отдельные поля формы.
 */
class PetEditController(
    private val scope: CoroutineScope,
    private val repo: PetRepository,
    private val spaceId: Long,
    existing: Pet?,
    /** Сервер подтвердил — главный экран перечитывает данные. */
    private val onSaved: (Pet) -> Unit,
    /** Ответ не пришёл — главный экран перечитывает данные. */
    private val onUnconfirmed: () -> Unit,
) {
    private val petId = existing?.id

    private val _state = MutableStateFlow(
        PetEditState(isNew = existing == null, draft = existing?.let(PetDraft::from) ?: PetDraft()),
    )
    val state: StateFlow<PetEditState> = _state.asStateFlow()

    init {
        loadTypes()
    }

    fun loadTypes() {
        _state.update { it.copy(typesError = null) }
        scope.launch {
            attempt { repo.getPetTypes() }.fold(
                { types -> _state.update { it.copy(types = types) } },
                { e -> _state.update { it.copy(typesError = e.toUserMessage("Не удалось загрузить виды животных")) } },
            )
        }
    }

    /** Изменение поля сбрасывает «Сохранено» — новые правки ещё не на сервере. */
    fun edit(transform: (PetDraft) -> PetDraft) {
        _state.update { s ->
            if (s.save == SaveState.Saving) return@update s
            val newDraft = transform(s.draft)
            val changed = changedFields(s.draft, newDraft)
            s.copy(
                draft = newDraft,
                fieldErrors = s.fieldErrors - changed,
                save = if (s.save is SaveState.Saved || s.save is SaveState.Failed) SaveState.Idle else s.save,
            )
        }
    }

    fun save() {
        val s = _state.value
        if (s.save == SaveState.Saving) return // двойное нажатие не отправляет второй запрос
        val errors = s.draft.validate()
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors, save = SaveState.Failed("Проверьте отмеченные поля")) }
            return
        }
        _state.update { it.copy(fieldErrors = emptyMap(), save = SaveState.Saving) }
        val draft = s.draft
        scope.launch {
            attempt {
                if (petId == null) repo.createPet(spaceId, draft) else repo.updatePet(spaceId, petId, draft)
            }.fold(
                { pet ->
                    _state.update { it.copy(draft = PetDraft.from(pet), save = SaveState.Saved()) }
                    onSaved(pet)
                },
                { e ->
                    val failure = e.toSaveFailure()
                    _state.update { it.copy(save = failure.state, fieldErrors = failure.fieldErrors) }
                    if (failure.state is SaveState.Unconfirmed) onUnconfirmed()
                },
            )
        }
    }

    private fun changedFields(a: PetDraft, b: PetDraft) = buildSet {
        if (a.name != b.name) add(PetDraft.FIELD_NAME)
        if (a.typeId != b.typeId) add(PetDraft.FIELD_TYPE)
        if (a.features != b.features) add(PetDraft.FIELD_FEATURES)
        if (a.allergies != b.allergies) add(PetDraft.FIELD_ALLERGIES)
        if (a.intolerances != b.intolerances) add(PetDraft.FIELD_INTOLERANCES)
        if (a.careNotes != b.careNotes) add(PetDraft.FIELD_CARE_NOTES)
    }
}
