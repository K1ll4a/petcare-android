package com.petcare.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.petcare.app.domain.model.*
import com.petcare.app.presentation.account.*
import com.petcare.app.presentation.edit.*
import com.petcare.app.presentation.home.*
import com.petcare.app.ui.theme.PetCareTheme

private sealed interface Screen {
    data object Home : Screen
    data class Profile(val pet: Pet) : Screen
    data object Invitations : Screen
    data class EditPet(val space: Space, val pet: Pet?, val token: Long) : Screen
    data class EditTask(val space: Space, val task: TaskItem?, val petId: Long?, val token: Long) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PetCareApplication).container
        setContent { PetCareTheme { PetCareApp(container) } }
    }
}

@Composable
private fun PetCareApp(container: AppContainer) {
    val vm: AccountViewModel = viewModel(factory = AccountViewModel.Factory(container.accountRepository))
    val controller = vm.controller
    val state by controller.state.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }
    if (state.session == null) AuthScreen(state, controller)
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
private fun ScopedViewModels(content: @Composable () -> Unit) {
    val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() } }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}

@Composable
private fun WorkspaceApp(container: AppContainer, account: AccountUiState, controller: AccountController, logout: () -> Unit) {
    val home: HomeViewModel = viewModel(factory = HomeViewModel.Factory(container.spaceRepository, container.petRepository, container.taskRepository, account.selectedSpaceId))
    val homeState by home.state.collectAsStateWithLifecycle()
    val currentSpace = (homeState as? HomeUiState.Ready)?.space
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val back = { screen = Screen.Home }
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
    BackHandler { if (screen != Screen.Home) back() else controller.showSpaces() }
    Column(Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = controller::showSpaces) { Text("Пространства") }
                if (currentSpace?.role == SpaceRole.OWNER) TextButton(onClick = { screen = Screen.Invitations }) { Text("Приглашения") }
                TextButton(onClick = logout) { Text("Выйти") }
            }
        }
        Box(Modifier.weight(1f)) {
            when (val s = screen) {
                Screen.Home -> HomeScreen(home, HomeNavigation(
                    onAddPet = { home.currentSpace?.let { screen = Screen.EditPet(it, null, System.nanoTime()) } },
                    onEditPet = { screen = Screen.Profile(it) },
                    onSelectSpace = controller::selectSpace,
                    onAddTask = { home.currentSpace?.let { screen = Screen.EditTask(it, null, (home.state.value as? HomeUiState.Ready)?.selectedPetId, System.nanoTime()) } },
                    onEditTask = { key -> home.findTask(key)?.let { task -> home.currentSpace?.let { screen = Screen.EditTask(it, task, task.task.petId, System.nanoTime()) } } },
                ))
                is Screen.Profile -> AccountPage("Профиль питомца") {
                    Text(s.pet.name, style = MaterialTheme.typography.headlineMedium)
                    Text(s.pet.typeName ?: "Вид не указан")
                    Text("Аллергии: ${s.pet.allergies ?: "не указаны"}")
                    Text("Непереносимости: ${s.pet.intolerances ?: "не указаны"}")
                    Text("Особенности: ${s.pet.features ?: "не указаны"}")
                    Text("Уход: ${s.pet.careNotes ?: "нет заметок"}")
                    Button(onClick = { home.currentSpace?.let { screen = Screen.EditPet(it, s.pet, System.nanoTime()) } }) { Text("Изменить профиль") }
                    TextButton(onClick = back) { Text("К задачам") }
                }
                Screen.Invitations -> currentSpace?.let { InvitationsScreen(account, it.id, controller, back) }
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
