package io.legado.app.video.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import io.legado.app.video.api.ApiProviderFactory
import io.legado.app.video.api.VideoApiConfigManager
import io.legado.app.video.ui.theme.VideoColors

class ProviderSettingsActivity : ComponentActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, ProviderSettingsActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VideoApiConfigManager.init(applicationContext)
        ApiProviderFactory.ensureInitialized(applicationContext)
        
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = VideoColors.Primary,
                    secondary = VideoColors.Secondary,
                    surface = VideoColors.Surface,
                    background = VideoColors.Background,
                    onPrimary = VideoColors.OnPrimary,
                    onSurface = VideoColors.OnSurface,
                    onBackground = VideoColors.OnBackground
                )
            ) {
                ProviderSettingsScreen(onBack = { finish() })
            }
        }
    }
}
