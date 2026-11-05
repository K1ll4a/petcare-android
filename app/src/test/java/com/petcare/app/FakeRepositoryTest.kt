package com.petcare.app

import com.petcare.app.data.fake.DemoFixture
import kotlinx.datetime.*
import com.petcare.app.time.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import com.petcare.app.data.fake.FakePetCareRepository
import com.petcare.app.data.fake.FakeSaveMode
import com.petcare.app.data.fake.FakeScenario
import com.petcare.app.data.fake.RawJsonSource
import com.petcare.app.data.fake.TestDataParser
import com.petcare.app.domain.model.PetDraft
import com.petcare.app.domain.model.RepeatType
import com.petcare.app.domain.model.TaskDraft
import com.petcare.app.domain.model.TaskStatus
import com.petcare.app.domain.repository.ApiException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlinx.io.IOException

class FakeRepositoryTest {

    /** Тесты запускаются из папки модуля app/. */
    private val json = RawJsonSource { DemoFixture.json }

    private fun repo(today: String = "2026-10-22", scenario: FakeScenario = FakeScenario.NORMAL) =
        FakePetCareRepository(
            source = json,
            scenario = scenario,
            clock = fixedClock(Instant.parse("${today}T09:00:00Z"), kotlinx.datetime.TimeZone.UTC),
            baseDelayMs = 0,
        )

    // ---------- 8 тестов недели 1, адаптированы под формат сервера и пространства ----------

    @Test
    fun testDataFile_parsesInServerFormat() {
        val data = TestDataParser.parse(json.read())
        assertEquals(6, data.pets.size)
        assertEquals(3, data.spaces.size)
        assertEquals("Europe/Moscow", data.spaces.first().timezone)
    }

    @Test
    fun testData_coversAllStatuses_andBothRepeatTypes() = runBlocking {
        val r = repo()
        val all = listOf(10L, 11L, 12L).flatMap { r.getTaskItems(1, it) }
        // UNKNOWN не приходит от сервера — все строки со статусом.
        assertEquals(TaskStatus.entries.toSet() - TaskStatus.UNKNOWN, all.map { it.status }.toSet())
        assertTrue(all.any { !it.task.isActive })
        assertEquals(setOf(true, false), all.map { it.occurrenceDate != null }.toSet())
    }

    @Test
    fun tasks_areFilteredByPet() = runBlocking {
        val r = repo()
        assertTrue(r.getTaskItems(1, 10).all { it.task.petId == 10L })
        assertEquals(6, r.getTaskItems(1, 10).size)
        assertTrue(r.getTaskItems(1, 12).isEmpty())
    }

    @Test
    fun missingResponsibleName_staysNull() = runBlocking {
        // Сервер всегда присылает responsible_name; если нет — экран покажет «участник #id».
        assertNull(repo().getTaskItems(1, 11).first { it.task.id == 8L }.task.assigneeName)
    }

    @Test
    fun pets_ofSpace_getTypeFromDictionary() = runBlocking {
        val r = repo()
        val pets = r.getPets(1)
        assertEquals(listOf("Tom", "Рекс", "Кеша"), pets.map { it.name })
        assertEquals("собака", pets[1].typeName)
        assertEquals("курица", r.getPet(1, 10).allergies)
        assertEquals("молоко", r.getPet(1, 10).intolerances)
    }

    @Test
    fun dates_areShiftedToToday() = runBlocking {
        val items = repo(today = "2026-11-05").getTaskItems(1, 10)
        val feed = items.first { it.task.id == 1L && it.status == TaskStatus.DONE }
        assertEquals(LocalDate(2026, 11, 5), feed.occurrenceDate)
        val local = feed.dueAt.toLocalDateTime(TimeZone.of("Europe/Moscow"))
        assertEquals(LocalDate(2026, 11, 5), local.date)
        assertEquals("08:00", local.time.toString())
        assertEquals("1:2026-11-05", feed.key)
    }

    @Test
    fun emptyScenario_returnsNoTasks() = runBlocking {
        assertTrue(repo(scenario = FakeScenario.EMPTY).getTaskItems(1, 10).isEmpty())
    }

