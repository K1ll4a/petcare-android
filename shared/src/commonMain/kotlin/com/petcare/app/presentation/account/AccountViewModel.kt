package com.petcare.app.presentation.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.petcare.app.domain.repository.AccountRepository
import com.petcare.app.domain.repository.TaskRepository

class AccountViewModel(accounts: AccountRepository, tasks: TaskRepository? = null) : ViewModel() {
    val controller = AccountController(viewModelScope, accounts, tasks)
    companion object {
        fun Factory(accounts: AccountRepository, tasks: TaskRepository? = null): ViewModelProvider.Factory = viewModelFactory {
            initializer { AccountViewModel(accounts, tasks) }
        }
    }
}
