package ch.home.chat

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import ch.home.chat.service.CHChatService

class CHApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == CHChatService.ACTION_REPLY_READY) {
                    ChatActivity.pendingReplyFromBackground = true
                }
            }
        }
        val filter = IntentFilter(CHChatService.ACTION_REPLY_READY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }
}
