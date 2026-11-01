package com.petcare.app

import android.content.Context
import com.petcare.app.data.fake.AssetJsonSource
import com.petcare.app.data.fake.FakePetCareRepository
import com.petcare.app.data.fake.FakeSaveMode
import com.petcare.app.data.fake.FakeScenario
import com.petcare.app.data.remote.DevLoginTransport
import com.petcare.app.data.remote.OkHttpTransport
import com.petcare.app.data.remote.PetCareApi
import com.petcare.app.data.repository.ApiPetCareRepository
import com.petcare.app.domain.repository.PetRepository
import com.petcare.app.domain.repository.SpaceRepository
import com.petcare.app.domain.repository.TaskRepository

/** Откуда приложение берёт данные. */
enum class DataSource { FAKE, SERVER }

/**
 * Настройки для разработки и демонстрации QA. Меняются здесь.
 * - FAKE — тестовые данные из assets, сервер не нужен; [FAKE_SCENARIO] и [FAKE_SAVE_MODE]
 *   включают пустой список, ошибку, медленный ответ и сбои сохранения.
 * - SERVER — petcare-backend по [BASE_URL] (10.0.2.2 — компьютер, на котором запущен эмулятор).
 */
object DevConfig {
    val DATA_SOURCE = DataSource.FAKE
    val FAKE_SCENARIO = FakeScenario.NORMAL
    val FAKE_SAVE_MODE = FakeSaveMode.OK

    const val BASE_URL = "http://10.0.2.2:8000"

    /** Тестовый пользователь из seed сервера (petcare-backend/README). Временно, до входа из #13. */
    const val DEV_EMAIL = "anna@example.com"
    const val DEV_PASSWORD = "demo-password"
}

/** Ручной DI: одно место, где выбирается реализация репозиториев. */
class AppContainer(context: Context) {

    private val repository: Any = when (DevConfig.DATA_SOURCE) {
        DataSource.FAKE -> FakePetCareRepository(
            source = AssetJsonSource(context.applicationContext),
            scenario = DevConfig.FAKE_SCENARIO,
            saveMode = DevConfig.FAKE_SAVE_MODE,
        )
        DataSource.SERVER -> ApiPetCareRepository(
            PetCareApi(
                DevLoginTransport(
                    innerFactory = { tokens -> OkHttpTransport(DevConfig.BASE_URL, tokens) },
                    email = DevConfig.DEV_EMAIL,
                    password = DevConfig.DEV_PASSWORD,
                ),
            ),
        )
    }

    val spaceRepository: SpaceRepository = repository as SpaceRepository
    val petRepository: PetRepository = repository as PetRepository
    val taskRepository: TaskRepository = repository as TaskRepository
}
