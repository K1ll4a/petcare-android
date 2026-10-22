package com.petcare.app.data.fake

import com.petcare.app.data.mapper.toDomain
import com.petcare.app.domain.model.Pet
import com.petcare.app.domain.model.Space
import com.petcare.app.domain.model.TaskItem
import com.petcare.app.domain.repository.PetRepository
import com.petcare.app.domain.repository.SpaceRepository
import com.petcare.app.domain.repository.TaskRepository
import kotlinx.coroutines.delay
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** Сценарии, чтобы проверить все состояния экрана без Backend (FR-20). */
enum class FakeScenario {
    /** Обычные данные. */
    NORMAL,

    /** У питомцев нет задач — проверка пустого состояния. */
    EMPTY,

    /** Каждый запрос задач падает — проверка ошибки и кнопки «Повторить». */
    ERROR,

    /** Задержка 3 с — проверка загрузки и гонок при быстром переключении питомца. */
    SLOW,
}

/**
 * Один фейковый источник для всех трёх репозиториев.
 * На неделе 2 вместо него подставляются Api*Repository с теми же интерфейсами.
 */
class FakePetCareRepository(
    private val source: RawJsonSource,
    var scenario: FakeScenario = FakeScenario.NORMAL,
    private val clock: Clock = Clock.systemUTC(),
    private val baseDelayMs: Long = 600,
) : SpaceRepository, PetRepository, TaskRepository {

    private val data: TestDataDto by lazy {
        val parsed = TestDataParser.parse(source.read())
        val zone = runCatching { ZoneId.of(parsed.space.timezone) }.getOrDefault(ZoneId.of("UTC"))
        parsed.shiftedTo(LocalDate.now(clock.withZone(zone)))
    }

    private val petTypes: Map<Int, String> by lazy {
        data.petTypes.associate { it.petTypeId to it.name }
    }

    private val memberNames: Map<Long, String> by lazy {
        data.members.associate { it.userId to it.name }
    }

    override suspend fun getCurrentSpace(): Space {
        simulateNetwork(failable = false)
        return data.space.toDomain()
    }

    override suspend fun getPets(spaceId: Long): List<Pet> {
        simulateNetwork(failable = false)
        // У части питомцев в тестовых данных space_id нет (как в текущем контракте) —
        // считаем, что они из текущего пространства.
        return data.pets
            .filter { (it.spaceId ?: data.space.spaceId) == spaceId }
            .map { it.toDomain(petTypes) }
    }

    override suspend fun getPet(petId: Long): Pet {
        simulateNetwork(failable = false)
        return data.pets.firstOrNull { it.petId == petId }?.toDomain(petTypes)
            ?: throw NoSuchElementException("Питомец $petId не найден")
    }

    override suspend fun getTaskItems(petId: Long): List<TaskItem> {
        simulateNetwork(failable = true)
        if (scenario == FakeScenario.EMPTY) return emptyList()
        return data.tasks
            .filter { it.petId == petId }
            .map { dto ->
                // Реальный репозиторий возьмёт имена из списка участников пространства.
                dto.copy(responsibleName = dto.responsibleName ?: memberNames[dto.responsibleId])
                    .toDomain()
            }
    }

    private suspend fun simulateNetwork(failable: Boolean) {
        delay(if (scenario == FakeScenario.SLOW) 3_000 else baseDelayMs)
        if (failable && scenario == FakeScenario.ERROR) {
            throw IOException("Нет соединения с сервером (тестовый сценарий)")
        }
    }
}
