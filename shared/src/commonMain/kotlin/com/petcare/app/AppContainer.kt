package com.petcare.app

import com.petcare.app.data.fake.*
import com.petcare.app.domain.repository.*

object DevConfig {
    val FAKE_SCENARIO = FakeScenario.NORMAL
    val FAKE_SAVE_MODE = FakeSaveMode.OK
}
/** Shared dependency graph; only the persisted demo store is provided by the platform. */
class AppContainer(store: DemoStateStore) {
    private val accounts = DemoAccountRepository(store)
    val accountRepository: AccountRepository = accounts
    private val data = FakePetCareRepository(RawJsonSource { DemoFixture.json }, scenario = DevConfig.FAKE_SCENARIO, saveMode = DevConfig.FAKE_SAVE_MODE)
    private val repository = SessionPetCareRepository(accounts, data)
    val spaceRepository: SpaceRepository = repository
    val petRepository: PetRepository = repository
    val taskRepository: TaskRepository = repository

    /** Учебная привязка Telegram; серверная — serverTelegramRepository() после серверного входа. */
    val telegramRepository: TelegramRepository = accounts
    /** Только в учебном режиме: имитация нажатия Start в боте. На сервере null. */
    val demoTelegramBot: DemoTelegramBot? = accounts

    init {
        // FR-22: незавершённые задачи ушедшего или удалённого участника переходят владельцу.
        accounts.onMemberGone = { spaceId, userId, ownerId, ownerName ->
            data.reassignOpenTasks(spaceId, userId, ownerId, ownerName)
        }
    }
}
