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
    override suspend fun updatePet(spaceId: Long, petId: Long, original: PetDraft, draft: PetDraft) = guarded(spaceId) { data.updatePet(spaceId, petId, original, draft) }
    override suspend fun getTaskItems(spaceId: Long, petId: Long) = guarded(spaceId) { data.getTaskItems(spaceId, petId) }
    override suspend fun createTask(space: Space, draft: TaskDraft) = guarded(space.id) { data.createTask(space, draft) }
    override suspend fun updateTask(space: Space, taskId: Long, original: TaskDraft, draft: TaskDraft) = guarded(space.id) { data.updateTask(space, taskId, original, draft) }

    /** Только пространства, где аккаунт состоит сейчас: чужая задача не находится (AC-15). */
    override suspend fun findTask(taskId: Long, occurrenceId: Long?): TaskItem? {
        val session = accounts.session.value ?: unauthorized()
        val allowed = accounts.getSpaces().map { it.id }.toSet()
        val item = data.findTaskItem(taskId, occurrenceId, allowed)
        if (session != accounts.session.value) unauthorized()
        return item?.takeIf { it.spaceId != null && runCatching { accounts.requireSpace(it.spaceId) }.isSuccess }
    }

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
