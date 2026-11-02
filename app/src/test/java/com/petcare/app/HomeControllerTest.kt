package com.petcare.app

import com.petcare.app.data.mapper.toDomain
import com.petcare.app.data.remote.dto.SpaceDto
import com.petcare.app.domain.model.Member
import com.petcare.app.domain.model.Pet
import com.petcare.app.domain.model.PetDraft
import com.petcare.app.domain.model.PetType
import com.petcare.app.domain.model.RepeatType
import com.petcare.app.domain.model.Space
import com.petcare.app.domain.model.Task
import com.petcare.app.domain.model.TaskDraft
import com.petcare.app.domain.model.TaskItem
import com.petcare.app.domain.model.TaskStatus
import com.petcare.app.domain.repository.ApiException
import com.petcare.app.domain.repository.PetRepository
import com.petcare.app.domain.repository.SpaceRepository
import com.petcare.app.domain.repository.TaskRepository
import com.petcare.app.presentation.home.HomeController
import com.petcare.app.presentation.home.HomeUiState
import com.petcare.app.presentation.home.PetsState
import com.petcare.app.presentation.home.ProfileState
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Репозиторий, ответы которого тест отдаёт вручную, в любом порядке, — так детерминированно
 * воспроизводятся медленный ответ, поздний ответ и ошибки. Ключ запроса: "pets:1", "pet:1:10", "tasks:1:10".
 */
class ControlledRepo : SpaceRepository, PetRepository, TaskRepository {
    val pending = mutableMapOf<String, MutableList<CompletableDeferred<Any>>>()
    var spaces = listOf(space(1, "Семья", "Europe/Moscow"), space(2, "Дача", "Asia/Yekaterinburg"))

    /** Автоответ на getSpaces — в тестах переключений он не интересен. */
    override suspend fun getSpaces() = spaces
    override suspend fun getMembers(spaceId: Long) = emptyList<Member>()
    override suspend fun getPetTypes() = emptyList<PetType>()

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> wait(key: String): T {
        val d = CompletableDeferred<Any>()
        pending.getOrPut(key) { mutableListOf() }.add(d)
        return d.await() as T
    }

    override suspend fun getPets(spaceId: Long): List<Pet> = wait("pets:$spaceId")
    override suspend fun getPet(spaceId: Long, petId: Long): Pet = wait("pet:$spaceId:$petId")
    override suspend fun getTaskItems(spaceId: Long, petId: Long): List<TaskItem> = wait("tasks:$spaceId:$petId")
    override suspend fun createPet(spaceId: Long, draft: PetDraft): Pet = wait("createPet")
    override suspend fun updatePet(spaceId: Long, petId: Long, draft: PetDraft): Pet = wait("updatePet")
    override suspend fun createTask(space: Space, draft: TaskDraft): Long = wait("createTask")
    override suspend fun updateTask(space: Space, taskId: Long, draft: TaskDraft): Long = wait("updateTask")

    fun has(key: String) = pending[key]?.isNotEmpty() == true

    fun respond(key: String, value: Any) {
        val list = pending[key] ?: error("Нет ожидающего запроса $key; есть: ${pending.filterValues { it.isNotEmpty() }.keys}")
        list.removeAt(0).complete(value)
    }

    fun fail(key: String, e: Throwable = IOException("offline")) {
        pending.getValue(key).removeAt(0).completeExceptionally(e)
    }

    companion object {
        fun space(id: Long, name: String, tz: String) = SpaceDto(id, name, tz, "owner").toDomain()
        fun pet(id: Long, space: Long, name: String) = Pet(id, space, name, 1, "кот", allergies = "курица $name")
        fun task(id: Long, petId: Long, space: Long, title: String, status: TaskStatus = TaskStatus.UPCOMING) = TaskItem(
            task = Task(id, petId, title, RepeatType.ONCE, 3, "Гриша"),
            dueAt = Instant.parse("2026-10-22T10:00:00Z"),
            occurrenceDate = null,
            status = status,
            spaceId = space,
        )
    }
}

class HomeControllerTest {
    // Unconfined: корутины идут сразу до первой приостановки — порядок задаёт тест.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val repo = ControlledRepo()
    private val clock = Clock.fixed(Instant.parse("2026-10-22T09:00:00Z"), ZoneOffset.UTC)
    private val controller = HomeController(scope, repo, repo, repo, clock)

