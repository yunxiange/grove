package dev.grove.reader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import dev.grove.reader.ui.GroveApp

class MainActivity : ComponentActivity() {
    private lateinit var model: GroveViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model=ViewModelProvider(this)[GroveViewModel::class.java]
        setContent { GroveApp(model) }
        if(savedInstanceState==null)handleShare(intent)
    }
    override fun onNewIntent(intent: Intent) {super.onNewIntent(intent);handleShare(intent)}
    private fun handleShare(intent: Intent?) {
        if(intent?.action==Intent.ACTION_SEND) {
            val text=intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            val url=Regex("https?://github\\.com/[A-Za-z0-9-]+/[A-Za-z0-9_.-]+").find(text)?.value
            if(url!=null)model.addRepository(url)
        }
    }
    override fun onPause() {if(::model.isInitialized)model.flushProgress();super.onPause()}
}
