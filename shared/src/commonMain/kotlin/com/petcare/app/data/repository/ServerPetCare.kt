package com.petcare.app.data.repository

import com.petcare.app.data.remote.KtorHttpTransport
import com.petcare.app.data.remote.PetCareApi
import com.petcare.app.data.remote.TokenProvider

/**
 * Питомцы и задачи с сервера для Android и iOS (задача #23).
 *
 * Подключается в [com.petcare.app.AppContainer] вместо SessionPetCareRepository, когда серверный
 * вход Mobile №1 выдаёт токен: `tokens` должен возвращать токен текущей серверной сессии
 * (POST /auth/login), а после выхода — null. Без токена сервер отвечает 401, экран
 * показывает «Сессия истекла».
 *
 * Адрес сервера: эмулятор Android — `http://10.0.2.2:8000`, iOS Simulator — `http://localhost:8000`,
 * телефон — адрес компьютера в той же сети.
 */
fun serverPetCareRepository(baseUrl: String, tokens: TokenProvider): ApiPetCareRepository =
    ApiPetCareRepository(PetCareApi(KtorHttpTransport(baseUrl, tokens)))
