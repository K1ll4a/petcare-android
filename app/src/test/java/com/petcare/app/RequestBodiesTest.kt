package com.petcare.app

import com.petcare.app.data.remote.ForbiddenChangeException
import com.petcare.app.data.remote.PetRequests
import com.petcare.app.data.remote.PetWire
import com.petcare.app.data.remote.TaskRequests
import com.petcare.app.data.remote.TaskWire
import com.petcare.app.data.remote.dto.JsonCodec
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Тела запросов по окончательному контракту: petcare-backend/docs/API.md 1.2 §6–7
 * и docs/task20_contract.md (чек-лист «Что подтвердить Mobile №2»).
 */
class RequestBodiesTest {

    private fun json(raw: String): JsonObject = JsonCodec.parse(raw).jsonObject
    private fun JsonObject.s(key: String) = this[key]!!.jsonPrimitive.content

    private val once = TaskWire(9, "Покормить", 4, daily = false, scheduledAt = "2026-11-05T10:00:00Z", startDate = null, localTime = null)
    private val daily = TaskWire(9, "Выгулять", 4, daily = true, scheduledAt = null, startDate = "2026-11-01", localTime = "19:00")

    // ---------- создание задачи ----------

    @Test
    fun createOnce_matchesContractExample() {
        val b = json(TaskRequests.create(once))
        assertEquals(setOf("task_title", "pet_id", "assignee_id", "recurrence", "scheduled_at"), b.keys)
        assertEquals("Покормить", b.s("task_title"))
        assertEquals("9", b.s("pet_id"))
        assertEquals("4", b.s("assignee_id"))
        assertEquals("once", b.s("recurrence"))
        assertEquals("2026-11-05T10:00:00Z", b.s("scheduled_at"))
    }

    @Test
    fun createDaily_sendsStartDateAndLocalTime_notScheduledAt() {
        val b = json(TaskRequests.create(daily))
        assertEquals(setOf("task_title", "pet_id", "assignee_id", "recurrence", "start_date", "local_time"), b.keys)
        assertEquals("daily", b.s("recurrence"))
        assertEquals("2026-11-01", b.s("start_date"))
        assertEquals("19:00", b.s("local_time"))
    }

    @Test
    fun wire_rejectsMissingScheduleFields() {
        try {
            TaskWire(1, "x", 1, daily = false, scheduledAt = null, startDate = null, localTime = null); fail()
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("scheduled_at"))
        }
        try {
            TaskWire(1, "x", 1, daily = true, scheduledAt = null, startDate = "2026-11-01", localTime = null); fail()
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("local_time"))
        }
    }

    // ---------- изменение задачи: только изменённые поля ----------

    @Test
    fun patch_nothingChanged_isEmptyObject() {
        assertEquals(emptySet<String>(), json(TaskRequests.patch(daily, daily)).keys)
    }

    @Test
    fun patch_titleAndAssignee_only() {
        val b = json(TaskRequests.patch(once, once.copy(title = "Покормить вечером", assigneeId = 3)))
        assertEquals(setOf("task_title", "assignee_id"), b.keys)
        assertEquals("3", b.s("assignee_id"))
    }

    @Test
    fun patch_onceTime_sendsScheduledAtOnly() {
        val b = json(TaskRequests.patch(once, once.copy(scheduledAt = "2026-11-05T12:00:00Z")))
        assertEquals(setOf("scheduled_at"), b.keys)
    }

    @Test
    fun patch_dailyTime_sendsLocalTimeOnly() {
        val b = json(TaskRequests.patch(daily, daily.copy(localTime = "20:00")))
        assertEquals(setOf("local_time"), b.keys)
        assertEquals("20:00", b.s("local_time"))
    }

    @Test
    fun patch_dailyToOnce_sendsRecurrenceAndScheduledAt() {
        val b = json(TaskRequests.patch(daily, once))
        assertEquals(setOf("task_title", "recurrence", "scheduled_at"), b.keys)
        assertEquals("once", b.s("recurrence"))
    }

    @Test
    fun patch_onceToDaily_sendsRecurrenceStartDateAndLocalTime() {
        val b = json(TaskRequests.patch(once, daily.copy(title = once.title)))
        assertEquals(setOf("recurrence", "start_date", "local_time"), b.keys)
        assertEquals("daily", b.s("recurrence"))
    }

    @Test
    fun patch_neverSendsPetOrSpace_andRefusesPetChange() {
        val b = json(TaskRequests.patch(once, once.copy(title = "y")))
        assertTrue("pet_id" !in b && "space_id" !in b)
        try {
            TaskRequests.patch(once, once.copy(petId = 10)); fail()
        } catch (e: ForbiddenChangeException) {
            assertTrue(e.message!!.contains("Питомца задачи изменить нельзя"))
        }
    }

    // ---------- питомец ----------

    private val pet = PetWire("Барсик", 1, features = null, allergies = "курица", intolerances = null, careNotes = null)

    @Test
    fun petCreate_omitsEmptyOptionalFields() {
        val b = json(PetRequests.create(pet))
        assertEquals(setOf("pet_name", "pet_type_id", "pet_allergies"), b.keys)
    }

    @Test
    fun petPatch_onlyChanged_nullClears() {
        val b = json(PetRequests.patch(pet, pet.copy(allergies = null, intolerances = "молоко")))
        assertEquals(setOf("pet_allergies", "pet_intolerances"), b.keys)
        assertEquals(JsonNull, b["pet_allergies"])
        assertEquals("молоко", b.s("pet_intolerances"))
    }

    @Test
    fun petPatch_nameAndType() {
        val b = json(PetRequests.patch(pet, pet.copy(name = "Барсик II", typeId = 2)))
        assertEquals(setOf("pet_name", "pet_type_id"), b.keys)
    }

    // ---------- ответ списка: новое поле completion_id ----------

    @Test
    fun taskItem_completionId_isRead() {
        val item = JsonCodec.task(
            JsonCodec.obj(
                """{"item_id": "8:once", "id": 8, "completion_id": 12, "occurrence_id": null, "occurrence_date": null,
                   "space_id": 1, "pet_id": 1, "responsible_id": 1, "responsible_name": "Анна",
                   "task_name": "Покормить Барсика", "date_time": 1793863200, "is_daily": false, "is_active": true,
                   "status": "done", "completed_by_id": 2, "completed_by_name": "Иван", "completed_at": 1793863500}""",
            ),
        )
        assertEquals(12L, item.completionId)
        assertEquals(8L, item.taskId)
        assertEquals("Иван", item.completedByName)
    }
}
