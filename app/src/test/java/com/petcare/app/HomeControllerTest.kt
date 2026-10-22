package com.petcare.app

import com.petcare.app.domain.model.Pet
import com.petcare.app.domain.model.RepeatType
import com.petcare.app.domain.model.Space
import com.petcare.app.domain.model.Task
import com.petcare.app.domain.model.TaskItem
import com.petcare.app.domain.model.TaskStatus
import com.petcare.app.domain.repository.PetRepository
import com.petcare.app.domain.repository.SpaceRepository
import com.petcare.app.domain.repository.TaskRepository
import com.petcare.app.presentation.home.HomeController
import com.petcare.app.presentation.home.HomeUiState
import com.petcare.app.presentation.home.TasksState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Репозиторий, ответы которого тест отдаёт вручную — так можно
 * детерминированно воспроизвести «поздний ответ» и ошибки.
 */
private class ControlledRepo : SpaceRepository, PetRepository, TaskRepository {
    val pending = mutableMapOf<Long, MutableList<CompletableDeferred<List<TaskItem>>>>()
    var petList = listOf(
        Pet(10, 1, "Tom", 1, "кот"),
        Pet(11, 1, "Рекс", 2, "собака"),
    )

    override suspend fun getCurrentSpace() = Space(1, "Дом", ZoneId.of("Europe/Moscow"))
    override suspend fun getPets(spaceId: Long) = petList
    override suspend fun getPet(petId: Long) = petList.first { it.id == petId }

    override suspend fun getTaskItems(petId: Long): List<TaskItem> {
        val d = CompletableDeferred<List<TaskItem>>()
        pending.getOrPut(petId) { mutableListOf() }.add(d)
        return d.await()
    }

    fun respond(petId: Long, items: List<TaskItem>) =
        pending.getValue(petId).removeAt(0).complete(items)

    fun fail(petId: Long) =
        pending.getValue(petId).removeAt(0).completeExceptionally(IOException("offline"))
}

private fun task(id: Long, petId: Long, title: String, status: TaskStatus = TaskStatus.UPCOMING) =
    TaskItem(
        task = Task(id, petId, title, RepeatType.ONCE, 3, "Гриша"),
        dueAt = Instant.parse("2026-10-22T10:00:00Z"),
        occurrenceDate = null,
        status = status,
    )

class HomeControllerTest {

    // Unconfined: корутины выполняются сразу до первой приостановки — порядок полностью задаёт тест.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val repo = ControlledRepo()
    private val clock = Clock.fixed(Instant.parse("2026-10-22T09:00:00Z"), ZoneOffset.UTC)
    private val controller = HomeController(scope, repo, repo, repo, clock)

    private val ready get() = controller.state.value as HomeUiState.Ready

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun start_selectsFirstPet_andShowsItsTasks() = runBlocking {
        controller.start()
        yield()
        assertEquals(10L, ready.selectedPetId)
        assertEquals(TasksState.Loading, ready.tasks)

        repo.respond(10, listOf(task(1, 10, "покормить")))
        yield()
        val content = ready.tasks as TasksState.Content
        assertEquals(listOf("Покормить"), content.items.map { it.title })
        assertEquals("сегодня, 13:00", content.items[0].dueText) // 10:00 UTC = 13:00 МСК
    }

    @Test
    fun lateResponseForPreviousPet_isDiscarded() = runBlocking {
        controller.start()
        yield()
        controller.selectPet(11) // переключились, пока задачи Tom ещё грузятся
        repo.respond(11, listOf(task(2, 11, "прогулка")))
        yield()
        // Запрос Tom был отменён; даже если бы ответ пришёл — Рекс не получит чужие задачи.
        repo.pending[10]?.firstOrNull()?.complete(listOf(task(1, 10, "покормить")))
        yield()

        assertEquals(11L, ready.selectedPetId)
        val titles = (ready.tasks as TasksState.Content).items.map { it.title }
        assertEquals(listOf("Прогулка"), titles)
    }

    @Test
    fun tasksOfAnotherPet_inResponse_areNotShown() = runBlocking {
        controller.start()
        yield()
        repo.respond(10, listOf(task(1, 10, "своя"), task(2, 11, "чужая")))
        yield()
        assertEquals(listOf("Своя"), (ready.tasks as TasksState.Content).items.map { it.title })
    }

    @Test
    fun error_thenRetry_showsContent() = runBlocking {
        controller.start()
        yield()
        repo.fail(10)
        yield()
        val error = ready.tasks as TasksState.Error
        assertTrue(error.message.contains("соединения"))

        controller.retry()
        assertEquals(TasksState.Loading, ready.tasks)
        repo.respond(10, listOf(task(1, 10, "покормить")))
        yield()
        assertTrue(ready.tasks is TasksState.Content)
    }

    @Test
    fun noTasks_isEmptyState() = runBlocking {
        controller.start()
        yield()
        repo.respond(10, emptyList())
        yield()
        assertEquals(TasksState.Empty, ready.tasks)
    }

    @Test
    fun noPets_isNoPetsState() = runBlocking {
        repo.petList = emptyList()
        controller.start()
        yield()
        assertEquals(TasksState.NoPets, ready.tasks)
    }

    @Test
    fun refresh_keepsOldListUntilNewArrives() = runBlocking {
        controller.start()
        yield()
        repo.respond(10, listOf(task(1, 10, "старая")))
        yield()

        controller.refresh()
        assertTrue(ready.isRefreshing)
        assertTrue(ready.tasks is TasksState.Content) // старый список виден

        repo.respond(10, listOf(task(1, 10, "новая")))
        yield()
        assertEquals(false, ready.isRefreshing)
        assertEquals(listOf("Новая"), (ready.tasks as TasksState.Content).items.map { it.title })
    }
}
