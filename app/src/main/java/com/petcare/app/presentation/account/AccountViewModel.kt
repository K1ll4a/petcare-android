package com.petcare.app.presentation.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.petcare.app.domain.repository.AccountRepository

class AccountViewModel(accounts: AccountRepository) : ViewModel() {
    val controller = AccountController(viewModelScope, accounts)
    class Factory(private val accounts: AccountRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AccountViewModel(accounts) as T
    }
}
