package com.example.genbrush.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.genbrush.BuildConfig
import com.example.genbrush.R
import com.example.genbrush.data.local.AppLogStore
import com.example.genbrush.data.local.DataMigrationManager
import com.example.genbrush.data.local.ModelCapability
import com.example.genbrush.data.local.ModelCatalogRules
import com.example.genbrush.data.local.PreferencesManager
import com.example.genbrush.data.provider.ProviderDefinition
import com.example.genbrush.ui.components.AspectRatioSizeSheet
import com.example.genbrush.ui.components.ModelPickerSheet
import com.example.genbrush.ui.components.SizeOptionParser
import com.example.genbrush.ui.components.StudioHeader
import com.example.genbrush.ui.localization.LocalStrings
import com.example.genbrush.ui.theme.studioColors
import com.example.genbrush.ui.theme.tabular
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    themeMode: String,
    dynamicColor: Boolean,
    onLanguageChange: (String) -> Unit,
    onThemeModeChange: (String) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onOpenModelManagement: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val s = LocalStrings.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val migrationManager = remember { DataMigrationManager(context) }

    var showProviderSheet by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }
    var showSizeSheet by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.setMigrating(true)
            viewModel.setMigrationMessage(null)
            scope.launch {
                try {
                    val outputStream = context.contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException("Unable to open output stream")
                    val count = migrationManager.exportData(outputStream)
                    viewModel.setMigrationMessage(s.migrationExportSuccess.format(count))
                } catch (e: Exception) {
                    viewModel.setMigrationMessage(s.migrationError + (e.message ?: "Unknown"))
                } finally {
                    viewModel.setMigrating(false)
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.setMigrating(true)
            viewModel.setMigrationMessage(null)
            scope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Unable to open input stream")
                    val result = migrationManager.importData(inputStream)
                    viewModel.reload()
                    val (importedLang, importedTheme, importedDynColor) = viewModel.readImportedAppearance()
                    onLanguageChange(importedLang)
                    onThemeModeChange(importedTheme)
                    onDynamicColorChange(importedDynColor)
                    viewModel.setMigrationMessage(s.migrationImportSuccess.format(result.imageCount))
                } catch (e: Exception) {
                    viewModel.setMigrationMessage(s.migrationError + (e.message ?: "Unknown"))
                } finally {
                    viewModel.setMigrating(false)
                }
            }
        }
    }

    val logExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            viewModel.setExportingLogs(true)
            viewModel.setMigrationMessage(null)
            scope.launch {
                try {
                    AppLogStore.info("Recent logs export requested")
                    val outputStream = context.contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException("Unable to open output file")
                    AppLogStore.exportRecent(outputStream)
                    viewModel.setMigrationMessage(s.logExportSuccess)
                } catch (e: Exception) {
                    viewModel.setMigrationMessage(s.migrationError + (e.message ?: "Unknown"))
                } finally {
                    viewModel.setExportingLogs(false)
                }
            }
        }
    }

    LaunchedEffect(state.migrationMessage) {
        state.migrationMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.setMigrationMessage(null)
        }
    }

    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            snackbarHostState.showSnackbar(s.settingsSaved)
            viewModel.consumeSaveSuccess()
        }
    }

    val currentProvider = state.providers.firstOrNull { it.id == state.providerId }
    val providerName = currentProvider?.displayName(state.language) ?: state.providerId
    val configuredGenModels = ModelCatalogRules.visibleIds(
        state.modelCatalog,
        ModelCapability.GENERATION
    )
    val totalCatalogModels = state.modelCatalog.models.size
    val enabledCatalogModels = state.modelCatalog.models.count {
        it.enabledGeneration || (state.supportsImageEdit && it.enabledEdit)
    }

    if (showProviderSheet) {
        ProviderPickerSheet(
            providers = state.providers,
            selectedId = state.providerId,
            configuredIds = state.configuredProviderIds,
            language = state.language,
            onProviderSelected = viewModel::selectProvider,
            onDismiss = { showProviderSheet = false }
        )
    }

    if (showModelSheet) {
        ModelPickerSheet(
            selectedModel = if (state.defaultModel in configuredGenModels) {
                state.defaultModel
            } else {
                configuredGenModels.firstOrNull().orEmpty()
            },
            models = configuredGenModels,
            providerName = providerName,
            onModelSelected = viewModel::selectModel,
            onManageModels = onOpenModelManagement,
            onDismiss = { showModelSheet = false }
        )
    }

    if (showSizeSheet) {
        AspectRatioSizeSheet(
            selectedSize = state.defaultSize,
            sizeOptions = state.availableSizes,
            onSizeSelected = viewModel::selectSize,
            onDismiss = { showSizeSheet = false }
        )
    }

    if (showLanguageSheet) {
        LanguagePickerSheet(
            selectedLanguage = state.language,
            onLanguageSelected = { code ->
                viewModel.selectLanguage(code)
                onLanguageChange(code)
            },
            onDismiss = { showLanguageSheet = false }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            StudioHeader(title = s.settingsTitle)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // ── Section 1: AI Service & Defaults ──
                SettingsGroup(title = s.settingsImageService) {
                    val isConfigured = state.providerId in state.configuredProviderIds
                    SettingsNavRow(
                        icon = Icons.Rounded.Cloud,
                        title = s.settingsProvider,
                        value = providerName,
                        badgeText = if (isConfigured) s.settingsConfigured else s.settingsNotConfigured,
                        badgePositive = isConfigured,
                        onClick = { showProviderSheet = true }
                    )

                    SettingsInsetDivider()

                    // Inline API Key Editor
                    ApiKeyRow(
                        apiKey = state.apiKey,
                        isVisible = state.isApiKeyVisible,
                        isDirty = state.apiKeyDirty,
                        isSavedConfigured = isConfigured && !state.apiKeyDirty,
                        hint = currentProvider?.apiKeyHint(state.language)?.ifBlank { null }
                            ?: s.settingsApiKeyHint,
                        onValueChange = viewModel::updateApiKey,
                        onToggleVisibility = viewModel::toggleApiKeyVisibility,
                        onSave = {
                            focusManager.clearFocus()
                            viewModel.saveApiKey()
                        }
                    )

                    SettingsInsetDivider()

                    SettingsNavRow(
                        icon = Icons.Rounded.Tune,
                        title = s.settingsModelManagement,
                        value = s.settingsModelsEnabledSummary.format(
                            enabledCatalogModels,
                            totalCatalogModels
                        ),
                        tabularValue = true,
                        onClick = onOpenModelManagement
                    )

                    if (configuredGenModels.isNotEmpty()) {
                        SettingsInsetDivider()
                        SettingsNavRow(
                            icon = Icons.Rounded.AutoAwesome,
                            title = s.settingsDefaultModel,
                            value = if (state.defaultModel in configuredGenModels) {
                                state.defaultModel
                            } else {
                                configuredGenModels.first()
                            },
                            onClick = { showModelSheet = true }
                        )
                    }

                    SettingsInsetDivider()

                    SettingsNavRow(
                        icon = Icons.Rounded.AspectRatio,
                        title = s.settingsDefaultSize,
                        value = SizeOptionParser.compactLabel(
                            selectedSize = state.defaultSize,
                            options = state.availableSizes
                        ),
                        tabularValue = true,
                        onClick = { showSizeSheet = true }
                    )
                }

                // ── Section 2: Appearance & Language ──
                SettingsGroup(title = s.settingsAppearance) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SettingsRowIcon(icon = Icons.Rounded.Palette)
                            Text(
                                text = s.settingsTheme,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        ThemeModeSegmentedControl(
                            selectedMode = themeMode,
                            onModeSelected = onThemeModeChange
                        )
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SettingsInsetDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDynamicColorChange(!dynamicColor) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = s.settingsDynamicColor,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = s.settingsDynamicColorDesc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = dynamicColor,
                                onCheckedChange = onDynamicColorChange
                            )
                        }
                    }

                    SettingsInsetDivider()

                    val languageDisplay = when (state.language) {
                        PreferencesManager.LANGUAGE_ZH -> "中文"
                        PreferencesManager.LANGUAGE_EN -> "English"
                        else -> s.settingsLanguageSystem
                    }
                    SettingsNavRow(
                        icon = Icons.Rounded.Language,
                        title = s.settingsLanguage,
                        value = languageDisplay,
                        onClick = { showLanguageSheet = true }
                    )
                }

                // ── Section 3: Data & Diagnostics ──
                SettingsGroup(title = s.settingsDataAndLogs) {
                    SettingsActionRow(
                        icon = Icons.Rounded.Upload,
                        title = s.migrationExport,
                        subtitle = s.settingsExportDesc,
                        isLoading = state.isMigrating,
                        enabled = !state.isMigrating,
                        onClick = { exportLauncher.launch("GenBrush_backup.zip") }
                    )
                    SettingsInsetDivider()
                    SettingsActionRow(
                        icon = Icons.Rounded.Download,
                        title = s.migrationImport,
                        subtitle = s.settingsImportDesc,
                        isLoading = false,
                        enabled = !state.isMigrating,
                        onClick = { importLauncher.launch(arrayOf("application/zip")) }
                    )
                    SettingsInsetDivider()
                    SettingsActionRow(
                        icon = Icons.Rounded.Description,
                        title = s.logExport,
                        subtitle = s.settingsLogExportDesc,
                        isLoading = state.isExportingLogs,
                        enabled = !state.isMigrating && !state.isExportingLogs,
                        onClick = { logExportLauncher.launch("GenBrush_logs.txt") }
                    )
                }

                // ── Section 4: About ──
                SettingsGroup(title = s.settingsAbout) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, MaterialTheme.studioColors.hairline),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "GenBrush",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = s.settingsAboutDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.studioColors.hairline)
                        ) {
                            Text(
                                text = "v${BuildConfig.APP_VERSION}",
                                style = MaterialTheme.typography.labelMedium.tabular(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.studioColors.hairline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun SettingsInsetDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.studioColors.hairline,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun SettingsRowIcon(icon: ImageVector) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.size(34.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    badgeText: String? = null,
    badgePositive: Boolean = false,
    tabularValue: Boolean = false
) {
    val studioColors = MaterialTheme.studioColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsRowIcon(icon = icon)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (badgeText != null) {
            Surface(
                shape = CircleShape,
                color = if (badgePositive) {
                    studioColors.successContainer
                } else {
                    studioColors.warningContainer
                }
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (badgePositive) studioColors.success else studioColors.warning,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Text(
            text = value,
            style = if (tabularValue) {
                MaterialTheme.typography.bodyMedium.tabular()
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsRowIcon(icon = icon)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ApiKeyRow(
    apiKey: String,
    isVisible: Boolean,
    isDirty: Boolean,
    isSavedConfigured: Boolean,
    hint: String,
    onValueChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onSave: () -> Unit
) {
    val s = LocalStrings.current
    val studioColors = MaterialTheme.studioColors
    var isFocused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
        } else {
            studioColors.hairline
        },
        animationSpec = tween(180),
        label = "apiKeyBorder"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = s.settingsApiKeyLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            when {
                isDirty -> {
                    Surface(
                        onClick = onSave,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = s.commonConfirm,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                isSavedConfigured -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = studioColors.success,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = s.settingsApiKeySaved,
                            style = MaterialTheme.typography.labelSmall,
                            color = studioColors.success
                        )
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (apiKey.isEmpty()) {
                        Text(
                            text = s.settingsApiPlaceholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    BasicTextField(
                        value = apiKey,
                        onValueChange = onValueChange,
                        singleLine = true,
                        visualTransformation = if (isVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { onSave() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isFocused = it.isFocused }
                    )
                }
                IconButton(
                    onClick = onToggleVisibility,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (isVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (isVisible) s.settingsHide else s.settingsShow,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThemeModeSegmentedControl(
    selectedMode: String,
    onModeSelected: (String) -> Unit
) {
    val s = LocalStrings.current
    val options = listOf(
        PreferencesManager.THEME_SYSTEM to s.settingsThemeSystem,
        PreferencesManager.THEME_LIGHT to s.settingsThemeLight,
        PreferencesManager.THEME_DARK to s.settingsThemeDark
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (mode, label) ->
            val selected = mode == selectedMode
            Surface(
                onClick = { onModeSelected(mode) },
                shape = RoundedCornerShape(9.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.surface
                } else {
                    Color.Transparent
                },
                border = if (selected) {
                    BorderStroke(1.dp, MaterialTheme.studioColors.hairline)
                } else {
                    null
                },
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderPickerSheet(
    providers: List<ProviderDefinition>,
    selectedId: String,
    configuredIds: Set<String>,
    language: String,
    onProviderSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalStrings.current
    val studioColors = MaterialTheme.studioColors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = s.studioSelectProvider,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            providers.forEach { provider ->
                val selected = provider.id == selectedId
                val configured = provider.id in configuredIds
                Surface(
                    onClick = {
                        onProviderSelected(provider.id)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        } else {
                            studioColors.hairline
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = provider.displayName(language),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (configured) studioColors.success else studioColors.warning
                                        )
                                )
                                Text(
                                    text = if (configured) s.settingsConfigured else s.settingsNotConfigured,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (configured) studioColors.success else studioColors.warning
                                )
                            }
                        }
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePickerSheet(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalStrings.current
    val options = listOf(
        PreferencesManager.LANGUAGE_SYSTEM to s.settingsLanguageSystem,
        PreferencesManager.LANGUAGE_ZH to "中文",
        PreferencesManager.LANGUAGE_EN to "English"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = s.settingsLanguage,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            options.forEach { (code, label) ->
                val selected = code == selectedLanguage
                Surface(
                    onClick = {
                        onLanguageSelected(code)
                        onDismiss()
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.studioColors.hairline
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
