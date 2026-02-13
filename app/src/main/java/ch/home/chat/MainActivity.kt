package ch.home.chat

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import ch.home.chat.service.StorageService

class MainActivity : AppCompatActivity() {
    private lateinit var storage: StorageService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        storage = StorageService(this)
        if (storage.isFirstRun) {
            showFragment(ApiKeyFragment.newInstance(storage) {
                storage.setFirstRunDone()
                openChat()
            })
        } else {
            openChat()
        }
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .commit()
    }

    private fun openChat() {
        startActivity(Intent(this, ChatActivity::class.java))
        finish()
    }
}
