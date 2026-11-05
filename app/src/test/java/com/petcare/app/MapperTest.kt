package com.petcare.app

import kotlinx.datetime.*
import com.petcare.app.time.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import com.petcare.app.data.mapper.toDomain
import com.petcare.app.data.mapper.toDraft
import com.petcare.app.data.mapper.toRequestBody
import com.petcare.app.data.remote.dto.JsonCodec
import com.petcare.app.data.remote.dto.PetDto
import com.petcare.app.data.remote.dto.SpaceDto
import com.petcare.app.data.remote.dto.TaskDto
import com.petcare.app.domain.model.PetDraft
import com.petcare.app.domain.model.RepeatType
import com.petcare.app.domain.model.SpaceRole
import com.petcare.app.domain.model.TaskDraft
import com.petcare.app.domain.model.TaskStatus
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapperTest {

    /** Ровно пример из контракта Backend недели 1 (контракты_api.txt). */
    private val backendTask = TaskDto(
        taskId = 1, taskName = "покормить", petId = 10,
        dateTime = 1792536557, responsibleId = 4, isDaily = true,
    )

    // ---------- 11 тестов недели 1 (DTO строятся по именам Kotlin — не менялись) ----------

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
        assertEquals(LocalDate(2026, 10, 22), daily.occurrenceDate)
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
        assertEquals(Instant.fromEpochSeconds(1792645800), item.completedAt)
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

    /** ИЗМЕНЁН по задаче #14: раньше неверный пояс молча становился UTC. */
    @Test
    fun badTimezone_isReported_notReplacedWithUtc() {
        val bad = SpaceDto(1, "Дом", "Mars/Olympus").toDomain()
        assertNull(bad.zone)
        assertFalse(bad.isTimezoneValid)
        assertTrue(bad.timezoneLabel().contains("неверный"))
        assertEquals("Europe/Moscow", SpaceDto(1, "Дом", "Europe/Moscow").toDomain().zone?.id)
        assertNull(SpaceDto(1, "Дом", "").toDomain().zone)
    }

    // ---------- новые: реальный формат сервера (petcare-backend) ----------

    /** Элемент GET /task ровно как его собирает build_task_items в app/queries.py. */
    private val serverItem = """
        {"item_id": "7:2026-10-22", "id": 7, "occurrence_id": 55, "occurrence_date": "2026-10-22",
         "space_id": 5, "pet_id": 9, "responsible_id": 4, "responsible_name": "Анна",
         "task_name": "прогулка", "date_time": 1792688400, "is_daily": true, "is_active": true,
         "status": "upcoming", "completed_by_id": null, "completed_by_name": null, "completed_at": null}
    """

    @Test
    fun realServerTaskItem_isParsed() {
        val item = JsonCodec.task(JsonCodec.obj(serverItem)).toDomain()
        assertEquals(7L, item.task.id)
        assertEquals(9L, item.task.petId)
        assertEquals(5L, item.spaceId)
        assertEquals("Анна", item.task.assigneeName)
        assertEquals(TaskStatus.UPCOMING, item.status)
        assertEquals("7:2026-10-22", item.key)
        assertNull(item.completedAt)
    }

    @Test
    fun realServerPet_hasSeparateAllergiesAndIntolerances() {
        val pet = JsonCodec.pet(
            JsonCodec.obj(
                """{"pet_id": 9, "space_id": 5, "pet_name": "Барсик", "pet_type_id": 1,
                   "pet_features": null, "pet_allergies": "курица", "pet_intolerances": "молоко", "pet_care_notes": null}""",
            ),
        ).toDomain(mapOf(1 to "кот"))
        assertEquals("курица", pet.allergies)
        assertEquals("молоко", pet.intolerances)
        assertEquals(5L, pet.spaceId)
        assertEquals("кот", pet.typeName)
    }

    @Test
    fun realServerSpace_roleAndTimezone() {
        val s = JsonCodec.space(
            JsonCodec.obj("""{"space_id": 5, "space_name": "Семья", "space_timezone": "Europe/Moscow", "role": "owner"}"""),
        ).toDomain()
        assertEquals(SpaceRole.OWNER, s.role)
        assertEquals("Europe/Moscow, UTC+03:00", s.timezoneLabel(Instant.parse("2026-10-22T10:00:00Z")))
    }

    @Test
    fun petBody_sendsSeparateFields_andNullForEmpty() {
        val body = JsonCodec.parse(
            PetDraft(name = " Барсик ", typeId = 1, allergies = "курица", intolerances = "", features = "  ").toRequestBody(),
        ).jsonObject
        assertEquals("Барсик", body["pet_name"]!!.jsonPrimitive.content)
        assertEquals("курица", body["pet_allergies"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, body["pet_intolerances"])
        assertEquals(JsonNull, body["pet_features"])
    }

    @Test
    fun taskBody_timeIsInSpaceZone_notDeviceZone() {
        val draft = TaskDraft(9, "прогулка", 4, RepeatType.DAILY, LocalDate(2026, 10, 22), LocalTime(20, 0))
        val moscow = JsonCodec.parse(draft.toRequestBody(TimeZone.of("Europe/Moscow"))).jsonObject
        val yekat = JsonCodec.parse(draft.toRequestBody(TimeZone.of("Asia/Yekaterinburg"))).jsonObject
        // 20:00 МСК = 17:00 UTC; 20:00 Екатеринбург = 15:00 UTC.
        assertEquals(Instant.parse("2026-10-22T17:00:00Z").epochSeconds, moscow["date_time"]!!.jsonPrimitive.content.toLong())
        assertEquals(Instant.parse("2026-10-22T15:00:00Z").epochSeconds, yekat["date_time"]!!.jsonPrimitive.content.toLong())
        assertEquals("2026-10-22", moscow["start_date"]!!.jsonPrimitive.content)
        assertEquals("20:00", moscow["local_time"]!!.jsonPrimitive.content)
    }

    @Test
    fun taskItem_toDraft_usesSpaceZone() {
        val item = JsonCodec.task(JsonCodec.obj(serverItem)).toDomain() // 1792688400 = 22.10 17:00 UTC
        val d = item.toDraft(TimeZone.of("Europe/Moscow"))
        assertEquals(LocalTime(20, 0), d.time)
        assertEquals(LocalDate(2026, 10, 22), d.date)
        assertEquals(4L, d.assigneeId)
    }
}