    @Test
    fun errorScenario_throwsIOException() = runBlocking {
        try {
            repo(scenario = FakeScenario.ERROR).getTaskItems(1, 10)
            fail("ожидалась ошибка")
        } catch (e: IOException) {
            // ок
        }
    }

    // ---------- новые: два пространства, сохранение ----------

    @Test
    fun twoSpaces_doNotMix() = runBlocking {
        val r = repo()
        assertEquals(listOf("Мурка", "Шарик"), r.getPets(2).map { it.name })
        assertTrue(r.getTaskItems(2, 20).all { it.spaceId == 2L })
        // Питомец пространства 1 через пространство 2 недоступен.
        try {
            r.getTaskItems(2, 10); fail()
        } catch (e: ApiException) {
            assertEquals(404, e.httpCode)
        }
    }

    @Test
    fun unknownSpace_isForbidden() = runBlocking {
        try {
            repo().getPets(99); fail()
        } catch (e: ApiException) {
            assertTrue(e.isAccessLost)
        }
    }

    @Test
    fun createAndUpdatePet_areVisibleAfterReload() = runBlocking {
        val r = repo()
        val created = r.createPet(2, PetDraft("Бобик", 2, allergies = "пыль", intolerances = "лактоза"))
        assertEquals(2L, created.spaceId)
        r.updatePet(2, created.id, PetDraft("Бобик", 2, allergies = "", intolerances = "лактоза"))
        val reloaded = r.getPet(2, created.id)
        assertNull(reloaded.allergies)
        assertEquals("лактоза", reloaded.intolerances)
        assertTrue(r.getPets(1).none { it.id == created.id })
    }

    @Test
    fun petWithoutName_isRejected422() = runBlocking {
        try {
            repo().createPet(1, PetDraft("  ", 1)); fail()
        } catch (e: ApiException) {
            assertTrue(e.isValidation)
            assertTrue(PetDraft.FIELD_NAME in e.fields)
        }
    }

    @Test
    fun createTask_thenUpdate_inSpaceZone() = runBlocking {
        val r = repo()
        val dacha = r.getSpaces().first { it.id == 2L }
        val id = r.createTask(dacha, TaskDraft(21, "погулять", 3, RepeatType.ONCE, LocalDate(2026, 10, 23), LocalTime(10, 0)))
        val created = r.getTaskItems(2, 21).single { it.task.id == id }
        // 10:00 Екатеринбург (UTC+5) = 05:00 UTC
        assertEquals(Instant.parse("2026-10-23T05:00:00Z"), created.dueAt)
        assertEquals("Гриша", created.task.assigneeName)

        r.updateTask(dacha, id, TaskDraft(21, "погулять подольше", 5, RepeatType.ONCE, LocalDate(2026, 10, 23), LocalTime(11, 0)))
        val updated = r.getTaskItems(2, 21).filter { it.task.id == id }
        assertEquals(1, updated.size)
        assertEquals("погулять подольше", updated.single().task.title)
        assertEquals("Олег", updated.single().task.assigneeName)
    }

    @Test
    fun taskWithAssigneeFromOtherSpace_isRejected() = runBlocking {
        val r = repo()
        val family = r.getSpaces().first { it.id == 1L }
        try {
            // Олег (5) — участник только «Дачи».
            r.createTask(family, TaskDraft(10, "x", 5, RepeatType.ONCE, LocalDate(2026, 10, 23), LocalTime(12, 0)))
            fail()
        } catch (e: ApiException) {
            assertTrue(TaskDraft.FIELD_ASSIGNEE in e.fields)
        }
    }

    @Test
    fun noResponse_savesButThrows_soResultIsUnconfirmed() = runBlocking {
        val r = repo().apply { saveMode = FakeSaveMode.NO_RESPONSE }
        try {
            r.createPet(1, PetDraft("Тихоня", 1)); fail()
        } catch (e: IOException) {
            // Ответа нет, но на «сервере» питомец есть — поэтому приложение перечитывает данные.
            assertTrue(r.getPets(1).any { it.name == "Тихоня" })
        }
    }
}
