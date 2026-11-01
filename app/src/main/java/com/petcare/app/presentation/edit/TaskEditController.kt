package com.petcare.app.presentation.edit

import com.petcare.app.data.mapper.toDraft
import com.petcare.app.domain.model.Member
import com.petcare.app.domain.model.Pet
import com.petcare.app.domain.model.RepeatType
import com.petcare.app.domain.model.Space
import com.petcare.app.domain.model.TaskDraft
import com.petcare.app.domain.model.TaskItem
import com.petcare.app.domain.repository.PetRepository
import com.petcare.app.domain.repository.SpaceRepository
import com.petcare.app.domain.repository.TaskRepository
import com.petcare.app.presentation.common.attempt
import com.petcare.app.presentation.common.invalidTimezoneMessage
import com.petcare.app.presentation.common.toUserMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

data class TaskEditState(
    val isNew: Boolean,
    val title: String = "",
    val petId: Long? = null,
    val assigneeId: Long? = null,
    val repeat: RepeatType = RepeatType.ONCE,
    /** ДД.ММ.ГГГГ — дата в поясе пространства; для ежедневной — дата начала. */
    val dateText: String = "",
    /** ЧЧ:ММ — время в поясе пространства. */
    val timeText: String = "",
    val pets: List<Pet> = emptyList(),
    val members: List<Member> = emptyList(),
    val isLoading: Boolean = true,
    val loadError: String? = null,
    /** «Europe/Moscow, UTC+03:00» — подпись к полям даты и времени. */
    val timezoneLabel: String = "",
    /** Пояс неверный — сохранять нельзя (срок посчитался бы неверно). */
    val timezoneError: String? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val save: SaveState = SaveState.Idle,
) {
    val canSave: Boolean
        get() = timezoneError == null && !isLoading && loadError == null && save != SaveState.Saving
}

/**
 * Создание и изменение задачи (FR-06, FR-10). Срок вводится в местном времени пространства.
 * Выбор питомца и ответственного — только из выбранного пространства (BR-01).
 */
