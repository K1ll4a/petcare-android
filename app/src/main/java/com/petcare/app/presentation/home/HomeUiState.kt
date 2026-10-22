package com.petcare.app.presentation.home

import com.petcare.app.domain.model.Pet

/** Состояние главного экрана целиком. */
sealed interface HomeUiState {
    /** Первый запуск: грузим пространство и питомцев. */
    data object Loading : HomeUiState

    /** Не удалось загрузить пространство/питомцев — показываем ошибку с «Повторить». */
    data class Error(val message: String) : HomeUiState

    data class Ready(
        val spaceName: String,
        val pets: List<Pet>,
        val selectedPetId: Long?,
        val tasks: TasksState,
        /** Идёт обновление поверх уже показанных данных (pull-to-refresh). */
        val isRefreshing: Boolean = false,
    ) : HomeUiState {
        val selectedPet: Pet? get() = pets.firstOrNull { it.id == selectedPetId }
    }
}

/** Состояние списка задач выбранного питомца (FR-20). */
sealed interface TasksState {
    data object Loading : TasksState
    data object NoPets : TasksState
    data object Empty : TasksState
    data class Error(val message: String) : TasksState
    data class Content(val items: List<TaskItemUi>) : TasksState
}
