package com.petcare.app.data.remote.dto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Разбор и сборка JSON вручную, по ключам из API.md.
 * Неизвестные ключи игнорируются; отсутствие обязательного ключа — понятная ошибка формата.
 */
object JsonCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): JsonElement = try {
        json.parseToJsonElement(raw)
    } catch (e: Exception) {
        throw FormatException("Сервер прислал не JSON: ${e.message}")
    }

    fun list(raw: String): List<JsonObject> = parseArray(parse(raw))

    fun obj(raw: String): JsonObject = parse(raw).asObject("ответ")

    fun parseArray(el: JsonElement): List<JsonObject> =
        (el as? JsonArray ?: throw FormatException("Ожидался массив")).map { it.asObject("элемент массива") }

    // ---------- разбор ----------

    fun task(o: JsonObject) = TaskDto(
        taskId = o.reqLong("id"),
        taskName = o.reqString("task_name"),
        petId = o.reqLong("pet_id"),
        dateTime = o.reqLong("date_time"),
        responsibleId = o.reqLong("responsible_id"),
        isDaily = o.bool("is_daily") ?: throw FormatException("Нет поля is_daily"),
        responsibleName = o.str("responsible_name"),
        status = o.str("status"),
        occurrenceDate = o.str("occurrence_date"),
        isActive = o.bool("is_active"),
        completedById = o.long("completed_by_id"),
        completedByName = o.str("completed_by_name"),
        completedAt = o.long("completed_at"),
        spaceId = o.long("space_id"),
        itemId = o.str("item_id"),
        occurrenceId = o.long("occurrence_id"),
    )

    fun pet(o: JsonObject) = PetDto(
        petId = o.reqLong("pet_id"),
        petName = o.reqString("pet_name"),
        petTypeId = o.int("pet_type_id") ?: throw FormatException("Нет поля pet_type_id"),
        spaceId = o.long("space_id"),
        petType = o.str("pet_type"),
        features = o.str("pet_features"),
        allergies = o.str("pet_allergies"),
        intolerances = o.str("pet_intolerances"),
        careNotes = o.str("pet_care_notes"),
    )

    fun petType(o: JsonObject) = PetTypeDto(
        petTypeId = o.int("id") ?: throw FormatException("Нет поля id у вида"),
        name = o.reqString("name"),
    )

    fun space(o: JsonObject) = SpaceDto(
        spaceId = o.reqLong("space_id"),
        spaceName = o.reqString("space_name"),
        timezone = o.str("space_timezone") ?: "",
        role = o.str("role"),
    )

    fun member(o: JsonObject) = MemberDto(
        userId = o.reqLong("user_id"),
        name = o.reqString("user_name"),
        role = o.str("role"),
    )

    /** null — тело не в формате ошибки API (например, HTML прокси). */
    fun error(raw: String?): ErrorDto? {
        if (raw.isNullOrBlank()) return null
        return try {
            val o = json.parseToJsonElement(raw).jsonObject
            val code = o.str("code") ?: return null
            val fields = (o["fields"] as? JsonArray)?.mapNotNull { f ->
                val fo = f as? JsonObject ?: return@mapNotNull null
                val name = fo.str("field") ?: return@mapNotNull null
                name to (fo.str("message") ?: "Неверное значение")
            }.orEmpty()
            ErrorDto(code, o.str("message") ?: code, fields)
        } catch (e: Exception) {
            null
        }
    }

    // ---------- сборка тел запросов ----------

    /** POST /spaces/{id}/pets и PATCH /pet/{id}: все поля формы; пустые необязательные — null (очищают значение). */
    fun petBody(
        name: String,
        typeId: Int,
        features: String?,
        allergies: String?,
        intolerances: String?,
        careNotes: String?,
    ): String = buildJsonObject {
        put("pet_name", name)
        put("pet_type_id", typeId)
        put("pet_features", features)
        put("pet_allergies", allergies)
        put("pet_intolerances", intolerances)
        put("pet_care_notes", careNotes)
    }.toString()

    /**
     * Тело создания/изменения задачи.
     * ПРЕДПОЛОЖЕНИЕ: серверных методов ещё нет (задача #11). Ключи взяты из ответа GET /task
     * и требований #11: для ежедневной — дата начала и местное время пространства.
     * Если Backend выберет другие имена — правится только эта функция.
     */
    fun taskBody(
        petId: Long,
        name: String,
        responsibleId: Long,
        isDaily: Boolean,
        dateTime: Long,
        startDate: String?,
        localTime: String?,
    ): String = buildJsonObject {
        put("pet_id", petId)
        put("task_name", name)
        put("responsible_id", responsibleId)
        put("is_daily", isDaily)
        put("date_time", dateTime)
        put("start_date", startDate)
        put("local_time", localTime)
    }.toString()

    // ---------- помощники ----------

    private fun JsonElement.asObject(what: String): JsonObject =
        this as? JsonObject ?: throw FormatException("Ожидался объект: $what")

    private fun JsonObject.prim(key: String): JsonPrimitive? =
        this[key]?.takeUnless { it is JsonNull } as? JsonPrimitive

    private fun JsonObject.str(key: String): String? = prim(key)?.contentOrNull
    private fun JsonObject.long(key: String): Long? = prim(key)?.longOrNull
    private fun JsonObject.int(key: String): Int? = prim(key)?.intOrNull
    private fun JsonObject.bool(key: String): Boolean? = prim(key)?.booleanOrNull

    private fun JsonObject.reqString(key: String): String =
        str(key) ?: throw FormatException("Нет поля $key")

    private fun JsonObject.reqLong(key: String): Long =
        long(key) ?: throw FormatException("Нет числового поля $key")

    /** Не IOException: ошибка формата — не «нет связи», повтор её не исправит. */
    class FormatException(message: String) : Exception(message)
}

/** Полезно в тестах и фейке: массив из файла. */
fun JsonElement.objects(): List<JsonObject> = jsonArray.map { it.jsonObject }
