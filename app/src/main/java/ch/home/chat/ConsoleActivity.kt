package ch.home.chat

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ch.home.chat.service.StorageService

class ConsoleActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_console)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)?.let {
            setSupportActionBar(it)
            it.setNavigationOnClickListener { finish() }
        }
        val recycler = findViewById<RecyclerView>(R.id.recycler)
        val messages = StorageService(this).getMessages()
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = ConsoleAdapter(messages)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
