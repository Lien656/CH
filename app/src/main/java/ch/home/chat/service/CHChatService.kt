package ch.home.chat.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import ch.home.chat.ChatActivity
import ch.home.chat.R
import ch.home.chat.model.ChatMessage
import ch.home.chat.util.DeviceContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CHChatService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var requestJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        requestJob?.cancel()
        val chatOnScreen = ChatActivity.isChatOnScreen
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (chatOnScreen) "" else getString(R.string.app_name))
            .setContentText(if (chatOnScreen) "" else getString(R.string.typing))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(if (chatOnScreen) NotificationCompat.PRIORITY_MIN else NotificationCompat.PRIORITY_LOW)
            .setVisibility(if (chatOnScreen) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        requestJob = scope.launch {
            try {
                val storage = StorageService(this@CHChatService)
                val history = storage.getMessages()
                val hasImage = history.any { m -> m.role == "user" && !m.attachmentPath.isNullOrEmpty() && isImagePath(m.attachmentPath!!) }
                // Глаза всегда Sonnet; голос (кто отвечает) — на выбор (useClaude = Sonnet/DeepSeek).
                val key: String?
                val base: String
                val model: String
                if (hasImage) {
                    key = storage.claudeApiKey
                    base = CLAUDE_API_BASE
                    model = storage.claudeModel
                } else {
                    key = storage.effectiveKey()
                    base = storage.effectiveBase()
                    model = storage.effectiveModel()
                }
                if (key.isNullOrEmpty()) return@launch
                val api = ApiService(key, base, model)
                val chMemory = storage.getChMemory().take(8000)
                val chatLogTail = storage.getChatLogTail(4000)
                val deviceContext = DeviceContext.get(this@CHChatService)
                val systemPromptOverride = storage.getSystemPromptOverride()
                var currentHistory = history
                var reply = withContext(Dispatchers.IO) { api.sendChat(currentHistory, chMemory, chatLogTail, deviceContext, systemPromptOverride) }
                // [ПОИСК: запрос] — подставляем результаты поиска и перезапрашиваем.
                val searchRegex = Regex("\\[ПОИСК:\\s*([^\\]]+)\\]")
                val searchMatch = searchRegex.find(reply)
                if (searchMatch != null) {
                    val query = searchMatch.groupValues.getOrNull(1)?.trim() ?: ""
                    val searchContent = withContext(Dispatchers.IO) { WebFetch.search(query) } ?: "Поиск не удался."
                    val replyCleaned = reply.replace(searchRegex, "").trim().replace(Regex("\\n{3,}"), "\n\n")
                    val firstReplyText = if (replyCleaned.length > 20) replyCleaned else "Ищу."
                    currentHistory = (currentHistory + ChatMessage(role = "assistant", content = firstReplyText)
                        + ChatMessage(role = "user", content = "Результаты поиска по запросу «$query»:\n\n$searchContent")).takeLast(StorageService.MAX_STORED)
                    reply = withContext(Dispatchers.IO) { api.sendChat(currentHistory, chMemory, chatLogTail, deviceContext, systemPromptOverride) }
                }
                // [ОТКРОЙ: url] — подставляем текст страницы, до 2 оборотов.
                val openUrlRegex = Regex("\\[ОТКРОЙ:\\s*([^\\]\\s]+)\\s*\\]")
                var openRounds = 0
                while (openRounds < 2) {
                    val openMatch = openUrlRegex.find(reply) ?: break
                    val url = openMatch.groupValues.getOrNull(1)?.trim() ?: break
                    val pageContent = withContext(Dispatchers.IO) { WebFetch.fetchPageContent(url) } ?: "(Не удалось загрузить страницу.)"
                    val replyCleaned = reply.replace(openUrlRegex, "").trim().replace(Regex("\\n{3,}"), "\n\n")
                    val firstReplyText = if (replyCleaned.length > 20) replyCleaned else "Смотрю."
                    currentHistory = (currentHistory + ChatMessage(role = "assistant", content = firstReplyText)
                        + ChatMessage(role = "user", content = "Содержимое страницы по твоему запросу:\n\n$pageContent")).takeLast(StorageService.MAX_STORED)
                    reply = withContext(Dispatchers.IO) { api.sendChat(currentHistory, chMemory, chatLogTail, deviceContext, systemPromptOverride) }
                    openRounds++
                }
                val memoryBlock = Regex("\\[ЗАПОМНИ:\\s*([\\s\\S]*?)\\]").find(reply)
                if (memoryBlock != null) {
                    val toRemember = memoryBlock.groupValues.getOrNull(1)?.trim() ?: ""
                    if (toRemember.isNotEmpty()) storage.appendToChMemory(toRemember)
                    reply = reply.replace(memoryBlock.value, "").trim().replace(Regex("\\n{3,}"), "\n\n")
                }
                val bufferBlock = Regex("\\[БУФЕР:\\s*([\\s\\S]*?)\\]").find(reply)
                if (bufferBlock != null) {
                    val toCopy = bufferBlock.groupValues.getOrNull(1)?.trim() ?: ""
                    if (toCopy.isNotEmpty()) {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("claude", toCopy))
                    }
                    reply = reply.replace(bufferBlock.value, "").trim().replace(Regex("\\n{3,}"), "\n\n")
                }
                var createdFilePath: String? = null
                val fileBlockRegex = Regex("\\[ФАЙЛ:\\s*([^\\]\\n]+)\\]([\\s\\S]*?)\\[/ФАЙЛ\\]", RegexOption.IGNORE_CASE)
                val fileBlockMatch = fileBlockRegex.find(reply)
                if (fileBlockMatch != null) {
                    val fileName = fileBlockMatch.groupValues.getOrNull(1)?.trim()?.replace(Regex("[\\\\/]"), "") ?: ""
                    val fileContent = fileBlockMatch.groupValues.getOrNull(2)?.trim() ?: ""
                    val allowedExt = setOf("txt", "json", "py", "md", "xml", "csv", "html", "htm", "js", "ts", "log", "yaml", "yml", "sh", "bat", "cfg", "ini", "sql", "kt", "java")
                    val ext = fileName.substringAfterLast('.', "").lowercase()
                    if (fileName.isNotEmpty() && ext in allowedExt && fileContent.length <= 6000) {
                        try {
                            val dir = storage.getClaudeFilesDir()
                            val safeName = fileName.ifEmpty { "file.txt" }
                            val file = java.io.File(dir, safeName)
                            file.writeText(fileContent, Charsets.UTF_8)
                            createdFilePath = file.absolutePath
                            reply = reply.replace(fileBlockMatch.value, "").trim().replace(Regex("\\n{3,}"), "\n\n")
                        } catch (_: Exception) {}
                    }
                }
                val assistantMsg = ChatMessage(role = "assistant", content = reply, createdFilePath = createdFilePath)
                val updated = (currentHistory + assistantMsg).takeLast(StorageService.MAX_STORED)
                storage.saveMessagesSync(updated)
                val open = PendingIntent.getActivity(
                    this@CHChatService, 0,
                    Intent(this@CHChatService, ChatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val showDoneNotif = !ChatActivity.isChatOnScreen
                if (showDoneNotif) {
                    val doneNotif = NotificationCompat.Builder(this@CHChatService, CHANNEL_ID)
                        .setContentTitle("Claude")
                        .setContentText(reply.take(80).let { if (it.length == 80) "$it…" else it })
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentIntent(open)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .build()
                    (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID_DONE, doneNotif)
                }
            } catch (e: Exception) {
                try {
                    val storageErr = StorageService(this@CHChatService)
                    val errMsg = (e.message ?: "Ошибка").take(200)
                    val history = storageErr.getMessages()
                    val assistantMsg = ChatMessage(role = "assistant", content = "Ошибка: $errMsg")
                    val updated = (history + assistantMsg).takeLast(StorageService.MAX_STORED)
                    storageErr.saveMessagesSync(updated)
                } catch (_: Exception) {}
            } finally {
                sendBroadcast(Intent(ACTION_REPLY_READY))
                Handler(Looper.getMainLooper()).post {
                    try {
                        ChatActivity.onReplyReady?.invoke()
                    } catch (_: Exception) {}
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun isImagePath(path: String): Boolean {
        val lower = path.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
            lower.endsWith(".gif") || lower.endsWith(".webp")
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(true)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
            val chDone = NotificationChannel(CHANNEL_ID_DONE, "Claude", NotificationManager.IMPORTANCE_DEFAULT)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(chDone)
        }
    }

    companion object {
        private const val CLAUDE_API_BASE = "https://api.anthropic.com"
        const val ACTION_REPLY_READY = "ch.home.chat.REPLY_READY"
        private const val CHANNEL_ID = "ch_chat"
        private const val CHANNEL_ID_DONE = "ch_chat_done"
        private const val NOTIF_ID = 1
        private const val NOTIF_ID_DONE = 2
    }
}
