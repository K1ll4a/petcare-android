package com.petcare.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.petcare.app.domain.model.*
import com.petcare.app.presentation.account.*
import com.petcare.app.presentation.edit.*
import com.petcare.app.presentation.home.*
import com.petcare.app.platform.PetCareBackHandler
import com.petcare.app.navigation.PendingDeepLinks
import com.petcare.app.presentation.settings.SettingsScreen
import com.petcare.app.presentation.settings.TelegramViewModel
import com.petcare.app.time.offsetLabel
import kotlinx.datetime.Clock
import kotlinx.datetime.todayIn

private sealed interface Screen {
    data object Home : Screen
    data class Profile(val pet: Pet) : Screen
    data object Invitations : Screen
    data object Settings : Screen
    /** Задача из ссылки Telegram; доступ уже подтверждён сервером. */
    data class TaskCard(val task: TaskItem) : Screen
    data class EditPet(val space: Space, val pet: Pet?, val token: Long) : Screen
    data class EditTask(val space: Space, val task: TaskItem?, val petId: Long?, val token: Long) : Screen
}

@Composable
fun PetCareApp(container: AppContainer) {
    val vm: AccountViewModel = viewModel(factory = AccountViewModel.Factory(container.accountRepository, container.taskRepository))
    val controller = vm.controller
    val state by controller.state.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }
    // Ссылка из Telegram ждёт входа; после входа открывается с проверкой доступа (задача #28).
    val pendingLink by PendingDeepLinks.link.collectAsStateWithLifecycle()
    LaunchedEffect(state.session?.generation, pendingLink) {
        if (state.session != null && pendingLink != null) PendingDeepLinks.take()?.let(controller::openTaskLink)
    }
    if (state.session == null) AuthScreen(
        state, controller,
        notice = if (pendingLink != null) "Войдите, чтобы открыть задачу из Telegram." else null,
    )
    else {
        val logout = { confirmLogout = true }
        if (state.selectedSpaceId == null) SpacesScreen(state, controller, logout)
        else key(state.session!!.generation, state.selectedSpaceId) {
            ScopedViewModels {
                WorkspaceApp(container, state, controller, logout)
            }
        }
    }
    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false },
        title = { Text("Выйти из аккаунта?") },
        text = { Text("Для доступа к пространствам понадобится снова войти.") },
        confirmButton = { TextButton(onClick = { confirmLogout = false; controller.logout() }) { Text("Выйти") } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Отмена") } },
    )
}

/** Отдельный store для защищённого раздела и каждой формы: dispose отменяет запросы и очищает модели. */
@Composable
internal fun ScopedViewModels(content: @Composable () -> Unit) {
    val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}

