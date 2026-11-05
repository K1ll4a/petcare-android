package com.petcare.app.presentation.account

import com.petcare.app.domain.model.*
import com.petcare.app.domain.repository.AccountRepository
import com.petcare.app.domain.repository.ApiException
import com.petcare.app.presentation.common.attempt
import com.petcare.app.presentation.common.toUserMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Навигация защищённых экранов выводится из session; выход нельзя отменить кнопкой «Назад». */
data class AccountUiState(
    val session: AccountSession? = null,
    val spaces: List<Space> = emptyList(),
    val invitations: List<Invitation> = emptyList(),
    val selectedSpaceId: Long? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

class AccountController(private val scope: CoroutineScope, private val accounts: AccountRepository) {
    private val mutableState = MutableStateFlow(AccountUiState(session = accounts.session.value))
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var generation = 0L
    private var retryAction: (() -> Unit)? = null

    init { if (accounts.session.value != null) loadSpaces() }

    fun login(email: String, password: String) = authenticate { accounts.login(email, password) }
    fun register(name: String, email: String, password: String) = authenticate { accounts.register(name, email, password) }
    private fun authenticate(block: suspend () -> Unit) = runOperation(retry = false) {
        block()
        AccountUiState(session = accounts.session.value, spaces = accounts.getSpaces())
    }

    fun loadSpaces() = runOperation {
        state.value.copy(spaces = accounts.getSpaces(), selectedSpaceId = null, invitations = emptyList())
    }
    fun createSpace(name: String, zone: String) = runOperation {
        val space = accounts.createSpace(name, zone)
        state.value.copy(spaces = accounts.getSpaces(), notice = "Пространство «${space.name}» создано.")
    }
    fun joinSpace(code: String) = runOperation {
        val space = accounts.joinSpace(code)
        state.value.copy(spaces = accounts.getSpaces(), notice = "Вы присоединились к «${space.name}».")
    }
    fun selectSpace(id: Long) {
        if (state.value.busy || state.value.session == null || state.value.spaces.none { it.id == id }) return
        cancelOperation()
        mutableState.value = state.value.copy(selectedSpaceId = id, invitations = emptyList(), error = null, notice = null)
    }
    fun showSpaces() {
        cancelOperation()
        mutableState.value = state.value.copy(selectedSpaceId = null, invitations = emptyList(), busy = false, error = null, notice = null)
        loadSpaces()
    }
    fun loadInvitations(spaceId: Long) {
        if (state.value.busy) return
        mutableState.value = state.value.copy(invitations = emptyList())
        runOperation { state.value.copy(invitations = accounts.getInvitations(spaceId)) }
    }
    fun createInvitation(spaceId: Long) = runOperation {
        accounts.createInvitation(spaceId)
        state.value.copy(invitations = accounts.getInvitations(spaceId), notice = "Создан одноразовый код на 24 часа.")
    }
    fun revokeInvitation(spaceId: Long, code: String) = runOperation {
        accounts.revokeInvitation(spaceId, code)
        state.value.copy(invitations = accounts.getInvitations(spaceId), notice = "Приглашение отозвано.")
    }
    fun retry() { retryAction?.invoke() }
    fun clearMessage() { mutableState.value = state.value.copy(error = null, notice = null) }
    fun logout() {
        cancelOperation()
        accounts.logout()
        mutableState.value = AccountUiState()
    }

    private fun cancelOperation() { ++generation; job?.cancel(); retryAction = null }
    private fun runOperation(retry: Boolean = true, block: suspend () -> AccountUiState) {
        if (state.value.busy) return
        val gen = ++generation
        mutableState.value = state.value.copy(busy = true, error = null, notice = null)
        retryAction = if (retry) ({ runOperation(retry, block) }) else null
        job = scope.launch {
            val result = attempt { withContext(Dispatchers.Default) { block() } }
            if (gen != generation) return@launch
            result.fold(
                { mutableState.value = it.copy(session = accounts.session.value, busy = false, error = null) },
                { e ->
                    val session = accounts.session.value
                    mutableState.value = if (session == null && state.value.session != null) AccountUiState()
                    else state.value.copy(session = session, busy = false, error = if (e is ApiException) e.message else e.toUserMessage("Не удалось выполнить действие. Повторите."))
                },
            )
        }
    }
}
