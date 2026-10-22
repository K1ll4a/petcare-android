package com.petcare.app

import com.petcare.app.data.fake.FakePetCareRepository
import com.petcare.app.data.fake.FakeScenario
import com.petcare.app.data.fake.RawJsonSource
import com.petcare.app.data.fake.TestDataParser
import com.petcare.app.domain.model.TaskStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class FakeRepositoryTest {

    /** Тесты запускаются из папки модуля app/. */
    private val json = RawJsonSource { File("src/main/assets/test_data.json").readText() }

    private fun repo(today: String = "2026-10-22", scenario: FakeScenario = FakeScenario.NORMAL) =
        FakePetCareRepository(
            source = json,
            scenario = scenario,
            clock = Clock.fixed(Instant.parse("${today}T09:00:00Z"), ZoneOffset.UTC),
            baseDelayMs = 0,
        )

    @Test
    fun testDataFile_parsesInBackendFormat() {
        val data = TestDataParser.parse(json.read())
        assertEquals(3, data.pets.size)
        assertEquals(9, data.tasks.size)
        assertEquals("Europe/Moscow", data.space.timezone)
    }

    @Test
    fun testData_coversAllStatuses_andBothRepeatTypes() = runBlocking {
        val r = repo()
        val all = listOf(10L, 11L, 12L).flatMap { r.getTaskItems(it) }
        assertEquals(TaskStatus.entries.toSet(), all.map { it.status }.toSet())
        assertTrue(all.any { it.task.isActive.not() })
        assertEquals(setOf(true, false), all.map { it.occurrenceDate != null }.toSet())
    }

    @Test
    fun tasks_areFilteredByPet() = runBlocking {
        val r = repo()
        assertTrue(r.getTaskItems(10).all { it.task.petId == 10L })
        assertEquals(6, r.getTaskItems(10).size)
        assertTrue(r.getTaskItems(12).isEmpty())
    }

    @Test
    fun missingResponsibleName_isTakenFromMembers() = runBlocking {
        val walk = repo().getTaskItems(11).first { it.task.id == 7L }
        assertEquals("Гриша", walk.task.assigneeName)
    }

    @Test
    fun pets_fromBackendFormat_getTypeFromDictionary() = runBlocking {
        val r = repo()
        val pets = r.getPets(1)
        assertEquals(listOf("Tom", "Рекс", "Кеша"), pets.map { it.name })
        assertEquals("собака", pets[1].typeName)
        assertEquals("курица", r.getPet(10).allergies)
    }

    @Test
    fun dates_areShiftedToToday() = runBlocking {
        val items = repo(today = "2026-11-05").getTaskItems(10)
        val feed = items.first { it.task.id == 1L && it.status == TaskStatus.DONE }
        assertEquals(LocalDate.of(2026, 11, 5), feed.occurrenceDate)
        assertEquals(
            LocalDate.of(2026, 11, 5),
            feed.dueAt.atZone(ZoneId.of("Europe/Moscow")).toLocalDate(),
        )
        assertEquals("08:00", feed.dueAt.atZone(ZoneId.of("Europe/Moscow")).toLocalTime().toString())
    }

    @Test
    fun emptyScenario_returnsNoTasks() = runBlocking {
        assertTrue(repo(scenario = FakeScenario.EMPTY).getTaskItems(10).isEmpty())
    }

    @Test
    fun errorScenario_throwsIOException() = runBlocking {
        try {
            repo(scenario = FakeScenario.ERROR).getTaskItems(10)
            fail("ожидалась ошибка")
        } catch (e: IOException) {
            // ок
        }
    }
}
