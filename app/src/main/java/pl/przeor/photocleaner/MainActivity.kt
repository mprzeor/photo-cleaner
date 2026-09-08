package pl.przeor.photocleaner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pl.przeor.photocleaner.ui.navigation.AppNavGraph
import pl.przeor.photocleaner.ui.theme.PhotoCleanerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PhotoCleanerApp).container
        setContent {
            PhotoCleanerTheme {
                AppNavGraph(container)
            }
        }
    }
}
