package ch.home.chat

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import ch.home.chat.service.StorageService

/** Ключ Claude (Sonnet) для «глаз» — распознавание картинок. */
class VisionEyesActivity : AppCompatActivity() {
    private lateinit var storage: StorageService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vision_eyes)
        storage = StorageService(this)
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        findViewById<EditText>(R.id.visionKeyInput).setText(storage.claudeApiKey ?: "")
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val key = findViewById<EditText>(R.id.visionKeyInput).text?.toString()?.trim()
            if (!key.isNullOrEmpty()) storage.claudeApiKey = key
            finish()
        }
    }
}
