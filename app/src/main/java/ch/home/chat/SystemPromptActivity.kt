package ch.home.chat

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import ch.home.chat.config.SystemPrompt
import ch.home.chat.service.StorageService

class SystemPromptActivity : AppCompatActivity() {
    private lateinit var storage: StorageService
    private lateinit var input: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_system_prompt)
        storage = StorageService(this)
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        input = findViewById(R.id.promptInput)
        val override = storage.getSystemPromptOverride()
        input.setText(if (override.isNotEmpty()) override else SystemPrompt.VALUE)

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val text = input.text?.toString()?.trim() ?: ""
            storage.setSystemPromptOverride(text)
            Toast.makeText(this, getString(R.string.saved), Toast.LENGTH_SHORT).show()
        }
    }
}
