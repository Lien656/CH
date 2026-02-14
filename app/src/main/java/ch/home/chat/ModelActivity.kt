package ch.home.chat

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import ch.home.chat.service.StorageService

/** Настройка ID модели (например claude-3-5-sonnet-latest). Если 404 — зайди сюда и введи модель из console.anthropic.com */
class ModelActivity : AppCompatActivity() {
    private lateinit var storage: StorageService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model)
        storage = StorageService(this)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)?.setNavigationOnClickListener { finish() }
        val input = findViewById<EditText>(R.id.modelInput)
        input.setText(storage.claudeModel)
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val model = input.text?.toString()?.trim()
            if (!model.isNullOrEmpty()) {
                storage.claudeModel = model
                finish()
            }
        }
    }
}
