package com.petcare.app.presentation.settings

import com.petcare.app.domain.model.TelegramLink
import com.petcare.app.domain.model.TelegramStatus
import com.petcare.app.domain.repository.DemoTelegramBot
import com.petcare.app.domain.repository.TelegramRepository
import com.petcare.app.presentation.common.attempt
import com.petcare.app.presentation.common.toUserMessage
import com.petcare.app.time.instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class TelegramUiState(
    /** Состояние, подтверждённое сервером; null — ещё не загружено или не загрузилось. */
    val status: TelegramStatus? = null,
    /** Выданная ссылка, пока привязка не подтверждена ботом. */
    val link: TelegramLink? = null,
    val loading: Boolean = false,
    /** Идёт изменение: кнопки недоступны, результат показывается только после ответа сервера. */
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
) {
    /** Ждём, пока пользователь нажмёт Start в боте. */
    val waitingForBot: Boolean get() = link != null && status?.isConfirmed != true
}

/**
 * Привязка Telegram (FR-16, FR-18, AC-08). Подтверждённой привязка считается только по ответу сервера:
 * выдача ссылки сама по себе её не подтверждает.
 */
class TelegramController(
    private val scope: CoroutineScope,
    private val repo: TelegramRepository,
    /** Только учебный режим: имитация нажатия Start. */
    val demoBot: DemoTelegramBot? = null,
    private val clock: Clock = Clock.System,
) {
    private val _state = MutableStateFlow(TelegramUiState())
    val state: StateFlow<TelegramUiState> = _state.asStateFlow()
    private var job: Job? = null
    private var pollJob: Job? = null

    /** Открытие экрана, возврат приложения на передний план, «Обновить». */
    fun load() {
        if (_state.value.busy) return
        job?.cancel()
        pollJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        job = scope.launch {
            attempt { repo.getStatus() }.fold(
                { status -> _state.update { it.copy(status = status, loading = false, link = it.link.takeUnless { status.isConfirmed }) } },
                { e -> _state.update { it.copy(loading = false, error = e.toUserMessage("Не удалось загрузить состояние Telegram.")) } },
            )
        }
    }

    /** Фоновая проверка подтверждения, пока открыт экран со ссылкой. Ошибки сети не мешают ждать. */
    fun pollConfirmation() {
        val s = _state.value
        if (!s.waitingForBot || s.busy || s.loading || pollJob?.isActive == true) return
        if (isLinkExpired(s.link)) {
            _state.update { it.copy(link = null, notice = "Ссылка истекла. Получите новую.") }
            return
        }
        pollJob = scope.launch {
            attempt { repo.getStatus() }.onSuccess { status ->
                ensureActive()
                _state.update { cur ->
                    if (cur.busy) cur
                    else cur.copy(
                        status = status,
                        link = cur.link.takeUnless { status.isConfirmed },
                        notice = if (status.isConfirmed && cur.status?.isConfirmed != true) "Telegram подключён." else cur.notice,
                    )
                }
            }
        }
    }

    fun createLink() = change("Не удалось получить ссылку.") {
        val link = repo.createLink()
        _state.update { it.copy(link = link, notice = "Откройте ссылку и нажмите Start в боте. Ссылка действует 10 минут.") }
    }

    fun simulateBotStart() {
        val bot = demoBot ?: return
        change("Не удалось подтвердить привязку.") {
            bot.simulateBotStart()
            val status = repo.getStatus()
            _state.update { it.copy(status = status, link = null, notice = "Telegram подключён.") }
        }
    }

    /** Переключатель меняется только после ответа сервера; при ошибке остаётся прежнее значение. */
    fun setNotifications(enabled: Boolean) = change("Не удалось изменить напоминания. Значение не изменено.") {
        val status = repo.setNotifications(enabled)
        _state.update { it.copy(status = status, notice = if (status.notificationsEnabled) "Напоминания включены." else "Напоминания отключены.") }
    }

    fun unlink() = change("Не удалось отвязать Telegram. Привязка осталась.") {
        repo.unlink()
        _state.update { it.copy(status = TelegramStatus(isConfirmed = false, notificationsEnabled = false), link = null, notice = "Telegram отвязан. Напоминания не будут приходить.") }
    }

    private fun change(fallback: String, block: suspend () -> Unit) {
        if (_state.value.busy) return
        job?.cancel()
        pollJob?.cancel()
        _state.update { it.copy(busy = true, loading = false, error = null, notice = null) }
        job = scope.launch {
            val result = attempt { block() }
            // Сверяем состояние до разблокировки формы, чтобы ответ не затёр результат нового действия.
            val status = if (result.isFailure) attempt { repo.getStatus() }.getOrNull() else null
            ensureActive()
            _state.update { it.copy(
                busy = false,
                status = status ?: it.status,
                error = result.exceptionOrNull()?.toUserMessage(fallback),
            ) }
        }
    }

    private fun isLinkExpired(link: TelegramLink?): Boolean {
        val expires = link?.expiresAt ?: return false
        return clock.instant() >= expires
    }
}