class TaskEditController(
    private val scope: CoroutineScope,
    private val spaceRepo: SpaceRepository,
    private val petRepo: PetRepository,
    private val taskRepo: TaskRepository,
    private val space: Space,
    existing: TaskItem?,
    defaultPetId: Long?,
    private val onSaved: (Long) -> Unit,
    private val onUnconfirmed: () -> Unit,
    clock: Clock = Clock.systemUTC(),
) {
    private val taskId = existing?.task?.id

    private val _state = MutableStateFlow(initialState(existing, defaultPetId, clock))
    val state: StateFlow<TaskEditState> = _state.asStateFlow()

    init {
        loadChoices()
    }

    private fun initialState(existing: TaskItem?, defaultPetId: Long?, clock: Clock): TaskEditState {
        val zone = space.zone
        val base = TaskEditState(
            isNew = existing == null,
            petId = defaultPetId,
            timezoneLabel = space.timezoneLabel(clock.instant()),
            timezoneError = if (zone == null) invalidTimezoneMessage(space.timezoneId) else null,
        )
        if (zone == null) return base
        if (existing == null) {
            // По умолчанию — через час, в поясе пространства.
            val soon = clock.instant().atZone(zone).plusHours(1).withMinute(0)
            return base.copy(dateText = soon.toLocalDate().format(DATE), timeText = soon.toLocalTime().format(TIME))
        }
        val d = existing.toDraft(zone)
        return base.copy(
            title = d.title,
            petId = d.petId,
            assigneeId = d.assigneeId,
            repeat = d.repeat,
            dateText = d.date?.format(DATE).orEmpty(),
            timeText = d.time?.format(TIME).orEmpty(),
        )
    }

    /** Питомцы и участники ВЫБРАННОГО пространства. */
    fun loadChoices() {
        _state.update { it.copy(isLoading = true, loadError = null) }
        scope.launch {
            val (pets, members) = coroutineScope {
                val p = async { attempt { petRepo.getPets(space.id) } }
                val m = async { attempt { spaceRepo.getMembers(space.id) } }
                p.await() to m.await()
            }
            val error = pets.exceptionOrNull() ?: members.exceptionOrNull()
            _state.update { s ->
                val petList = pets.getOrDefault(emptyList())
                val memberList = members.getOrDefault(emptyList())
                s.copy(
                    isLoading = false,
                    loadError = error?.toUserMessage("Не удалось загрузить питомцев и участников"),
                    pets = petList,
                    members = memberList,
                    petId = s.petId?.takeIf { id -> petList.any { it.id == id } } ?: petList.firstOrNull()?.id,
                    // Ответственный из другого пространства не подставляется.
                    assigneeId = s.assigneeId?.takeIf { id -> memberList.any { it.id == id } },
                )
            }
        }
    }

    fun edit(transform: (TaskEditState) -> TaskEditState) {
        _state.update { s ->
            if (s.save == SaveState.Saving) return@update s
            val n = transform(s)
            n.copy(
                fieldErrors = s.fieldErrors - changedFields(s, n),
                save = if (s.save is SaveState.Saved || s.save is SaveState.Failed) SaveState.Idle else s.save,
            )
        }
    }

    fun save() {
        val s = _state.value
        if (!s.canSave) return // в том числе двойное нажатие во время Saving
        val (draft, errors) = buildDraft(s)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors, save = SaveState.Failed("Проверьте отмеченные поля")) }
            return
        }
        _state.update { it.copy(fieldErrors = emptyMap(), save = SaveState.Saving) }
        scope.launch {
            attempt {
                if (taskId == null) taskRepo.createTask(space, draft) else taskRepo.updateTask(space, taskId, draft)
            }.fold(
                { id ->
                    _state.update { it.copy(save = SaveState.Saved()) }
                    onSaved(id)
                },
                { e ->
                    val failure = e.toSaveFailure(::serverFieldToForm)
                    _state.update { it.copy(save = failure.state, fieldErrors = failure.fieldErrors) }
                    if (failure.state is SaveState.Unconfirmed) onUnconfirmed()
                },
            )
        }
    }

    private fun buildDraft(s: TaskEditState): Pair<TaskDraft, Map<String, String>> {
        val date = parseDate(s.dateText)
        val time = parseTime(s.timeText)
        val draft = TaskDraft(s.petId, s.title, s.assigneeId, s.repeat, date, time)
        val errors = draft.validate().toMutableMap()
        if (s.dateText.isNotBlank() && date == null) errors[TaskDraft.FIELD_DATE] = "Неверная дата, нужно ДД.ММ.ГГГГ"
        if (s.timeText.isNotBlank() && time == null) errors[TaskDraft.FIELD_TIME] = "Неверное время, нужно ЧЧ:ММ"
        if (s.petId != null && s.pets.none { it.id == s.petId }) errors[TaskDraft.FIELD_PET] = "Питомец не из этого пространства"
        if (s.assigneeId != null && s.members.none { it.id == s.assigneeId }) {
            errors[TaskDraft.FIELD_ASSIGNEE] = "Ответственный не из этого пространства"
        }
        return draft to errors
    }

    private fun changedFields(a: TaskEditState, b: TaskEditState) = buildSet {
        if (a.title != b.title) add(TaskDraft.FIELD_TITLE)
        if (a.petId != b.petId) add(TaskDraft.FIELD_PET)
        if (a.assigneeId != b.assigneeId) add(TaskDraft.FIELD_ASSIGNEE)
        if (a.dateText != b.dateText) add(TaskDraft.FIELD_DATE)
        if (a.timeText != b.timeText) add(TaskDraft.FIELD_TIME)
    }

    companion object {
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)

        fun parseDate(text: String): LocalDate? = try {
            LocalDate.parse(text.trim(), DATE)
        } catch (e: DateTimeParseException) {
            null
        }

        fun parseTime(text: String): LocalTime? = try {
            LocalTime.parse(text.trim(), TIME)
        } catch (e: DateTimeParseException) {
            null
        }

        /** Имена полей сервера (422 fields) → поля формы. */
        fun serverFieldToForm(field: String): String? = when (field) {
            "task_name" -> TaskDraft.FIELD_TITLE
            "pet_id" -> TaskDraft.FIELD_PET
            "responsible_id" -> TaskDraft.FIELD_ASSIGNEE
            "date_time", "start_date" -> TaskDraft.FIELD_DATE
            "local_time" -> TaskDraft.FIELD_TIME
            else -> null
        }
    }
}
