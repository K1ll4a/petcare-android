package com.petcare.app

import android.app.Application
import com.petcare.app.data.fake.PreferencesDemoStateStore

/** Один контейнер на процесс: поворот экрана не перезапускает учебный сервер. */
class PetCareApplication : Application() {
    val container by lazy { AppContainer(PreferencesDemoStateStore(this)) }
}
