package com.example.genbrush

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.genbrush.data.local.PreferencesManager
import com.example.genbrush.ui.navigation.AppNavigation
import com.example.genbrush.ui.theme.GenBrushTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as GenBrushApp
        setContent {
            val language by app.language.collectAsState()
            val themeMode by app.themeMode.collectAsState()
            val dynamicColor by app.dynamicColor.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                PreferencesManager.THEME_LIGHT -> false
                PreferencesManager.THEME_DARK -> true
                else -> systemDark
            }

            LaunchedEffect(darkTheme) {
                val barStyle = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle
                )
            }

            GenBrushTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColor
            ) {
                AppNavigation(
                    prefs = app.prefs,
                    repository = app.repository,
                    registry = app.providerRegistry,
                    language = language,
                    themeMode = themeMode,
                    dynamicColor = dynamicColor,
                    onLanguageChange = app::setLanguage,
                    onThemeModeChange = app::setThemeMode,
                    onDynamicColorChange = app::setDynamicColor
                )
            }
        }
    }
}
