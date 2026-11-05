package com.petcare.app

import kotlinx.datetime.*
import com.petcare.app.time.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import com.petcare.app.data.mapper.toDomain
import com.petcare.app.data.remote.HttpResult
import com.petcare.app.data.remote.HttpTransport
import com.petcare.app.data.remote.PetCareApi
import com.petcare.app.data.remote.dto.JsonCodec
import com.petcare.app.data.remote.dto.SpaceDto
import com.petcare.app.data.repository.ApiPetCareRepository
import com.petcare.app.domain.model.PetDraft
import com.petcare.app.domain.model.RepeatType
import com.petcare.app.domain.model.TaskDraft
import com.petcare.app.domain.repository.ApiException
import com.petcare.app.domain.repository.InvalidServerDataException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlinx.io.IOException

/** Подставной HTTP: записывает запросы и отвечает заготовками по "МЕТОД путь". */
class RecordingTransport : HttpTransport {
    data class Call(val method: String, val path: String, val query: Map<String, String>, val body: String?)

    val calls = mutableListOf<Call>()
    val routes = mutableMapOf<String, HttpResult>()
    var failWith: IOException? = null

    override suspend fun send(method: String, path: String, query: Map<String, String>, body: String?): HttpResult {
        calls += Call(method, path, query, body)
        failWith?.let { throw it }
        return routes["$method $path"] ?: HttpResult(404, """{"code":"not_found","message":"Не найдено","fields":[]}""")
    }
}

private const val TYPES = """[{"id": 1, "name": "кот"}, {"id": 2, "name": "собака"}]"""
private fun petJson(id: Long, space: Long, name: String = "Барсик") =
    """{"pet_id": $id, "space_id": $space, "pet_name": "$name", "pet_type_id": 1,
       "pet_features": null, "pet_allergies": "курица", "pet_intolerances": "молоко", "pet_care_notes": null}"""

private fun taskJson(id: Long, pet: Long, space: Long) =
    """{"item_id": "$id:once", "id": $id, "occurrence_id": null, "occurrence_date": null, "space_id": $space,
       "pet_id": $pet, "responsible_id": 4, "responsible_name": "Анна", "task_name": "задача $id",
       "date_time": 1792688400, "is_daily": false, "is_active": true, "status": "upcoming",
       "completed_by_id": null, "completed_by_name": null, "completed_at": null}"""

class ApiTest {
    private val http = RecordingTransport().apply { routes["GET /pet-types"] = HttpResult(200, TYPES) }
    private val repo = ApiPetCareRepository(PetCareApi(http))
    private val moscow = SpaceDto(5, "Семья", "Europe/Moscow", "owner").toDomain()

    @Test
    fun pets_requestContainsSelectedSpace() = runBlocking {
        http.routes["GET /spaces/5/pets"] = HttpResult(200, "[${petJson(9, 5)}]")
        val pets = repo.getPets(5)
        assertTrue(http.calls.any { it.method == "GET" && it.path == "/spaces/5/pets" })
        assertEquals("кот", pets.single().typeName)
    }

    @Test
    fun tasks_requestContainsSpaceAndPet() = runBlocking {
        http.routes["GET /task"] = HttpResult(200, "[${taskJson(1, 9, 5)}]")
        repo.getTaskItems(5, 9)
        val call = http.calls.single { it.path == "/task" }
        assertEquals(mapOf("space_id" to "5", "pet_id" to "9"), call.query)
    }

    @Test
    fun tasksOfOtherPetOrSpace_areDropped() = runBlocking {
        http.routes["GET /task"] = HttpResult(200, "[${taskJson(1, 9, 5)}, ${taskJson(2, 8, 5)}, ${taskJson(3, 9, 6)}]")
        assertEquals(listOf(1L), repo.getTaskItems(5, 9).map { it.task.id })
    }

    @Test
    fun petOfAnotherSpace_isRejected() = runBlocking {
        http.routes["GET /pet/9"] = HttpResult(200, petJson(9, space = 6))
        try {
            repo.getPet(5, 9)
            fail("ожидался отказ")
        } catch (e: InvalidServerDataException) {
            assertTrue(e.message!!.contains("не из выбранного пространства"))
        }
    }

    @Test
    fun createPet_postsToSpace_withSeparateAllergyFields() = runBlocking {
        http.routes["POST /spaces/5/pets"] = HttpResult(201, petJson(10, 5))
        val pet = repo.createPet(5, PetDraft("Барсик", 1, allergies = "курица", intolerances = "молоко"))
        val body = JsonCodec.parse(http.calls.single { it.method == "POST" }.body!!).jsonObject
        assertEquals("курица", body["pet_allergies"]!!.jsonPrimitive.content)
        assertEquals("молоко", body["pet_intolerances"]!!.jsonPrimitive.content)
        assertEquals(10L, pet.id)
    }

