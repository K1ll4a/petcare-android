package com.petcare.app.data.fake

import com.petcare.app.data.mapper.parseZoneOrNull
import com.petcare.app.data.remote.dto.JsonCodec
import com.petcare.app.data.remote.dto.MemberDto
import com.petcare.app.data.remote.dto.PetDto
import com.petcare.app.data.remote.dto.PetTypeDto
import com.petcare.app.data.remote.dto.SpaceDto
import com.petcare.app.data.remote.dto.TaskDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Откуда берётся сырой JSON: в приложении — из assets, в тестах — из строки или файла. */
fun interface RawJsonSource {
    fun read(): String
}

/** Содержимое assets/test_data.json. Внутри — объекты в формате сервера. */
data class TestData(
    val baseDate: LocalDate,
    val petTypes: List<PetTypeDto>,
    val spaces: List<SpaceDto>,
    val members: Map<Long, List<MemberDto>>,
    val pets: List<PetDto>,
    val tasks: List<TaskDto>,
)

object TestDataParser {
    /** Тот же разбор, что и для ответов сервера, — так тестовые данные проверяют и формат. */
    fun parse(raw: String): TestData {
        val root = JsonCodec.obj(raw)
        fun array(key: String) = JsonCodec.parseArray(root[key] ?: error("В тестовых данных нет $key"))
        val members = (root["members"] as? JsonObject).orEmpty().mapKeys { it.key.toLong() }
            .mapValues { (_, v) -> JsonCodec.parseArray(v).map(JsonCodec::member) }
        return TestData(
            baseDate = LocalDate.parse((root["base_date"] as JsonPrimitive).contentOrNull),
            petTypes = array("pet_types").map(JsonCodec::petType),
            spaces = array("spaces").map(JsonCodec::space),
            members = members,
            pets = array("pets").map(JsonCodec::pet),
            tasks = array("tasks").map(JsonCodec::task),
        )
    }
}

/**
 * Сдвигает даты задач так, чтобы base_date стал «сегодня» в поясе пространства задачи.
 * Иначе через пару дней все задачи окажутся в прошлом.
 */
internal fun TestData.shiftedTo(today: LocalDate): TestData {
    val days = today.toEpochDay() - baseDate.toEpochDay()
    if (days == 0L) return this
    val zones = spaces.associate { it.spaceId to (parseZoneOrNull(it.timezone) ?: ZoneId.of("UTC")) }

    return copy(
        baseDate = today,
        tasks = tasks.map { t ->
            val zone = zones[t.spaceId] ?: ZoneId.of("UTC")
            fun Long.shift(): Long = Instant.ofEpochSecond(this).atZone(zone).plusDays(days).toEpochSecond()
            val newDate = t.occurrenceDate?.let { runCatching { LocalDate.parse(it).plusDays(days).toString() }.getOrDefault(it) }
            t.copy(
                dateTime = t.dateTime.shift(),
                completedAt = t.completedAt?.shift(),
                occurrenceDate = newDate,
                itemId = if (t.isDaily && newDate != null) "${t.taskId}:$newDate" else t.itemId,
            )
        },
    )
}
