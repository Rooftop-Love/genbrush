package com.example.genbrush.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.genbrush.ui.localization.AppStrings

sealed class Screen(
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    private val titleKey: (AppStrings) -> String
) {
    fun title(strings: AppStrings) = titleKey(strings)

    data object TextToImage : Screen(
        route = "text_to_image",
        selectedIcon = Icons.Rounded.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome,
        titleKey = { it.navGenerate }
    )

    data object ImageEdit : Screen(
        route = "image_edit",
        selectedIcon = Icons.Rounded.AutoFixHigh,
        unselectedIcon = Icons.Outlined.AutoFixHigh,
        titleKey = { it.navEdit }
    )

    data object Gallery : Screen(
        route = "gallery",
        selectedIcon = Icons.Rounded.PhotoLibrary,
        unselectedIcon = Icons.Outlined.PhotoLibrary,
        titleKey = { it.navGallery }
    )

    data object Settings : Screen(
        route = "settings",
        selectedIcon = Icons.Rounded.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        titleKey = { it.navSettings }
    )

    companion object {
        const val ROUTE_FULL_VIEWER = "gallery/image/{imageId}"
        const val ROUTE_MODEL_MANAGEMENT = "settings/models"

        fun fullViewerRoute(imageId: String): String = "gallery/image/$imageId"
    }
}