    @Test
    fun updatePet_checksSpaceFirst_thenPatches() = runBlocking {
        http.routes["GET /pet/9"] = HttpResult(200, petJson(9, 5))
        http.routes["PATCH /pet/9"] = HttpResult(200, petJson(9, 5, "Барсик II"))
        val pet = repo.updatePet(5, 9, PetDraft("Барсик II", 1))
        val order = http.calls.map { "${it.method} ${it.path}" }
        // PATCH отправляется только после проверки, что питомец из выбранного пространства.
        assertTrue(order.indexOf("GET /pet/9") in 0 until order.indexOf("PATCH /pet/9"))
        assertEquals("Барсик II", pet.name)
    }

    @Test
    fun updatePet_ofAnotherSpace_isNotSent() = runBlocking {
        http.routes["GET /pet/9"] = HttpResult(200, petJson(9, space = 6))
        try {
            repo.updatePet(5, 9, PetDraft("X", 1))
            fail("ожидался отказ")
        } catch (e: InvalidServerDataException) {
            assertTrue(http.calls.none { it.method == "PATCH" })
        }
    }

    @Test
    fun createTask_postsToSpace_withTimeInSpaceZone() = runBlocking {
        http.routes["POST /spaces/5/tasks"] = HttpResult(201, taskJson(42, 9, 5))
        val id = repo.createTask(moscow, TaskDraft(9, "прогулка", 4, RepeatType.ONCE, LocalDate(2026, 10, 22), LocalTime(20, 0)))
        assertEquals(42L, id)
        val body = JsonCodec.parse(http.calls.single { it.method == "POST" }.body!!).jsonObject
        assertEquals("1792688400", body["date_time"]!!.jsonPrimitive.content) // 20:00 МСК
        assertEquals("false", body["is_daily"]!!.jsonPrimitive.content)
    }

    @Test
    fun createTask_withInvalidTimezone_isNotSent() = runBlocking {
        val broken = SpaceDto(5, "Семья", "Mars/Olympus").toDomain()
        try {
            repo.createTask(broken, TaskDraft(9, "x", 4, RepeatType.ONCE, LocalDate(2026, 10, 22), LocalTime(12, 0)))
            fail("ожидался отказ")
        } catch (e: InvalidServerDataException) {
            assertTrue(http.calls.none { it.method == "POST" })
        }
    }

    @Test
    fun validationError_hasFieldMessages() = runBlocking {
        http.routes["POST /spaces/5/pets"] = HttpResult(
            422,
            """{"code":"validation_error","message":"Ошибка валидации запроса","fields":[{"field":"pet_name","message":"Пустое имя"}]}""",
        )
        try {
            repo.createPet(5, PetDraft("x", 1))
            fail("ожидалась ошибка")
        } catch (e: ApiException) {
            assertTrue(e.isValidation)
            assertEquals("Пустое имя", e.fields["pet_name"])
        }
    }

    @Test
    fun forbidden_and_nonJsonError_areReadable() = runBlocking {
        http.routes["GET /spaces/5/pets"] = HttpResult(403, """{"code":"forbidden","message":"Нет доступа","fields":[]}""")
        http.routes["GET /spaces"] = HttpResult(502, "<html>Bad gateway</html>")
        try {
            repo.getPets(5); fail()
        } catch (e: ApiException) {
            assertTrue(e.isAccessLost)
        }
        try {
            repo.getSpaces(); fail()
        } catch (e: ApiException) {
            assertEquals(502, e.httpCode)
            assertTrue(e.message.contains("502"))
        }
    }

    @Test
    fun networkFailure_isIOException() = runBlocking {
        http.failWith = IOException("timeout")
        try {
            repo.getSpaces(); fail()
        } catch (e: IOException) {
            // ок: экран покажет «нет соединения», а сохранение — «результат не подтверждён»
        }
    }

    @Test
    fun brokenJson_isFormatError_notCrash() = runBlocking {
        http.routes["GET /spaces"] = HttpResult(200, """[{"space_name": "без id"}]""")
        try {
            repo.getSpaces(); fail()
        } catch (e: JsonCodec.FormatException) {
            assertTrue(e.message!!.contains("space_id"))
        }
    }

    @Test
    fun unauthorized_isReported_withoutHiddenLogin() = runBlocking {
        http.routes["GET /spaces"] = HttpResult(401, """{"code":"unauthorized","message":"Сессия истекла","fields":[]}""")
        try { repo.getSpaces(); fail() } catch (e: ApiException) { assertTrue(e.isUnauthorized) }
    }
}
