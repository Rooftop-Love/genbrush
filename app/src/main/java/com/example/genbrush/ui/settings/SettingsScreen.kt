package com.example.genbrush.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.genbrush.data.local.AppLogStore
import com.example.genbrush.data.local.DataMigrationManager
import com.example.genbrush.data.local.PreferencesManager
import com.example.genbrush.ui.components.ModelSelector
import com.example.genbrush.ui.components.PRESET_EDIT_MODELS
import com.example.genbrush.ui.components.PRESET_GENERATION_MODELS
import com.example.genbrush.ui.components.SizeSelector
import com.example.genbrush.ui.components.buildAvailableModels
import com.example.genbrush.ui.localization.AppStrings
import com.example.genbrush.ui.localization.LocalStrings
import kotlinx.coroutines.launch

// ── 可折叠分组面板 ─────────────────────────────────────────────

@Composable
private fun SettingsSectionCard(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "sectionArrow"
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(rotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    content()
                }
            }
        }
    }
}

// ── 模型列表子区块 ─────────────────────────────────────────────

@Composable
private fun ModelListSection(
    presetModels: List<String>,
    disabledModels: Set<String>,
    customModels: List<String>,
    customModelInput: String,
    onCustomInputChanged: (String) -> Unit,
    onTogglePreset: (String) -> Unit,
    onAddCustom: () -> Unit,
    onRemoveCustom: (String) -> Unit,
    s: AppStrings
) {
    Text(
        s.settingsPresetModels,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(4.dp))
    presetModels.forEach { model ->
        val isEnabled = model !in disabledModels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = isEnabled, onValueChange = { onTogglePreset(model) })
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = isEnabled, onCheckedChange = null, modifier = Modifier.size(32.dp))
            Text(
                model,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        s.settingsCustomModels,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(4.dp))
    customModels.forEach { model ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                model,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f).padding(start = 38.dp)
            )
            IconButton(
                onClick = { onRemoveCustom(model) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = s.settingsRemoveModel,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = customModelInput,
            onValueChange = onCustomInputChanged,
            placeholder = { Text(s.settingsCustomModelHint, style = MaterialTheme.typography.bodySmall) },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = onAddCustom, modifier = Modifier.height(56.dp)) {
            Text(s.settingsAddButton)
        }
    }
}

