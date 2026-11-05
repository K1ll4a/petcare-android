package com.petcare.app.data.remote
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
internal actual fun platformHttpClient(): HttpClient = HttpClient(OkHttp) { install(HttpTimeout) }
