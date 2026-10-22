package com.petcare.app.domain.repository

import com.petcare.app.domain.model.Pet
import com.petcare.app.domain.model.Space
import com.petcare.app.domain.model.TaskItem

/**
 * Интерфейсы, через которые экраны получают данные.
 * Сейчас реализованы фейками на тестовых данных; на неделе 2
 * подменяются реализацией через API без изменения экранов.
 */
interface SpaceRepository {
    suspend fun getCurrentSpace(): Space
}

interface PetRepository {
    suspend fun getPets(spaceId: Long): List<Pet>
    suspend fun getPet(petId: Long): Pet
}

interface TaskRepository {
    /** Задачи питомца (разовые + экземпляры ежедневных) со статусами от сервера. */
    suspend fun getTaskItems(petId: Long): List<TaskItem>
}
