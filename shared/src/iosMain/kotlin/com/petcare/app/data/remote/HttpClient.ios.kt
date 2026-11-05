package com.petcare.app.data.remote
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
internal actual fun platformHttpClient(): HttpClient = HttpClient(Darwin) { install(HttpTimeout) }
