package io.legado.app.video.templates

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import io.legado.app.video.ui.theme.VideoColors

class TemplateBrowseActivity : ComponentActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, TemplateBrowseActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                TemplateBrowseScreen(
                    onSelectTemplate = { template ->
                        val intent = Intent().apply {
                            putExtra("templateId", template.id)
                        }
                        setResult(RESULT_OK, intent)
                        finish()
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}
