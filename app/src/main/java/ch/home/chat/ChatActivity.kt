package ch.home.chat

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ch.home.chat.model.ChatMessage
import ch.home.chat.service.ApiService
import ch.home.chat.service.StorageService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatActivity : AppCompatActivity() {

    companion object {
        /** true когда чат на экране — уведомление о ответе не показываем */
        var isChatOnScreen = false
        /** ответ пришёл, пока чат был в фоне — при следующем onResume обновить список и проскроллить */
        var pendingReplyFromBackground = false
        /** вызывается, когда ответ готов — чтобы чат обновился сразу, не только по broadcast */
        var onReplyReady: (() -> Unit)? = null
    }

    private lateinit var storage: StorageService
    private lateinit var recycler: RecyclerView
    private lateinit var input: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()
    private var loading = false
    private var typingMessageIndex = -1
    private var typingLength = 0
    private var typingJob: Job? = null
    private var pendingAttachmentPath: String? = null
    private var cameraPhotoPath: String? = null

    private val replyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ch.home.chat.service.CHChatService.ACTION_REPLY_READY) return
            loading = false
            adapter.showTyping = false
            refreshMessagesFromStorage()
            if (messages.isNotEmpty() && messages.last().role == "assistant") {
                typingMessageIndex = messages.size - 1
                typingLength = 0
                startTypingAnimation()
            }
        }
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
        val path = cameraPhotoPath
        cameraPhotoPath = null
        if (success && path != null && java.io.File(path).exists()) {
            pendingAttachmentPath = path
            updatePendingAttachmentUi()
        }
    }

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        try {
            val name = resolveFileName(uri) ?: "file_${System.currentTimeMillis()}"
            val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifEmpty { "file" }
            val dir = java.io.File(filesDir, "attachments").also { if (!it.exists()) it.mkdirs() }
            val file = java.io.File(dir, safeName)
            contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            if (file.exists()) {
                pendingAttachmentPath = file.absolutePath
                updatePendingAttachmentUi()
            }
        } catch (_: Exception) {}
    }

    private fun resolveFileName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        storage = StorageService(this)
        recycler = findViewById(R.id.recycler)
        input = findViewById(R.id.inputMessage)
        btnSend = findViewById(R.id.btnSend)
        val btnAttach = findViewById<ImageButton>(R.id.btnAttach)
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { } // no back from chat to api key
        supportActionBar?.setDisplayShowTitleEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        toolbar.menu.clear()
        toolbar.inflateMenu(R.menu.chat_menu)
        // Тап по заголовку открывает настройки (как у Kael)
        toolbar.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        adapter = ChatAdapter(
            messages = messages,
            typingIndex = { typingMessageIndex },
            typingLength = { typingLength },
            onLongClickMessage = { text ->
                (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(
                    ClipData.newPlainText("message", text)
                )
                Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
            },
            onDownloadFile = { fileName, content ->
                try {
                    val dir = java.io.File(filesDir, "downloads").also { if (!it.exists()) it.mkdirs() }
                    val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifEmpty { "file.txt" }
                    val file = java.io.File(dir, safeName)
                    file.writeText(content)
                    val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                    startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "text/*"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) })
                    Toast.makeText(this, getString(R.string.saved) + " " + safeName, Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    Toast.makeText(this, R.string.error_save, Toast.LENGTH_SHORT).show()
                }
            },
            onOpenCreatedFile = { filePath ->
                try {
                    val file = java.io.File(filePath)
                    if (file.exists()) {
                        val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                        val mime = when (filePath.substringAfterLast('.').lowercase()) {
                            "json" -> "application/json"
                            "html", "htm" -> "text/html"
                            "xml" -> "application/xml"
                            "md" -> "text/markdown"
                            else -> "text/*"
                        }
                        startActivity(Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, mime)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        })
                    }
                } catch (_: Exception) {}
            }
        )
        recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recycler.adapter = adapter

        messages.addAll(storage.getMessages())
        adapter.notifyDataSetChanged()
        scrollToBottom()

        btnSend.isEnabled = true
        btnSend.setOnClickListener { send() }
        input.imeOptions = EditorInfo.IME_ACTION_SEND
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                send()
                true
            } else false
        }
        btnAttach.setOnClickListener { showAttachOptions() }
        findViewById<ImageButton>(R.id.btnRemoveAttachment).setOnClickListener {
            pendingAttachmentPath = null
            updatePendingAttachmentUi()
        }
        updatePendingAttachmentUi()
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.settings, R.id.settings_overflow -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun send() {
        val text = input.text?.toString()?.trim() ?: ""
        val attachmentPath = pendingAttachmentPath
        if ((text.isEmpty() && attachmentPath == null) || loading) return
        input.setText("")
        pendingAttachmentPath = null
        updatePendingAttachmentUi()
        val userMsg = ChatMessage(
            role = "user",
            content = if (text.isEmpty()) "(файл)" else text,
            attachmentPath = attachmentPath
        )
        messages.add(userMsg)
        adapter.notifyItemInserted(messages.size - 1)
        scrollToBottom()
        saveMessages()

        val key = storage.effectiveKey()
        if (key.isNullOrEmpty()) {
            addAssistantMessage("API ключ не задан. Зайдите в настройки.")
            return
        }
        lifecycleScope.launch {
            val err = withContext(Dispatchers.IO) {
                ApiService(key, storage.effectiveBase(), storage.effectiveModel()).checkApiConnection()
            }
            if (err != null) {
                addAssistantMessage(err)
                return@launch
            }
            loading = true
            scrollToBottom()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this@ChatActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
            }
            startService(Intent(this@ChatActivity, ch.home.chat.service.CHChatService::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        isChatOnScreen = true
        onReplyReady = {
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                loading = false
                adapter.showTyping = false
                refreshMessagesFromStorage()
                if (messages.isNotEmpty() && messages.last().role == "assistant") {
                    typingMessageIndex = messages.size - 1
                    typingLength = 0
                    startTypingAnimation()
                }
            }
        }
        registerReceiver(replyReceiver, IntentFilter(ch.home.chat.service.CHChatService.ACTION_REPLY_READY), if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_NOT_EXPORTED else 0)
        refreshMessagesFromStorage()
        if (messages.isNotEmpty() && messages.last().role == "assistant") {
            loading = false
            adapter.showTyping = false
            adapter.notifyDataSetChanged()
        }
        if (pendingReplyFromBackground) {
            pendingReplyFromBackground = false
            loading = false
            adapter.showTyping = false
            refreshMessagesFromStorage()
            scrollToBottom()
            if (messages.isNotEmpty() && messages.last().role == "assistant") {
                typingMessageIndex = messages.size - 1
                typingLength = 0
                startTypingAnimation()
            }
        }
    }

    private fun refreshMessagesFromStorage() {
        messages.clear()
        messages.addAll(storage.getMessages())
        adapter.notifyDataSetChanged()
        scrollToBottom()
    }

    override fun onPause() {
        super.onPause()
        isChatOnScreen = false
        onReplyReady = null
        try { unregisterReceiver(replyReceiver) } catch (_: Exception) {}
    }

    private fun startTypingAnimation() {
        typingJob?.cancel()
        val full = messages.getOrNull(typingMessageIndex)?.content ?: ""
        if (full.isEmpty()) {
            typingMessageIndex = -1
            typingLength = 0
            adapter.notifyItemChanged(messages.size - 1)
            return
        }
        val animateUpTo = minOf(full.length, 350)
        val stepSize = maxOf(1, animateUpTo / 35)
        typingJob = lifecycleScope.launch {
            for (i in (1..animateUpTo).step(stepSize)) {
                delay(50)
                typingLength = i
                adapter.notifyItemChanged(typingMessageIndex)
                recycler.post { scrollToBottom() }
            }
            typingLength = full.length
            adapter.notifyItemChanged(typingMessageIndex)
            delay(60)
            typingMessageIndex = -1
            typingLength = 0
            adapter.notifyItemChanged(messages.size - 1)
        }
    }

    private fun addAssistantMessage(content: String) {
        messages.add(ChatMessage(role = "assistant", content = content))
        trimMessages()
        saveMessages()
        adapter.notifyItemInserted(messages.size - 1)
        scrollToBottom()
    }

    private fun trimMessages() {
        while (messages.size > StorageService.MAX_STORED) messages.removeAt(0)
    }

    private fun saveMessages() {
        storage.saveMessages(messages)
    }

    private fun scrollToBottom() {
        if (adapter.itemCount > 0) recycler.smoothScrollToPosition(adapter.itemCount - 1)
    }

    private fun userFriendlyError(e: Exception): String? {
        val s = e.message?.lowercase() ?: ""
        return when {
            s.contains("socket") || s.contains("connection") || s.contains("network") ->
                "Не удалось подключиться к серверу. Проверьте интернет или VPN/DNS."
            s.contains("401") || s.contains("неверный") || s.contains("invalid") ->
                "Неверный API ключ. Проверьте ключ в настройках."
            else -> "Ошибка: ${e.message}"
        }
    }

    private fun showAttachOptions() {
        val sheet = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_attach, null)
        sheet.setContentView(view)
        view.findViewById<View>(R.id.btnSheetCamera).setOnClickListener {
            sheet.dismiss()
            takePicture()
        }
        view.findViewById<View>(R.id.btnSheetFiles).setOnClickListener {
            sheet.dismiss()
            pickFileLauncher.launch("*/*")
        }
        sheet.show()
    }

    private fun takePicture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
            return
        }
        try {
            val dir = java.io.File(filesDir, "attachments").also { if (!it.exists()) it.mkdirs() }
            val file = java.io.File(dir, "photo_${System.currentTimeMillis()}.jpg")
            cameraPhotoPath = file.absolutePath
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            takePictureLauncher.launch(uri)
        } catch (_: Exception) {}
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) takePicture()
    }

    private fun pickFile() {
        pickFileLauncher.launch("*/*")
    }

    private fun updatePendingAttachmentUi() {
        val container = findViewById<LinearLayout>(R.id.pendingAttachmentContainer)
        val thumb = findViewById<ImageView>(R.id.pendingAttachmentThumb)
        val nameView = findViewById<android.widget.TextView>(R.id.pendingAttachmentName)
        val path = pendingAttachmentPath
        if (path != null) {
            container.visibility = View.VISIBLE
            val fileName = path.substringAfterLast('/')
            nameView.text = fileName
            nameView.visibility = View.VISIBLE
            try {
                val bmp = BitmapFactory.decodeFile(path)
                if (bmp != null) {
                    thumb.setImageBitmap(bmp)
                    thumb.visibility = View.VISIBLE
                } else {
                    thumb.setImageResource(android.R.drawable.ic_menu_upload)
                    thumb.visibility = View.VISIBLE
                }
            } catch (_: Exception) {
                thumb.setImageResource(android.R.drawable.ic_menu_upload)
                thumb.visibility = View.VISIBLE
            }
        } else {
            container.visibility = View.GONE
            thumb.setImageDrawable(null)
            nameView.text = ""
        }
    }
}
