package com.petcare.app.domain.repository

import com.petcare.app.domain.model.AccountSession
import com.petcare.app.domain.model.Invitation
import com.petcare.app.domain.model.Space
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
}

/** Локальное хранилище учебного сервера; сессия в него не записывается. */
interface DemoStateStore {
    fun read(): String?
    fun write(value: String)
}