@Composable
private fun WorkspaceApp(container: AppContainer, account: AccountUiState, controller: AccountController, logout: () -> Unit) {
    val home: HomeViewModel = viewModel(factory = HomeViewModel.Factory(
        container.spaceRepository, container.petRepository, container.taskRepository, account.selectedSpaceId,
        // Подтверждённый сервером отзыв доступа закрывает пространство вместе с его кешем.
        onAccessLost = controller::onAccessRevoked,
    ))
    val telegram: TelegramViewModel = viewModel(factory = TelegramViewModel.Factory(container.telegramRepository, container.demoTelegramBot))
    val homeState by home.state.collectAsStateWithLifecycle()
    val currentSpace = (homeState as? HomeUiState.Ready)?.space
    val linked = account.linkedTask?.takeIf { it.spaceId == account.selectedSpaceId }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    LaunchedEffect(linked) {
        if (linked != null) {
            screen = Screen.TaskCard(linked)
            controller.consumeLinkedTask()
        }
    }
    var formSequence by remember { mutableStateOf(0L) }
    fun nextFormId(): Long = ++formSequence
    val back = { screen = Screen.Home }
    val homeNavigation = HomeNavigation(
        onAddPet = { home.currentSpace?.let { screen = Screen.EditPet(it, null, nextFormId()) } },
        onEditPet = { screen = Screen.Profile(it) },
        onSelectSpace = controller::selectSpace,
        onAddTask = { home.currentSpace?.let { screen = Screen.EditTask(it, null, (home.state.value as? HomeUiState.Ready)?.selectedPetId, nextFormId()) } },
        onEditTask = { key -> home.findTask(key)?.let { task -> home.currentSpace?.let { screen = Screen.EditTask(it, task, task.task.petId, nextFormId()) } } },
    )
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, home) {
        // Первый ON_RESUME сопровождает начальную загрузку ViewModel, повторный обновляет данные.
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (resumed) home.refresh()
                resumed = true
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    PetCareBackHandler { if (screen != Screen.Home) back() else controller.showSpaces() }
    Column(Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = controller::showSpaces) { Text("Пространства") }
                TextButton(onClick = { screen = Screen.Settings }) { Text("Настройки") }
                TextButton(onClick = logout) { Text("Выйти") }
            }
        }
        Box(Modifier.weight(1f)) {
            when (val s = screen) {
                Screen.Home -> HomeScreen(home, homeNavigation)
                is Screen.Profile -> AccountPage("Профиль питомца") {
                    Text(s.pet.name, style = MaterialTheme.typography.headlineMedium)
                    Text(s.pet.typeName ?: "Вид не указан")
                    Text("Аллергии: ${s.pet.allergies ?: "не указаны"}")
                    Text("Непереносимости: ${s.pet.intolerances ?: "не указаны"}")
                    Text("Особенности: ${s.pet.features ?: "не указаны"}")
                    Text("Уход: ${s.pet.careNotes ?: "нет заметок"}")
                    Button(onClick = { home.currentSpace?.let { screen = Screen.EditPet(it, s.pet, nextFormId()) } }) { Text("Изменить профиль") }
                    TextButton(onClick = back) { Text("К задачам") }
                }
                Screen.Invitations -> account.selectedSpaceId?.let { InvitationsScreen(account, it, controller) { screen = Screen.Settings } }
                Screen.Settings -> account.selectedSpaceId?.let { spaceId ->
                    SettingsScreen(account, controller, spaceId, telegram.controller, onInvitations = { screen = Screen.Invitations }, onBack = back)
                }
                is Screen.TaskCard -> {
                    val ready = homeState as? HomeUiState.Ready
                    val pets = (ready?.pets as? PetsState.Content)?.items.orEmpty()
                    if (ready != null && ready.space.id == s.task.spaceId && pets.any { it.id == s.task.task.petId }) {
                        LinkedTaskCard(s.task, ready.space, onOpenPet = { home.showPetOf(s.task); back() })
                    } else {
                        HomeScreen(home, homeNavigation)
                    }
                }
                is Screen.EditPet -> key(s.token) { ScopedViewModels {
                    val vm: PetEditViewModel = viewModel(factory = PetEditViewModel.Factory(container.petRepository, s.space.id, s.pet, { pet -> home.reloadAfterSave(pet.id) }, { home.refresh() }))
                    PetEditScreen(vm.controller, back)
                } }
                is Screen.EditTask -> key(s.token) { ScopedViewModels {
                    val vm: TaskEditViewModel = viewModel(factory = TaskEditViewModel.Factory(container.spaceRepository, container.petRepository, container.taskRepository, s.space, s.task, s.petId, { home.refresh() }, { home.refresh() }))
                    TaskEditScreen(vm.controller, back)
                } }
            }
        }
    }
}

/** Карточка задачи из ссылки Telegram. Показывается только после проверки доступа сервером. */
@Composable
private fun LinkedTaskCard(task: TaskItem, space: Space?, onOpenPet: () -> Unit) {
    AccountPage("Задача из Telegram") {
        Text(task.task.title.replaceFirstChar { it.titlecase() }, style = MaterialTheme.typography.headlineSmall)
        Text("Состояние: ${task.status.label()}")
        val zone = space?.zone
        if (zone != null) {
            val formatter = SpaceTimeFormatter(zone, Clock.System.todayIn(zone))
            Text("Срок: ${formatter.format(task.dueAt)} (${space.timezoneId}, UTC${zone.offsetLabel(task.dueAt)})")
        } else {
            Text("Срок появится после загрузки пространства.")
        }
        Text("Ответственный: ${task.task.assigneeName ?: "участник #${task.task.assigneeId}"}")
        task.completedByName?.let { Text("Выполнил(а): $it") }
        Button(onClick = onOpenPet) { Text("К задачам питомца") }
    }
}
