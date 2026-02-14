package ch.home.chat

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ch.home.chat.model.ChatMessage
import ch.home.chat.service.StorageService

/** Поиск по чату по словам. */
class SearchChatActivity : AppCompatActivity() {
    private lateinit var storage: StorageService
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: SearchAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_chat)
        storage = StorageService(this)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)?.let {
            setSupportActionBar(it)
            it.setNavigationOnClickListener { finish() }
        }
        val queryInput = findViewById<android.widget.EditText>(R.id.searchQuery)
        recycler = findViewById(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = SearchAdapter(emptyList(), "")
        recycler.adapter = adapter

        fun runSearch() {
            val q = queryInput.text?.toString()?.trim() ?: ""
            if (q.isEmpty()) {
                adapter.update(emptyList(), "")
                return
            }
            val messages = storage.getMessages()
            val lowerQ = q.lowercase()
            val matched = messages.filter { m ->
                (m.content ?: "").lowercase().contains(lowerQ)
            }
            adapter.update(matched, q)
        }

        queryInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { runSearch() }
        })
        runSearch()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

private class SearchAdapter(
    private var items: List<ChatMessage>,
    private var query: String
) : RecyclerView.Adapter<SearchAdapter.Holder>() {

    fun update(messages: List<ChatMessage>, q: String) {
        items = messages
        query = q
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_search_result, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val m = items[position]
        val role = if (m.role == "assistant") "Claude" else "Ты"
        holder.role.text = role
        holder.role.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.context,
            if (m.role == "assistant") ch.home.chat.R.color.name_claude else ch.home.chat.R.color.name_you))
        val content = m.content ?: ""
        val snippet = if (content.length > 200) content.take(200) + "…" else content
        holder.snippet.text = snippet
    }

    override fun getItemCount(): Int = items.size

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val role: TextView = itemView.findViewById(R.id.searchRole)
        val snippet: TextView = itemView.findViewById(R.id.searchSnippet)
    }
}
