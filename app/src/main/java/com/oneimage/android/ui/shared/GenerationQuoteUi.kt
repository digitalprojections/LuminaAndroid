package com.oneimage.android.ui.shared

import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.oneimage.android.BuildConfig
import com.oneimage.android.api.ActiveGenerationQuotes
import com.oneimage.android.api.GenerationQuoteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.json.JSONObject

data class GenerationQuoteUi(val ready: Boolean = false, val credits: Int = 0, val error: String? = null, val retry: () -> Unit = {}) {
    val label: String get() = if (ready) "$credits credits" else error ?: "Calculating price…"
}

@Composable
fun rememberGenerationQuote(workflow: String, settings: JSONObject, baseUrl: String = BuildConfig.ONEIMAGE_API_BASE_URL): GenerationQuoteUi {
    val key = settings.toString()
    var retry by remember { mutableIntStateOf(0) }
    var state by remember(workflow, key) { mutableStateOf(GenerationQuoteUi()) }
    LaunchedEffect(workflow) {
        ActiveGenerationQuotes.invalidations.collect { changed -> if (changed == workflow) retry++ }
    }
    LaunchedEffect(workflow, key, retry, baseUrl) {
        ActiveGenerationQuotes.store.clear(workflow)
        state = GenerationQuoteUi()
        delay(250)
        try {
            val quote = GenerationQuoteRepository.quote(baseUrl, workflow, key)
            ActiveGenerationQuotes.store.put(workflow, quote)
            state = GenerationQuoteUi(ready = true, credits = quote.credits)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { state = GenerationQuoteUi(error = e.message ?: "Price unavailable") }
    }
    DisposableEffect(workflow) { onDispose { ActiveGenerationQuotes.store.clear(workflow) } }
    return state.copy(retry = { retry++ })
}

@Composable
fun GenerationQuoteStatus(quote: GenerationQuoteUi) {
    if (!quote.ready) {
        Text(quote.label)
        if (quote.error != null) TextButton(onClick = quote.retry) { Text("Retry price") }
    }
}
