package com.petcare.app.presentation.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.petcare.app.domain.model.Pet
import com.petcare.app.domain.model.Space
import com.petcare.app.domain.model.TaskItem
import com.petcare.app.domain.repository.PetRepository
import com.petcare.app.domain.repository.SpaceRepository
import com.petcare.app.domain.repository.TaskRepository

/*
 * ViewModel формы живёт в отдельном store открытого экрана.
 * Закрытие формы, смена пространства или выход из аккаунта очищает store и отменяет запросы.
 */

class PetEditViewModel(
    repo: PetRepository,
    spaceId: Long,
    existing: Pet?,
    onSaved: (Pet) -> Unit,
    onUnconfirmed: () -> Unit,
) : ViewModel() {
    val controller = PetEditController(viewModelScope, repo, spaceId, existing, onSaved, onUnconfirmed)

    companion object {
        fun Factory(
            repo: PetRepository,
            spaceId: Long,
            existing: Pet?,
            onSaved: (Pet) -> Unit,
            onUnconfirmed: () -> Unit,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { PetEditViewModel(repo, spaceId, existing, onSaved, onUnconfirmed) }
        }
    }
}

class TaskEditViewModel(
    spaces: SpaceRepository,
    pets: PetRepository,
    tasks: TaskRepository,
    space: Space,
    existing: TaskItem?,
    defaultPetId: Long?,
    onSaved: (Long) -> Unit,
    onUnconfirmed: () -> Unit,
) : ViewModel() {
    val controller = TaskEditController(
        viewModelScope, spaces, pets, tasks, space, existing, defaultPetId, onSaved, onUnconfirmed,
    )

    companion object {
        fun Factory(
            spaces: SpaceRepository,
            pets: PetRepository,
            tasks: TaskRepository,
            space: Space,
            existing: TaskItem?,
            defaultPetId: Long?,
            onSaved: (Long) -> Unit,
            onUnconfirmed: () -> Unit,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { TaskEditViewModel(spaces, pets, tasks, space, existing, defaultPetId, onSaved, onUnconfirmed) }
        }
    }
}
