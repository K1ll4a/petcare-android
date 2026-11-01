package com.petcare.app.presentation.home

import com.petcare.app.domain.model.Pet
import com.petcare.app.domain.model.Space
import com.petcare.app.domain.model.TaskItem
import com.petcare.app.domain.repository.ApiException
import com.petcare.app.domain.repository.PetRepository
import com.petcare.app.domain.repository.SpaceRepository
import com.petcare.app.domain.repository.TaskRepository
import com.petcare.app.presentation.common.attempt
import com.petcare.app.presentation.common.invalidTimezoneMessage
import com.petcare.app.presentation.common.toUserMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/**
 * Логика главного экрана без Android — тестируется на JVM.
 *
 * Защита от поздних ответов (FR-19, AC-19, задача #14): каждое переключение пространства
 * или питомца и каждое обновление увеличивает [generation] и отменяет прошлую загрузку.
 * Результат применяется, только если поколение не сменилось, — старый ответ не может
 * заменить данные выбранного пространства или питомца.
 */
class HomeController(
    private val scope: CoroutineScope,
    private val spaces: SpaceRepository,
    private val pets: PetRepository,
    private val tasks: TaskRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var generation = 0
    private var job: Job? = null

    /** Задачи, показанные сейчас, — для открытия формы редактирования. */
    private var shownTasks: List<TaskItem> = emptyList()

    private val ready get() = _state.value as? HomeUiState.Ready

    /** Загрузить пространства и показать первое (или ранее выбранное). */
    fun start(preferSpaceId: Long? = ready?.space?.id) {
        val gen = newGeneration()
        _state.value = HomeUiState.Loading
        job = scope.launch {
            val result = attempt { spaces.getSpaces() }
            if (gen != generation) return@launch
            val list = result.getOrElse {
                _state.value = HomeUiState.Error(it.toUserMessage("Не удалось загрузить пространства"))
                return@launch
            }
            if (list.isEmpty()) {
                _state.value = HomeUiState.NoSpaces()
                return@launch
            }
            val space = list.firstOrNull { it.id == preferSpaceId } ?: list.first()
            openSpace(list, space, gen)
        }
    }

    /** Переключить пространство: старые данные сразу убираются с экрана (#13, FR-21). */
    fun selectSpace(spaceId: Long) {
        val current = ready ?: return
        val space = current.spaces.firstOrNull { it.id == spaceId } ?: return
        if (space.id == current.space.id && current.pets !is PetsState.Error) return
        val gen = newGeneration()
        job = scope.launch { openSpace(current.spaces, space, gen) }
    }

    fun selectPet(petId: Long) {
        val current = ready ?: return
        if (current.selectedPetId == petId &&
            current.tasks !is TasksState.Error && current.profile !is ProfileState.Error
        ) return
        val gen = newGeneration()
        _state.value = current.copy(
            selectedPetId = petId,
            profile = ProfileState.Loading,
            tasks = TasksState.Loading,
            isRefreshing = false,
            staleMessage = null,
        )
        val space = current.space
        job = scope.launch { loadPetData(space, petId, gen) }
    }

    /** «Повторить»: перезагрузить то, что не загрузилось. */
    fun retry() {
        val current = ready ?: return start()
        when {
            current.pets is PetsState.Error -> {
                val gen = newGeneration()
                job = scope.launch { openSpace(current.spaces, current.space, gen) }
            }
            current.selectedPetId != null -> {
                val gen = newGeneration()
                _state.value = current.copy(
                    profile = ProfileState.Loading, tasks = TasksState.Loading, staleMessage = null,
                )
                val petId = current.selectedPetId
                job = scope.launch { loadPetData(current.space, petId, gen) }
            }
            else -> start()
        }
    }

    /**
     * Ручное обновление (задача #14, шаг 4): список питомцев, профиль И задачи.
     * Прежние данные остаются на экране, пока не придут новые; при ошибке — помечаются
     * как не обновлённые (FR-19).
     */
    fun refresh() {
        val current = ready ?: return start()
        val gen = newGeneration()
        _state.value = current.copy(isRefreshing = true, staleMessage = null)
        val space = current.space
        val petId = current.selectedPetId
        job = scope.launch {
            val (petsResult, profileResult, tasksResult) = coroutineScope {
                val p = async { attempt { pets.getPets(space.id) } }
                val pr = async { if (petId == null) null else attempt { pets.getPet(space.id, petId) } }
                val t = async { if (petId == null || space.zone == null) null else attempt { tasks.getTaskItems(space.id, petId) } }
                Triple(p.await(), pr.await(), t.await())
            }
            if (gen != generation) return@launch
            val before = ready ?: return@launch
            if (handleAccessLost(listOfNotNull(petsResult.exceptionOrNull(), profileResult?.exceptionOrNull(), tasksResult?.exceptionOrNull()))) {
                return@launch
            }

            val errors = mutableListOf<Throwable>()
            val newPets = petsResult.fold(
                { PetsState.Content(it) },
                { errors += it; if (before.pets is PetsState.Content) before.pets else PetsState.Error(it.toUserMessage("Не удалось загрузить питомцев")) },
            )
            // Питомца удалили или перенесли — выбираем первого из нового списка.
            val stillThere = (newPets as? PetsState.Content)?.items?.any { it.id == petId } ?: true
            if (!stillThere) {
                _state.value = before.copy(pets = newPets, isRefreshing = false)
                selectFirstPet(space, newPets, gen)
                return@launch
            }
            val newProfile = when {
                profileResult == null -> before.profile
                else -> profileResult.fold(
                    { ProfileState.Content(it) },
                    { errors += it; if (before.profile is ProfileState.Content) before.profile else ProfileState.Error(it.toUserMessage("Не удалось загрузить профиль")) },
                )
            }
            val newTasks = when {
                petId == null -> TasksState.NoPets
                space.zone == null -> TasksState.Error(invalidTimezoneMessage(space.timezoneId))
                tasksResult == null -> before.tasks
                else -> tasksResult.fold(
                    { tasksState(space, petId, it) },
                    { errors += it; if (before.tasks is TasksState.Content) before.tasks else TasksState.Error(it.toUserMessage("Не удалось загрузить задачи")) },
                )
            }
            _state.value = before.copy(
                pets = newPets,
                profile = newProfile,
                tasks = newTasks,
                isRefreshing = false,
                staleMessage = errors.firstOrNull()?.let {
                    "Данные не обновлены: ${it.toUserMessage("ошибка загрузки")} Показаны прежние."
                },
            )
        }
    }

    /** Вызывается после подтверждённого (или неподтверждённого) сохранения — показываем серверное состояние. */
    fun reloadAfterSave(selectPetId: Long? = null) {
        val current = ready ?: return start()
        if (selectPetId != null && selectPetId != current.selectedPetId) {
            // Новый питомец: сначала перечитываем список, затем открываем его.
            val gen = newGeneration()
            job = scope.launch { openSpace(current.spaces, current.space, gen, preferPetId = selectPetId) }
        } else {
            refresh()
        }
    }

    // ---------- загрузка ----------

    private suspend fun openSpace(
        allSpaces: List<Space>,
        space: Space,
        gen: Int,
        preferPetId: Long? = null,
        notice: String? = null,
    ) {
        if (gen != generation) return
        _state.value = HomeUiState.Ready(
            spaces = allSpaces,
            space = space,
            pets = PetsState.Loading,
            selectedPetId = null,
            profile = ProfileState.None,
            tasks = TasksState.Loading,
            staleMessage = notice,
        )
        val result = attempt { pets.getPets(space.id) }
        if (gen != generation) return
        result.exceptionOrNull()?.let { if (handleAccessLost(listOf(it))) return }
        val petsState = result.fold(
            { PetsState.Content(it) },
            { PetsState.Error(it.toUserMessage("Не удалось загрузить питомцев")) },
        )
        if (petsState is PetsState.Error) {
            _state.value = (ready ?: return).copy(pets = petsState, tasks = TasksState.Loading)
            return
        }
        selectFirstPet(space, petsState as PetsState.Content, gen, preferPetId)
    }

    private suspend fun selectFirstPet(space: Space, petsState: PetsState, gen: Int, preferPetId: Long? = null) {
        val list = (petsState as? PetsState.Content)?.items.orEmpty()
        val pet = list.firstOrNull { it.id == preferPetId } ?: list.firstOrNull()
        val base = ready ?: return
        _state.value = base.copy(
            pets = petsState,
            selectedPetId = pet?.id,
            profile = if (pet == null) ProfileState.None else ProfileState.Loading,
            tasks = if (pet == null) TasksState.NoPets else TasksState.Loading,
            isRefreshing = false,
        )
        if (pet != null) loadPetData(space, pet.id, gen)
    }

    /** Профиль и задачи питомца — параллельно; каждая часть со своим состоянием. */
    private suspend fun loadPetData(space: Space, petId: Long, gen: Int) {
        val (profileResult, tasksResult) = coroutineScope {
            val pr = async { attempt { pets.getPet(space.id, petId) } }
            // Неверный пояс: задачи не запрашиваем и не показываем со сроком в UTC.
            val t = async { if (space.zone == null) null else attempt { tasks.getTaskItems(space.id, petId) } }
            pr.await() to t.await()
        }
        if (gen != generation) return
        if (handleAccessLost(listOfNotNull(profileResult.exceptionOrNull(), tasksResult?.exceptionOrNull()))) return
        val current = ready ?: return
        if (current.space.id != space.id || current.selectedPetId != petId) return

        _state.value = current.copy(
            profile = profileResult.fold(
                { ProfileState.Content(it) },
                { ProfileState.Error(it.toUserMessage("Не удалось загрузить профиль")) },
            ),
            tasks = when {
                tasksResult == null -> TasksState.Error(invalidTimezoneMessage(space.timezoneId))
                else -> tasksResult.fold(
                    { tasksState(space, petId, it) },
                    { TasksState.Error(it.toUserMessage("Не удалось загрузить задачи")) },
                )
            },
            isRefreshing = false,
        )
    }

    private fun tasksState(space: Space, petId: Long, items: List<TaskItem>): TasksState {
        // Вторая линия защиты от смешивания данных: только задачи этого питомца и пространства.
        val own = items.filter { it.task.petId == petId && (it.spaceId == null || it.spaceId == space.id) }
        shownTasks = own
        if (own.isEmpty()) return TasksState.Empty
        val zone = space.zone ?: return TasksState.Error(invalidTimezoneMessage(space.timezoneId))
        val formatter = SpaceTimeFormatter(zone, LocalDate.now(clock.withZone(zone)))
        return TasksState.Content(
            items = own.sortedForHome().map { it.toUi(formatter) },
            timezoneLabel = space.timezoneLabel(clock.instant()),
        )
    }

    /**
     * Сервер сказал «нет доступа к пространству» (403 forbidden): убираем его данные
     * с экрана и из списка, другие пространства не трогаем (FR-19, FR-22).
     */
    private fun handleAccessLost(errors: List<Throwable>): Boolean {
        if (errors.none { it is ApiException && it.isAccessLost }) return false
        val current = ready ?: return false
        val rest = current.spaces.filterNot { it.id == current.space.id }
        val message = "Доступ к пространству «${current.space.name}» закрыт. Его данные убраны с экрана."
        if (rest.isEmpty()) {
            newGeneration()
            _state.value = HomeUiState.NoSpaces(message)
        } else {
            val gen = newGeneration()
            job = scope.launch { openSpace(rest, rest.first(), gen, notice = message) }
        }
        return true
    }

    private fun newGeneration(): Int {
        job?.cancel()
        return ++generation
    }

    /** Задача из показанного списка по ключу строки. */
    fun findTask(key: String): TaskItem? = shownTasks.firstOrNull { it.key == key }

    /** Для экранов редактирования: текущее пространство и питомцы. */
    val currentSpace: Space? get() = ready?.space
    val currentPets: List<Pet> get() = ((ready?.pets) as? PetsState.Content)?.items.orEmpty()
}
