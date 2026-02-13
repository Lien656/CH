package ch.home.chat.util

data class ParsedFile(val fileName: String, val content: String)

object FileParse {
    private val regex = Regex("\\[FILE:([^\\]]+)\\]([\\s\\S]*?)\\[/FILE\\]")

    fun parse(content: String): Pair<String, List<ParsedFile>> {
        val files = mutableListOf<ParsedFile>()
        val textWithoutFiles = regex.replace(content) { match ->
            val name = match.groupValues[1].trim()
            val body = match.groupValues[2].trim()
            if (name.isNotEmpty()) files.add(ParsedFile(name, body))
            ""
        }.trim()
        return textWithoutFiles to files
    }
}
