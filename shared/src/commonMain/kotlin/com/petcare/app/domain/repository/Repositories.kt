package com.petcare.app.domain.repository

import com.petcare.app.domain.model.Member
import com.petcare.app.domain.model.Pet
import com.petcare.app.domain.model.PetDraft
import com.petcare.app.domain.model.PetType
import com.petcare.app.domain.model.Space
import com.petcare.app.domain.model.TaskDraft
import com.petcare.app.domain.model.TaskItem

/*
 * Интерфейсы, через которые экраны получают и отправляют данные.
 * Реализации: ApiPetCareRepository (сервер) и FakePetCareRepository (тестовые данные).
 *
 * Каждый запрос явно содержит выбранное пространство (задача #14, API.md §1):
 * сервер не хранит «текущее пространство», это состояние клиента.
 *
 * Ошибки: IOException — нет связи или ответа; ApiException — сервер ответил ошибкой.
 */

interface SpaceRepository {
    suspend fun getSpaces(): List<Space>
    suspend fun getMembers(spaceId: Long): List<Member>
}

interface PetRepository {
    suspend fun getPetTypes(): List<PetType>
    suspend fun getPets(spaceId: Long): List<Pet>

    /** Профиль питомца ВЫБРАННОГО пространства; питомец другого пространства — ошибка. */
    suspend fun getPet(spaceId: Long, petId: Long): Pet

    /** Возвращает профиль, сохранённый сервером. */
    suspend fun createPet(spaceId: Long, draft: PetDraft): Pet
    suspend fun updatePet(spaceId: Long, petId: Long, draft: PetDraft): Pet
}

interface TaskRepository {
    /** Задачи питомца выбранного пространства (разовые + экземпляры ежедневных) со статусами от сервера. */
    suspend fun getTaskItems(spaceId: Long, petId: Long): List<TaskItem>

    /**
     * [space] нужен целиком: дата и время формы переводятся в момент по его часовому поясу.
     * Возвращает id задачи, подтверждённый сервером; актуальный список затем перечитывается.
     */
    suspend fun createTask(space: Space, draft: TaskDraft): Long
    suspend fun updateTask(space: Space, taskId: Long, draft: TaskDraft): Long
}

/** Ответ сервера с ошибкой в формате API.md §2: {code, message, fields}. */
class ApiException(
    val httpCode: Int,
    val code: String,
    override val message: String,
    val fields: Map<String, String> = emptyMap(),
) : Exception(message) {
    val isUnauthorized get() = httpCode == 401
    val isAccessLost get() = httpCode == 403 && code == "forbidden"
    val isValidation get() = httpCode == 422
}

/** Сервер прислал данные, которые нельзя показать (чужое пространство, неверный формат). */
class InvalidServerDataException(message: String) : Exception(message)
