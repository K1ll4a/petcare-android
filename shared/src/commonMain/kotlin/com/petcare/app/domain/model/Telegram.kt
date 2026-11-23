package com.petcare.app.domain.model

import kotlinx.datetime.Instant

/**
 * Состояние привязки Telegram, как его хранит сервер (FR-16, FR-18).
 * До запуска бота пользователем [isConfirmed] = false — приложение не показывает привязку подтверждённой.
 */
data class TelegramStatus(val isConfirmed: Boolean, val notificationsEnabled: Boolean)

/** Одноразовая ссылка на бота: действует 10 минут, новая заменяет прежнюю. */
data class TelegramLink(val url: String, val botUsername: String?, val expiresAt: Instant?)
