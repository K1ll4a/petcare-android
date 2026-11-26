package com.petcare.app.presentation.account

import com.petcare.app.domain.model.*
import com.petcare.app.domain.repository.AccountRepository
import com.petcare.app.domain.repository.ApiException
import com.petcare.app.domain.repository.TaskRepository
import com.petcare.app.navigation.TaskLink
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
    /** Участники выбранного пространства для экрана «Настройки». */
    val members: List<Member> = emptyList(),
    /** Задача из ссылки Telegram, доступ к которой подтвердил сервер; открывается один раз. */
    val linkedTask: TaskItem? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

class AccountController(
    private val scope: CoroutineScope,
    private val accounts: AccountRepository,
    /** Нужен только для ссылки из Telegram: найти задачу среди доступных пространств. */
    private val tasks: TaskRepository? = null,
) {
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
        mutableState.value = state.value.copy(selectedSpaceId = id, invitations = emptyList(), members = emptyList(), linkedTask = null, error = null, notice = null)
    }
    fun showSpaces() {
        cancelOperation()
        mutableState.value = state.value.copy(selectedSpaceId = null, invitations = emptyList(), members = emptyList(), linkedTask = null, busy = false, error = null, notice = null)
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
    // ---------------- Состав группы (FR-21–FR-23) ----------------

    fun loadMembers(spaceId: Long) {
        if (state.value.busy) return
        runOperation { refreshedAfterMembership(spaceId, notice = null) }
    }

    fun removeMember(spaceId: Long, member: Member) = runOperation {
        accounts.removeMember(spaceId, member.id)
        refreshedAfterMembership(spaceId, "${member.name} удалён(а) из пространства. Незавершённые задачи переданы владельцу.")
    }

    fun transferOwnership(spaceId: Long, member: Member) = runOperation {
        accounts.transferOwnership(spaceId, member.id)
        refreshedAfterMembership(spaceId, "Владелец теперь ${member.name}. Вы стали участником и можете выйти из пространства.")
    }

    /** После выхода данные пространства убираются с экрана; остальные пространства не трогаются. */
    fun leaveSpace(spaceId: Long) = runOperation {
        val name = state.value.spaces.firstOrNull { it.id == spaceId }?.name ?: "пространства"
        accounts.leaveSpace(spaceId)
        state.value.copy(
            spaces = accounts.getSpaces(), selectedSpaceId = null, members = emptyList(), invitations = emptyList(),
            linkedTask = null, notice = "Вы вышли из «$name». Его данные убраны с устройства.",
        )
    }

    /**
     * Сервер подтвердил отзыв доступа (403 forbidden) к выбранному пространству (FR-19, FR-22, AC-15):
     * закрываем его экраны — их ViewModel и кеш очищаются вместе с ними — и перечитываем список.
     */
    fun onAccessRevoked(spaceId: Long) {
        if (state.value.selectedSpaceId != spaceId) return
        val name = state.value.spaces.firstOrNull { it.id == spaceId }?.name
        cancelOperation()
        val message = "Доступ к пространству${name?.let { " «$it»" }.orEmpty()} закрыт. Его данные убраны с экрана."
        mutableState.value = state.value.copy(
            spaces = state.value.spaces.filterNot { it.id == spaceId },
            selectedSpaceId = if (state.value.selectedSpaceId == spaceId) null else state.value.selectedSpaceId,
            members = emptyList(), invitations = emptyList(), linkedTask = null, busy = false, error = null,
        )
        runOperation { state.value.copy(spaces = accounts.getSpaces().filterNot { it.id == spaceId }, notice = message) }
    }

    /** Состав и роль перечитываются с сервера: передача владения меняет и роль текущего пользователя. */
    private suspend fun refreshedAfterMembership(spaceId: Long, notice: String?): AccountUiState {
        val spaces = accounts.getSpaces()
        if (spaces.none { it.id == spaceId }) {
            return state.value.copy(spaces = spaces, selectedSpaceId = null, members = emptyList(), notice = "Доступ к пространству закрыт.")
        }
        val members = accounts.getMembers(spaceId)
        val invitations = state.value.invitations.takeIf { spaces.any { s -> s.id == spaceId && s.role == SpaceRole.OWNER } }.orEmpty()
        return state.value.copy(spaces = spaces, members = members, invitations = invitations, notice = notice)
    }

    // ---------------- Ссылка на задачу из Telegram ----------------

    /**
     * Открыть задачу из ссылки. Задача ищется только среди пространств текущего аккаунта;
     * чужая или недоступная — общая ошибка без названия и данных задачи.
     */
    fun openTaskLink(link: TaskLink) {
        val repo = tasks ?: return
        if (state.value.session == null) return
        cancelOperation()
        // Текущее пространство закрывается: итог (задача или ошибка) показывается на списке пространств.
        mutableState.value = state.value.copy(busy = false, selectedSpaceId = null, members = emptyList(), invitations = emptyList())
        runOperation(retry = false) {
            val item = repo.findTask(link.taskId, link.occurrenceId)
            val spaces = accounts.getSpaces()
            val spaceId = item?.spaceId?.takeIf { id -> spaces.any { it.id == id } }
            if (item == null || spaceId == null) {
                throw ApiException(404, "not_found", "Задача из ссылки недоступна этому аккаунту. Войдите под аккаунтом, которому пришло напоминание.")
            }
            state.value.copy(spaces = spaces, selectedSpaceId = spaceId, linkedTask = item, members = emptyList(), invitations = emptyList())
        }
    }

    fun consumeLinkedTask() {
        if (state.value.linkedTask != null) mutableState.value = state.value.copy(linkedTask = null)
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
                    val selectedSpace = state.value.selectedSpaceId
                    if (e is ApiException && e.isAccessLost && selectedSpace != null) {
                        onAccessRevoked(selectedSpace)
                        return@launch
                    }
                    val session = accounts.session.value
                    mutableState.value = if (session == null && state.value.session != null) AccountUiState()
                    else state.value.copy(session = session, busy = false, error = if (e is ApiException) e.message else e.toUserMessage("Не удалось выполнить действие. Повторите."))
                },
            )
        }
    }
}
