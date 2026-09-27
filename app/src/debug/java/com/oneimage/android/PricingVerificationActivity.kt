package com.oneimage.android
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import com.oneimage.android.ui.shared.rememberGenerationQuote
import org.json.JSONObject

/** Debug-only quote preview; cannot submit jobs or debit credits. */
class PricingVerificationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var duration by remember { mutableIntStateOf(6) }
                var fps by remember { mutableIntStateOf(25) }
                val quote = rememberGenerationQuote("video", JSONObject().put("duration", duration).put("frameRate", fps).put("width", 512).put("height", 512), "http://127.0.0.1:3001")
                Surface(modifier = Modifier.fillMaxSize()) { Column(modifier = Modifier.padding(16.dp)) {
                    Text("GenStudio pricing verification")
                    Text(quote.label)
                    Button(onClick = { duration = 12 }) { Text("Longer clip") }
                    Button(onClick = { fps = 30 }) { Text("Higher FPS") }
                    Button(onClick = {}, enabled = quote.ready) { Text("Quote confirmed") }
                } }
            }
        }
    }
}
