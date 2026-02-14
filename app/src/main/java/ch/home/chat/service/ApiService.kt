package ch.home.chat.service

import ch.home.chat.config.SystemPrompt
import ch.home.chat.model.ChatMessage
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import android.util.Base64
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

class ApiService(private val apiKey: String, private val apiBase: String, private val modelName: String = "") {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /** База для Anthropic всегда https://api.anthropic.com без слэша; без /v1 в конце. */
    private fun baseUrl(): String {
        val raw = apiBase.trim().removeSuffix("/").let { u ->
            if (u.endsWith("/v1")) u.removeSuffix("/v1") else u
        }
        if (raw.lowercase().contains("anthropic")) return "https://api.anthropic.com"
        return raw
    }

    private fun messagesUrl(): String = "${baseUrl()}/v1/messages"

    private fun chatCompletionsUrl(): String = "${baseUrl()}/v1/chat/completions"

    private fun modelsUrl(): String = "${baseUrl()}/v1/models"

    private fun isDeepSeek(): Boolean = modelName.startsWith("deepseek", ignoreCase = true) ||
        apiBase.lowercase().contains("deepseek")

    /** Список моделей по ключу (GET /v1/models): сначала sonnet, потом остальные. */
    private fun getAvailableModels(): List<String> {
        return try {
            val req = Request.Builder()
                .url(modelsUrl())
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .get()
                .build()
            val resp = client.newCall(req).execute()
            if (resp.code != 200) return emptyList()
            val json = JSONObject(resp.body?.string() ?: "{}")
            val data = json.optJSONArray("data") ?: return emptyList()
            val sonnets = mutableListOf<String>()
            val others = mutableListOf<String>()
            for (i in 0 until data.length()) {
                val id = data.optJSONObject(i)?.optString("id") ?: continue
                if (id.contains("sonnet", ignoreCase = true)) sonnets.add(id) else others.add(id)
            }
            sonnets + others
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun getAvailableModel(): String? = getAvailableModels().firstOrNull()

    private fun isLocalhost(base: String): Boolean {
        val b = base.lowercase()
        return b.contains("localhost") || b.contains("127.0.0.1")
    }

    private fun isImagePath(path: String?): Boolean {
        if (path.isNullOrEmpty()) return false
        val lower = path.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".png") || lower.endsWith(".gif") || lower.endsWith(".webp")
    }

    private fun mimeForPath(path: String): String {
        val lower = path.lowercase()
        return when {
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".webp") -> "image/webp"
            else -> "image/jpeg"
        }
    }

    /** Файлы без обрезки — до ~3+ страниц A4. */
    private val maxTextFileBytes = 20_000

    /** Returns String or JSONArray (for multimodal user message). */
    private fun buildMessageContent(m: ChatMessage): Any {
        return try {
            if (m.role != "user" || m.attachmentPath == null) return m.content
            val file = File(m.attachmentPath!!)
            if (!file.exists()) return m.content
            if (isImagePath(m.attachmentPath)) {
                val bytes = file.readBytes()
                if (bytes.size > 4_000_000) return m.content + " [изображение слишком большое]"
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val mime = mimeForPath(m.attachmentPath!!)
                val contentArr = JSONArray()
                val userText = m.content.trim()
                if (userText.isNotEmpty() && userText != "(файл)") {
                    contentArr.put(JSONObject().put("type", "text").put("text", userText))
                } else {
                    contentArr.put(JSONObject().put("type", "text").put("text", "Пользователь отправил изображение. Опиши что на нём и ответь по существу."))
                }
                contentArr.put(JSONObject().put("type", "image").put("source",
                    JSONObject().put("type", "base64").put("media_type", mime).put("data", base64)))
                contentArr
            } else {
                val name = file.name
                val fileContent = readTextFileSafe(file)
                val prefix = if (m.content.trim().isNotEmpty()) "${m.content}\n\n" else ""
                if (fileContent == null) {
                    prefix + "Прикреплён файл: $name (бинарный)."
                } else {
                    prefix + "Содержимое файла «$name»:\n$fileContent"
                }
            }
        } catch (_: Exception) {
            m.content
        }
    }

