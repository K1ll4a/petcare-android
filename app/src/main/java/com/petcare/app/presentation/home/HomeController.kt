package com.petcare.app.presentation.home

import com.petcare.app.domain.model.Space
import com.petcare.app.domain.repository.PetRepository
import com.petcare.app.domain.repository.SpaceRepository
import com.petcare.app.domain.repository.TaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Clock
import java.time.LocalDate

/**
 * Логика главного экрана без зависимостей от Android — её можно тестировать на JVM.
 * [HomeViewModel] просто создаёт контроллер в viewModelScope.
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

    private var space: Space? = null
    private var screenJob: Job? = null
    private var tasksJob: Job? = null

    /** Загрузка пространства и питомцев, затем задач первого питомца. */
    fun start() {
        screenJob?.cancel()
        tasksJob?.cancel()
        _state.value = HomeUiState.Loading
        screenJob = scope.launch {
            try {
                val currentSpace = spaces.getCurrentSpace()
                val petList = pets.getPets(currentSpace.id)
                space = currentSpace
                val firstPet = petList.firstOrNull()
                _state.value = HomeUiState.Ready(
                    spaceName = currentSpace.name,
                    pets = petList,
                    selectedPetId = firstPet?.id,
                    tasks = if (firstPet == null) TasksState.NoPets else TasksState.Loading,
                )
                firstPet?.let { loadTasks(it.id, keepContent = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = HomeUiState.Error(e.toUserMessage("Не удалось загрузить данные"))
            }
        }
    }

    fun selectPet(petId: Long) {
        val ready = _state.value as? HomeUiState.Ready ?: return
        if (ready.selectedPetId == petId && ready.tasks !is TasksState.Error) return
        _state.value = ready.copy(selectedPetId = petId, tasks = TasksState.Loading, isRefreshing = false)
        loadTasks(petId, keepContent = false)
    }

    /** Кнопка «Повторить» в любом состоянии ошибки. */
    fun retry() {
        val ready = _state.value as? HomeUiState.Ready
        val petId = ready?.selectedPetId
        if (ready == null || space == null) {
            start()
        } else if (petId != null) {
            _state.value = ready.copy(tasks = TasksState.Loading)
            loadTasks(petId, keepContent = false)
        }
    }

    /** Pull-to-refresh: старый список остаётся на экране, пока не придёт новый. */
    fun refresh() {
        val ready = _state.value as? HomeUiState.Ready ?: return start()
        val petId = ready.selectedPetId ?: return
        _state.value = ready.copy(isRefreshing = true)
        loadTasks(petId, keepContent = true)
    }

    private fun loadTasks(petId: Long, keepContent: Boolean) {
        // Отменяем предыдущий запрос: ответ для старого питомца не нужен.
        tasksJob?.cancel()
        if (!keepContent) {
            _state.update { (it as? HomeUiState.Ready)?.copy(tasks = TasksState.Loading) ?: it }
        }
        tasksJob = scope.launch {
            val newTasks: TasksState = try {
                val items = tasks.getTaskItems(petId)
                // Защита от смешивания данных: задачи другого питомца не показываем (FR-19, AC-19).
                val ownItems = items.filter { it.task.petId == petId }
                if (ownItems.isEmpty()) {
                    TasksState.Empty
                } else {
                    val zone = space!!.timezone
                    val formatter = SpaceTimeFormatter(zone, LocalDate.now(clock.withZone(zone)))
                    TasksState.Content(ownItems.sortedForHome().map { it.toUi(formatter) })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TasksState.Error(e.toUserMessage("Не удалось загрузить задачи"))
            }

            _state.update { current ->
                // Поздний ответ: пока грузили, пользователь выбрал другого питомца — выбрасываем.
                if (current !is HomeUiState.Ready || current.selectedPetId != petId) {
                    current
                } else {
                    current.copy(tasks = newTasks, isRefreshing = false)
                }
            }
        }
    }

    private fun Exception.toUserMessage(fallback: String): String = when (this) {
        is IOException -> "Нет соединения с сервером. Проверьте интернет и повторите."
        else -> fallback
    }
}
