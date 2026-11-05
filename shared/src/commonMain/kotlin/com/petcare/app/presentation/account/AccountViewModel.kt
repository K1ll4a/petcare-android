package com.petcare.app.presentation.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.petcare.app.domain.repository.AccountRepository

class AccountViewModel(accounts: AccountRepository) : ViewModel() {
    val controller = AccountController(viewModelScope, accounts)
    companion object {
        fun Factory(accounts: AccountRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { AccountViewModel(accounts) }
        }
    }
}
