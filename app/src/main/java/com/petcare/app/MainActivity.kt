package com.petcare.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.petcare.app.navigation.PendingDeepLinks
import com.petcare.app.ui.theme.PetCareTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Ссылка из Telegram при холодном старте. После поворота экрана повторно не открывается.
        if (savedInstanceState == null) offerLink(intent)
        val container = (application as PetCareApplication).container
        setContent { PetCareTheme { PetCareApp(container) } }
    }

    /** singleTask: ссылка, открытая при запущенном приложении, приходит сюда. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        offerLink(intent)
    }

    private fun offerLink(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) PendingDeepLinks.offer(intent.dataString)
    }
}
