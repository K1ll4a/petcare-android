package com.petcare.app.data.fake

import android.content.Context
import com.petcare.app.domain.repository.DemoStateStore

/** Хранилище метаданных учебного сервера, а не кеш авторизованного экрана. */
class PreferencesDemoStateStore(context: Context) : DemoStateStore {
    private val preferences = context.getSharedPreferences("demo-backend", Context.MODE_PRIVATE)
    override fun read(): String? = preferences.getString("state", null)
    override fun write(value: String) { preferences.edit().putString("state", value).apply() }
}
