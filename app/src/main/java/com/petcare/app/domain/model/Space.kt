package com.petcare.app.domain.model

import java.time.ZoneId

/**
 * Совместное пространство семьи. Нужен здесь ради [timezone]:
 * все сроки показываются в часовом поясе пространства, а не устройства (ТЗ BR-05).
 */
data class Space(
    val id: Long,
    val name: String,
    val timezone: ZoneId,
)
