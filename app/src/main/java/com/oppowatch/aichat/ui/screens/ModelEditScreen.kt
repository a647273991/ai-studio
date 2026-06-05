package com.oppowatch.aichat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oppowatch.aichat.data.entity.ModelConfig
import com.oppowatch.aichat.ui.theme.GlassBorder
import com.oppowatch.aichat.ui.theme.DeepSurfaceVariant
import com.oppowatch.aichat.ui.theme.Error
import com.oppowatch.aichat.viewmodel.ConfigViewModel

// ────────────────────────────────────────────────────────────
// 模型编辑页 — 新建 / 编辑 AI 模型配置
// 针对 OPPO Watch SE 372×430 AMOLED 小屏适配
// ────────────────────────────────────────────────────────────

/**
 * 最大 Token 候选值列表。
 * 手表小屏场景推荐较小的值以控制响应长度。
 */
private val MAX_TOKEN_OPTIONS = listOf(256, 512, 1024, 2048, 4096, 8192)

/**
 * 模型编辑 / 新建页面。
 *
 * 表单字段：
 *  - 名称（name）
 *  - API 地址（apiUrl）
 *  - API Key（apiKey）
 *  - 模型名称（modelName）
 *  - Temperature 滑条（0.0 ~ 2.0）
 *  - Max Tokens 下拉选择
 *  - Stream 开关
 *
 * @param modelId  非 null 表示编辑已有模型；null 表示新建。
 * @param onBack   返回上一页（模型列表）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelEditScreen(
    modelId: Long? = null,
    onBack: () -> Unit,
    viewModel: ConfigViewModel = viewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    // ── 表单状态 ──────────────────────────────────────────
    var name by remember { mutableStateOf("") }
    var apiUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }
    var temperature by remember { mutableFloatStateOf(0.7f) }
    var maxTokens by remember { mutableIntStateOf(2048) }
    var streamEnabled by remember { mutableStateOf(false) }

    // API Key 可见性
    var apiKeyVisible by remember { mutableStateOf(false) }

    // Max Tokens 下拉展开
    var maxTokensDropdownExpanded by remember { mutableStateOf(false) }

    // 正在编辑的原始实体（编辑模式时）
    var editingConfig by remember { mutableStateOf<ModelConfig?>(null) }
    var isLoaded by remember { mutableStateOf(false) }

    // ── 加载已有模型数据 ──────────────────────────────────
    LaunchedEffect(modelId) {
        if (modelId != null && modelId > 0) {
            viewModel.getById(modelId) { config ->
                if (config != null) {
                    editingConfig = config
                    name = config.name
                    apiUrl = config.apiUrl
                    apiKey = config.apiKey
                    modelName = config.modelName
                    temperature = config.temperature.toFloat()
                    maxTokens = config.maxTokens
                    // stream 非实体字段，编辑时默认 false
                }
                isLoaded = true
            }
        } else {
            isLoaded = true
        }
    }

    // ── 工具方法 ──────────────────────────────────────────
    fun save() {
        // 隐藏键盘
        focusManager.clearFocus()

        val config = ModelConfig(
            id = editingConfig?.id ?: 0,
            name = name.trim(),
            apiUrl = apiUrl.trim(),
            apiKey = apiKey.trim(),
            modelName = modelName.trim(),
            temperature = temperature.toDouble(),
            maxTokens = maxTokens,
            systemPrompt = editingConfig?.systemPrompt ?: "",
            isActive = editingConfig?.isActive ?: false,
            createdAt = editingConfig?.createdAt ?: System.currentTimeMillis()
        )

        if (editingConfig != null) {
            viewModel.update(config)
        } else {
            viewModel.insert(config)
        }
        onBack()
    }

    fun delete() {
        editingConfig?.let { config ->
            viewModel.delete(config)
            onBack()
        }
    }

    // ── UI ────────────────────────────────────────────────

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (editingConfig != null) "编辑模型" else "新建模型",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->

        if (!isLoaded) {
            // 加载中占位
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── 名称 ─────────────────────────────────────
            FormTextField(
                label = "名称",
                value = name,
                onValueChange = { name = it },
                placeholder = "例如：阿喵、GPT-4o",
                imeAction = ImeAction.Next
            )

            // ── API 地址 ─────────────────────────────────
            FormTextField(
                label = "API 地址",
                value = apiUrl,
                onValueChange = { apiUrl = it },
                placeholder = "https://api.example.com/v1/chat/completions",
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next
            )

            // ── API Key ──────────────────────────────────
            FormTextField(
                label = "API Key",
                value = apiKey,
                onValueChange = { apiKey = it },
                placeholder = "sk-xxxxxxxxxxxxxxxx",
                visualTransformation = if (apiKeyVisible)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(
                        onClick = { apiKeyVisible = !apiKeyVisible },
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(
                            text = if (apiKeyVisible) "隐藏" else "显示",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                imeAction = ImeAction.Next
            )

            // ── 模型名称 ─────────────────────────────────
            FormTextField(
                label = "模型名称",
                value = modelName,
                onValueChange = { modelName = it },
                placeholder = "例如：gpt-4o-mini、deepseek-chat",
                imeAction = ImeAction.Done
            )

            // ── Temperature 滑条 ─────────────────────────
            TemperatureSlider(
                value = temperature,
                onValueChange = { temperature = it }
            )

            // ── Max Tokens 下拉 ──────────────────────────
            MaxTokensDropdown(
                selected = maxTokens,
                expanded = maxTokensDropdownExpanded,
                onToggle = { maxTokensDropdownExpanded = !maxTokensDropdownExpanded },
                onDismiss = { maxTokensDropdownExpanded = false },
                onSelect = {
                    maxTokens = it
                    maxTokensDropdownExpanded = false
                }
            )

            // ── Stream 开关 ──────────────────────────────
            StreamSwitch(
                enabled = streamEnabled,
                onToggle = { streamEnabled = !streamEnabled }
            )

            Spacer(modifier = Modifier.height(6.dp))

            // ── 保存按钮 ─────────────────────────────────
            Button(
                onClick = { save() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                enabled = name.isNotBlank() && apiUrl.isNotBlank() && modelName.isNotBlank(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "保存",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // ── 删除按钮（仅编辑模式） ───────────────────
            if (editingConfig != null) {
                OutlinedButton(
                    onClick = { delete() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Error
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Error.copy(alpha = 0.5f))
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "删除",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 底部留白，避免被系统导航遮挡
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  表单子组件
// ═══════════════════════════════════════════════════════════════

/**
 * 通用表单输入框，手表适配：
 *  - 标签 12sp
 *  - 输入文字 13sp
 *  - 最小高度 48dp 保证可触摸
 */