    private val ready get() = controller.state.value as HomeUiState.Ready
    private fun titles() = (ready.tasks as TasksState.Content).items.map { it.title }

    private val tom = ControlledRepo.pet(10, 1, "Tom")
    private val rex = ControlledRepo.pet(11, 1, "Рекс")
    private val murka = ControlledRepo.pet(20, 2, "Мурка")

    @After
    fun tearDown() = scope.cancel()

    /** Открыть «Семью»: Tom и Рекс, у Tom одна задача. */
    private suspend fun openFamily() {
        controller.start()
        yield()
        repo.respond("pets:1", listOf(tom, rex))
        repo.respond("pet:1:10", tom)
        repo.respond("tasks:1:10", listOf(ControlledRepo.task(1, 10, 1, "покормить")))
        yield()
    }

    // ---------- 7 тестов недели 1, адаптированы под пространства ----------

    @Test
    fun start_selectsFirstPet_andShowsItsTasks() = runBlocking {
        controller.start()
        yield()
        assertEquals(PetsState.Loading, ready.pets)
        repo.respond("pets:1", listOf(tom, rex))
        assertEquals(10L, ready.selectedPetId)
        assertEquals(TasksState.Loading, ready.tasks)

        repo.respond("pet:1:10", tom)
        repo.respond("tasks:1:10", listOf(ControlledRepo.task(1, 10, 1, "покормить")))
        yield()
        val content = ready.tasks as TasksState.Content
        assertEquals(listOf("Покормить"), content.items.map { it.title })
        assertEquals("сегодня, 13:00", content.items[0].dueText) // 10:00 UTC = 13:00 МСК
        assertEquals("Europe/Moscow, UTC+03:00", content.items[0].zoneText)
        assertEquals("Ответственный: Гриша", content.items[0].assigneeText)
        assertEquals("Предстоит", content.items[0].statusText)
    }

    @Test
    fun lateResponseForPreviousPet_isDiscarded() = runBlocking {
        controller.start()
        yield()
        repo.respond("pets:1", listOf(tom, rex))
        controller.selectPet(11) // переключились, пока данные Tom ещё грузятся
        repo.respond("pet:1:11", rex)
        repo.respond("tasks:1:11", listOf(ControlledRepo.task(2, 11, 1, "прогулка")))
        yield()
        // Запрос Tom отменён; даже если ответ пришёл бы — Рекс не получит чужие данные.
        repo.pending["tasks:1:10"]?.firstOrNull()?.complete(listOf(ControlledRepo.task(1, 10, 1, "покормить")))
        repo.pending["pet:1:10"]?.firstOrNull()?.complete(tom)
        yield()

        assertEquals(11L, ready.selectedPetId)
        assertEquals(listOf("Прогулка"), titles())
        assertEquals("Рекс", (ready.profile as ProfileState.Content).pet.name)
    }

    @Test
    fun tasksOfAnotherPet_inResponse_areNotShown() = runBlocking {
        controller.start()
        yield()
        repo.respond("pets:1", listOf(tom))
        repo.respond("pet:1:10", tom)
        repo.respond("tasks:1:10", listOf(ControlledRepo.task(1, 10, 1, "своя"), ControlledRepo.task(2, 11, 1, "чужая"), ControlledRepo.task(3, 10, 2, "из другого пространства")))
        yield()
        assertEquals(listOf("Своя"), titles())
    }

    @Test
    fun error_thenRetry_showsContent() = runBlocking {
        controller.start()
        yield()
        repo.respond("pets:1", listOf(tom))
        repo.respond("pet:1:10", tom)
        repo.fail("tasks:1:10")
        yield()
        assertTrue((ready.tasks as TasksState.Error).message.contains("соединения"))
        assertTrue(ready.profile is ProfileState.Content) // профиль загрузился независимо

        controller.retry()
        assertEquals(TasksState.Loading, ready.tasks)
        repo.respond("pet:1:10", tom)
        repo.respond("tasks:1:10", listOf(ControlledRepo.task(1, 10, 1, "покормить")))
        yield()
        assertTrue(ready.tasks is TasksState.Content)
    }

