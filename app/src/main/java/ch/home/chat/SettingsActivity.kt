package ch.home.chat

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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

        val claudeKeyInput = findViewById<EditText>(R.id.claudeKeyInput)
        val deepseekKeyInput = findViewById<EditText>(R.id.deepseekKeyInput)
        val deepseekUrlInput = findViewById<EditText>(R.id.deepseekUrlInput)
        val claudeModelLabel = findViewById<TextView>(R.id.claudeModelLabel)
        val deepseekModelLabel = findViewById<TextView>(R.id.deepseekModelLabel)
        val modelValue = findViewById<TextView>(R.id.modelValue)

        claudeKeyInput.setText(storage.claudeApiKey ?: "")
        deepseekKeyInput.setText(storage.deepseekApiKey ?: "")
        deepseekUrlInput.setText(storage.deepseekApiBase)
        claudeModelLabel.text = claudeModelDisplayName(storage.claudeModel)
        deepseekModelLabel.text = deepseekModelDisplayName(storage.deepseekModel)
        modelValue.text = if (storage.useClaude) getString(R.string.provider_claude) else getString(R.string.provider_deepseek)

        claudeModelLabel.setOnClickListener { showClaudeModelDialog { storage.claudeModel = it; claudeModelLabel.text = claudeModelDisplayName(it) } }
        deepseekModelLabel.setOnClickListener { showDeepseekModelDialog { storage.deepseekModel = it; deepseekModelLabel.text = deepseekModelDisplayName(it) } }

        findViewById<View>(R.id.modelRow).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.model)
                .setItems(arrayOf(getString(R.string.provider_claude), getString(R.string.provider_deepseek))) { _, which ->
                    storage.useClaude = (which == 0)
                    modelValue.text = if (storage.useClaude) getString(R.string.provider_claude) else getString(R.string.provider_deepseek)
                    Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        findViewById<View>(R.id.itemExport).setOnClickListener { exportChat() }
        findViewById<View>(R.id.itemRestore).setOnClickListener { restoreChatLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
        findViewById<View>(R.id.itemSystemPrompt).setOnClickListener { startActivity(Intent(this, SystemPromptActivity::class.java)) }
        findViewById<View>(R.id.itemConsole).setOnClickListener { startActivity(Intent(this, ConsoleActivity::class.java)) }
        findViewById<View>(R.id.itemAttachments).setOnClickListener { startActivity(Intent(this, AttachmentsActivity::class.java)) }
    }

    override fun onPause() {
        super.onPause()
        storage.claudeApiKey = findViewById<EditText>(R.id.claudeKeyInput).text?.toString()?.trim()
        storage.deepseekApiKey = findViewById<EditText>(R.id.deepseekKeyInput).text?.toString()?.trim()
        val url = findViewById<EditText>(R.id.deepseekUrlInput).text?.toString()?.trim()
        if (!url.isNullOrEmpty()) storage.deepseekApiBase = url
    }

    private fun claudeModelDisplayName(id: String): String = when (id) {
        "claude-3-5-sonnet-latest" -> "Claude 3.5 Sonnet"
        "claude-3-sonnet-20240229" -> "Claude 3 Sonnet"
        "claude-3-opus-20240229" -> "Claude 3 Opus"
        else -> id
    }

    private fun deepseekModelDisplayName(id: String): String = when (id) {
        "deepseek-chat" -> "DeepSeek Chat"
        "deepseek-reasoner" -> "DeepSeek Reasoner"
        else -> id
    }

    private fun showClaudeModelDialog(onPick: (String) -> Unit) {
        val models = arrayOf(
            "Claude 3.5 Sonnet" to "claude-3-5-sonnet-latest",
            "Claude 3 Sonnet" to "claude-3-sonnet-20240229",
            "Claude 3 Opus" to "claude-3-opus-20240229"
        )
        showModelDialog(models) { onPick(it) }
    }

    private fun showDeepseekModelDialog(onPick: (String) -> Unit) {
        val models = arrayOf(
            "DeepSeek Chat" to "deepseek-chat",
            "DeepSeek Reasoner" to "deepseek-reasoner"
        )
        showModelDialog(models) { onPick(it) }
    }

    private fun showModelDialog(models: Array<Pair<String, String>>, onPick: (String) -> Unit) {
        val displayNames = models.map { it.first }.toTypedArray()
        val dialog = Dialog(this, R.style.Theme_CH_Dialog)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val root = LayoutInflater.from(this).inflate(R.layout.dialog_model_glass, null)
        root.background = getDrawable(R.drawable.bg_glass_dialog)
        dialog.setContentView(root)
        val list = root.findViewById<ListView>(R.id.modelList)
        list.adapter = ArrayAdapter(this, R.layout.item_settings, displayNames)
        list.setOnItemClickListener { _: AdapterView<*>, _: View, position: Int, _: Long ->
            onPick(models[position].second)
            dialog.dismiss()
            Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show()
        }
        root.findViewById<View>(R.id.dialogCancel).setOnClickListener { dialog.dismiss() }
        dialog.show()
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
}
