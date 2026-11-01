package com.petcare.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.petcare.app.domain.model.Pet
import com.petcare.app.domain.model.Space
import com.petcare.app.domain.model.TaskItem
import com.petcare.app.presentation.edit.PetEditScreen
import com.petcare.app.presentation.edit.PetEditViewModel
import com.petcare.app.presentation.edit.TaskEditScreen
import com.petcare.app.presentation.edit.TaskEditViewModel
import com.petcare.app.presentation.home.HomeNavigation
import com.petcare.app.presentation.home.HomeScreen
import com.petcare.app.presentation.home.HomeUiState
import com.petcare.app.presentation.home.HomeViewModel
import com.petcare.app.ui.theme.PetCareTheme

/** Временная навигация до общей навигации Mobile №1 (задача #13). */
private sealed interface Screen {
    data object Home : Screen
    data class EditPet(val space: Space, val pet: Pet?, val token: Long) : Screen
    data class EditTask(val space: Space, val task: TaskItem?, val petId: Long?, val token: Long) : Screen
}

class MainActivity : ComponentActivity() {

    private val container by lazy { AppContainer(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PetCareTheme {
                val home: HomeViewModel = viewModel(
                    factory = HomeViewModel.Factory(
                        container.spaceRepository,
                        container.petRepository,
                        container.taskRepository,
                    ),
                )
                var screen by remember { mutableStateOf<Screen>(Screen.Home) }
                val back = { screen = Screen.Home }
                // token делает ключ ViewModel формы уникальным при каждом открытии.
                fun token() = System.nanoTime()

                when (val s = screen) {
                    Screen.Home -> HomeScreen(
                        viewModel = home,
                        nav = HomeNavigation(
                            onAddPet = { home.currentSpace?.let { screen = Screen.EditPet(it, null, token()) } },
                            onEditPet = { pet -> home.currentSpace?.let { screen = Screen.EditPet(it, pet, token()) } },
                            onAddTask = {
                                val selected = (home.state.value as? HomeUiState.Ready)?.selectedPetId
                                home.currentSpace?.let { screen = Screen.EditTask(it, null, selected, token()) }
                            },
                            onEditTask = { key ->
                                val task = home.findTask(key)
                                home.currentSpace?.let { if (task != null) screen = Screen.EditTask(it, task, task.task.petId, token()) }
                            },
                        ),
                    )

                    is Screen.EditPet -> {
                        BackHandler(onBack = back)
                        val vm: PetEditViewModel = viewModel(
                            key = "pet-${s.token}",
                            factory = PetEditViewModel.Factory(
                                repo = container.petRepository,
                                spaceId = s.space.id,
                                existing = s.pet,
                                onSaved = { pet -> home.reloadAfterSave(selectPetId = pet.id) },
                                onUnconfirmed = { home.refresh() },
                            ),
                        )
                        PetEditScreen(vm.controller, onBack = back)
                    }

                    is Screen.EditTask -> {
                        BackHandler(onBack = back)
                        val vm: TaskEditViewModel = viewModel(
                            key = "task-${s.token}",
                            factory = TaskEditViewModel.Factory(
                                spaces = container.spaceRepository,
                                pets = container.petRepository,
                                tasks = container.taskRepository,
                                space = s.space,
                                existing = s.task,
                                defaultPetId = s.petId,
                                onSaved = { home.refresh() },
                                onUnconfirmed = { home.refresh() },
                            ),
                        )
                        TaskEditScreen(vm.controller, onBack = back)
                    }
                }
            }
        }
    }
}
