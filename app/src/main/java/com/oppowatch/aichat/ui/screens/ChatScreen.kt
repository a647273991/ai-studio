package com.oppowatch.aichat.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oppowatch.aichat.data.entity.Message
import com.oppowatch.aichat.ui.theme.DeepBackground
import com.oppowatch.aichat.ui.theme.DeepSurfaceVariant
import com.oppowatch.aichat.ui.theme.Error as ErrorColor
import com.oppowatch.aichat.ui.theme.GlassCard
import com.oppowatch.aichat.ui.theme.GlassCardAlt
import com.oppowatch.aichat.ui.theme.GlassBorder
import com.oppowatch.aichat.ui.theme.Purple400
import com.oppowatch.aichat.ui.theme.Purple500
import com.oppowatch.aichat.ui.theme.TextPrimary
import com.oppowatch.aichat.ui.theme.TextSecondary
import com.oppowatch.aichat.ui.theme.TextTertiary
import com.oppowatch.aichat.viewmodel.ChatViewModel

// =============================================================================
// ⚡ 预设快捷回复（手表小屏打字不便，一键填充输入框）
// =============================================================================

private val quickReplies = listOf(
    "👋 你好",
    "😂 讲个笑话",
    "❓ 这是什么",
    "💪 今天状态",
    "📖 讲个故事",
    "🎵 推荐首歌",
    "🌤️ 天气怎么样",
    "🍜 推荐美食",
)

// =============================================================================
// 🧩 ChatScreen — 手表聊天主界面（适配 372dp 宽度）
// =============================================================================

/**
 * 聊天主界面。
 *
 * 布局（纵轴）：
 * ┌──────────────────────────┐
 * │  模型名          ⚙️ 🆕  │ ← 顶部栏（48dp）
 * ├──────────────────────────┤
 * │                          │
 * │  ┌──────────────────┐   │
 * │  │ 用户消息气泡      │   │ ← LazyColumn 消息列表
 * │  │     AI 消息气泡   │   │   weight(1f) 占满剩余空间
 * │  └──────────────────┘   │
 * │                          │
 * ├──────────────────────────┤
 * │ [快捷回复1][快捷回复2]…  │ ← 横向滚动的快捷回复条
 * │ [____输入框____][➤发送] │ ← 输入行
 * └──────────────────────────┘
 *
 * @param onNavigateToSettings 点击齿轮 → 设置页。
 * @param onNavigateToModelList 点击模型名 → 模型列表页。
 */
@Composable
fun ChatScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToModelList: () -> Unit,
    chatViewModel: ChatViewModel = viewModel(),
) {
    val messages by chatViewModel.messages.collectAsState()
    val currentModelConfig by chatViewModel.currentModelConfig.collectAsState()
    val isLoading by chatViewModel.isLoading.collectAsState()
    val errorMessage by chatViewModel.errorMessage.collectAsState()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    // ── 自动滚动到底部（新消息到达时）──
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // ── 自动创建对话（如果没有当前对话且已有模型配置）──
    LaunchedEffect(currentModelConfig) {
        if (currentModelConfig != null && chatViewModel.currentConversation.collectAsState().value == null) {
            chatViewModel.createConversation()
        }
    }

    // ── 错误提示自动消失 ──
    LaunchedEffect(errorMessage) {
        // errorMessage 由 ViewModel 管理，这里只是占位；若需要自动清除，
        // 可在 ViewModel 中增加延迟清除逻辑。
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBackground),
    ) {
        // ════════════════════════════════════════════════════
        // 🔝 顶部栏：模型名 + 齿轮 + 新建对话
        // ════════════════════════════════════════════════════
        ChatTopBar(
            modelName = currentModelConfig?.name ?: "未配置模型",
            onModelNameClick = onNavigateToModelList,
            onSettingsClick = onNavigateToSettings,
            onNewChatClick = { chatViewModel.createConversation() },
        )

        // ════════════════════════════════════════════════════
        // ❗ 错误提示条
        // ════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = errorMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            errorMessage?.let { err ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = ErrorColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(0.dp),
                ) {
                    Text(
                        text = err,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // ════════════════════════════════════════════════════
        // 💬 中间：消息列表 LazyColumn
        // ════════════════════════════════════════════════════
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (messages.isEmpty() && !isLoading) {
                // 空状态引导
                EmptyChatHint(
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(
                        items = messages,
                        key = { it.id },
                    ) { message ->
                        ChatBubble(message = message)
                    }

                    // 加载中指示器
                    if (isLoading) {
                        item(key = "loading") {
                            LoadingBubble()
                        }
                    }
                }
            }
        }

        // ════════════════════════════════════════════════════
        // ⚡ 快捷回复条（横向滚动）
        // ════════════════════════════════════════════════════
        QuickReplyBar(
            onQuickReplyClick = { text ->
                inputText = text
            },
        )

        // ════════════════════════════════════════════════════
        // ⌨️ 底部：输入框 + 发送按钮
        // ════════════════════════════════════════════════════
        ChatInputRow(
            inputText = inputText,
            onInputChange = { inputText = it },
            onSend = {
                if (inputText.isNotBlank()) {
                    chatViewModel.sendMessage(inputText.trim())
                    inputText = ""
                    focusManager.clearFocus()
                }
            },
            enabled = !isLoading,
        )
    }
}

