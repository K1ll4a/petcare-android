package com.petcare.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.petcare.app.domain.model.Pet
import com.petcare.app.domain.model.Space
import com.petcare.app.domain.model.TaskItem
import com.petcare.app.domain.repository.PetRepository
import com.petcare.app.domain.repository.SpaceRepository
import com.petcare.app.domain.repository.TaskRepository
import kotlinx.coroutines.flow.StateFlow

/** Тонкая обёртка: вся логика в [HomeController] (тестируется без Android). */
class HomeViewModel(
    spaces: SpaceRepository,
    pets: PetRepository,
    tasks: TaskRepository,
) : ViewModel() {

    private val controller = HomeController(viewModelScope, spaces, pets, tasks)

    val state: StateFlow<HomeUiState> = controller.state

    init {
        controller.start()
    }

    fun selectSpace(spaceId: Long) = controller.selectSpace(spaceId)
    fun selectPet(petId: Long) = controller.selectPet(petId)
    fun retry() = controller.retry()
    fun refresh() = controller.refresh()
    fun reloadAfterSave(selectPetId: Long? = null) = controller.reloadAfterSave(selectPetId)

    fun findTask(key: String): TaskItem? = controller.findTask(key)
    val currentSpace: Space? get() = controller.currentSpace
    val currentPets: List<Pet> get() = controller.currentPets

    class Factory(
        private val spaces: SpaceRepository,
        private val pets: PetRepository,
        private val tasks: TaskRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(spaces, pets, tasks) as T
    }
}
