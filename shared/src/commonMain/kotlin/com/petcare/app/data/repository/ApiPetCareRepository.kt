package com.petcare.app.data.repository

import com.petcare.app.data.mapper.toDomain
import com.petcare.app.data.mapper.toRequestBody
import com.petcare.app.data.remote.PetCareApi
import com.petcare.app.domain.model.Member
import com.petcare.app.domain.model.Pet
import com.petcare.app.domain.model.PetDraft
import com.petcare.app.domain.model.PetType
import com.petcare.app.domain.model.Space
import com.petcare.app.domain.model.TaskDraft
import com.petcare.app.domain.model.TaskItem
import com.petcare.app.domain.repository.InvalidServerDataException
import com.petcare.app.domain.repository.PetRepository
import com.petcare.app.domain.repository.SpaceRepository
import com.petcare.app.domain.repository.TaskRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Репозиторий поверх сервера. Кроме вызовов API — проверка, что данные из выбранного пространства. */
class ApiPetCareRepository(private val api: PetCareApi) : SpaceRepository, PetRepository, TaskRepository {

    private val typesLock = Mutex()
    private var typesCache: List<PetType>? = null

    override suspend fun getSpaces(): List<Space> = api.getSpaces().map { it.toDomain() }

    override suspend fun getMembers(spaceId: Long): List<Member> = api.getMembers(spaceId).map { it.toDomain() }

    /** Справочник видов почти не меняется — держим в памяти. */
    override suspend fun getPetTypes(): List<PetType> = typesLock.withLock {
        typesCache ?: api.getPetTypes().map { it.toDomain() }.also { typesCache = it }
    }

    private suspend fun typeNames(): Map<Int, String> = getPetTypes().associate { it.id to it.name }

    override suspend fun getPets(spaceId: Long): List<Pet> {
        val names = typeNames()
        return api.getPets(spaceId).map { it.toDomain(names) }
            .onEach { it.checkSpace(spaceId) }
    }

    override suspend fun getPet(spaceId: Long, petId: Long): Pet =
        api.getPet(petId).toDomain(typeNames()).also { it.checkSpace(spaceId) }

    override suspend fun createPet(spaceId: Long, draft: PetDraft): Pet =
        api.createPet(spaceId, draft.toRequestBody()).toDomain(typeNames())

    override suspend fun updatePet(spaceId: Long, petId: Long, draft: PetDraft): Pet {
        // Сначала убеждаемся, что питомец из выбранного пространства, — иначе не меняем.
        getPet(spaceId, petId)
        return api.updatePet(petId, draft.toRequestBody()).toDomain(typeNames())
    }

    override suspend fun getTaskItems(spaceId: Long, petId: Long): List<TaskItem> =
        api.getTasks(spaceId, petId).map { it.toDomain() }
            // Защита от смешивания: сервер уже фильтрует, но чужие строки не показываем никогда.
            .filter { it.task.petId == petId && (it.spaceId == null || it.spaceId == spaceId) }

    override suspend fun createTask(space: Space, draft: TaskDraft): Long =
        api.createTask(space.id, draft.toRequestBody(space.zoneOrThrow()))

    override suspend fun updateTask(space: Space, taskId: Long, draft: TaskDraft): Long =
        api.updateTask(taskId, draft.toRequestBody(space.zoneOrThrow()))

    private fun Pet.checkSpace(spaceId: Long) {
        if (this.spaceId != null && this.spaceId != spaceId) {
            throw InvalidServerDataException("Питомец ${this.name} не из выбранного пространства")
        }
    }
}

/** Без верного пояса нельзя перевести дату и время формы в срок — не отправляем. */
fun Space.zoneOrThrow() = zone
    ?: throw InvalidServerDataException("Неверный часовой пояс пространства «$timezoneId»")
