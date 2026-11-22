package com.example.genbrush.ui.navigation

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.genbrush.data.local.PreferencesManager
import com.example.genbrush.data.provider.ProviderRegistry
import com.example.genbrush.data.repository.GenerationRepository
import com.example.genbrush.ui.gallery.FullImageViewerScreen
import com.example.genbrush.ui.gallery.GalleryScreen
import com.example.genbrush.ui.gallery.GalleryViewModel
import com.example.genbrush.ui.imageedit.ImageEditScreen
import com.example.genbrush.ui.imageedit.ImageEditViewModel
import com.example.genbrush.ui.localization.AppStrings
import com.example.genbrush.ui.localization.LocalStrings
import com.example.genbrush.ui.localization.resolveAppStrings
import com.example.genbrush.ui.settings.ModelManagementScreen
import com.example.genbrush.ui.settings.SettingsScreen
import com.example.genbrush.ui.settings.SettingsViewModel
import com.example.genbrush.ui.texttoimage.TextToImageScreen
import com.example.genbrush.ui.texttoimage.TextToImageViewModel
import com.example.genbrush.ui.theme.studioColors
import java.io.File

private data class PendingRegenerate(
    val prompt: String,
    val negativePrompt: String?,
    val model: String,
    val size: String?
)

@Composable
fun AppNavigation(
    prefs: PreferencesManager,
    repository: GenerationRepository,
    registry: ProviderRegistry,
    language: String,
    themeMode: String,
    dynamicColor: Boolean,
    onLanguageChange: (String) -> Unit,
    onThemeModeChange: (String) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit
) {
    val strings = resolveAppStrings(language)

    CompositionLocalProvider(LocalStrings provides strings) {
        val navController = rememberNavController()
        val bottomBarScreens = listOf(
            Screen.TextToImage,
            Screen.ImageEdit,
            Screen.Gallery,
            Screen.Settings
        )
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        var pendingRegenerate by remember { mutableStateOf<PendingRegenerate?>(null) }
        var pendingEditImagePath by remember { mutableStateOf<String?>(null) }

        val navigateToTab: (String) -> Unit = { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (currentRoute in bottomBarScreens.map { it.route }) {
                    StudioBottomBar(
                        screens = bottomBarScreens,
                        currentRoute = currentRoute,
                        strings = strings,
                        onSelect = navigateToTab
                    )
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.TextToImage.route,
                modifier = Modifier
                    .padding(bottom = innerPadding.calculateBottomPadding())
                    .consumeWindowInsets(innerPadding),
                enterTransition = { fadeIn(tween(200)) },
                exitTransition = { fadeOut(tween(150)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = { fadeOut(tween(150)) }
            ) {
                composable(Screen.TextToImage.route) {
                    val vm: TextToImageViewModel = viewModel(
                        factory = TextToImageViewModel.factory(repository, prefs, registry)
                    )
                    LaunchedEffect(pendingRegenerate) {
                        pendingRegenerate?.let { req ->
                            vm.applyRegenerate(
                                prompt = req.prompt,
                                negativePrompt = req.negativePrompt,
                                model = req.model,
                                size = req.size
                            )
                            pendingRegenerate = null
                        }
                    }
                    TextToImageScreen(
                        viewModel = vm,
                        onOpenViewer = { entryId ->
                            navController.navigate(Screen.fullViewerRoute(entryId))
                        },
                        onSendToEdit = { imagePath ->
                            pendingEditImagePath = imagePath
                            navigateToTab(Screen.ImageEdit.route)
                        },
                        onManageModels = {
                            navigateToModelsFromAnywhere(navController)
                        },
                        onNavigateToSettings = {
                            navigateToTab(Screen.Settings.route)
                        }
                    )
                }

                composable(Screen.ImageEdit.route) {
                    val vm: ImageEditViewModel = viewModel(
                        factory = ImageEditViewModel.factory(repository, prefs, registry)
                    )
                    LaunchedEffect(pendingEditImagePath) {
                        pendingEditImagePath?.let { path ->
                            vm.setSourceImage(Uri.fromFile(File(path)))
                            pendingEditImagePath = null
                        }
                    }
                    ImageEditScreen(
                        viewModel = vm,
                        onOpenViewer = { entryId ->
                            navController.navigate(Screen.fullViewerRoute(entryId))
                        },
                        onManageModels = {
                            navigateToModelsFromAnywhere(navController)
                        },
                        onNavigateToSettings = {
                            navigateToTab(Screen.Settings.route)
                        }
                    )
                }

                composable(Screen.Gallery.route) {
                    val vm: GalleryViewModel = viewModel(
                        factory = GalleryViewModel.factory(repository)
                    )
                    GalleryScreen(
                        viewModel = vm,
                        onImageClick = { entryId ->
                            navController.navigate(Screen.fullViewerRoute(entryId))
                        }
                    )
                }

                composable(Screen.Settings.route) {
                    val vm: SettingsViewModel = viewModel(
                        factory = SettingsViewModel.factory(prefs, registry)
                    )
                    SettingsScreen(
                        viewModel = vm,
                        themeMode = themeMode,
                        dynamicColor = dynamicColor,
                        onLanguageChange = onLanguageChange,
                        onThemeModeChange = onThemeModeChange,
                        onDynamicColorChange = onDynamicColorChange,
                        onOpenModelManagement = {
                            navController.navigate(Screen.ROUTE_MODEL_MANAGEMENT)
                        }
                    )
                }

                composable(
                    route = Screen.ROUTE_MODEL_MANAGEMENT,
                    enterTransition = {
                        slideInHorizontally(tween(240)) { it / 3 } + fadeIn(tween(240))
                    },
                    exitTransition = {
                        slideOutHorizontally(tween(220)) { it / 3 } + fadeOut(tween(180))
                    },
                    popEnterTransition = { fadeIn(tween(200)) },
                    popExitTransition = {
                        slideOutHorizontally(tween(240)) { it / 3 } + fadeOut(tween(200))
                    }
                ) { backStackEntry ->
                    val settingsEntry = remember(backStackEntry) {
                        runCatching { navController.getBackStackEntry(Screen.Settings.route) }.getOrNull()
                    }
                    val vm: SettingsViewModel = if (settingsEntry != null) {
                        viewModel(
                            viewModelStoreOwner = settingsEntry,
                            factory = SettingsViewModel.factory(prefs, registry)
                        )
                    } else {
                        viewModel(factory = SettingsViewModel.factory(prefs, registry))
                    }
                    ModelManagementScreen(
                        viewModel = vm,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Screen.ROUTE_FULL_VIEWER,
                    arguments = listOf(navArgument("imageId") { type = NavType.StringType }),
                    enterTransition = {
                        fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.95f)
                    },
                    exitTransition = {
                        fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.96f)
                    },
                    popEnterTransition = { fadeIn(tween(200)) },
                    popExitTransition = {
                        fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.95f)
                    }
                ) { backStackEntry ->
                    val imageId = backStackEntry.arguments?.getString("imageId") ?: return@composable
                    FullImageViewerScreen(
                        imageId = imageId,
                        repository = repository,
                        onBack = { navController.popBackStack() },
                        onRegenerate = { entry ->
                            pendingRegenerate = PendingRegenerate(
                                prompt = entry.prompt,
                                negativePrompt = entry.negativePrompt,
                                model = entry.model,
                                size = entry.size
                            )
                            navController.popBackStack()
                            navigateToTab(Screen.TextToImage.route)
                        },
                        onEditImage = { imagePath ->
                            pendingEditImagePath = imagePath
                            navController.popBackStack()
                            navigateToTab(Screen.ImageEdit.route)
                        }
                    )
                }
            }
        }
    }
}

private fun navigateToModelsFromAnywhere(navController: NavController) {
    navController.navigate(Screen.Settings.route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
    navController.navigate(Screen.ROUTE_MODEL_MANAGEMENT) {
        launchSingleTop = true
    }
}

@Composable
private fun StudioBottomBar(
    screens: List<Screen>,
    currentRoute: String?,
    strings: AppStrings,
    onSelect: (String) -> Unit
) {
    val studioColors = MaterialTheme.studioColors
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(thickness = 0.5.dp, color = studioColors.hairline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(60.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                screens.forEach { screen ->
                    val selected = currentRoute == screen.route
                    val label = screen.title(strings)
                    val interactionSource = remember { MutableInteractionSource() }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) { onSelect(screen.route) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .width(20.dp)
                                    .height(2.5.dp)
                                    .clip(CircleShape)
                                    .background(studioColors.auroraBrush)
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = label,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