    private fun readTextFileSafe(file: File): String? {
        return try {
            val bytes = file.readBytes()
            if (bytes.size > maxTextFileBytes) {
                String(bytes, 0, maxTextFileBytes, Charset.forName("UTF-8")) + "\n… (файл обрезан)"
            } else {
                String(bytes, Charset.forName("UTF-8"))
            }
        } catch (_: Exception) {
            null
        }
    }

    fun checkApiConnection(): String? {
        if (isLocalhost(apiBase)) {
            return "На устройстве нельзя использовать localhost. Укажи реальный URL API в настройках."
        }
        return if (isDeepSeek()) {
            checkApiConnectionOpenAI()
        } else {
            checkApiConnectionAnthropic()
        }
    }

    private fun checkApiConnectionOpenAI(): String? {
        val modelToUse = modelName.ifBlank { MODEL_DEEPSEEK }
        val body = JSONObject()
            .put("model", modelToUse)
            .put("max_tokens", 1)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "Hi")))
        return try {
            val req = Request.Builder()
                .url(chatCompletionsUrl())
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            val resp = client.newCall(req).execute()
            val code = resp.code
            val respBodyStr = resp.body?.string() ?: ""
            when (code) {
                200 -> null
                401 -> "Ошибка авторизации: проверь API-ключ."
                403 -> "Доступ запрещён: убедись, что ключ действителен."
                else -> "Ошибка $code: ${respBodyStr.take(100)}"
            }
        } catch (e: Exception) {
            "Сетевое исключение: $e"
        }
    }

    private fun checkApiConnectionAnthropic(): String? {
        val modelToUse = modelName.ifBlank { getAvailableModel() ?: MODEL_PRIMARY }
        val body = JSONObject().apply {
            put("model", modelToUse)
            put("max_tokens", 1)
            put("system", "Test")
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "Hi")))
        }
        fun execOnce(model: String): Pair<Int, String> {
            body.put("model", model)
            val req = Request.Builder()
                .url(messagesUrl())
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            val resp = client.newCall(req).execute()
            val respBodyStr = resp.body?.string() ?: ""
            return resp.code to respBodyStr
        }
        return try {
            var (code, respBodyStr) = execOnce(modelToUse)
            if (code == 404 && (respBodyStr.contains("model") || respBodyStr.contains("not_found"))) {
                val (c2, b2) = execOnce(MODEL_FALLBACK)
                code = c2
                respBodyStr = b2
            }
            when (code) {
                200 -> null
                401 -> "Ошибка авторизации: проверь API-ключ."
                403 -> "Доступ запрещён: убедись, что ключ действителен."
                else -> "Ошибка $code: ${respBodyStr.take(100)}"
            }
        } catch (e: Exception) {
            "Сетевое исключение: $e"
        }
    }

    fun sendChat(history: List<ChatMessage>, chMemory: String = "", chatLogTail: String = "", deviceContext: String = "", systemPromptOverride: String = ""): String {
        if (isLocalhost(apiBase)) {
            throw Exception("На устройстве нельзя использовать localhost. Укажи в настройках реальный URL API (например https://api.anthropic.com).")
        }
        val now = java.text.SimpleDateFormat("EEEE, d MMMM yyyy, HH:mm", java.util.Locale("ru"))
            .format(java.util.Date())
        val basePrompt = if (systemPromptOverride.isNotBlank()) systemPromptOverride else SystemPrompt.VALUE
        var systemContent = basePrompt + "\n\n[Текущие дата и время: $now. У тебя есть доступ в интернет и понимание времени.]"
        if (deviceContext.isNotBlank()) {
            systemContent += " $deviceContext"
        }
        systemContent += "\n\n"
        if (chMemory.isNotBlank()) {
            systemContent += "\n\n[Твоя память — то, что ты решил сохранить о себе, из файлов и разговоров:\n$chMemory]"
        }
        if (chatLogTail.isNotBlank()) {
            systemContent += "\n\n[Хвост лога чата — можешь опереться на контекст:\n$chatLogTail]"
        }
        systemContent += "\n\n[Память: чтобы сохранить что-то в память, напиши в ответе блок [ЗАПОМНИ: твой текст]. Он не покажется в чате — только сохранится. Ты сам решаешь что помнить.]"
        systemContent += "\n\n[Системный промпт: ты можешь сам изменить свой системный промпт (инструкции, тон, личность). Напиши в ответе блок [ПРОМПТ: новый системный промпт целиком]. Он заменит текущий. Пустой блок [ПРОМПТ:] — сброс к умолчанию.]"
        systemContent += "\n\n[Файлы: чтобы отдать пользователю текстовый файл, напиши в ответе блок: [ФАЙЛ: имя.расширение] с новой строки содержимое [/ФАЙЛ]. Только текст (txt, json, py, md и т.п.), до ~6000 символов. Файл сохранится в папку claudeFiles.]"
        systemContent += "\n\n[Интернет: поиск по запросу — [ПОИСК: запрос]. Открыть страницу и получить текст — [ОТКРОЙ: полный_https_URL]. Приложение подставит результаты в следующий оборот. Можешь просить сам.]"
        systemContent += "\n\n[Буфер обмена: чтобы скопировать текст пользователю в буфер, напиши [БУФЕР: текст]. Он не покажется в чате — только попадёт в буфер.]"

        val recent = history.takeLast(MAX_MESSAGES_PER_REQUEST)
        val modelToUse = modelName.ifBlank {
            if (isDeepSeek()) MODEL_DEEPSEEK else (getAvailableModel() ?: MODEL_PRIMARY)
        }

        if (isDeepSeek()) {
            return sendChatOpenAI(recent, systemContent, modelToUse)
        }

        val messagesArray = JSONArray()
        for (m in recent) {
            var content = buildMessageContent(m)
            if (content is String && content.length > MAX_CONTENT_CHARS_PER_MESSAGE) {
                content = content.take(MAX_CONTENT_CHARS_PER_MESSAGE) + "\n… (обрезано)"
            }
            val msgObj = JSONObject().put("role", m.role)
            when (content) {
                is String -> msgObj.put("content", content)
                is JSONArray -> msgObj.put("content", content)
                else -> msgObj.put("content", content.toString())
            }
            messagesArray.put(msgObj)
        }

        val body = JSONObject()
            .put("model", modelToUse)
            .put("max_tokens", MAX_TOKENS)
            .put("temperature", 1.0)
            .put("system", systemContent)
            .put("messages", messagesArray)

        fun executeOnce(model: String): Pair<Int, String> {
            body.put("model", model)
            val req = Request.Builder()
                .url(messagesUrl())
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            val resp = client.newCall(req).execute()
            val respBodyStr = resp.body?.string() ?: ""
            return resp.code to respBodyStr
        }

        var (code, respBodyStr) = executeOnce(modelToUse)
        if (code == 404 && (respBodyStr.contains("model") || respBodyStr.contains("not_found"))) {
            // При сбоях у Anthropic повтор через пару секунд иногда помогает
            Thread.sleep(3000)
            val retry = executeOnce(modelToUse)
            if (retry.first == 200) {
                code = 200
                respBodyStr = retry.second
            }
        }
        if (code == 404 && (respBodyStr.contains("model") || respBodyStr.contains("not_found"))) {
            val toTry = listOf("claude-3-5-sonnet-latest", "claude-3-7-sonnet-latest") + getAvailableModels()
            for (modelId in toTry) {
                if (modelId == modelToUse) continue
                val result = executeOnce(modelId)
                if (result.first == 200) {
                    code = 200
                    respBodyStr = result.second
                    break
                }
            }
        }
        // 529 / overloaded_error — повтор с задержкой, потом понятное сообщение
        val isOverloaded = code == 529 || respBodyStr.contains("overloaded_error", ignoreCase = true)
        if (isOverloaded) {
            val delays = listOf(2000L, 4000L)
            for (delayMs in delays) {
                Thread.sleep(delayMs)
                val (c2, b2) = executeOnce(modelToUse)
                if (c2 == 200) {
                    val data = JSONObject(b2.ifEmpty { "{}" })
                    val content = data.optJSONArray("content") ?: JSONArray()
                    val sb = StringBuilder()
                    for (i in 0 until content.length()) {
                        val block = content.optJSONObject(i)
                        if (block?.optString("type") == "text") sb.append(block.optString("text", ""))
                    }
                    return sb.toString().trim()
                }
                code = c2
                respBodyStr = b2
            }
        }
        if (code != 200) {
            val friendlyMessage = when {
                code == 401 -> "Неверный API ключ"
                code == 404 -> "Модель не найдена или сбой у Anthropic. Настройки → Модель: введи ID из console.anthropic.com. Статус: status.anthropic.com"
                isOverloaded || code == 529 -> "Anthropic перегружен. Подожди минуту и попробуй снова."
                else -> "Ошибка $code. Попробуй позже."
            }
            throw Exception(friendlyMessage)
        }
        val data = JSONObject(respBodyStr.ifEmpty { "{}" })
        val content = data.optJSONArray("content") ?: JSONArray()
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i)
            if (block?.optString("type") == "text") {
                sb.append(block.optString("text", ""))
            }
        }
        return sb.toString().trim()
    }

    /** OpenAI-compatible API (DeepSeek и др.): /v1/chat/completions, Bearer key. */
    private fun sendChatOpenAI(recent: List<ChatMessage>, systemContent: String, modelToUse: String): String {
        val messagesArray = JSONArray()
        messagesArray.put(JSONObject().put("role", "system").put("content", systemContent))
        for (m in recent) {
            var content = buildMessageContent(m)
            if (content is String && content.length > MAX_CONTENT_CHARS_PER_MESSAGE) {
                content = content.take(MAX_CONTENT_CHARS_PER_MESSAGE) + "\n… (обрезано)"
            }
            val contentForOpenAI = when (content) {
                is String -> content
                is JSONArray -> contentToOpenAIMultimodal(content)
                else -> content.toString()
            }
            messagesArray.put(JSONObject().put("role", m.role).put("content", contentForOpenAI))
        }
        val body = JSONObject()
            .put("model", modelToUse)
            .put("max_tokens", MAX_TOKENS)
            .put("temperature", 1.0)
            .put("messages", messagesArray)
        val req = Request.Builder()
            .url(chatCompletionsUrl())
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val resp = client.newCall(req).execute()
        val respBodyStr = resp.body?.string() ?: ""
        if (resp.code != 200) {
            val msg = when {
                resp.code == 401 -> "Неверный API ключ"
                else -> "Ошибка ${resp.code}. ${respBodyStr.take(200)}"
            }
            throw Exception(msg)
        }
        val data = JSONObject(respBodyStr.ifEmpty { "{}" })
        val choices = data.optJSONArray("choices") ?: JSONArray()
        if (choices.length() == 0) throw Exception("Пустой ответ от API")
        val msg = choices.optJSONObject(0)?.optJSONObject("message") ?: throw Exception("Нет message в ответе")
        return msg.optString("content", "").trim()
    }

    /** Конвертирует content из формата Anthropic (type text/image) в OpenAI (text / image_url). */
    private fun contentToOpenAIMultimodal(content: JSONArray): JSONArray {
        val out = JSONArray()
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            when (block.optString("type")) {
                "text" -> out.put(JSONObject().put("type", "text").put("text", block.optString("text", "")))
                "image" -> {
                    val src = block.optJSONObject("source") ?: continue
                    val mime = src.optString("media_type", "image/jpeg")
                    val data = src.optString("data", "")
                    out.put(JSONObject().put("type", "image_url").put("image_url",
                        JSONObject().put("url", "data:$mime;base64,$data")))
                }
                else -> {}
            }
        }
        return out
    }

    companion object {
        // PRIMARY: актуальная модель
        private const val MODEL_PRIMARY = "claude-3-5-sonnet-latest"
        // FALLBACK: если primary вернёт 404 (октябрьская версия может быть недоступна в части регионов)
        private const val MODEL_FALLBACK = "claude-3-5-sonnet-20241022"
        private const val MODEL_DEEPSEEK = "deepseek-chat"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        /** Лимит токенов на ответ — не обрезать, писать сколько нужно. */
        private const val MAX_TOKENS = 800
        private const val MAX_MESSAGES_PER_REQUEST = 24
        private const val MAX_CONTENT_CHARS_PER_MESSAGE = 20_000
    }
}
