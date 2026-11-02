package com.petcare.app.data.fake

import com.petcare.app.data.mapper.toDomain
import com.petcare.app.data.remote.dto.PetDto
import com.petcare.app.data.remote.dto.TaskDto
import com.petcare.app.data.repository.zoneOrThrow
import com.petcare.app.domain.model.Member
import com.petcare.app.domain.model.Pet
import com.petcare.app.domain.model.PetDraft
import com.petcare.app.domain.model.PetType
import com.petcare.app.domain.model.RepeatType
import com.petcare.app.domain.model.Space
import com.petcare.app.domain.model.TaskDraft
import com.petcare.app.domain.model.TaskItem
import com.petcare.app.domain.repository.ApiException
import com.petcare.app.domain.repository.PetRepository
import com.petcare.app.domain.repository.SpaceRepository
import com.petcare.app.domain.repository.TaskRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Сценарии загрузки — проверка всех состояний экрана без сервера (FR-20). */
enum class FakeScenario {
    /** Обычные данные. */
    NORMAL,

    /** У питомцев нет задач — пустой список. */
    EMPTY,

    /** Загрузка задач и профиля падает — ошибка и «Повторить». */
    ERROR,

    /** Задержка 3 с — загрузка и гонки при быстром переключении. */
    SLOW,
}

/** Что делает сохранение. */
enum class FakeSaveMode {
    /** Сервер сохраняет и отвечает. */
    OK,

    /** Сервер отвечает 500 — точно не сохранено. */
    SERVER_ERROR,

    /** Ответ не пришёл (обрыв после отправки) — результат неизвестен. */
    NO_RESPONSE,
}

/**
 * Тестовые данные в формате сервера, которые ведут себя как сервер:
 * фильтр по пространству и питомцу, проверка полей (422), сохранение в памяти.
 */
