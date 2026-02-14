package ch.home.chat

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.appcompat.widget.SwitchCompat
import ch.home.chat.service.StorageService

class SettingsActivity : AppCompatActivity() {
    private lateinit var storage: StorageService
    private var headerView: View? = null

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
        headerView = LayoutInflater.from(this).inflate(R.layout.ch_settings_model_header, list, false)
        list.addHeaderView(headerView, null, false)

        val switchSonnet = headerView!!.findViewById<SwitchCompat>(R.id.switchSonnet)
        val switchDeepSeek = headerView!!.findViewById<SwitchCompat>(R.id.switchDeepSeek)
        val claudeKeyInput = headerView!!.findViewById<EditText>(R.id.claudeKeyInput)
        val deepseekKeyInput = headerView!!.findViewById<EditText>(R.id.deepseekKeyInput)
        val deepseekUrlInput = headerView!!.findViewById<EditText>(R.id.deepseekUrlInput)

        claudeKeyInput.setText(storage.claudeApiKey ?: "")
        deepseekKeyInput.setText(storage.deepseekApiKey ?: "")
        deepseekUrlInput.setText(storage.deepseekApiBase)
        switchSonnet.isChecked = storage.useClaude
        switchDeepSeek.isChecked = !storage.useClaude

        switchSonnet.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                storage.useClaude = true
                switchDeepSeek.isChecked = false
            }
        }
        switchDeepSeek.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                storage.useClaude = false
                switchSonnet.isChecked = false
            }
        }

        val items = listOf(
            getString(R.string.export_chat),
            getString(R.string.restore_chat_from_file),
            getString(R.string.system_prompt_title),
            getString(R.string.console),
            getString(R.string.attachments)
        )
        list.adapter = ArrayAdapter(this, R.layout.item_settings, items)
        list.setOnItemClickListener { _: AdapterView<*>, _: View, position: Int, _: Long ->
            val index = position - 1
            if (index < 0) return@setOnItemClickListener
            when (index) {
                0 -> exportChat()
                1 -> restoreChatLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                2 -> startActivity(Intent(this, SystemPromptActivity::class.java))
                3 -> startActivity(Intent(this, ConsoleActivity::class.java))
                4 -> startActivity(Intent(this, AttachmentsActivity::class.java))
            }
        }
    }

    override fun onPause() {
        super.onPause()
        headerView?.let { h ->
            storage.claudeApiKey = h.findViewById<EditText>(R.id.claudeKeyInput).text?.toString()?.trim()
            storage.deepseekApiKey = h.findViewById<EditText>(R.id.deepseekKeyInput).text?.toString()?.trim()
            val url = h.findViewById<EditText>(R.id.deepseekUrlInput).text?.toString()?.trim()
            if (!url.isNullOrEmpty()) storage.deepseekApiBase = url
            storage.useClaude = h.findViewById<SwitchCompat>(R.id.switchSonnet).isChecked
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
}
