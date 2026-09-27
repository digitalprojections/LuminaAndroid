package com.oneimage.android.api

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GenerationQuoteRepository {
    private val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    suspend fun quote(baseUrl: String, workflow: String, settings: String): GenerationQuote = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("workflow", workflow).put("settings", JSONObject(settings))
        val request = Request.Builder().url("${baseUrl.trimEnd('/')}/api/pricing/quote")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
        FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.awaitResult()?.token?.let {
            request.header("Authorization", "Bearer $it")
        }
        client.newCall(request.build()).execute().use { response ->
            val json = JSONObject(response.body?.string().orEmpty())
            check(response.isSuccessful) { json.optString("message", "Could not calculate the price.") }
            val credits = json.getInt("chargeCredits")
            val id = json.getString("id")
            check(credits >= 0 && id.isNotBlank()) { "Invalid price received." }
            GenerationQuote(id, credits)
        }
    }
}
