package com.petcare.app.data.fake

import android.content.Context

/** Читает тестовые данные из app/src/main/assets. */
class AssetJsonSource(
    private val context: Context,
    private val fileName: String = "test_data.json",
) : RawJsonSource {
    override fun read(): String =
        context.assets.open(fileName).bufferedReader(Charsets.UTF_8).use { it.readText() }
}
