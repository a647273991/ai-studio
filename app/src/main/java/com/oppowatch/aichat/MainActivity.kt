package com.oppowatch.aichat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.oppowatch.aichat.ui.screens.ChatScreen
import com.oppowatch.aichat.ui.theme.OppoWatchAITheme

// =============================================================================
// 主导航目的地（手表小屏用简单枚举 + 状态切换，不引入 NavHost 额外依赖）
// =============================================================================

/**
 * 应用一级页面。
 * 聊天页 → 设置页 / 模型列表页 → 模型编辑页。
 */
enum class Screen {
    /** 主聊天页 */
    Chat,
    /** 设置页 */
    Settings,
    /** 模型列表页 */
    ModelList,
    /** 模型编辑页（新建 / 编辑已有模型） */
    ModelEdit
}

// =============================================================================
// MainActivity
// =============================================================================

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OppoWatchAITheme {
                MainApp()
            }
        }
    }
}

// =============================================================================
// 顶层可组合项 —— Scaffold 无底部栏，内含页面切换逻辑
// =============================================================================

@Composable
fun MainApp() {
    var currentScreen by remember { mutableStateOf(Screen.Chat) }

    Scaffold(
        // 无底部栏：手表小屏不占额外空间
        bottomBar = {},
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                Screen.Chat -> ChatScreen(
                    onNavigateToSettings = { currentScreen = Screen.Settings },
                    onNavigateToModelList = { currentScreen = Screen.ModelList },
                )

                Screen.Settings -> SettingsScreen(
                    onBack = { currentScreen = Screen.Chat },
                )

                Screen.ModelList -> ModelListScreen(
                    onBack = { currentScreen = Screen.Chat },
                    onNavigateToModelEdit = { currentScreen = Screen.ModelEdit },
                )

                Screen.ModelEdit -> ModelEditScreen(
                    onBack = { currentScreen = Screen.ModelList },
                )
            }
        }
    }
}

// =============================================================================
// 四个页面占位 Composable（具体 UI 由子 Agent 后续填充）
// =============================================================================

/**
 * 设置页 —— API 地址、主题偏好等。
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    // TODO: 设置 UI 实现
}

/**
 * 模型列表页 —— 展示已配置的 AI 模型列表。
 */
@Composable
fun ModelListScreen(
    onBack: () -> Unit,
    onNavigateToModelEdit: () -> Unit,
) {
    // TODO: 模型列表 UI 实现
}

/**
 * 模型编辑页 —— 新建或编辑某个 AI 模型配置。
 */
@Composable
fun ModelEditScreen(onBack: () -> Unit) {
    // TODO: 模型编辑 UI 实现
}
