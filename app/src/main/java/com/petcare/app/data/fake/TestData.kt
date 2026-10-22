package com.petcare.app.data.fake

import com.petcare.app.data.remote.dto.PetDto
import com.petcare.app.data.remote.dto.PetTypeDto
import com.petcare.app.data.remote.dto.SpaceDto
import com.petcare.app.data.remote.dto.TaskDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Структура файла assets/test_data.json. Внутри — DTO в формате Backend. */
@Serializable
data class TestDataDto(
    /** День, к которому привязаны даты в файле. Фейк сдвигает их на «сегодня». */
    @SerialName("base_date") val baseDate: String,
    @SerialName("space") val space: SpaceDto,
    @SerialName("members") val members: List<MemberDto> = emptyList(),
    @SerialName("pet_types") val petTypes: List<PetTypeDto> = emptyList(),
    @SerialName("pets") val pets: List<PetDto>,
    @SerialName("tasks") val tasks: List<TaskDto>,
)

@Serializable
data class MemberDto(
    @SerialName("user_id") val userId: Long,
    @SerialName("name") val name: String,
)

/** Откуда берётся сырой JSON: в приложении — из assets, в тестах — из строки. */
fun interface RawJsonSource {
    fun read(): String
}

/**
 * Сдвигает все даты тестовых данных так, чтобы base_date стал «сегодня»
 * в часовом поясе пространства. Иначе через пару дней все задачи окажутся в прошлом.
 */
internal fun TestDataDto.shiftedTo(today: LocalDate): TestDataDto {
    val zone = runCatching { ZoneId.of(space.timezone) }.getOrDefault(ZoneId.of("UTC"))
    val days = today.toEpochDay() - LocalDate.parse(baseDate).toEpochDay()
    if (days == 0L) return this

    fun Long.shift(): Long =
        Instant.ofEpochSecond(this).atZone(zone).plusDays(days).toEpochSecond()

    fun String.shiftDate(): String =
        runCatching { LocalDate.parse(this).plusDays(days).toString() }.getOrDefault(this)

    return copy(
        baseDate = today.toString(),
        tasks = tasks.map { t ->
            t.copy(
                dateTime = t.dateTime.shift(),
                completedAt = t.completedAt?.shift(),
                occurrenceDate = t.occurrenceDate?.shiftDate(),
            )
        },
    )
}
