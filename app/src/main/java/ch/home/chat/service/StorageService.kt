package ch.home.chat.service

import android.content.Context
import android.content.SharedPreferences
import ch.home.chat.model.ChatMessage
import org.json.JSONArray
import java.io.File

class StorageService(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    var claudeApiKey: String?
        get() = prefs.getString(KEY_CLAUDE_API_KEY, prefs.getString(KEY_API_KEY, null))
        set(value) { prefs.edit().putString(KEY_CLAUDE_API_KEY, value?.trim().takeIf { !it.isNullOrEmpty() }).apply() }

    var claudeModel: String
        get() = prefs.getString(KEY_CLAUDE_MODEL, DEFAULT_API_MODEL) ?: DEFAULT_API_MODEL
        set(value) { prefs.edit().putString(KEY_CLAUDE_MODEL, value?.trim() ?: DEFAULT_API_MODEL).apply() }

    fun effectiveKey(): String? = claudeApiKey
    fun effectiveBase(): String = DEFAULT_API_BASE
    fun effectiveModel(): String = claudeModel

    var apiKey: String?
        get() = claudeApiKey
        set(value) { claudeApiKey = value }

    /** Только для совместимости с ApiUrlActivity; всегда Anthropic. */
    var apiBase: String
        get() = DEFAULT_API_BASE
        set(_) { /* фиксированный URL для Claude */ }

    var vibrationOnReply: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION_ON_REPLY, true)
        set(value) { prefs.edit().putBoolean(KEY_VIBRATION_ON_REPLY, value).apply() }

    var isFirstRun: Boolean
        get() = prefs.getBoolean(KEY_FIRST_RUN, true)
        set(value) {
            prefs.edit().putBoolean(KEY_FIRST_RUN, value).apply()
        }

    fun setFirstRunDone() {
        isFirstRun = false
    }

    fun getMessages(): List<ChatMessage> {
        val raw = prefs.getString(KEY_MESSAGES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i -> ChatMessage.fromJson(arr.getJSONObject(i)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveMessages(messages: List<ChatMessage>) {
        val toSave = if (messages.size > MAX_STORED) messages.takeLast(MAX_STORED) else messages
        val list = toSave.map { m ->
            val map = m.toJson()
            if (map.optString("content").length > MAX_CONTENT_LENGTH) {
                map.put("content", map.optString("content").take(MAX_CONTENT_LENGTH))
            }
            map
        }
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(KEY_MESSAGES, arr.toString()).apply()
        saveChatLog(toSave)
    }

    /** Сохранение синхронно (commit), чтобы после возврата из метода чат уже видел новые сообщения. */
    fun saveMessagesSync(messages: List<ChatMessage>) {
        val toSave = if (messages.size > MAX_STORED) messages.takeLast(MAX_STORED) else messages
        val list = toSave.map { m ->
            val map = m.toJson()
            if (map.optString("content").length > MAX_CONTENT_LENGTH) {
                map.put("content", map.optString("content").take(MAX_CONTENT_LENGTH))
            }
            map
        }
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(KEY_MESSAGES, arr.toString()).commit()
        saveChatLog(toSave)
    }

    fun getChMemory(): String {
        return try {
            File(appContext.filesDir, "ch_memory.txt").readText(Charsets.UTF_8).trim()
        } catch (_: Exception) {
            ""
        }
    }

    fun appendToChMemory(text: String) {
        if (text.isBlank()) return
        try {
            val file = File(appContext.filesDir, "ch_memory.txt")
            val current = if (file.exists()) file.readText(Charsets.UTF_8) else ""
            val maxMemoryChars = 100_000
            val toAppend = if (current.length + text.length > maxMemoryChars)
                text.take(maxMemoryChars - current.length) else text
            file.writeText((current + "\n\n" + toAppend).trim(), Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    private fun saveChatLog(messages: List<ChatMessage>) {
        try {
            val file = File(appContext.filesDir, "chat_log.txt")
            val sb = StringBuilder()
            for (m in messages) {
                val ts = if (m.timestamp > 0) {
                    val d = java.util.Date(m.timestamp)
                    java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(d)
                } else ""
                sb.append("[$ts] ${m.displayName}: ${m.content}\n")
            }
            file.writeText(sb.toString())
        } catch (_: Exception) {}
    }

    fun getChatLogTail(maxChars: Int = 4000): String {
        return try {
            val file = File(appContext.filesDir, "chat_log.txt")
            if (!file.exists()) return ""
            val full = file.readText(Charsets.UTF_8)
            if (full.length <= maxChars) full.trim() else full.takeLast(maxChars).trim().let { "…\n$it" }
        } catch (_: Exception) { "" }
    }

    /** Переопределение системного промпта. Если файл пустой или не существует — используется встроенный. */
    fun getSystemPromptOverride(): String {
        return try {
            val f = File(appContext.filesDir, "system_prompt_override.txt")
            if (f.exists()) f.readText(Charsets.UTF_8).trim() else ""
        } catch (_: Exception) { "" }
    }

    fun setSystemPromptOverride(text: String) {
        try {
            File(appContext.filesDir, "system_prompt_override.txt").writeText(text, Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    fun getClaudeFilesDir(): File {
        val dir = File(appContext.filesDir, "claudeFiles")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun exportChatToFile(): File? {
        return try {
            val dir = File(appContext.filesDir, "exports").also { if (!it.exists()) it.mkdirs() }
            val file = File(dir, "claude_chat_${System.currentTimeMillis()}.json")
            val list = getMessages()
            val arr = org.json.JSONArray()
            list.forEach { arr.put(it.toJson()) }
            file.writeText(arr.toString(2), Charsets.UTF_8)
            file
        } catch (_: Exception) { null }
    }

    fun loadBackupFromStream(json: String): List<ChatMessage>? {
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { i -> ChatMessage.fromJson(arr.getJSONObject(i)) }
        } catch (_: Exception) { null }
    }

    companion object {
        private const val PREFS_NAME = "ch_chat"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_CLAUDE_API_KEY = "claude_api_key"
        private const val KEY_CLAUDE_MODEL = "claude_model"
        private const val KEY_VIBRATION_ON_REPLY = "vibration_on_reply"
        private const val KEY_FIRST_RUN = "first_run"
        private const val KEY_MESSAGES = "messages"
        private const val DEFAULT_API_BASE = "https://api.anthropic.com"
        private const val DEFAULT_API_MODEL = "claude-3-5-sonnet-latest"
        const val MAX_STORED = 4000
        private const val MAX_CONTENT_LENGTH = 20_000
    }
}
