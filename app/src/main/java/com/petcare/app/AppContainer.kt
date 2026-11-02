package com.petcare.app

import android.content.Context
import com.petcare.app.data.fake.*
import com.petcare.app.domain.repository.*

/** Учебная сборка работает без сервера. Ошибки и задержки включаются для проверки UI. */
object DevConfig {
    val FAKE_SCENARIO = FakeScenario.NORMAL
    val FAKE_SAVE_MODE = FakeSaveMode.OK
}

/** Общая точка сборки зависимостей для обоих мобильных разработчиков.
 * Сетевые PetCareApi / ApiPetCareRepository сохранены; включать после согласования auth-контракта.
 */
class AppContainer(context: Context) {
    private val accounts = DemoAccountRepository(PreferencesDemoStateStore(context.applicationContext))
    val accountRepository: AccountRepository = accounts
    private val data = FakePetCareRepository(
        AssetJsonSource(context.applicationContext),
        scenario = DevConfig.FAKE_SCENARIO,
        saveMode = DevConfig.FAKE_SAVE_MODE,
    )
    private val repository = SessionPetCareRepository(accounts, data)
    val spaceRepository: SpaceRepository = repository
    val petRepository: PetRepository = repository
    val taskRepository: TaskRepository = repository
}
