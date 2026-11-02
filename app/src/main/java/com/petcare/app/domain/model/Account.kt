package com.petcare.app.domain.model

import java.time.Instant

/** Сессия живёт только в памяти; пароль и токен не входят в состояние экранов. */
data class AccountSession(val userId: Long, val name: String, val email: String, val generation: Long)
data class Invitation(val code: String, val spaceId: Long, val expiresAt: Instant, val revoked: Boolean = false, val used: Boolean = false) {
    fun isActive(now: Instant) = !revoked && !used && now.isBefore(expiresAt)
}
