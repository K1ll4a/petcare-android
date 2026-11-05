package com.petcare.app

import androidx.compose.ui.window.ComposeUIViewController
import com.petcare.app.data.fake.AppleDemoStateStore
import com.petcare.app.ui.theme.PetCareTheme
import platform.UIKit.UIViewController

private val iosContainer by lazy { AppContainer(AppleDemoStateStore()) }
fun MainViewController(): UIViewController = ComposeUIViewController {
    ScopedViewModels { PetCareTheme { PetCareApp(iosContainer) } }
}
