package ch.home.chat

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import ch.home.chat.service.StorageService

class ApiKeyActivity : AppCompatActivity() {
    private lateinit var storage: StorageService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_api_key)
        storage = StorageService(this)
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        val input = findViewById<EditText>(R.id.apiKeyInput)
        input.setText(storage.apiKey ?: "")
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val key = input.text?.toString()?.trim()
            if (!key.isNullOrEmpty()) {
                storage.apiKey = key
                finish()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val EXTRA_FROM_SETTINGS = "from_settings"
    }
}
