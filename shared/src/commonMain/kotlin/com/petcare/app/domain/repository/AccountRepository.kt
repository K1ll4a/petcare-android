package com.petcare.app.domain.repository

import com.petcare.app.domain.model.AccountSession
import com.petcare.app.domain.model.Invitation
import com.petcare.app.domain.model.Member
import com.petcare.app.domain.model.Space
import com.petcare.app.domain.model.TelegramLink
import com.petcare.app.domain.model.TelegramStatus
import kotlinx.coroutines.flow.StateFlow

/** Граница интеграции авторизации и пространств. Учебная реализация — DemoAccountRepository. */
interface AccountRepository {
    val session: StateFlow<AccountSession?>
    suspend fun login(email: String, password: String)
    suspend fun register(name: String, email: String, password: String)
    /** Локальная очистка обязательна даже при недоступном сервере. */
    fun logout()
    suspend fun getSpaces(): List<Space>
    suspend fun createSpace(name: String, timezone: String): Space
    suspend fun joinSpace(code: String): Space
    suspend fun getInvitations(spaceId: Long): List<Invitation>
    suspend fun createInvitation(spaceId: Long): Invitation
    suspend fun revokeInvitation(spaceId: Long, code: String)

    // ---- Состав группы (FR-21–FR-23). Серверные пути появятся с задачей Backend #26. ----

    /** Участники пространства; доступно любому участнику. */
    suspend fun getMembers(spaceId: Long): List<Member>

    /**
     * Выйти из пространства. Владелец сначала передаёт владение (FR-23):
     * иначе ошибка с объяснением. Выполненные действия остаются в истории.
     */
    suspend fun leaveSpace(spaceId: Long)

    /** Удалить участника — только владелец; себя удалить нельзя. */
    suspend fun removeMember(spaceId: Long, userId: Long)

    /** Передать владение действующему участнику; прежний владелец становится участником. */
    suspend fun transferOwnership(spaceId: Long, userId: Long)
}

/**
 * Привязка Telegram текущего аккаунта (API.md 1.2 §7.1): GET/DELETE /telegram,
 * POST /telegram/link, PATCH /telegram/notifications. Состояние хранит сервер.
 */
interface TelegramRepository {
    suspend fun getStatus(): TelegramStatus
    suspend fun createLink(): TelegramLink
    suspend fun setNotifications(enabled: Boolean): TelegramStatus
    suspend fun unlink()
}

/** Только для учебного режима: имитация того, что пользователь нажал Start в боте. */
interface DemoTelegramBot {
    suspend fun simulateBotStart()
}

/** Локальное хранилище учебного сервера; сессия в него не записывается. */
interface DemoStateStore {
    fun read(): String?
    fun write(value: String)
}
