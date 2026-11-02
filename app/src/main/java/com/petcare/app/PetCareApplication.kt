package com.petcare.app

import android.app.Application

/** Один контейнер на процесс: поворот экрана не перезапускает учебный сервер. */
class PetCareApplication : Application() {
    val container by lazy { AppContainer(this) }
}
