package com.petcare.app.presentation.home

import com.petcare.app.domain.model.Pet
import com.petcare.app.domain.model.Space

/** Состояние главного экрана целиком. */
sealed interface HomeUiState {
    /** Первый запуск: грузим список пространств. */
    data object Loading : HomeUiState

    /** Не удалось загрузить пространства — ошибка с «Повторить». */
    data class Error(val message: String) : HomeUiState

    /** Пользователь не состоит ни в одном пространстве (создание — экран Mobile №1). */
    data class NoSpaces(val message: String? = null) : HomeUiState

    data class Ready(
        val spaces: List<Space>,
        val space: Space,
        val pets: PetsState,
        val selectedPetId: Long?,
        val profile: ProfileState,
        val tasks: TasksState,
        /** Ручное обновление идёт поверх показанных данных. */
        val isRefreshing: Boolean = false,
        /** Обновить не удалось — данные на экране прежние (FR-19). */
        val staleMessage: String? = null,
    ) : HomeUiState {
        val selectedPet: Pet?
            get() = (pets as? PetsState.Content)?.items?.firstOrNull { it.id == selectedPetId }
    }
}

sealed interface PetsState {
    data object Loading : PetsState
    data class Error(val message: String) : PetsState
    data class Content(val items: List<Pet>) : PetsState
}

/** Профиль выбранного питомца, загружается с сервера отдельно от списка. */
sealed interface ProfileState {
    data object None : ProfileState
    data object Loading : ProfileState
    data class Error(val message: String) : ProfileState
    data class Content(val pet: Pet) : ProfileState
}

/** Состояние списка задач выбранного питомца (FR-20). */
sealed interface TasksState {
    data object Loading : TasksState
    data object NoPets : TasksState
    data object Empty : TasksState
    data class Error(val message: String) : TasksState
    data class Content(
        val items: List<TaskItemUi>,
        /** «Europe/Moscow, UTC+03:00» — пояс, в котором показаны сроки. */
        val timezoneLabel: String = "",
    ) : TasksState
}
