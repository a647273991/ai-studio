package com.oppowatch.aichat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oppowatch.aichat.data.AppDatabase
import com.oppowatch.aichat.data.entity.Conversation
import com.oppowatch.aichat.data.entity.Message
import com.oppowatch.aichat.data.entity.ModelConfig
import com.oppowatch.aichat.network.ApiClient
import com.oppowatch.aichat.network.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel : ViewModel() {

    private val db = AppDatabase.getInstance()
    private val convDao = db.conversationDao()
    private val msgDao = db.messageDao()
    private val modelDao = db.modelConfigDao()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentConvId = MutableStateFlow<Long?>(null)
    val currentConvId: StateFlow<Long?> = _currentConvId.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _activeModel = MutableStateFlow<ModelConfig?>(null)
    val activeModel: StateFlow<ModelConfig?> = _activeModel.asStateFlow()

    init {
        viewModelScope.launch {
            val active = modelDao.getActive()
            _activeModel.value = active
        }
    }

    fun loadActiveModel() {
        viewModelScope.launch {
            _activeModel.value = modelDao.getActive()
        }
    }

    fun startNewConversation() {
        viewModelScope.launch {
            val model = _activeModel.value ?: return@launch
            val conv = Conversation(modelConfigId = model.id, title = "新对话")
            val convId = convDao.insert(conv)
            _currentConvId.value = convId
            _messages.value = emptyList()
            _error.value = null
        }
    }

    fun loadConversation(convId: Long) {
        viewModelScope.launch {
            _currentConvId.value = convId
            convDao.getById(convId).collect { conv ->
                if (conv != null) {
                    msgDao.getByConversationId(convId).collect { msgs ->
                        _messages.value = msgs
                    }
                }
            }
        }
    }

    fun sendMessage(text: String) {
        val model = _activeModel.value ?: run {
            _error.value = "请先配置并选择一个AI模型"
            return
        }
        if (model.apiKey.isBlank()) {
            _error.value = "请在模型配置中填写 API Key"
            return
        }
        if (text.isBlank()) return

        viewModelScope.launch {
            // Ensure conversation exists
            var convId = _currentConvId.value
            if (convId == null) {
                val conv = Conversation(modelConfigId = model.id, title = text.take(20))
                convId = convDao.insert(conv)
                _currentConvId.value = convId
            }

            // Save user message
            val userMsg = Message(conversationId = convId!!, role = "user", content = text)
            msgDao.insert(userMsg)

            // Update UI
            _messages.value = _messages.value + userMsg
            _isLoading.value = true
            _error.value = null

            try {
                // Prepare messages for API
                val apiMessages = mutableListOf<ChatMessage>()
                if (model.systemPrompt.isNotBlank()) {
                    apiMessages.add(ChatMessage("system", model.systemPrompt))
                }
                // Add recent context (last 20 messages)
                val contextMsgs = _messages.value.takeLast(20)
                contextMsgs.forEach { msg ->
                    apiMessages.add(ChatMessage(msg.role, msg.content))
                }

                // Call API
                val apiClient = ApiClient.init(model.apiUrl, model.apiKey)
                val response = withContext(Dispatchers.IO) {
                    apiClient.chat(
                        model = model.modelName,
                        messages = apiMessages,
                        temperature = model.temperature,
                        maxTokens = model.maxTokens
                    )
                }

                val reply = response.content
                if (reply.isNotBlank()) {
                    val aiMsg = Message(conversationId = convId, role = "assistant", content = reply)
                    msgDao.insert(aiMsg)
                    _messages.value = _messages.value + aiMsg
                } else {
                    _error.value = "AI 返回了空回复，请检查模型配置"
                }
            } catch (e: Exception) {
                _error.value = when {
                    e.message?.contains("timeout", true) == true -> "请求超时，请检查网络或API地址"
                    e.message?.contains("401", true) == true -> "API Key 无效，请检查配置"
                    e.message?.contains("404", true) == true -> "API 地址不正确，请检查URL"
                    else -> "请求失败: ${e.message?.take(60)}"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
