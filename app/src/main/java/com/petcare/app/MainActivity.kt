package com.petcare.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.petcare.app.presentation.home.HomeScreen
import com.petcare.app.presentation.home.HomeViewModel
import com.petcare.app.ui.theme.PetCareTheme

class MainActivity : ComponentActivity() {

    private val container by lazy { AppContainer(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PetCareTheme {
                val vm: HomeViewModel = viewModel(
                    factory = HomeViewModel.Factory(
                        container.spaceRepository,
                        container.petRepository,
                        container.taskRepository,
                    ),
                )
                HomeScreen(viewModel = vm)
            }
        }
    }
}
