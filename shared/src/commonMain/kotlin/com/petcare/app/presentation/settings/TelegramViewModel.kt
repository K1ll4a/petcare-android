package com.petcare.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.petcare.app.domain.repository.DemoTelegramBot
import com.petcare.app.domain.repository.TelegramRepository

/** Живёт в защищённом разделе: выход из аккаунта очищает его вместе с остальными экранами. */
class TelegramViewModel(repo: TelegramRepository, demoBot: DemoTelegramBot?) : ViewModel() {
    val controller = TelegramController(viewModelScope, repo, demoBot)

    companion object {
        fun Factory(repo: TelegramRepository, demoBot: DemoTelegramBot?): ViewModelProvider.Factory = viewModelFactory {
            initializer { TelegramViewModel(repo, demoBot) }
        }
    }
}
