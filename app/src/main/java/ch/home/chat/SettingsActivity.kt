package ch.home.chat

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import ch.home.chat.service.StorageService

class SettingsActivity : AppCompatActivity() {
    private lateinit var storage: StorageService

    private val restoreChatLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val content = input.bufferedReader().readText()
                val list = storage.loadBackupFromStream(content)
                if (!list.isNullOrEmpty()) {
                    val toSave = if (list.size > StorageService.MAX_STORED) list.takeLast(StorageService.MAX_STORED) else list
                    storage.saveMessagesSync(toSave)
                    Toast.makeText(this, R.string.restore_chat_done, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, R.string.restore_chat_error, Toast.LENGTH_SHORT).show()
                }
            } ?: Toast.makeText(this, R.string.restore_chat_error, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.restore_chat_error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        storage = StorageService(this)
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val list = findViewById<ListView>(R.id.list)
        val items = listOf(
            getString(R.string.change_api_key),
            getString(R.string.export_chat),
            getString(R.string.restore_chat_from_file),
            getString(R.string.change_api_url),
            getString(R.string.model),
            getString(R.string.system_prompt_title),
            getString(R.string.console),
            getString(R.string.attachments)
        )
        list.adapter = ArrayAdapter(this, R.layout.item_settings, items)
        list.setOnItemClickListener { _, _, position, _ ->
            when (position) {
                0 -> startActivity(Intent(this, ApiKeyActivity::class.java).apply {
                    putExtra(ApiKeyActivity.EXTRA_FROM_SETTINGS, true)
                })
                1 -> exportChat()
                2 -> restoreChatLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                3 -> startActivity(Intent(this, ApiUrlActivity::class.java))
                4 -> showModelDialog()
                5 -> startActivity(Intent(this, SystemPromptActivity::class.java))
                6 -> startActivity(Intent(this, ConsoleActivity::class.java))
                7 -> startActivity(Intent(this, AttachmentsActivity::class.java))
            }
        }
    }

    private fun exportChat() {
        val file = storage.exportChatToFile()
        if (file == null || !file.exists()) {
            Toast.makeText(this, R.string.export_chat_error, Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        startActivity(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
        Toast.makeText(this, R.string.export_chat_done, Toast.LENGTH_SHORT).show()
    }

    private fun showModelDialog() {
        val models = arrayOf(
            "Claude 3.5 Sonnet" to "claude-3-5-sonnet-latest",
            "Claude 3 Sonnet" to "claude-3-sonnet-20240229",
            "Claude 3 Opus" to "claude-3-opus-20240229",
            "DeepSeek Chat" to "deepseek-chat",
            "DeepSeek Reasoner" to "deepseek-reasoner"
        )
        val displayNames = models.map { it.first }.toTypedArray()
        val dialog = Dialog(this, R.style.Theme_CH_Dialog)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val root = LayoutInflater.from(this).inflate(R.layout.dialog_model_glass, null)
        root.background = getDrawable(R.drawable.bg_glass_dialog)
        dialog.setContentView(root)
        val list = root.findViewById<ListView>(R.id.modelList)
        list.adapter = ArrayAdapter(this, R.layout.item_settings, displayNames)
        list.setOnItemClickListener { _, _, position, _ ->
            storage.apiModel = models[position].second
            dialog.dismiss()
            Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show()
            if (models[position].second.startsWith("deepseek", ignoreCase = true)) {
                Toast.makeText(this, R.string.model_deepseek_hint, Toast.LENGTH_LONG).show()
            }
        }
        root.findViewById<View>(R.id.dialogCancel).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
