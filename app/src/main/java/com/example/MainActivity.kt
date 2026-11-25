package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.mutableStateOf
import com.example.core.common.UrlValidator
import com.example.feature.main.KlipzAppRoot

class MainActivity : ComponentActivity() {

    private val incomingSharedUrl = mutableStateOf<String?>(null)

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        val appContainer = (application as KlipzApplication).container

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            KlipzAppRoot(
                container = appContainer,
                widthSizeClass = windowSizeClass.widthSizeClass,
                incomingSharedUrl = incomingSharedUrl.value,
                onSharedUrlConsumed = { incomingSharedUrl.value = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val extracted = UrlValidator.extractSharedUrl(intent)
        if (!extracted.isNullOrBlank()) {
            incomingSharedUrl.value = extracted
        }
    }
}
