package com.oppowatch.aichat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oppowatch.aichat.data.entity.Message
import com.oppowatch.aichat.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val activeModel by viewModel.activeModel.collectAsState()
    val currentConvId by viewModel.currentConvId.collectAsState()
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.loadActiveModel() }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }
    if (error != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("提示", fontSize = 14.sp) },
            text = { Text(error ?: "", fontSize = 12.sp) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("知道了", fontSize = 12.sp)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A18))
    ) {
        // ── 顶部状态栏 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 模型名 + 绿点
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF4ADE80))
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = activeModel?.name ?: "未选择模型",
                    color = Color(0xFFCCCCDD),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            // 设置按钮
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "设置",
                    tint = Color(0xFF9999AA),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // ── 聊天消息列表 ──
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (currentConvId == null && messages.isEmpty()) {
                item {
                    Text(
                        "输入内容开始与 ${activeModel?.name ?: "AI"} 对话",
                        color = Color(0xFF555566),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            items(messages) { msg ->
                ChatBubble(msg)
            }
            if (isLoading) {
                item {
                    Text(
                        "AI 正在回复…",
                        color = Color(0xFF666688),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // ── 快捷回复 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            listOf("你好👋", "帮我想想", "讲个笑话", "今天运势").forEach { label ->
                SuggestionChip(
                    onClick = { inputText = label },
                    label = {
                        Text(label, fontSize = 10.sp, color = Color(0xFF8888AA))
                    },
                    modifier = Modifier.height(28.dp),
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = Color(0x15FFFFFF)
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(
                        borderColor = Color(0x10FFFFFF),
                        enabled = true
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // ── 输入栏 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 语音按钮
            IconButton(
                onClick = { /* TODO 语音输入 */ },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = "语音",
                    tint = Color(0xFF7777AA),
                    modifier = Modifier.size(17.dp)
                )
            }

            // 输入框
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f).height(40.dp),
                textStyle = TextStyle(fontSize = 11.sp, color = Color(0xFFE0E0EE)),
                placeholder = {
                    Text("说点什么…", fontSize = 11.sp, color = Color(0xFF444466))
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0x12FFFFFF),
                    unfocusedContainerColor = Color(0x0AFFFFFF),
                    focusedBorderColor = Color(0x307850FF),
                    unfocusedBorderColor = Color(0x10FFFFFF)
                )
            )

            // 发送按钮
            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                },
                modifier = Modifier.size(36.dp),
                enabled = inputText.isNotBlank() && !isLoading
            ) {
                Icon(
                    Icons.Filled.Send,
                    contentDescription = "发送",
                    tint = if (inputText.isNotBlank() && !isLoading)
                        Color(0xFFA080FF) else Color(0xFF444466),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ChatBubble(msg: Message) {
    val isUser = msg.role == "user"
    val bubbleColor = if (isUser)
        Brush.horizontalGradient(listOf(Color(0x307850FF), Color(0x1850A0FF)))
    else
        Brush.horizontalGradient(listOf(Color(0x12FFFFFF), Color(0x08FFFFFF)))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 14.dp, topEnd = 14.dp,
                        bottomStart = if (isUser) 14.dp else 3.dp,
                        bottomEnd = if (isUser) 3.dp else 14.dp
                    )
                )
                .background(bubbleColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = msg.content,
                color = if (isUser) Color(0xFFD8D0FF) else Color(0xFFC0C0CC),
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
        Text(
            text = formatTime(msg.timestamp),
            color = Color(0xFF444455),
            fontSize = 8.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

fun formatTime(ts: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(ts))
}