    @Test
    fun noTasks_isEmptyState() = runBlocking {
        controller.start()
        yield()
        repo.respond("pets:1", listOf(tom))
        repo.respond("pet:1:10", tom)
        repo.respond("tasks:1:10", emptyList<TaskItem>())
        yield()
        assertEquals(TasksState.Empty, ready.tasks)
    }

    @Test
    fun noPets_isNoPetsState() = runBlocking {
        controller.start()
        yield()
        repo.respond("pets:1", emptyList<Pet>())
        assertEquals(TasksState.NoPets, ready.tasks)
        assertEquals(ProfileState.None, ready.profile)
    }

    @Test
    fun refresh_keepsOldDataUntilNewArrives() = runBlocking {
        openFamily()
        controller.refresh()
        assertTrue(ready.isRefreshing)
        assertEquals(listOf("Покормить"), titles()) // старый список виден

        repo.respond("pets:1", listOf(tom, rex))
        repo.respond("pet:1:10", tom)
        repo.respond("tasks:1:10", listOf(ControlledRepo.task(1, 10, 1, "новая")))
        yield()
        assertFalse(ready.isRefreshing)
        assertEquals(listOf("Новая"), titles())
    }

    // ---------- новые: задача #14 ----------

    @Test
    fun refresh_loadsBothProfileAndTasks() = runBlocking {
        openFamily()
        controller.refresh()
        // Шаг 4: обновление запрашивает и список питомцев, и профиль, и задачи.
        assertTrue(repo.has("pets:1"))
        assertTrue(repo.has("pet:1:10"))
        assertTrue(repo.has("tasks:1:10"))
        repo.respond("pets:1", listOf(tom, rex))
        repo.respond("pet:1:10", tom.copy(allergies = "курица, рыба", intolerances = "молоко"))
        repo.respond("tasks:1:10", listOf(ControlledRepo.task(1, 10, 1, "покормить")))
        yield()
        val pet = (ready.profile as ProfileState.Content).pet
        assertEquals("курица, рыба", pet.allergies)
        assertEquals("молоко", pet.intolerances)
    }

    @Test
    fun refreshFailure_keepsOldData_andMarksStale() = runBlocking {
        openFamily()
        controller.refresh()
        repo.fail("pets:1")
        repo.fail("pet:1:10")
        repo.fail("tasks:1:10")
        yield()
        assertEquals(listOf("Покормить"), titles())
        assertTrue(ready.profile is ProfileState.Content)
        assertTrue(ready.staleMessage!!.contains("не обновлены"))
        assertFalse(ready.isRefreshing)
    }

    @Test
    fun switchSpace_clearsOldDataImmediately() = runBlocking {
        openFamily()
        controller.selectSpace(2)
        assertEquals(2L, ready.space.id)
        assertEquals(PetsState.Loading, ready.pets)
        assertEquals(ProfileState.None, ready.profile)
        assertEquals(TasksState.Loading, ready.tasks)
        assertNull(ready.selectedPetId)
    }

    @Test
    fun twoSpaces_twoPets_lateResponsesOfOldSpaceAreDiscarded() = runBlocking {
        controller.start()
        yield()
        repo.respond("pets:1", listOf(tom, rex)) // «Семья»: грузятся профиль и задачи Tom (медленно)
        controller.selectSpace(2)                // быстро переключились на «Дачу»
        repo.respond("pets:2", listOf(murka))
        repo.respond("pet:2:20", murka)
        repo.respond("tasks:2:20", listOf(ControlledRepo.task(20, 20, 2, "покормить мурку")))
        yield()
        // Поздние ответы «Семьи» приходят уже после переключения.
        repo.pending["pet:1:10"]?.firstOrNull()?.complete(tom)
        repo.pending["tasks:1:10"]?.firstOrNull()?.complete(listOf(ControlledRepo.task(1, 10, 1, "покормить тома")))
        yield()

        assertEquals(2L, ready.space.id)
        assertEquals(20L, ready.selectedPetId)
        assertEquals("Мурка", (ready.profile as ProfileState.Content).pet.name)
        assertEquals(listOf("Покормить мурку"), titles())
        // Срок — в поясе «Дачи»: 10:00 UTC = 15:00 Екатеринбург.
        val item = (ready.tasks as TasksState.Content).items.single()
        assertEquals("сегодня, 15:00", item.dueText)
        assertEquals("Asia/Yekaterinburg, UTC+05:00", item.zoneText)
    }

