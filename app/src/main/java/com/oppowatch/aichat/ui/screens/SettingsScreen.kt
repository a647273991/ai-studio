package com.oppowatch.aichat.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ────────────────────────────────────────────────────────────
// 设置菜单页 — 针对 OPPO Watch SE 372×430 AMOLED 小屏适配
// ────────────────────────────────────────────────────────────

/**
 * 设置主页面。
 *
 * 入口卡片：
 *  - 模型配置 → ModelConfigScreen
 *  - 人设管理 → PersonaManagementScreen
 *  - 聊天记录 → HistoryScreen
 *  - 关于     → AboutDialog / AboutScreen
 *
 * @param onBack                  返回上一页（聊天主页）
 * @param onNavigateToModelConfig 跳转到模型配置页
 * @param onNavigateToPersona     跳转到人设管理页
 * @param onNavigateToHistory     跳转到聊天记录页
 * @param onNavigateToAbout       跳转到关于页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToModelConfig: () -> Unit,
    onNavigateToPersona: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToAbout: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "设置",
                        fontSize = 18.sp,
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
                // 深色 AMOLED 友好
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // ── 模型配置 ──────────────────────────────────
            SettingsMenuItem(
                icon = Icons.Filled.Settings,
                title = "模型配置",
                subtitle = "管理AI模型、API地址及参数",
                onClick = onNavigateToModelConfig
            )

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // ── 人设管理 ──────────────────────────────────
            SettingsMenuItem(
                icon = Icons.Filled.Person,
                title = "人设管理",
                subtitle = "编辑AI角色设定与System Prompt",
                onClick = onNavigateToPersona
            )

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // ── 聊天记录 ──────────────────────────────────
            SettingsMenuItem(
                icon = Icons.Filled.History,
                title = "聊天记录",
                subtitle = "查看与管理历史对话",
                onClick = onNavigateToHistory
            )

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // ── 关于 ──────────────────────────────────────
            SettingsMenuItem(
                icon = Icons.Filled.Info,
                title = "关于",
                subtitle = "版本信息与开源许可",
                onClick = onNavigateToAbout
            )
        }
    }
}

// ────────────────────────────────────────────────────────────
// 单个菜单项组件
// ────────────────────────────────────────────────────────────

/**
 * 设置菜单中的单行条目。
 *
 * 手表适配要点：
 *  - 最小触摸高度 ≥ 48dp
 *  - 图标 28dp，保证拇指可点
 *  - 主标题 15sp，副标题 12sp
 */
@Composable
private fun SettingsMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧图标
        Icon(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.width(14.dp))

        // 中间文字区
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1
            )
        }

        // 右侧箭头
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "进入",
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}
