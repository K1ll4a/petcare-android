package com.petcare.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.petcare.app.domain.repository.PetRepository
import com.petcare.app.domain.repository.SpaceRepository
import com.petcare.app.domain.repository.TaskRepository
import kotlinx.coroutines.flow.StateFlow

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

    fun selectPet(petId: Long) = controller.selectPet(petId)
    fun retry() = controller.retry()
    fun refresh() = controller.refresh()

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