@Composable
private fun FormTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
    imeAction: ImeAction = ImeAction.Next
) {
    val focusManager = LocalFocusManager.current

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = placeholder,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            singleLine = true,
            visualTransformation = visualTransformation,
            trailingIcon = trailingIcon,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) },
                onDone = { focusManager.clearFocus() }
            ),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                unfocusedBorderColor = GlassBorder.copy(alpha = 0.3f),
                focusedContainerColor = DeepSurfaceVariant.copy(alpha = 0.4f),
                unfocusedContainerColor = DeepSurfaceVariant.copy(alpha = 0.2f),
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

/**
 * Temperature 滑条组件。
 *
 * 范围 0.0 ~ 2.0，步进 0.1。
 * 手表适配：紧凑高度，当前值显示在滑条右侧。
 */
@Composable
private fun TemperatureSlider(
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Temperature",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = String.format("%.1f", value),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = { onValueChange((it * 10).roundToInt() / 10f) },
            valueRange = 0f..2f,
            steps = 19, // 0.1 step: 20 intervals → 19 steps
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                activeTickColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                inactiveTickColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
        // 刻度标签
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("0.0", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            Text("1.0", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            Text("2.0", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}

/**
 * Max Tokens 下拉选择组件。
 *
 * 手表适配：使用 ExposedDropdownMenuBox 实现触控友好的选择。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaxTokensDropdown(
    selected: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = "Max Tokens",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (it) onToggle() else onDismiss() }
        ) {
            OutlinedTextField(
                value = selected.toString(),
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "展开",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    unfocusedBorderColor = GlassBorder.copy(alpha = 0.3f),
                    focusedContainerColor = DeepSurfaceVariant.copy(alpha = 0.4f),
                    unfocusedContainerColor = DeepSurfaceVariant.copy(alpha = 0.2f),
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = onDismiss,
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                MAX_TOKEN_OPTIONS.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option.toString(),
                                fontSize = 13.sp,
                                fontWeight = if (option == selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (option == selected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = { onSelect(option) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Stream 流式响应开关。
 *
 * 手表适配：整行可点击，开关在右侧。
 */
@Composable
private fun StreamSwitch(
    enabled: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DeepSurfaceVariant.copy(alpha = 0.2f))
            .border(
                width = 0.5.dp,
                color = GlassBorder.copy(alpha = 0.25f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Stream 流式响应",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (enabled) "逐字显示 AI 回复" else "等待完整回复后显示",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}
