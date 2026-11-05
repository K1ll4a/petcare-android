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
}
