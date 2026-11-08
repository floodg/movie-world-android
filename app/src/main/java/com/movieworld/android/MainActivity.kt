package com.movieworld.android

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.movieworld.android.ui.AppGraph
import com.movieworld.android.ui.MovieWorldApp
import com.movieworld.android.ui.theme.MovieWorldTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val graph = AppGraph(applicationContext, ::openTelegram)
        setContent {
            MovieWorldTheme { MovieWorldApp(graph) }
        }
    }

    override fun onResume() {
        super.onResume()
        CloudSync.get(applicationContext).reconcileNow()
    }

    private fun openTelegram(link: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).addCategory(Intent.CATEGORY_BROWSABLE)
        try {
            startActivity(Intent(intent).setPackage("org.telegram.messenger"))
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, "Install Telegram or a web browser to open this post.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
