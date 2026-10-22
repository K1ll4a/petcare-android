package com.petcare.app

import com.petcare.app.data.mapper.toDomain
import com.petcare.app.data.remote.dto.PetDto
import com.petcare.app.data.remote.dto.SpaceDto
import com.petcare.app.data.remote.dto.TaskDto
import com.petcare.app.domain.model.RepeatType
import com.petcare.app.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class MapperTest {

    /** Ровно пример из контракта Backend (контракты_api.txt). */
    private val backendTask = TaskDto(
        taskId = 1, taskName = "покормить", petId = 10,
        dateTime = 1792536557, responsibleId = 4, isDaily = true,
    )

    @Test
    fun taskInBackendFormat_mapsWithoutNewFields() {
        val item = backendTask.toDomain()
        assertEquals(1L, item.task.id)
        assertEquals("покормить", item.task.title)
        assertEquals(RepeatType.DAILY, item.task.repeat)
        // 1792536557 = 20.10.2026 22:49:17 UTC = 21.10.2026 01:49:17 МСК
        assertEquals(Instant.parse("2026-10-20T22:49:17Z"), item.dueAt)
        assertEquals(4L, item.task.assigneeId)
        assertNull(item.task.assigneeName)
        assertTrue(item.task.isActive)
    }

    @Test
    fun missingStatus_isUnknown_notComputedOnClient() {
        // Срок давно прошёл, но клиент не имеет права сам решить «просрочено» (BR-02).
        assertEquals(TaskStatus.UNKNOWN, backendTask.copy(dateTime = 0).toDomain().status)
    }

    @Test
    fun serverStatus_isParsedCaseInsensitive() {
        assertEquals(TaskStatus.OVERDUE, backendTask.copy(status = "overdue").toDomain().status)
        assertEquals(TaskStatus.DONE, backendTask.copy(status = "DONE").toDomain().status)
        assertEquals(TaskStatus.UNKNOWN, backendTask.copy(status = "что-то новое").toDomain().status)
    }

    @Test
    fun onceTask_hasNoOccurrenceDate_dailyTaskHasIt() {
        val once = backendTask.copy(isDaily = false, occurrenceDate = "2026-10-22").toDomain()
        assertEquals(RepeatType.ONCE, once.task.repeat)
        assertNull(once.occurrenceDate)
        assertEquals("1", once.key)

        val daily = backendTask.copy(occurrenceDate = "2026-10-22").toDomain()
        assertEquals(LocalDate.of(2026, 10, 22), daily.occurrenceDate)
        assertEquals("1_2026-10-22", daily.key)
    }

    @Test
    fun brokenOccurrenceDate_doesNotCrash() {
        assertNull(backendTask.copy(occurrenceDate = "22.10.2026").toDomain().occurrenceDate)
    }

    @Test
    fun completionFields_areMapped() {
        val item = backendTask.copy(
            status = "done", completedById = 3, completedByName = "Гриша", completedAt = 1792645800,
        ).toDomain()
        assertEquals("Гриша", item.completedByName)
        assertEquals(Instant.ofEpochSecond(1792645800), item.completedAt)
    }

    @Test
    fun disabledSchedule_isMapped() {
        assertFalse(backendTask.copy(isActive = false).toDomain().task.isActive)
    }

    @Test
    fun petInBackendFormat_takesTypeFromDictionary() {
        val pet = PetDto(petId = 10, petName = " Tom ", petTypeId = 1).toDomain(mapOf(1 to "кот"))
        assertEquals("Tom", pet.name)
        assertEquals("кот", pet.typeName)
        assertNull(pet.spaceId)
        assertFalse(pet.hasSpecialNotes)
    }

    @Test
    fun petTypeString_winsOverDictionary_andBlankFieldsBecomeNull() {
        val pet = PetDto(
            petId = 10, petName = "Tom", petTypeId = 1, petType = "котик",
            allergies = "курица", features = "   ",
        ).toDomain(mapOf(1 to "кот"))
        assertEquals("котик", pet.typeName)
        assertEquals("курица", pet.allergies)
        assertNull(pet.features)
        assertTrue(pet.hasSpecialNotes)
    }

    @Test
    fun unknownPetType_givesNullTypeName() {
        assertNull(PetDto(petId = 1, petName = "X", petTypeId = 99).toDomain().typeName)
    }

    @Test
    fun badTimezone_fallsBackToUtc() {
        assertEquals(ZoneOffset.UTC, SpaceDto(1, "Дом", "Mars/Olympus").toDomain().timezone)
        assertEquals("Europe/Moscow", SpaceDto(1, "Дом", "Europe/Moscow").toDomain().timezone.id)
    }
}
