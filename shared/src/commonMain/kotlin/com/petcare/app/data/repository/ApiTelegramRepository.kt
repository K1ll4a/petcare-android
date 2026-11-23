package com.petcare.app.data.repository

import com.petcare.app.data.remote.PetCareApi
import com.petcare.app.data.remote.TelegramStatusDto
import com.petcare.app.domain.model.TelegramLink
import com.petcare.app.domain.model.TelegramStatus
import com.petcare.app.domain.repository.TelegramRepository
import kotlinx.datetime.Instant

/** Привязка Telegram на сервере (API.md 1.2 §7.1). Подключается вместе с серверным входом. */
class ApiTelegramRepository(private val api: PetCareApi) : TelegramRepository {
    override suspend fun getStatus() = api.getTelegram().toDomain()

    override suspend fun createLink(): TelegramLink {
        val dto = api.createTelegramLink()
        return TelegramLink(
            url = dto.linkUrl,
            botUsername = dto.botUsername,
            // Неверный формат времени не ломает экран: просто не показываем срок.
            expiresAt = dto.expiresAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
        )
    }

    override suspend fun setNotifications(enabled: Boolean) = api.setTelegramNotifications(enabled).toDomain()

    override suspend fun unlink() = api.deleteTelegram()

    private fun TelegramStatusDto.toDomain() = TelegramStatus(isConfirmed, notificationsEnabled)
}

/** Серверные Telegram-методы для обеих платформ; токен — от серверного входа Mobile №1. */
fun serverTelegramRepository(baseUrl: String, tokens: com.petcare.app.data.remote.TokenProvider): ApiTelegramRepository =
    ApiTelegramRepository(PetCareApi(com.petcare.app.data.remote.KtorHttpTransport(baseUrl, tokens)))
