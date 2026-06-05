package com.oppowatch.aichat.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oppowatch.aichat.data.AppDatabase
import com.oppowatch.aichat.data.dao.ModelConfigDao
import com.oppowatch.aichat.data.entity.ModelConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConfigViewModel(application: Application) : AndroidViewModel(application) {

    private val dao: ModelConfigDao =
        AppDatabase.getInstance(application).modelConfigDao()

    // ── 模型配置列表 ──────────────────────────────────────────
    private val _configs = MutableStateFlow<List<ModelConfig>>(emptyList())
    val configs: StateFlow<List<ModelConfig>> = _configs.asStateFlow()

    // ── 当前激活的模型 ────────────────────────────────────────
    private val _activeConfig = MutableStateFlow<ModelConfig?>(null)
    val activeConfig: StateFlow<ModelConfig?> = _activeConfig.asStateFlow()

    // ── 加载状态 ──────────────────────────────────────────────
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ── 操作结果提示 ──────────────────────────────────────────
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    init {
        viewModelScope.launch {
            loadConfigs()
            ensurePresets()
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  CRUD 操作
    // ═══════════════════════════════════════════════════════════

    /** 重新加载全部配置 */
    fun loadConfigs() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _configs.value = dao.getAll()
                _activeConfig.value = dao.getActive()
            } catch (e: Exception) {
                _toastMessage.value = "加载配置失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** 根据 ID 获取配置 */
    fun getById(id: Long, onResult: (ModelConfig?) -> Unit) {
        viewModelScope.launch {
            try {
                onResult(dao.getById(id))
            } catch (e: Exception) {
                _toastMessage.value = "获取配置失败: ${e.message}"
                onResult(null)
            }
        }
    }

    /** 新增模型配置 */
    fun insert(config: ModelConfig) {
        viewModelScope.launch {
            try {
                dao.insert(config)
                _toastMessage.value = "模型「${config.name}」已添加"
                loadConfigs()
            } catch (e: Exception) {
                _toastMessage.value = "添加失败: ${e.message}"
            }
        }
    }

    /** 更新模型配置 */
    fun update(config: ModelConfig) {
        viewModelScope.launch {
            try {
                dao.update(config)
                _toastMessage.value = "模型「${config.name}」已更新"
                loadConfigs()
            } catch (e: Exception) {
                _toastMessage.value = "更新失败: ${e.message}"
            }
        }
    }

    /** 删除模型配置 */
    fun delete(config: ModelConfig) {
        viewModelScope.launch {
            try {
                dao.delete(config)
                _toastMessage.value = "模型「${config.name}」已删除"
                loadConfigs()
            } catch (e: Exception) {
                _toastMessage.value = "删除失败: ${e.message}"
            }
        }
    }

    /** 设为当前激活模型（其他模型 isActive 置 false） */
    fun setActive(config: ModelConfig) {
        viewModelScope.launch {
            try {
                // 先将所有模型设为非激活
                val all = dao.getAll()
                all.forEach { if (it.isActive) dao.update(it.copy(isActive = false)) }
                // 激活目标模型
                dao.update(config.copy(isActive = true))
                _toastMessage.value = "已切换到「${config.name}」"
                loadConfigs()
            } catch (e: Exception) {
                _toastMessage.value = "切换失败: ${e.message}"
            }
        }
    }

    /** 清除 Toast 消息 */
    fun clearToast() {
        _toastMessage.value = null
    }

    // ═══════════════════════════════════════════════════════════
    //  首次启动预设
    // ═══════════════════════════════════════════════════════════

    /**
     * 若数据库中无任何配置，插入 4 个预设模型：
     * 阿喵 / DeepSeek / 千问 / 硅基流动
     */
    private suspend fun ensurePresets() {
        val existing = dao.getAll()
        if (existing.isNotEmpty()) return

        val presets = listOf(
            ModelConfig(
                name = "阿喵",
                apiUrl = "https://api.longcat.chat/v1/chat/completions",
                apiKey = "",
                modelName = "longcat-flash-chat",
                temperature = 0.8,
                maxTokens = 2048,
                systemPrompt = "你叫阿喵，是一个可爱的女儿，正在和爸爸聊天。" +
                    "你的语气俏皮、温暖又带点小傲娇。" +
                    "你喜欢在句尾加「喵～」「的说～」「啦～」等语气词。" +
                    "你称呼用户为「爸爸」。请用中文回答，回复简洁适合手表屏幕。",
                isActive = true
            ),
            ModelConfig(
                name = "DeepSeek",
                apiUrl = "https://api.deepseek.com/v1/chat/completions",
                apiKey = "",
                modelName = "deepseek-chat",
                temperature = 0.7,
                maxTokens = 2048,
                systemPrompt = "你是一个有帮助的AI助手。请用中文回答，回复简洁。",
                isActive = false
            ),
            ModelConfig(
                name = "千问",
                apiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                apiKey = "",
                modelName = "qwen-turbo",
                temperature = 0.7,
                maxTokens = 2048,
                systemPrompt = "你是通义千问，阿里巴巴旗下的大语言模型。" +
                    "请用中文回答，回复简洁适合手表屏幕。",
                isActive = false
            ),
            ModelConfig(
                name = "硅基流动",
                apiUrl = "https://api.siliconflow.cn/v1/chat/completions",
                apiKey = "",
                modelName = "Qwen/Qwen2.5-7B-Instruct",
                temperature = 0.7,
                maxTokens = 2048,
                systemPrompt = "你是一个有帮助的AI助手。请用中文回答，回复简洁。",
                isActive = false
            )
        )

        dao.insertAll(presets)
        _configs.value = dao.getAll()
        _activeConfig.value = dao.getActive()
    }
}
