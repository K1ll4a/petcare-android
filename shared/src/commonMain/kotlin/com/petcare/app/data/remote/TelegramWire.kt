package com.petcare.app.data.remote

import com.petcare.app.data.remote.dto.JsonCodec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/*
 * Telegram по petcare-backend/docs/API.md 1.2 §7.1. Здесь только JSON и строки:
 * время истечения ссылки разбирается в репозитории.
 */

/** GET /telegram, PATCH /telegram/notifications. */
data class TelegramStatusDto(val isConfirmed: Boolean, val notificationsEnabled: Boolean)

/** POST /telegram/link. [expiresAt] — ISO 8601 в UTC. */
data class TelegramLinkDto(val linkUrl: String, val botUsername: String?, val expiresAt: String?)

object TelegramWire {
    fun status(raw: String): TelegramStatusDto {
        val o = JsonCodec.obj(raw)
        return TelegramStatusDto(
            // Нет поля — привязки нет: не подтверждено (FR-16, AC-08).
            isConfirmed = o.bool("is_confirmed") ?: false,
            notificationsEnabled = o.bool("notifications_enabled") ?: false,
        )
    }

    fun link(raw: String): TelegramLinkDto {
        val o = JsonCodec.obj(raw)
        val url = o.str("link_url") ?: throw JsonCodec.FormatException("Нет поля link_url")
        if (!url.startsWith("https://t.me/")) throw JsonCodec.FormatException("Ссылка не ведёт в Telegram")
        return TelegramLinkDto(url, o.str("bot_username"), o.str("expires_at"))
    }

    fun notificationsBody(enabled: Boolean): String =
        buildJsonObject { put("notifications_enabled", enabled) }.toString()

    private fun JsonObject.prim(key: String) = this[key] as? JsonPrimitive
    private fun JsonObject.str(key: String) = prim(key)?.contentOrNull
    private fun JsonObject.bool(key: String) = prim(key)?.booleanOrNull
}
