package justcoinche.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import justcoinche.record.GameStore
import java.io.File

/**
 * The app's single screen host. The whole interface is drawn with Jetpack
 * Compose. Games are kept in the app's private storage, in "parties".
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Full screen, as for a card game: the system bars come back with a swipe.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        // One store for the activity: it remembers which record it is writing to.
        val store = GameStore(File(filesDir, "parties"))
        val options = AiOptions(File(filesDir, "options/ia.txt"))
        setContent {
            MaterialTheme {
                TableScreen(store, options)
            }
        }
    }
}
