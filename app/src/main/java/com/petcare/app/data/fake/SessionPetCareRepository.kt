package com.petcare.app.data.fake

import com.petcare.app.domain.model.*
import com.petcare.app.domain.repository.*

/** Доступ и результат каждого запроса привязаны к сессии. Старый запрос не переживает выход. */
class SessionPetCareRepository(
    private val accounts: DemoAccountRepository,
    private val data: FakePetCareRepository,
) : SpaceRepository, PetRepository, TaskRepository {
    override suspend fun getSpaces() = accounts.getSpaces()
    override suspend fun getMembers(spaceId: Long) = guarded(spaceId) { accounts.members(spaceId) }
    override suspend fun getPetTypes(): List<PetType> {
        val session = accounts.session.value ?: unauthorized()
        return data.getPetTypes().also { if (session != accounts.session.value) unauthorized() }
    }
    override suspend fun getPets(spaceId: Long) = guarded(spaceId) { data.getPets(spaceId) }
    override suspend fun getPet(spaceId: Long, petId: Long) = guarded(spaceId) { data.getPet(spaceId, petId) }
    override suspend fun createPet(spaceId: Long, draft: PetDraft) = guarded(spaceId) { data.createPet(spaceId, draft) }
    override suspend fun updatePet(spaceId: Long, petId: Long, draft: PetDraft) = guarded(spaceId) { data.updatePet(spaceId, petId, draft) }
    override suspend fun getTaskItems(spaceId: Long, petId: Long) = guarded(spaceId) { data.getTaskItems(spaceId, petId) }
    override suspend fun createTask(space: Space, draft: TaskDraft) = guarded(space.id) { data.createTask(space, draft) }
    override suspend fun updateTask(space: Space, taskId: Long, draft: TaskDraft) = guarded(space.id) { data.updateTask(space, taskId, draft) }

    private suspend fun <T> guarded(spaceId: Long, block: suspend () -> T): T {
        val session = accounts.session.value ?: unauthorized()
        val space = accounts.requireSpace(spaceId)
        data.configureSpace(space, accounts.members(spaceId))
        val result = block()
        if (session != accounts.session.value) unauthorized()
        accounts.requireSpace(spaceId)
        return result
    }
    private fun unauthorized(): Nothing = throw ApiException(401, "unauthorized", "Войдите в аккаунт.")
}
