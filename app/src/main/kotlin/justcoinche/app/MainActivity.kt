package justcoinche.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
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
        // One store for the activity: it remembers which record it is writing to.
        val store = GameStore(File(filesDir, "parties"))
        setContent {
            MaterialTheme {
                TableScreen(store)
            }
        }
    }
}
