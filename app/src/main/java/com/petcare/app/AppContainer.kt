package com.petcare.app

import android.content.Context
import com.petcare.app.data.fake.AssetJsonSource
import com.petcare.app.data.fake.FakePetCareRepository
import com.petcare.app.data.fake.FakeScenario
import com.petcare.app.domain.repository.PetRepository
import com.petcare.app.domain.repository.SpaceRepository
import com.petcare.app.domain.repository.TaskRepository

/**
 * Ручной DI: одно место, где выбирается реализация репозиториев.
 * На неделе 2 здесь фейк меняется на реализацию через API.
 * Сценарий фейка (EMPTY/ERROR/SLOW) удобно переключать для проверки состояний экрана.
 */
class AppContainer(context: Context) {
    private val fake = FakePetCareRepository(
        source = AssetJsonSource(context.applicationContext),
        scenario = FakeScenario.NORMAL,
    )

    val spaceRepository: SpaceRepository = fake
    val petRepository: PetRepository = fake
    val taskRepository: TaskRepository = fake
}