// ── 模型管理二级页面 ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelManagementPage(
    state: SettingsState,
    viewModel: SettingsViewModel,
    s: AppStrings,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(s.settingsModelManagement) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.viewerBack)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // 生成模型
            SettingsSectionCard(
                title = s.sectionGenModels,
                expanded = true,
                onToggle = {}
            ) {
                ModelListSection(
                    presetModels = PRESET_GENERATION_MODELS,
                    disabledModels = state.disabledGenerationModels,
                    customModels = state.customGenerationModels,
                    customModelInput = state.customGenModelInput,
                    onCustomInputChanged = viewModel::updateCustomGenModelInput,
                    onTogglePreset = viewModel::toggleGenerationModel,
                    onAddCustom = viewModel::addCustomGenerationModel,
                    onRemoveCustom = viewModel::removeCustomGenerationModel,
                    s = s
                )
            }

            // 编辑模型
            SettingsSectionCard(
                title = s.sectionEditModels,
                expanded = true,
                onToggle = {}
            ) {
                ModelListSection(
                    presetModels = PRESET_EDIT_MODELS,
                    disabledModels = state.disabledEditModels,
                    customModels = state.customEditModels,
                    customModelInput = state.customEditModelInput,
                    onCustomInputChanged = viewModel::updateCustomEditModelInput,
                    onTogglePreset = viewModel::toggleEditModel,
                    onAddCustom = viewModel::addCustomEditModel,
                    onRemoveCustom = viewModel::removeCustomEditModel,
                    s = s
                )
            }

            // 错误提示
            state.modelManageError?.let { error ->
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

// ── 设置主页面 ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onLanguageChange: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val s = LocalStrings.current
    val isDashScope = state.backend == PreferencesManager.BACKEND_DASHSCOPE
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val migrationManager = remember { DataMigrationManager(context) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.setMigrating(true)
            viewModel.setMigrationMessage(null)
            scope.launch {
                try {
                    val outputStream = context.contentResolver.openOutputStream(uri)!!
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
                    val inputStream = context.contentResolver.openInputStream(uri)!!
                    val result = migrationManager.importData(inputStream)
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
        state.migrationMessage?.let { snackbarHostState.showSnackbar(it); viewModel.setMigrationMessage(null) }
    }

    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) snackbarHostState.showSnackbar(s.settingsSaved)
    }

    // ── 模型管理二级页面（覆盖显示）──
    if (state.showModelManagement) {
        ModelManagementPage(
            state = state,
            viewModel = viewModel,
            s = s,
            onBack = viewModel::closeModelManagement
        )
        SnackbarHost(hostState = snackbarHostState)
        return
    }

    // ── 主设置页面 ──
    val selectedTabIndex = if (isDashScope) 0 else 1

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(s.settingsTitle) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        // ── 后端标签页 ──
        PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
            Tab(
                selected = isDashScope,
                onClick = { viewModel.selectBackend(PreferencesManager.BACKEND_DASHSCOPE) },
                text = { Text(s.tabDashScope) }
            )
            Tab(
                selected = !isDashScope,
                onClick = { viewModel.selectBackend(PreferencesManager.BACKEND_SD_WEBUI) },
                text = { Text(s.tabSdWebUI) }
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // ── DashScope 标签页内容 ──
            AnimatedVisibility(visible = isDashScope) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // API 配置
                    SettingsSectionCard(
                        title = s.settingsApiConfig,
                        expanded = SECTION_API_CONFIG in state.expandedSections,
                        onToggle = { viewModel.toggleSection(SECTION_API_CONFIG) }
                    ) {
                        OutlinedTextField(
                            value = state.apiKey,
                            onValueChange = viewModel::updateApiKey,
                            label = { Text(s.settingsApiKeyLabel) },
                            placeholder = { Text(s.settingsApiPlaceholder) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (state.isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = viewModel::toggleApiKeyVisibility) {
                                    Icon(
                                        if (state.isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (state.isApiKeyVisible) s.settingsHide else s.settingsShow
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(s.settingsApiKeyHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // 模型管理入口（二级菜单）
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.openModelManagement() },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    s.settingsModelManagement,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    s.settingsManageModels,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── SD WebUI 标签页内容 ──
            AnimatedVisibility(visible = !isDashScope) {
                SettingsSectionCard(
                    title = s.settingsSdServerConfig,
                    expanded = SECTION_SD_SERVER in state.expandedSections,
                    onToggle = { viewModel.toggleSection(SECTION_SD_SERVER) }
                ) {
                    OutlinedTextField(
                        value = state.sdServerUrl,
                        onValueChange = viewModel::updateSdServerUrl,
                        label = { Text(s.settingsSdServerUrl) },
                        placeholder = { Text("http://192.168.x.x:7860") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(s.settingsSdServerHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = viewModel::testSdConnection, enabled = !state.isTestingConnection) {
                            if (state.isTestingConnection) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(s.settingsSdTestConnection)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        state.sdConnectionStatus?.let { status ->
                            when (status) {
                                "ok" -> {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(s.settingsSdConnected, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                                else -> {
                                    Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(status.removePrefix("fail:"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            // ── 默认设置 ──
            SettingsSectionCard(
                title = s.settingsDefaults,
                expanded = SECTION_DEFAULTS in state.expandedSections,
                onToggle = { viewModel.toggleSection(SECTION_DEFAULTS) }
            ) {
                val configuredModels = buildAvailableModels(
                    PRESET_GENERATION_MODELS,
                    state.disabledGenerationModels,
                    state.customGenerationModels
                )
                if (configuredModels.isNotEmpty()) {
                    ModelSelector(
                        selectedModel = if (state.defaultModel in configuredModels) state.defaultModel else configuredModels.first(),
                        onModelSelected = viewModel::selectModel,
                        label = s.settingsDefaultModel,
                        models = configuredModels
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                SizeSelector(
                    selectedSize = state.defaultSize,
                    onSizeSelected = viewModel::selectSize,
                    label = s.settingsDefaultSize,
                    sizeOptions = state.availableSizes
                )
            }

            // ── 语言 ──
            SettingsSectionCard(
                title = s.settingsLanguage,
                expanded = SECTION_LANGUAGE in state.expandedSections,
                onToggle = { viewModel.toggleSection(SECTION_LANGUAGE) }
            ) {
                listOf(
                    PreferencesManager.LANGUAGE_SYSTEM to "跟随系统 (Follow System)",
                    PreferencesManager.LANGUAGE_ZH to "中文",
                    PreferencesManager.LANGUAGE_EN to "English"
                ).forEach { (code, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().selectable(
                            selected = state.language == code,
                            onClick = { viewModel.selectLanguage(code); onLanguageChange(code) }
                        ).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = state.language == code,
                            onClick = { viewModel.selectLanguage(code); onLanguageChange(code) }
                        )
                        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            // ── 保存按钮 ──
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text(s.settingsSave)
            }

            // ── 数据迁移 ──
            SettingsSectionCard(
                title = s.migrationTitle,
                expanded = SECTION_MIGRATION in state.expandedSections,
                onToggle = { viewModel.toggleSection(SECTION_MIGRATION) }
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch("GenBrush_backup.zip") },
                        enabled = !state.isMigrating,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (state.isMigrating) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(s.migrationExport)
                    }
                    Button(
                        onClick = { importLauncher.launch(arrayOf("application/zip")) },
                        enabled = !state.isMigrating,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(s.migrationImport)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { logExportLauncher.launch("GenBrush_logs.txt") },
                    enabled = !state.isMigrating && !state.isExportingLogs,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isExportingLogs) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(s.logExport)
                }
            }

            // ── 关于 ──
            SettingsSectionCard(
                title = s.settingsAbout,
                expanded = SECTION_ABOUT in state.expandedSections,
                onToggle = { viewModel.toggleSection(SECTION_ABOUT) }
            ) {
                Text("GenBrush", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text(s.settingsAboutDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(2.dp))
                Text("${s.settingsVersion} v1.0.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
    SnackbarHost(hostState = snackbarHostState)
}
