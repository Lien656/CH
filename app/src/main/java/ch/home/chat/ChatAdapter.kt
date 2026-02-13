package ch.home.chat

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import ch.home.chat.model.ChatMessage
import ch.home.chat.util.FileParse
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter(
    private val messages: List<ChatMessage>,
    private val typingIndex: () -> Int,
    private val typingLength: () -> Int,
    private val onLongClickMessage: (String) -> Unit,
    private val onDownloadFile: (fileName: String, content: String) -> Unit = { _, _ -> },
    private val onOpenCreatedFile: (filePath: String) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var showTyping = false

    companion object {
        private const val TYPE_LEFT = 0
        private const val TYPE_RIGHT = 1
        private const val TYPE_TYPING = 2
    }

    override fun getItemViewType(position: Int): Int {
        if (showTyping && position == messages.size) return TYPE_TYPING
        val m = messages[position]
        return if (m.isUser) TYPE_RIGHT else TYPE_LEFT
    }

    override fun getItemCount(): Int = messages.size + if (showTyping) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_LEFT -> {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_message_left, parent, false)
                LeftHolder(v, onLongClickMessage, onOpenCreatedFile)
            }
            TYPE_RIGHT -> {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_message_right, parent, false)
                RightHolder(v, onLongClickMessage)
            }
            else -> {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_typing, parent, false)
                TypingHolder(v)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is LeftHolder -> {
                val m = messages[position]
                val isTyping = position == typingIndex()
                val len = typingLength()
                val display = if (isTyping && len < m.content.length) m.content.take(len) else m.content
                holder.bind(m.displayName, display, m.content, m.timestamp, m.createdFilePath, onDownloadFile)
            }
            is RightHolder -> {
                val m = messages[position]
                holder.bind(m.displayName, m.content, m.content, m.attachmentPath, m.timestamp)
            }
            is TypingHolder -> holder.startDots()
        }
    }

    class LeftHolder(itemView: View, private val onLongClick: (String) -> Unit, private val onOpenCreatedFile: (String) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.senderName)
        private val time: TextView = itemView.findViewById(R.id.messageTime)
        private val text: TextView = itemView.findViewById(R.id.messageText)
        private val fileContainer: LinearLayout = itemView.findViewById(R.id.messageFileContainer)

        fun bind(displayName: String, displayText: String, fullText: String, timestamp: Long, createdFilePath: String?, onDownloadFile: (String, String) -> Unit) {
            name.text = displayName
            time.text = formatTime(timestamp)
            text.alpha = 1f
            fileContainer.removeAllViews()
            if (!createdFilePath.isNullOrEmpty()) {
                val row = LinearLayout(itemView.context).apply { orientation = LinearLayout.VERTICAL }
                val fileName = createdFilePath.substringAfterLast('/')
                val pathLabel = TextView(itemView.context).apply {
                    text = "📄 $fileName — claudeFiles"
                    setTextColor(ContextCompat.getColor(itemView.context, R.color.text))
                    textSize = 12f
                }
                val openBtn = Button(itemView.context).apply {
                    text = "Открыть"
                    setOnClickListener { onOpenCreatedFile(createdFilePath) }
                }
                row.addView(pathLabel)
                row.addView(openBtn)
                fileContainer.addView(row)
            }
            if (displayText.length >= fullText.length) {
                val (textPart, files) = FileParse.parse(fullText)
                text.text = if (textPart.isNotEmpty()) textPart else fullText
                for (f in files) {
                val row = LayoutInflater.from(itemView.context).inflate(android.R.layout.simple_list_item_1, fileContainer, false)
                val tv = row.findViewById<TextView>(android.R.id.text1)
                tv.text = "↓ ${f.fileName}"
                tv.setTextColor(ContextCompat.getColor(itemView.context, R.color.text))
                tv.textSize = 14f
                row.setOnClickListener { onDownloadFile(f.fileName, f.content) }
                    fileContainer.addView(row)
                }
            } else {
                text.text = displayText
            }
            itemView.setOnLongClickListener {
                if (fullText.isNotEmpty()) onLongClick(fullText)
                true
            }
        }

        private fun formatTime(ts: Long): String {
            if (ts <= 0) return ""
            val d = Date(ts)
            val now = Date()
            return if (d.date == now.date && d.month == now.month && d.year == now.year) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(d)
            } else {
                SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(d)
            }
        }
    }

    class RightHolder(itemView: View, private val onLongClick: (String) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.senderName)
        private val time: TextView = itemView.findViewById(R.id.messageTime)
        private val text: TextView = itemView.findViewById(R.id.messageText)
        private val attachment: ImageView = itemView.findViewById(R.id.messageAttachment)

        private fun isImagePath(path: String): Boolean {
            val lower = path.lowercase()
            return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".gif") || lower.endsWith(".webp")
        }

        fun bind(displayName: String, displayText: String, fullText: String, attachmentPath: String?, timestamp: Long) {
            name.text = displayName
            time.text = formatTime(timestamp)
            val fileName = attachmentPath?.substringAfterLast('/') ?: ""
            val showAsFileOnly = displayText == "(файл)" && fileName.isNotEmpty()
            text.text = if (showAsFileOnly) "📎 $fileName" else displayText
            text.alpha = 1f
            if (!attachmentPath.isNullOrEmpty()) {
                attachment.visibility = View.VISIBLE
                if (isImagePath(attachmentPath)) {
                    try {
                        val bmp = BitmapFactory.decodeFile(attachmentPath)
                        if (bmp != null) {
                            attachment.setImageBitmap(bmp)
                        } else {
                            attachment.setImageResource(android.R.drawable.ic_menu_upload)
                        }
                    } catch (_: Exception) {
                        attachment.setImageResource(android.R.drawable.ic_menu_upload)
                    }
                } else {
                    attachment.setImageResource(android.R.drawable.ic_menu_upload)
                }
            } else {
                attachment.visibility = View.GONE
            }
            itemView.setOnLongClickListener {
                if (fullText.isNotEmpty()) onLongClick(fullText)
                true
            }
        }

        private fun formatTime(ts: Long): String {
            if (ts <= 0) return ""
            val d = Date(ts)
            val now = Date()
            return if (d.date == now.date && d.month == now.month && d.year == now.year) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(d)
            } else {
                SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(d)
            }
        }
    }

    class TypingHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun startDots() {}
        fun stopDots() {}
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is TypingHolder) holder.stopDots()
        super.onViewRecycled(holder)
    }
}