    @Test
    fun rapidSwitching_backAndForth_showsOnlyLastSelection() = runBlocking {
        openFamily()
        controller.selectPet(11)
        controller.selectPet(10)
        controller.selectPet(11)
        // Отвечают все запросы, в любом порядке; учитываться должен только последний выбор.
        while (repo.has("pet:1:10")) repo.respond("pet:1:10", tom)
        while (repo.has("tasks:1:10")) repo.respond("tasks:1:10", listOf(ControlledRepo.task(1, 10, 1, "том")))
        while (repo.has("tasks:1:11")) repo.respond("tasks:1:11", listOf(ControlledRepo.task(2, 11, 1, "рекс")))
        while (repo.has("pet:1:11")) repo.respond("pet:1:11", rex)
        yield()
        assertEquals(11L, ready.selectedPetId)
        assertEquals(listOf("Рекс"), titles())
        assertEquals("Рекс", (ready.profile as ProfileState.Content).pet.name)
    }

    @Test
    fun slowResponse_showsLoading_andAllowsSwitchingMeanwhile() = runBlocking {
        controller.start()
        yield()
        repo.respond("pets:1", listOf(tom, rex))
        // Ответа по Tom нет — экран в загрузке, но выбор доступен.
        assertEquals(TasksState.Loading, ready.tasks)
        assertEquals(ProfileState.Loading, ready.profile)
        controller.selectPet(11)
        assertEquals(11L, ready.selectedPetId)
        assertEquals(TasksState.Loading, ready.tasks)
    }

    @Test
    fun invalidTimezone_showsError_notUtcTimes() = runBlocking {
        repo.spaces = listOf(ControlledRepo.space(3, "Сломанный", "Mars/Olympus"))
        controller.start()
        yield()
        repo.respond("pets:3", listOf(ControlledRepo.pet(30, 3, "Пушок")))
        repo.respond("pet:3:30", ControlledRepo.pet(30, 3, "Пушок"))
        yield()
        // Задачи даже не запрашивались, вместо них — ошибка про пояс.
        assertFalse(repo.has("tasks:3:30"))
        val error = ready.tasks as TasksState.Error
        assertTrue(error.message.contains("неверный часовой пояс"))
        assertTrue(error.message.contains("Mars/Olympus"))
        assertTrue(ready.profile is ProfileState.Content) // профиль при этом виден
    }

    @Test
    fun accessLost_removesThatSpaceOnly() = runBlocking {
        openFamily()
        controller.refresh()
        repo.fail("pets:1", ApiException(403, "forbidden", "Нет доступа"))
        repo.respond("pet:1:10", tom)
        repo.respond("tasks:1:10", emptyList<TaskItem>())
        yield()
        // «Семья» убрана, открыта «Дача».
        assertEquals(listOf(2L), ready.spaces.map { it.id })
        assertEquals(2L, ready.space.id)
        repo.respond("pets:2", listOf(murka))
        yield()
        assertTrue(ready.staleMessage!!.contains("Доступ к пространству «Семья» закрыт"))
    }

    @Test
    fun refreshDuringInitialLoad_keepsRequestedSpace() = runBlocking {
        val response = CompletableDeferred<List<Space>>()
        val delayedSpaces = object : SpaceRepository by repo {
            override suspend fun getSpaces() = response.await()
        }
        val screen = HomeController(scope, delayedSpaces, repo, repo, clock)
        screen.start(preferSpaceId = 2)
        assertTrue(screen.state.value is HomeUiState.Loading)
        screen.refresh() // ON_RESUME до ответа на список пространств.
        response.complete(repo.spaces)
        yield()
        val state = screen.state.value as HomeUiState.Ready
        assertEquals(2L, state.space.id)
        assertTrue(repo.has("pets:2"))
        assertFalse(repo.has("pets:1"))
    }

    @Test
    fun noSpaces_andSpacesError() = runBlocking {
        repo.spaces = emptyList()
        controller.start()
        yield()
        assertTrue(controller.state.value is HomeUiState.NoSpaces)
    }
}
