package ch.home.chat

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ch.home.chat.service.StorageService
import java.io.File

class AttachmentsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attachments)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)?.let {
            setSupportActionBar(it)
            it.setNavigationOnClickListener { finish() }
        }
        val paths = mutableSetOf<String>()
        for (m in StorageService(this).getMessages()) {
            m.attachmentPath?.let { if (File(it).exists()) paths.add(it) }
        }
        val dir = File(filesDir, "attachments")
        if (dir.exists()) dir.listFiles()?.forEach { paths.add(it.absolutePath) }
        val list = paths.toList().sortedBy { it }
        val recycler = findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = AttachmentsAdapter(list) { path ->
            try {
                val f = File(path)
                val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, getMime(path))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            } catch (_: Exception) {}
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun getMime(path: String): String {
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "jpg", "jpeg" -> "image/jpeg"
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            "mkv" -> "video/x-matroska"
            "txt" -> "text/plain"
            "py" -> "text/x-python"
            "kt", "kts" -> "text/x-kotlin"
            "js", "mjs" -> "text/javascript"
            "ts" -> "text/typescript"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "html", "htm" -> "text/html"
            "md" -> "text/markdown"
            "csv" -> "text/csv"
            "pdf" -> "application/pdf"
            else -> "*/*"
        }
    }
}

class AttachmentsAdapter(
    private val paths: List<String>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<AttachmentsAdapter.Holder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_attachment, parent, false)
        return Holder(v)
    }

    private fun isImagePath(path: String): Boolean {
        val lower = path.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".png") || lower.endsWith(".gif") || lower.endsWith(".webp")
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val path = paths[position]
        val name = path.substringAfterLast('/')
        holder.name.text = name
        if (isImagePath(path)) {
            try {
                val bmp = BitmapFactory.decodeFile(path)
                holder.thumb.setImageBitmap(bmp)
            } catch (_: Exception) {
                holder.thumb.setImageResource(android.R.drawable.ic_menu_upload)
            }
        } else {
            holder.thumb.setImageResource(android.R.drawable.ic_menu_upload)
        }
        holder.itemView.setOnClickListener { onItemClick(path) }
    }

    override fun getItemCount(): Int = paths.size

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val thumb: ImageView = itemView.findViewById(R.id.thumb)
        val name: TextView = itemView.findViewById(R.id.name)
    }
}