// =============================================================================
// 🔝 ChatTopBar
// =============================================================================

@Composable
private fun ChatTopBar(
    modelName: String,
    onModelNameClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNewChatClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DeepSurfaceVariant.copy(alpha = 0.6f),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 模型名（可点击跳转模型列表）
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onModelNameClick() }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = modelName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Purple400,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp),
                )
            }

            // 新建对话按钮
            IconButton(
                onClick = onNewChatClick,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "新建对话",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            // 齿轮设置按钮
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// =============================================================================
// 💬 ChatBubble — 单条消息气泡
// =============================================================================

@Composable
private fun ChatBubble(message: Message) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 280.dp),
            color = if (isUser) {
                // 用户气泡：紫色玻璃质感
                GlassCardAlt
            } else {
                // AI 气泡：亮色玻璃质感
                GlassCard
            },
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isUser) 14.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 14.dp,
            ),
            border = androidx.compose.foundation.BorderStroke(
                0.5.dp,
                GlassBorder.copy(alpha = 0.3f),
            ),
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                // 角色标签
                Text(
                    text = if (isUser) "你" else "AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) Purple400 else TextTertiary,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(modifier = Modifier.height(2.dp))

                // 消息内容
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    lineHeight = 16.sp,
                )

                Spacer(modifier = Modifier.height(2.dp))

                // 时间戳
                Text(
                    text = formatTimestamp(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

// =============================================================================
// ⏳ LoadingBubble — AI 正在输入...
// =============================================================================

@Composable
private fun LoadingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            color = GlassCard,
            shape = RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp),
            border = androidx.compose.foundation.BorderStroke(
                0.5.dp,
                GlassBorder.copy(alpha = 0.3f),
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = Purple500,
                    strokeWidth = 1.5.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI 正在思考…",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
    }
}

// =============================================================================
// 🫧 EmptyChatHint — 无消息时的空状态引导
// =============================================================================

@Composable
private fun EmptyChatHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "💬",
            fontSize = 32.sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "开始和 AI 聊天吧",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "点击下方快捷回复或输入文字",
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

// =============================================================================
// ⚡ QuickReplyBar — 横向滚动快捷回复
// =============================================================================

@Composable
private fun QuickReplyBar(onQuickReplyClick: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(quickReplies.size) { index ->
            val reply = quickReplies[index]
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onQuickReplyClick(reply) },
                color = GlassCard,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp,
                    GlassBorder.copy(alpha = 0.5f),
                ),
            ) {
                Text(
                    text = reply,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary,
                    maxLines = 1,
                )
            }
        }
    }
}

// =============================================================================
// ⌨️ ChatInputRow — 输入框 + 发送按钮
// =============================================================================

@Composable
private fun ChatInputRow(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DeepSurfaceVariant.copy(alpha = 0.5f),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp, max = 120.dp),
                placeholder = {
                    Text(
                        text = "输入消息…",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = TextPrimary,
                    fontSize = 13.sp,
                ),
                maxLines = 4,
                singleLine = false,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Purple500.copy(alpha = 0.6f),
                    unfocusedBorderColor = GlassBorder.copy(alpha = 0.4f),
                    focusedContainerColor = DeepBackground.copy(alpha = 0.7f),
                    unfocusedContainerColor = DeepBackground.copy(alpha = 0.5f),
                ),
            )

            Spacer(modifier = Modifier.width(6.dp))

            // 发送按钮
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(enabled = enabled && inputText.isNotBlank()) {
                        onSend()
                    },
                color = if (inputText.isNotBlank() && enabled)
                    Purple500
                else
                    Purple500.copy(alpha = 0.3f),
                shape = CircleShape,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "发送",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

// =============================================================================
// ⏱️ 时间格式化工具
// =============================================================================

/**
 * 将毫秒时间戳格式化为 HH:mm 格式。
 * 若消息不是今天，显示 MM-dd HH:mm。
 */
private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000}分钟前"
        diff < 86400_000 -> {
            // 今天，显示 HH:mm
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
            "%02d:%02d".format(
                cal.get(java.util.Calendar.HOUR_OF_DAY),
                cal.get(java.util.Calendar.MINUTE),
            )
        }
        else -> {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
            "%02d-%02d %02d:%02d".format(
                cal.get(java.util.Calendar.MONTH) + 1,
                cal.get(java.util.Calendar.DAY_OF_MONTH),
                cal.get(java.util.Calendar.HOUR_OF_DAY),
                cal.get(java.util.Calendar.MINUTE),
            )
        }
    }
}
