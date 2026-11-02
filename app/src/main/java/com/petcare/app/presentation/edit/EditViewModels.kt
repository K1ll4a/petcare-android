package com.petcare.app.presentation.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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

    class Factory(
        private val repo: PetRepository,
        private val spaceId: Long,
        private val existing: Pet?,
        private val onSaved: (Pet) -> Unit,
        private val onUnconfirmed: () -> Unit,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PetEditViewModel(repo, spaceId, existing, onSaved, onUnconfirmed) as T
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

    class Factory(
        private val spaces: SpaceRepository,
        private val pets: PetRepository,
        private val tasks: TaskRepository,
        private val space: Space,
        private val existing: TaskItem?,
        private val defaultPetId: Long?,
        private val onSaved: (Long) -> Unit,
        private val onUnconfirmed: () -> Unit,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TaskEditViewModel(spaces, pets, tasks, space, existing, defaultPetId, onSaved, onUnconfirmed) as T
    }
}