class FakePetCareRepository(
    private val source: RawJsonSource,
    var scenario: FakeScenario = FakeScenario.NORMAL,
    var saveMode: FakeSaveMode = FakeSaveMode.OK,
    private val clock: Clock = Clock.systemUTC(),
    private val baseDelayMs: Long = 500,
) : SpaceRepository, PetRepository, TaskRepository {

    private val lock = Mutex()
    private var loaded: TestData? = null
    private val pets = mutableListOf<PetDto>()
    private val tasks = mutableListOf<TaskDto>()
    private val extraSpaces = mutableMapOf<Long, Space>()
    private val extraMembers = mutableMapOf<Long, List<Member>>()

    /** Общий учебный источник аккаунтов передаёт сюда новые пространства и актуальный состав. */
    suspend fun configureSpace(space: Space, members: List<Member>) {
        data()
        lock.withLock { extraSpaces[space.id] = space; extraMembers[space.id] = members }
    }

    private suspend fun data(): TestData = lock.withLock {
        loaded ?: TestDataParser.parse(source.read()).shiftedTo(LocalDate.now(clock)).also {
            loaded = it
            pets += it.pets
            tasks += it.tasks
        }
    }

    override suspend fun getSpaces(): List<Space> {
        simulate(failable = false)
        return data().spaces.map { it.toDomain() }
    }

    override suspend fun getMembers(spaceId: Long): List<Member> {
        simulate(failable = false)
        checkSpace(spaceId)
        return extraMembers[spaceId] ?: data().members[spaceId].orEmpty().map { it.toDomain() }
    }

    override suspend fun getPetTypes(): List<PetType> = data().petTypes.map { it.toDomain() }

    private suspend fun typeNames() = data().petTypes.associate { it.petTypeId to it.name }

    override suspend fun getPets(spaceId: Long): List<Pet> {
        simulate(failable = false)
        checkSpace(spaceId)
        val names = typeNames()
        return lock.withLock { pets.filter { it.spaceId == spaceId }.map { it.toDomain(names) } }
    }

    override suspend fun getPet(spaceId: Long, petId: Long): Pet {
        simulate(failable = true)
        checkSpace(spaceId)
        val names = typeNames()
        return lock.withLock { findPet(spaceId, petId).toDomain(names) }
    }

    override suspend fun getTaskItems(spaceId: Long, petId: Long): List<TaskItem> {
        simulate(failable = true)
        checkSpace(spaceId)
        if (scenario == FakeScenario.EMPTY) return emptyList()
        return lock.withLock {
            findPet(spaceId, petId)
            tasks.filter { it.spaceId == spaceId && it.petId == petId }.map { it.toDomain() }
        }
    }

    override suspend fun createPet(spaceId: Long, draft: PetDraft): Pet {
        checkSpace(spaceId)
        validate(draft)
        return save {
            val dto = draft.toDto(id = (pets.maxOfOrNull { it.petId } ?: 0) + 1, spaceId = spaceId)
            pets += dto
            dto
        }.toDomain(typeNames())
    }

    override suspend fun updatePet(spaceId: Long, petId: Long, draft: PetDraft): Pet {
        checkSpace(spaceId)
        validate(draft)
        return save {
            val i = pets.indexOf(findPet(spaceId, petId))
            pets[i] = draft.toDto(petId, spaceId)
            pets[i]
        }.toDomain(typeNames())
    }

    override suspend fun createTask(space: Space, draft: TaskDraft): Long {
        checkSpace(space.id)
        validate(space, draft)
        val names = memberNames(space.id)
        return save {
            val id = (tasks.maxOfOrNull { it.taskId } ?: 0) + 1
            tasks += draftDto(draft, id, space, null, names)
            id
        }
    }

    override suspend fun updateTask(space: Space, taskId: Long, draft: TaskDraft): Long {
        checkSpace(space.id)
        validate(space, draft)
        val names = memberNames(space.id)
        return save {
            if (tasks.none { it.taskId == taskId && it.spaceId == space.id }) throw notFound()
            // Как на сервере: прошлые выполнения и пропуски остаются, меняется невыполненный экземпляр.
            val open = tasks.filter { it.taskId == taskId && it.status != "done" && it.status != "missed" }
            tasks.removeAll(open)
            tasks += draftDto(draft, taskId, space, open.firstOrNull(), names)
            taskId
        }
    }

    // ---------- имитация сервера ----------

    private suspend fun simulate(failable: Boolean) {
        delay(if (scenario == FakeScenario.SLOW) 3_000 else baseDelayMs)
        if (failable && scenario == FakeScenario.ERROR) {
            throw IOException("Нет соединения с сервером (тестовый сценарий)")
        }
    }

    private suspend fun <T> save(block: () -> T): T {
        delay(if (scenario == FakeScenario.SLOW) 3_000 else baseDelayMs)
        return when (saveMode) {
            FakeSaveMode.SERVER_ERROR ->
                throw ApiException(500, "internal_error", "Ошибка на сервере (тестовый сценарий)")
            // Сохраняем, но «теряем» ответ: так выглядит обрыв после отправки.
            FakeSaveMode.NO_RESPONSE -> {
                lock.withLock { block() }
                throw IOException("Соединение оборвалось до ответа (тестовый сценарий)")
            }
            FakeSaveMode.OK -> lock.withLock { block() }
        }
    }

    private suspend fun checkSpace(spaceId: Long) {
        if (spaceId !in extraSpaces && data().spaces.none { it.spaceId == spaceId }) {
            throw ApiException(403, "forbidden", "Нет доступа к этому пространству")
        }
    }

    private fun findPet(spaceId: Long, petId: Long): PetDto =
        pets.firstOrNull { it.petId == petId && it.spaceId == spaceId } ?: throw notFound()

    private fun notFound() = ApiException(404, "not_found", "Питомец или задача не найдены")

    private suspend fun validate(draft: PetDraft) {
        val errors = draft.validate().toMutableMap()
        if (draft.typeId != null && data().petTypes.none { it.petTypeId == draft.typeId }) {
            errors[PetDraft.FIELD_TYPE] = "Неизвестный вид животного"
        }
        if (errors.isNotEmpty()) throw ApiException(422, "validation_error", "Ошибка валидации запроса", errors)
    }

    private suspend fun validate(space: Space, draft: TaskDraft) {
        val errors = draft.validate().toMutableMap()
        if (draft.petId != null && lock.withLock { pets.none { it.petId == draft.petId && it.spaceId == space.id } }) {
            errors[TaskDraft.FIELD_PET] = "Питомец не из этого пространства"
        }
        if (draft.assigneeId != null && getConfiguredMembers(space.id).none { it.id == draft.assigneeId }) {
            errors[TaskDraft.FIELD_ASSIGNEE] = "Ответственный не из этого пространства"
        }
        if (errors.isNotEmpty()) throw ApiException(422, "validation_error", "Ошибка валидации запроса", errors)
        space.zoneOrThrow()
    }

    private fun PetDraft.toDto(id: Long, spaceId: Long) = PetDto(
        petId = id, petName = name.trim(), petTypeId = typeId!!, spaceId = spaceId,
        features = features.trim().ifEmpty { null }, allergies = allergies.trim().ifEmpty { null },
        intolerances = intolerances.trim().ifEmpty { null }, careNotes = careNotes.trim().ifEmpty { null },
    )

    private suspend fun memberNames(spaceId: Long): Map<Long, String> =
        getConfiguredMembers(spaceId).associate { it.id to it.name }

    private suspend fun getConfiguredMembers(spaceId: Long): List<Member> =
        extraMembers[spaceId] ?: data().members[spaceId].orEmpty().map { it.toDomain() }

    private fun draftDto(
        draft: TaskDraft,
        id: Long,
        space: Space,
        old: TaskDto?,
        names: Map<Long, String>,
    ): TaskDto {
        val zone: ZoneId = space.zoneOrThrow()
        val daily = draft.repeat == RepeatType.DAILY
        val today = LocalDate.now(clock.withZone(zone))
        // Ежедневная задача: экземпляр на сегодня (или на дату начала, если она позже).
        val day = if (daily) maxOf(draft.date!!, old?.occurrenceDate?.let(LocalDate::parse) ?: today) else draft.date!!
        val due = LocalDateTime.of(day, draft.time!!).atZone(zone).toInstant()
        return TaskDto(
            taskId = id, taskName = draft.title.trim(), petId = draft.petId!!, dateTime = due.epochSecond,
            responsibleId = draft.assigneeId!!, isDaily = daily, responsibleName = names[draft.assigneeId],
            status = if (due.isBefore(clock.instant())) "overdue" else "upcoming",
            occurrenceDate = if (daily) day.toString() else null, isActive = true,
            spaceId = space.id, itemId = if (daily) "$id:$day" else "$id:once",
        )
    }
}
