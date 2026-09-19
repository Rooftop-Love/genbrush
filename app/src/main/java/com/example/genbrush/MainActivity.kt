package com.example.genbrush

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.genbrush.ui.navigation.AppNavigation
import com.example.genbrush.ui.theme.GenBrushTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as GenBrushApp
        setContent {
            GenBrushTheme {
                val language by app.language.collectAsState()
                AppNavigation(
                    prefs = app.prefs,
                    repository = app.repository,
                    registry = app.providerRegistry,
                    language = language,
                    onLanguageChange = app::setLanguage
                )
            }
        }
    }
}
