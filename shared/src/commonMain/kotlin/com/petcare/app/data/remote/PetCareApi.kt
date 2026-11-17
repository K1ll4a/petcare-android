package com.petcare.app.data.remote

import com.petcare.app.data.remote.dto.JsonCodec
import com.petcare.app.data.remote.dto.MemberDto
import com.petcare.app.data.remote.dto.PetDto
import com.petcare.app.data.remote.dto.PetTypeDto
import com.petcare.app.data.remote.dto.SpaceDto
import com.petcare.app.data.remote.dto.TaskDto
import com.petcare.app.domain.repository.ApiException
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Методы сервера по petcare-backend/docs/API.md.
 * Выбранное пространство передаётся в каждом запросе: в пути /spaces/{space_id}/...
 * или фильтром ?space_id= у GET /task (API.md §1).
 */
class PetCareApi(private val http: HttpTransport) {

    suspend fun getSpaces(): List<SpaceDto> =
        JsonCodec.list(ok(http.send("GET", "/spaces"))).map(JsonCodec::space)

    suspend fun getMembers(spaceId: Long): List<MemberDto> =
        JsonCodec.list(ok(http.send("GET", "/spaces/$spaceId/members"))).map(JsonCodec::member)

    suspend fun getPetTypes(): List<PetTypeDto> =
        JsonCodec.list(ok(http.send("GET", "/pet-types"))).map(JsonCodec::petType)

    suspend fun getPets(spaceId: Long): List<PetDto> =
        JsonCodec.list(ok(http.send("GET", "/spaces/$spaceId/pets"))).map(JsonCodec::pet)

    suspend fun getPet(petId: Long): PetDto =
        JsonCodec.pet(JsonCodec.obj(ok(http.send("GET", "/pet/$petId"))))

    suspend fun createPet(spaceId: Long, body: String): PetDto =
        JsonCodec.pet(JsonCodec.obj(ok(http.send("POST", "/spaces/$spaceId/pets", body = body))))

    suspend fun updatePet(petId: Long, body: String): PetDto =
        JsonCodec.pet(JsonCodec.obj(ok(http.send("PATCH", "/pet/$petId", body = body))))

    /** Задачи одного питомца в одном пространстве: GET /task?space_id=..&pet_id=.. */
    suspend fun getTasks(spaceId: Long, petId: Long): List<TaskDto> =
        JsonCodec.list(
            ok(http.send("GET", "/task", query = mapOf("space_id" to "$spaceId", "pet_id" to "$petId"))),
        ).map(JsonCodec::task)

    /**
     * POST /spaces/{space_id}/tasks (API.md 1.2 §7). Ответ — объект задачи с task_id,
     * а не элемент списка, поэтому после успеха список перечитывается с сервера.
     */
    suspend fun createTask(spaceId: Long, body: String): Long =
        taskIdFrom(ok(http.send("POST", "/spaces/$spaceId/tasks", body = body)))

    /** PATCH /tasks/{task_id} — только изменённые поля. */
    suspend fun updateTask(taskId: Long, body: String): Long =
        taskIdFrom(ok(http.send("PATCH", "/tasks/$taskId", body = body)))

    /** Id из ответа: "task_id" (объект задачи) или "id" (элемент списка). */
    private fun taskIdFrom(raw: String): Long {
        val o = JsonCodec.obj(raw)
        return listOf("task_id", "id").firstNotNullOfOrNull { (o[it] as? JsonPrimitive)?.longOrNull }
            ?: throw JsonCodec.FormatException("В ответе на сохранение задачи нет id")
    }

    /** 2xx — тело; иначе ApiException в формате {code, message, fields}. */
    private fun ok(result: HttpResult): String {
        if (result.code in 200..299) return result.body.orEmpty()
        val err = JsonCodec.error(result.body)
        throw ApiException(
            httpCode = result.code,
            code = err?.code ?: defaultCode(result.code),
            message = err?.message ?: defaultMessage(result.code),
            fields = err?.fields?.toMap().orEmpty(),
        )
    }

    private fun defaultCode(http: Int) = when (http) {
        400 -> "bad_request"
        401 -> "unauthorized"
        403 -> "forbidden"
        404 -> "not_found"
        409 -> "conflict"
        422 -> "validation_error"
        else -> "http_$http"
    }

    private fun defaultMessage(http: Int) = when (http) {
        400 -> "Сервер отклонил запрос."
        401 -> "Сессия истекла. Войдите снова."
        403 -> "Нет доступа к этому пространству."
        404 -> "Данные не найдены."
        422 -> "Проверьте заполнение полей."
        in 500..599 -> "Ошибка на сервере ($http). Повторите позже."
        else -> "Сервер ответил ошибкой $http."
    }
}
