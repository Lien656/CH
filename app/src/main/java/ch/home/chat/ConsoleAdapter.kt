package ch.home.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import ch.home.chat.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConsoleAdapter(private val messages: List<ChatMessage>) : RecyclerView.Adapter<ConsoleAdapter.Holder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val m = messages[position]
        val ts = if (m.timestamp > 0) FORMAT.format(Date(m.timestamp)) else ""
        holder.text1.text = "${m.displayName} (${m.role}) $ts"
        holder.text2.text = m.content
    }

    override fun getItemCount(): Int = messages.size

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text1: TextView = itemView.findViewById(android.R.id.text1)
        val text2: TextView = itemView.findViewById(android.R.id.text2)

        init {
            val ctx = itemView.context
            text1.setTextColor(ContextCompat.getColor(ctx, R.color.text))
            text2.setTextColor(ContextCompat.getColor(ctx, R.color.text))
        }
    }

    companion object {
        private val FORMAT = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    }
}
