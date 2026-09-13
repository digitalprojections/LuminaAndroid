package com.oneimage.android.ui.workflow

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oneimage.android.R
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SoundEffectsControls(values: MutableMap<String, String>, enabled: Boolean) {
    val config = SoundEffectsConfig.from(values)
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(painterResource(R.drawable.sound_effects), "Sound Effects",
                    Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)))
                Column(Modifier.weight(1f)) {
                    Text("Make your scene heard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Foley, ambience, impacts, and interface sounds.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            BoxWithConstraints {
                val description: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(value = config.prompt, onValueChange = { values["prompt"] = it.take(2000) },
                            enabled = enabled, label = { Text("Describe your sound") }, minLines = 3,
                            placeholder = { Text("A campfire crackling softly, with sharp pops and a gentle breeze.") },
                            supportingText = { Text("Include the source, texture, and surroundings.") },
                            modifier = Modifier.fillMaxWidth().testTag("sound-prompt"), shape = RoundedCornerShape(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SuggestionChip(enabled = enabled, onClick = { values["prompt"] = "Steady rain on a window with soft distant thunder." }, label = { Text("Rain") })
                            SuggestionChip(enabled = enabled, onClick = { values["prompt"] = "A dragon roaring with a deep rumbling growl and a short outdoor echo." }, label = { Text("Dragon") })
                        }
                    }
                }
                val sliders: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SoundSlider("Duration", "${config.seconds} sec", config.seconds.toFloat(), 1f..47f, 45, enabled, "sound-duration") { values["seconds"] = it.roundToInt().toString() }
                        SoundSlider("Batch size", "${config.batchSize} effect${if (config.batchSize == 1) "" else "s"}", config.batchSize.toFloat(), 1f..3f, 1, enabled, "sound-batch") { values["batchSize"] = it.roundToInt().toString() }
                        SoundSlider("Prompt guidance (CFG)", String.format(Locale.US, "%.1f", config.cfg), config.cfg, 1f..10f, 17, enabled, "sound-cfg") { values["cfg"] = ((it * 2).roundToInt() / 2f).toString() }
                        Text("Higher guidance follows your description more closely.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (maxWidth >= 560.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Box(Modifier.weight(1f)) { description() }
                        Box(Modifier.weight(1f)) { sliders() }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { description(); sliders() }
                }
            }
        }
    }
}

@Composable
private fun SoundSlider(label: String, display: String, value: Float, range: ClosedFloatingPointRange<Float>,
                        steps: Int, enabled: Boolean, tag: String, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text(display, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range, steps = steps, enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag(tag).semantics { contentDescription = label })
    }
}
