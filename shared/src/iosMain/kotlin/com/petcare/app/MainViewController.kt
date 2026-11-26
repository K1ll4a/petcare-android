package com.petcare.app

import androidx.compose.ui.window.ComposeUIViewController
import com.petcare.app.data.fake.AppleDemoStateStore
import com.petcare.app.navigation.PendingDeepLinks
import com.petcare.app.ui.theme.PetCareTheme
import platform.UIKit.UIViewController

private val iosContainer by lazy { AppContainer(AppleDemoStateStore()) }
fun MainViewController(): UIViewController = ComposeUIViewController {
    ScopedViewModels { PetCareTheme { PetCareApp(iosContainer) } }
}

/** Вызывается из SwiftUI onOpenURL. true — ссылка на задачу принята; она откроется после входа и проверки доступа. */
fun handleDeepLink(url: String): Boolean = PendingDeepLinks.offer(url)
