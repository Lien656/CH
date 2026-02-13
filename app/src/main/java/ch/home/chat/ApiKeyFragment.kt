package ch.home.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import ch.home.chat.service.StorageService

class ApiKeyFragment : Fragment() {
    private var storage: StorageService? = null
    private var onDone: (() -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_api_key, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val input = view.findViewById<EditText>(R.id.apiKeyInput)
        val btn = view.findViewById<Button>(R.id.btnSave)
        btn.setOnClickListener {
            val key = input.text?.toString()?.trim()
            if (key.isNullOrEmpty()) return@setOnClickListener
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.first_run_provider_title)
                .setMessage(R.string.first_run_provider_message)
                .setPositiveButton(R.string.provider_claude) { _, _ ->
                    storage?.claudeApiKey = key
                    storage?.useClaude = true
                    storage?.setFirstRunDone()
                    onDone?.invoke()
                }
                .setNegativeButton(R.string.provider_deepseek) { _, _ ->
                    storage?.deepseekApiKey = key
                    storage?.useClaude = false
                    storage?.setFirstRunDone()
                    onDone?.invoke()
                }
                .setCancelable(false)
                .show()
        }
    }

    companion object {
        fun newInstance(storage: StorageService, onDone: () -> Unit): ApiKeyFragment {
            return ApiKeyFragment().apply {
                this.storage = storage
                this.onDone = onDone
            }
        }
    }
}
