package ch.home.chat.service

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Поиск и загрузка страниц для агента Claude.
 * [ПОИСК: запрос] — результаты поиска; [ОТКРОЙ: url] — текст страницы.
 */
object WebFetch {
    private const val MAX_PAGE_CHARS = 35_000
    private const val MAX_SEARCH_CHARS = 12_000
    private const val TIMEOUT_SEC = 15

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SEC.toLong(), TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SEC.toLong(), TimeUnit.SECONDS)
        .build()

    fun search(query: String): String? {
        if (query.isBlank()) return null
        val q = query.trim().take(200)
        return try {
            val body = FormBody.Builder().add("q", q).build()
            val req = Request.Builder()
                .url("https://html.duckduckgo.com/html/")
                .post(body)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) ClaudeHome/1.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val html = resp.body?.string() ?: return null
                parseSearchResults(html)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSearchResults(html: String): String {
        val snippets = mutableListOf<String>()
        val snippetRegex = Regex("""result__snippet[^>]*>([^<]+)""", RegexOption.IGNORE_CASE)
        snippetRegex.findAll(html).forEach { match ->
            val text = match.groupValues.getOrNull(1)?.let { decodeHtml(it.trim()) }?.take(500) ?: return@forEach
            if (text.length > 15) snippets.add(text)
        }
        if (snippets.isEmpty()) {
            val fallback = htmlToPlainText(html).take(MAX_SEARCH_CHARS)
            return if (fallback.length > 100) "Результаты поиска (текст страницы):\n$fallback" else "Поиск не вернул результатов."
        }
        val combined = snippets.take(15).mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n\n")
        return combined.take(MAX_SEARCH_CHARS).let { if (it.length == MAX_SEARCH_CHARS) "$it…" else it }
    }

    private fun decodeHtml(s: String): String {
        return s.replace("&nbsp;", " ", ignoreCase = true)
            .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
            .replace("&quot;", "\"").replace(Regex("&#?\\w+;"), " ")
            .replace(Regex("\\s+"), " ").trim()
    }

    fun fetchPageContent(url: String): String? {
        if (url.isBlank()) return null
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return null
        return try {
            val req = Request.Builder().url(trimmed).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val text = htmlToPlainText(body)
                if (text.length > MAX_PAGE_CHARS) text.take(MAX_PAGE_CHARS) + "\n… (обрезано)" else text
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun htmlToPlainText(html: String): String {
        var s = html
        s = Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE).replace(s, " ")
        s = Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE).replace(s, " ")
        s = Regex("<[^>]+>").replace(s, " ")
        s = Regex("&nbsp;", RegexOption.IGNORE_CASE).replace(s, " ")
        s = Regex("&lt;").replace(s, "<")
        s = Regex("&gt;").replace(s, ">")
        s = Regex("&amp;").replace(s, "&")
        s = Regex("&quot;").replace(s, "\"")
        s = Regex("&#?\\w+;").replace(s, " ")
        s = Regex("\\s+").replace(s, " ").trim()
        return s
    }
}
