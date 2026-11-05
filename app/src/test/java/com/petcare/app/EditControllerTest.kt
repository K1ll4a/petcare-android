package com.petcare.app

import kotlinx.datetime.*
import com.petcare.app.time.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import com.petcare.app.domain.model.Member
import com.petcare.app.domain.model.Pet
import com.petcare.app.domain.model.PetDraft
import com.petcare.app.domain.model.PetType
import com.petcare.app.domain.model.RepeatType
import com.petcare.app.domain.model.Space
import com.petcare.app.domain.model.SpaceRole
import com.petcare.app.domain.model.TaskDraft
import com.petcare.app.domain.repository.ApiException
import com.petcare.app.domain.repository.PetRepository
import com.petcare.app.domain.repository.SpaceRepository
import com.petcare.app.domain.repository.TaskRepository
import com.petcare.app.presentation.edit.PetEditController
import com.petcare.app.presentation.edit.SaveState
import com.petcare.app.presentation.edit.TaskEditController
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
import kotlinx.io.IOException

/** Сохранение отвечает только тогда, когда тест скажет. */
private class SaveRepo : SpaceRepository, PetRepository, TaskRepository {
    var petSave = CompletableDeferred<Pet>()
    var taskSave = CompletableDeferred<Long>()
    var petCalls = 0
    var taskCalls = 0
    var lastTask: TaskDraft? = null
    var lastTaskSpace: Space? = null

    override suspend fun getSpaces() = emptyList<Space>()
    override suspend fun getMembers(spaceId: Long) =
        listOf(Member(3, "Гриша", SpaceRole.OWNER), Member(4, "Аня", SpaceRole.MEMBER))
    override suspend fun getPetTypes() = listOf(PetType(1, "кот"), PetType(2, "собака"))
    override suspend fun getPets(spaceId: Long) = listOf(Pet(10, spaceId, "Tom", 1, "кот"))
    override suspend fun getPet(spaceId: Long, petId: Long) = getPets(spaceId).first()
    override suspend fun createPet(spaceId: Long, draft: PetDraft): Pet { petCalls++; return petSave.await() }
    override suspend fun updatePet(spaceId: Long, petId: Long, draft: PetDraft): Pet { petCalls++; return petSave.await() }
    override suspend fun getTaskItems(spaceId: Long, petId: Long) = emptyList<com.petcare.app.domain.model.TaskItem>()
    override suspend fun createTask(space: Space, draft: TaskDraft): Long {
        taskCalls++; lastTask = draft; lastTaskSpace = space; return taskSave.await()
    }
    override suspend fun updateTask(space: Space, taskId: Long, draft: TaskDraft): Long {
        taskCalls++; lastTask = draft; return taskSave.await()
    }
}

class EditControllerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val repo = SaveRepo()
    private val clock = fixedClock(Instant.parse("2026-10-22T09:00:00Z"), kotlinx.datetime.TimeZone.UTC)
    private var saved = 0
    private var unconfirmed = 0

    private val moscow = ControlledRepo.space(1, "Семья", "Europe/Moscow")
    private val broken = ControlledRepo.space(3, "Сломанный", "Mars/Olympus")

    @After
    fun tearDown() = scope.cancel()

    private fun petController(existing: Pet? = null) =
        PetEditController(scope, repo, 1, existing, onSaved = { saved++ }, onUnconfirmed = { unconfirmed++ })

    private fun taskController(space: Space = moscow) = TaskEditController(
        scope, repo, repo, repo, space, existing = null, defaultPetId = 10,
        onSaved = { saved++ }, onUnconfirmed = { unconfirmed++ }, clock = clock,
    )

    // ---------- питомец ----------

    @Test
    fun pet_notSavedUntilServerAnswers() = runBlocking {
        val c = petController()
        c.edit { it.copy(name = "Барсик", typeId = 1, allergies = "курица", intolerances = "молоко") }
        c.save()
        assertEquals(SaveState.Saving, c.state.value.save)
        assertEquals(0, saved)

        repo.petSave.complete(Pet(99, 1, "Барсик", 1, "кот", allergies = "курица", intolerances = "молоко"))
        yield()
        assertEquals(SaveState.Saved(), c.state.value.save)
        assertEquals(1, saved)
        assertEquals("молоко", c.state.value.draft.intolerances)
    }

    @Test
    fun pet_doubleTap_sendsOneRequest() = runBlocking {
        val c = petController()
        c.edit { it.copy(name = "Барсик", typeId = 1) }
        c.save()
        c.save()
        assertEquals(1, repo.petCalls)
    }

    @Test
    fun pet_withoutNameOrType_isNotSent() = runBlocking {
        val c = petController()
        c.save()
        assertEquals(0, repo.petCalls)
        assertTrue(PetDraft.FIELD_NAME in c.state.value.fieldErrors)
        assertTrue(PetDraft.FIELD_TYPE in c.state.value.fieldErrors)
        // Исправление поля убирает его ошибку.
        c.edit { it.copy(name = "Барсик") }
        assertFalse(PetDraft.FIELD_NAME in c.state.value.fieldErrors)
    }

    @Test
    fun pet_server422_showsFieldError() = runBlocking {
        val c = petController()
        c.edit { it.copy(name = "Барсик", typeId = 1) }
        c.save()
        repo.petSave.completeExceptionally(
            ApiException(422, "validation_error", "Ошибка валидации", mapOf("pet_allergies" to "Слишком длинно")),
        )
        yield()
        assertTrue(c.state.value.save is SaveState.Failed)
        assertEquals("Слишком длинно", c.state.value.fieldErrors[PetDraft.FIELD_ALLERGIES])
        assertEquals(0, saved)
    }

    @Test
    fun pet_noResponse_isUnconfirmed_andTriggersReload() = runBlocking {
        val c = petController()
        c.edit { it.copy(name = "Барсик", typeId = 1) }
        c.save()
        repo.petSave.completeExceptionally(IOException("обрыв"))
        yield()
        val s = c.state.value.save as SaveState.Unconfirmed
        assertTrue(s.message.contains("неизвестно, сохранились ли"))
        assertEquals(0, saved)
        assertEquals(1, unconfirmed)
    }

    @Test
    fun pet_serverError_isFailed_notSaved() = runBlocking {
        val c = petController(Pet(10, 1, "Tom", 1, "кот"))
        c.save()
        repo.petSave.completeExceptionally(ApiException(500, "internal_error", "Ошибка на сервере"))
        yield()
        assertEquals(SaveState.Failed("Ошибка на сервере"), c.state.value.save)
        assertEquals(0, saved)
    }

    // ---------- задача ----------

    @Test
    fun task_dateAndTime_areInSpaceZone_andSavedAfterAnswer() = runBlocking {
        val c = taskController()
        assertEquals("Europe/Moscow, UTC+03:00", c.state.value.timezoneLabel)
        // По умолчанию — через час по Москве: 09:00 UTC = 12:00 МСК → 13:00.
        assertEquals("22.10.2026", c.state.value.dateText)
        assertEquals("13:00", c.state.value.timeText)

        c.edit { it.copy(title = "прогулка", assigneeId = 4, repeat = RepeatType.DAILY, timeText = "20:00") }
        c.save()
        assertEquals(SaveState.Saving, c.state.value.save)
        assertEquals(LocalTime(20, 0), repo.lastTask!!.time)
        assertEquals(LocalDate(2026, 10, 22), repo.lastTask!!.date)
        assertEquals(1L, repo.lastTaskSpace!!.id)

        repo.taskSave.complete(42)
        yield()
        assertEquals(SaveState.Saved(), c.state.value.save)
        assertEquals(1, saved)
    }

    @Test
    fun task_badDateTime_andMissingFields_areNotSent() = runBlocking {
        val c = taskController()
        c.edit { it.copy(title = " ", dateText = "31.02.2026", timeText = "25:61") }
        c.save()
        assertEquals(0, repo.taskCalls)
        val e = c.state.value.fieldErrors
        assertTrue(TaskDraft.FIELD_TITLE in e)
        assertTrue(TaskDraft.FIELD_ASSIGNEE in e)
        assertTrue(e[TaskDraft.FIELD_DATE]!!.contains("ДД.ММ.ГГГГ"))
        assertTrue(e[TaskDraft.FIELD_TIME]!!.contains("ЧЧ:ММ"))
    }

    @Test
    fun task_assigneeFromOtherSpace_isRejectedOnClient() = runBlocking {
        val c = taskController()
        c.edit { it.copy(title = "x", assigneeId = 5) } // 5 — не участник этого пространства
        c.save()
        assertEquals(0, repo.taskCalls)
        assertTrue(c.state.value.fieldErrors[TaskDraft.FIELD_ASSIGNEE]!!.contains("не из этого пространства"))
    }

    @Test
    fun task_invalidTimezone_blocksSaving() = runBlocking {
        val c = taskController(broken)
        assertTrue(c.state.value.timezoneError!!.contains("Mars/Olympus"))
        assertFalse(c.state.value.canSave)
        c.edit { it.copy(title = "x", assigneeId = 3, dateText = "22.10.2026", timeText = "10:00") }
        c.save()
        assertEquals(0, repo.taskCalls)
    }

    @Test
    fun task_server422_mapsServerFieldNames() = runBlocking {
        val c = taskController()
        c.edit { it.copy(title = "x", assigneeId = 3) }
        c.save()
        repo.taskSave.completeExceptionally(
            ApiException(422, "validation_error", "Ошибка", mapOf("responsible_id" to "Не участник", "date_time" to "Неверная дата")),
        )
        yield()
        assertEquals("Не участник", c.state.value.fieldErrors[TaskDraft.FIELD_ASSIGNEE])
        assertEquals("Неверная дата", c.state.value.fieldErrors[TaskDraft.FIELD_DATE])
    }

    @Test
    fun task_noResponse_isUnconfirmed() = runBlocking {
        val c = taskController()
        c.edit { it.copy(title = "x", assigneeId = 3) }
        c.save()
        repo.taskSave.completeExceptionally(IOException("тайм-аут"))
        yield()
        assertTrue(c.state.value.save is SaveState.Unconfirmed)
        assertEquals(1, unconfirmed)
        assertEquals(0, saved)
    }

    @Test
    fun task_editAfterSaved_resetsSavedLabel() = runBlocking {
        val c = taskController()
        c.edit { it.copy(title = "x", assigneeId = 3) }
        c.save()
        repo.taskSave.complete(1)
        yield()
        c.edit { it.copy(title = "x2") }
        assertEquals(SaveState.Idle, c.state.value.save)
        assertNull(c.state.value.fieldErrors[TaskDraft.FIELD_TITLE])
    }
}
