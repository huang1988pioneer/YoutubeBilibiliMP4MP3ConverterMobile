package com.huang1988pioneer.mediaconverter

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.huang1988pioneer.mediaconverter.engine.DownloadPolicy
import com.huang1988pioneer.mediaconverter.ui.ConverterTheme
import com.huang1988pioneer.mediaconverter.ui.HomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeShare(intent)
        setContent {
            ConverterTheme {
                HomeScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeShare(intent)
    }

    private fun consumeShare(intent: Intent?) {
        val raw = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        } ?: return
        val urls = DownloadPolicy.splitUrls(raw)
        Session.setUrl(if (urls.isEmpty()) raw.trim() else urls.joinToString("\n"))
    }
}
