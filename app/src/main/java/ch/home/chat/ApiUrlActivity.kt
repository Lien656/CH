package ch.home.chat

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import ch.home.chat.service.StorageService

class ApiUrlActivity : AppCompatActivity() {
    private lateinit var storage: StorageService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_api_url)
        storage = StorageService(this)
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        val input = findViewById<EditText>(R.id.apiUrlInput)
        input.setText(storage.apiBase)
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val url = input.text?.toString()?.trim()
            if (!url.isNullOrEmpty()) {
                storage.apiBase = url
                finish()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
