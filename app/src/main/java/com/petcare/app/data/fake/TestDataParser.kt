package com.petcare.app.data.fake

import kotlinx.serialization.json.Json

object TestDataParser {
    private val json = Json {
        ignoreUnknownKeys = true // новые поля от Backend не ломают разбор
        coerceInputValues = true
    }

    fun parse(raw: String): TestDataDto = json.decodeFromString(TestDataDto.serializer(), raw)
}
