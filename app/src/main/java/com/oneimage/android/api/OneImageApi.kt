package com.oneimage.android.api

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OneImageFileInfo(
    val filename: String,
    val mimeType: String,
    val size: Long
)

data class OneImageTaskResult(
    val label: String,
    val url: String,
    val filename: String,
    val size: Long = 0L
)

data class OneImageTask(
    val id: String,
    val type: String,
    val status: String,
    val statusDetails: String?,
    val error: String?,
    val progressValue: Int,
    val progressMax: Int,
    val prompt: String?,
    val isLightning: Boolean,
    val createdAtMs: Long,
    val useWebRTC: Boolean,
    val resultRestoreUnavailable: Boolean,
    val results: List<OneImageTaskResult>
)

data class OneImageQueueStatus(
    val totalPending: Int,
    val totalProcessing: Int,
    val estimatedWaitTime: Int
)

data class OneImageAccountProfile(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val credits: Long,
    val tokenBalance: Long?,
    val role: String?,
    val subscriptionStatus: String?,
    val subscriptionPlan: String?,
    val isAdmin: Boolean,
    val admin: Boolean,
    val acceptedTermsVersion: String?,
    val acceptedPrivacyVersion: String?
) {
    val hasUnlimitedAccess: Boolean
        get() = role == "admin" || subscriptionStatus == "comped" || isAdmin || admin

    val isSubscribed: Boolean
        get() = subscriptionStatus == "active" || subscriptionStatus == "trialing" || subscriptionStatus == "comped"

    companion object {
        fun fromFirestoreMap(uid: String, data: Map<String, Any>): OneImageAccountProfile {
            val credits = if (data.containsKey("credits")) {
                (data["credits"] as? Number)?.toLong() ?: 0L
            } else {
                (data["tokenBalance"] as? Number)?.toLong() ?: 0L
            }
            return OneImageAccountProfile(
                uid = uid,
                email = data["email"]?.toString(),
                displayName = data["displayName"]?.toString(),
                credits = credits,
                tokenBalance = (data["tokenBalance"] as? Number)?.toLong(),
                role = data["role"]?.toString(),
                subscriptionStatus = data["subscriptionStatus"]?.toString(),
                subscriptionPlan = data["subscriptionPlan"]?.toString(),
                isAdmin = data["isAdmin"] as? Boolean ?: false,
                admin = data["admin"] as? Boolean ?: false,
                acceptedTermsVersion = data["acceptedTermsVersion"]?.toString(),
                acceptedPrivacyVersion = data["acceptedPrivacyVersion"]?.toString()
            )
        }
    }

    fun hasEnoughCredits(estimatedCredits: Int): Boolean {
        return hasUnlimitedAccess || credits >= estimatedCredits.toLong()
    }

    val creditBalanceText: String
        get() = if (hasUnlimitedAccess) "Unlimited" else credits.coerceAtLeast(0).toString()

    val planLabel: String
        get() = subscriptionPlan?.takeIf { it.isNotBlank() } ?: "none"

    val statusLabel: String
        get() = subscriptionStatus?.takeIf { it.isNotBlank() } ?: "unpaid"

    val hasAcceptedCurrentLegal: Boolean
        get() = acceptedTermsVersion == OneImageApi.CURRENT_TERMS_VERSION &&
            acceptedPrivacyVersion == OneImageApi.CURRENT_PRIVACY_VERSION
}

object OneImageApi {
    const val CURRENT_TERMS_VERSION = "2026-05-27"
    const val CURRENT_PRIVACY_VERSION = "2026-05-16"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun getFileInfo(contentResolver: ContentResolver, imageUri: Uri): OneImageFileInfo {
        val mimeType = contentResolver.getType(imageUri) ?: "image/png"
        val filename = getDisplayName(contentResolver, imageUri) ?: "input.png"
        val size = try {
            contentResolver.openAssetFileDescriptor(imageUri, "r")?.use { descriptor ->
                descriptor.length.takeIf { it > 0 }
            } ?: contentResolver.openInputStream(imageUri)?.use { it.readBytes().size.toLong() } ?: 0L
        } catch (e: Exception) {
            0L
        }
        return OneImageFileInfo(filename = filename, mimeType = mimeType, size = size)
    }

    suspend fun submitImageWorkflow(
        baseUrl: String,
        clientId: String,
        prompt: String,
        fileInfo: OneImageFileInfo,
        isLightning: Boolean = true,
        workflowFile: String = if (isLightning) "onetoeight.json" else "onetoeight_hq.json"
    ): String = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("clientId", clientId)
            .put("prompt", prompt)
            .put("seed", 0)
            .put("isLightning", isLightning)
            .put("workflowFile", workflowFile)
            .put("inputName", fileInfo.filename)
            .put("inputMimetype", fileInfo.mimeType)
            .put("inputSize", fileInfo.size)

        val requestBuilder = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/generate")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")

        FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.awaitResult()?.token?.let { token ->
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = JSONObject(text.ifBlank { "{}" })
            if (!response.isSuccessful || json.optBoolean("success") == false) {
                error(json.optString("message", "Generation failed to start."))
            }
            json.getString("taskId")
        }
    }

    suspend fun submitVideoWorkflow(
        baseUrl: String,
        clientId: String,
        prompt: String,
        startFileInfo: OneImageFileInfo,
        endFileInfo: OneImageFileInfo,
        duration: Int = 6,
        frameRate: Int = 25,
        width: Int = 1024,
        height: Int = 1024
    ): String = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("clientId", clientId)
            .put("prompt", prompt)
            .put("seed", 0)
            .put("duration", duration)
            .put("frameRate", frameRate)
            .put("width", width)
            .put("height", height)
            .put("startImageName", startFileInfo.filename)
            .put("startImageMimetype", startFileInfo.mimeType)
            .put("startImageSize", startFileInfo.size)
            .put("endImageName", endFileInfo.filename)
            .put("endImageMimetype", endFileInfo.mimeType)
            .put("endImageSize", endFileInfo.size)

        val requestBuilder = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/video/generate")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")

        FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.awaitResult()?.token?.let { token ->
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = JSONObject(text.ifBlank { "{}" })
            if (!response.isSuccessful || json.optBoolean("success") == false) {
                error(json.optString("message", "Video generation failed to start."))
            }
            json.getString("taskId")
        }
    }

    suspend fun submitSingleI2VWorkflow(
        baseUrl: String,
        clientId: String,
        prompt: String,
        imageFileInfo: OneImageFileInfo,
        duration: Int,
        frameRate: Int,
        resolutionMode: String,
        aspectRatio: String,
        inputWidth: Int,
        inputHeight: Int
    ): String = postGeneration(
        baseUrl = baseUrl,
        path = "/api/single-i2v/generate",
        payload = JSONObject()
            .put("clientId", clientId)
            .put("prompt", prompt)
            .put("seed", 0)
            .put("duration", duration)
            .put("frameRate", frameRate)
            .put("resolutionMode", resolutionMode)
            .put("aspectRatio", aspectRatio)
            .put("inputWidth", inputWidth)
            .put("inputHeight", inputHeight)
            .put("inputImageName", imageFileInfo.filename)
            .put("inputImageMimetype", imageFileInfo.mimeType)
            .put("inputImageSize", imageFileInfo.size),
        fallbackMessage = "Single I2V generation failed to start."
    )

    suspend fun submitLipSyncWorkflow(
        baseUrl: String,
        clientId: String,
        prompt: String,
        imageFileInfo: OneImageFileInfo,
        audioFileInfo: OneImageFileInfo,
        audioStart: Float = 0f,
        duration: Float = 10f,
        frameRate: Int = 24,
        width: Int = 512,
        height: Int = 512,
        audioDuration: Float = duration,
        useFullAudio: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("clientId", clientId)
            .put("prompt", prompt)
            .put("seed", 0)
            .put("audioStart", audioStart)
            .put("duration", duration)
            .put("lipSyncMode", if (useFullAudio) "full_audio" else "clip")
            .put("frameRate", frameRate)
            .put("width", width)
            .put("height", height)
            .put("inputImageName", imageFileInfo.filename)
            .put("inputImageMimetype", imageFileInfo.mimeType)
            .put("inputImageSize", imageFileInfo.size)
            .put("inputAudioName", audioFileInfo.filename)
            .put("inputAudioMimetype", audioFileInfo.mimeType)
            .put("inputAudioSize", audioFileInfo.size)
            .put("inputAudioDuration", audioDuration)

        val requestBuilder = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/lipsync/generate")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")

        FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.awaitResult()?.token?.let { token ->
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = JSONObject(text.ifBlank { "{}" })
            if (!response.isSuccessful || json.optBoolean("success") == false) {
                error(json.optString("message", "Lip sync generation failed to start."))
            }
            json.getString("taskId")
        }
    }

    suspend fun submitCharacterReplacementWorkflow(
        baseUrl: String,
        clientId: String,
        prompt: String,
        videoFileInfo: OneImageFileInfo,
        imageFileInfo: OneImageFileInfo,
        duration: Float,
        sourceDuration: Float
    ): String = postGeneration(
        baseUrl = baseUrl,
        path = "/api/character-replacement/generate",
        payload = JSONObject()
            .put("clientId", clientId)
            .put("prompt", prompt)
            .put("seed", 0)
            .put("duration", duration)
            .put("inputVideoName", videoFileInfo.filename)
            .put("inputVideoMimetype", videoFileInfo.mimeType)
            .put("inputVideoSize", videoFileInfo.size)
            .put("inputVideoDuration", sourceDuration)
            .put("inputImageName", imageFileInfo.filename)
            .put("inputImageMimetype", imageFileInfo.mimeType)
            .put("inputImageSize", imageFileInfo.size),
        fallbackMessage = "Character replacement failed to start."
    )

    suspend fun submitQwenStoryImagesWorkflow(
        baseUrl: String,
        clientId: String,
        imageFileInfo: OneImageFileInfo,
        storyPrompt: String,
        stylePrompt: String,
        aspectRatio: String = "16:9 (Widescreen)"
    ): String = postGeneration(
        baseUrl = baseUrl,
        path = "/api/qwen-story-images/generate",
        payload = JSONObject()
            .put("clientId", clientId)
            .put("storyPrompt", storyPrompt)
            .put("stylePrompt", stylePrompt)
            .put("aspectRatio", aspectRatio)
            .put("seed", 0)
            .put("inputImageName", imageFileInfo.filename)
            .put("inputImageMimetype", imageFileInfo.mimeType)
            .put("inputImageSize", imageFileInfo.size),
        fallbackMessage = "Story image generation failed to start."
    )

    suspend fun submitRefRestyleWorkflow(
        baseUrl: String,
        clientId: String,
        imageFileInfo: OneImageFileInfo,
        referenceImageFileInfo: OneImageFileInfo,
        prompt: String
    ): String = postGeneration(
        baseUrl = baseUrl,
        path = "/api/ref-restyle/generate",
        payload = JSONObject()
            .put("clientId", clientId)
            .put("prompt", prompt)
            .put("seed", 0)
            .put("inputImageName", imageFileInfo.filename)
            .put("inputImageMimetype", imageFileInfo.mimeType)
            .put("inputImageSize", imageFileInfo.size)
            .put("referenceImageName", referenceImageFileInfo.filename)
            .put("referenceImageMimetype", referenceImageFileInfo.mimeType)
            .put("referenceImageSize", referenceImageFileInfo.size),
        fallbackMessage = "Reference restyle failed to start."
    )

    suspend fun submitMeshModelWorkflow(
        baseUrl: String,
        clientId: String,
        imageFileInfo: OneImageFileInfo
    ): String = postGeneration(
        baseUrl = baseUrl,
        path = "/api/image-to-3d-mesh/generate",
        payload = JSONObject()
            .put("clientId", clientId)
            .put("seed", 0)
            .put("inputImageName", imageFileInfo.filename)
            .put("inputImageMimetype", imageFileInfo.mimeType)
            .put("inputImageSize", imageFileInfo.size),
        fallbackMessage = "Mesh generation failed to start."
    )

    suspend fun submitGameAssetUpscalerWorkflow(
        baseUrl: String,
        clientId: String,
        imageFileInfo: OneImageFileInfo,
        description: String,
        importantDescription: String,
        negativePrompt: String
    ): String = postGeneration(
        baseUrl = baseUrl,
        path = "/api/game-asset-upscaler/generate",
        payload = JSONObject()
            .put("clientId", clientId)
            .put("description", description)
            .put("importantDescription", importantDescription)
            .put("negativePrompt", negativePrompt)
            .put("seed", 0)
            .put("inputImageName", imageFileInfo.filename)
            .put("inputImageMimetype", imageFileInfo.mimeType)
            .put("inputImageSize", imageFileInfo.size),
        fallbackMessage = "Game asset upscaler failed to start."
    )

    suspend fun submitKeyframesWorkflow(
        baseUrl: String,
        clientId: String,
        inputs: List<KeyframeWorkflowInput>
    ): String {
        val inputArray = JSONArray()
        inputs.forEachIndexed { index, input ->
            inputArray.put(
                JSONObject()
                    .put("image", "webrtc://image_$index")
                    .put("filename", input.fileInfo.filename)
                    .put("mimetype", input.fileInfo.mimeType)
                    .put("size", input.fileInfo.size)
                    .put("prompt", input.prompt)
                    .put("durationFrames", input.durationFrames)
            )
        }
        return postGeneration(
            baseUrl = baseUrl,
            path = "/api/keyframes/generate",
            payload = JSONObject()
                .put("clientId", clientId)
                .put("useWebRTC", true)
                .put("params", JSONObject().put("inputs", inputArray)),
            fallbackMessage = "Keyframes generation failed to start."
        )
    }
    suspend fun cancelTask(baseUrl: String, clientId: String, taskId: String) = postTaskAction(
        baseUrl = baseUrl,
        path = "/api/task/cancel",
        clientId = clientId,
        taskId = taskId,
        fallbackMessage = "Could not cancel task."
    )

    suspend fun deleteTask(baseUrl: String, clientId: String, taskId: String) = postTaskAction(
        baseUrl = baseUrl,
        path = "/api/task/delete",
        clientId = clientId,
        taskId = taskId,
        fallbackMessage = "Could not delete task."
    )

    suspend fun reconcileTasks(baseUrl: String, clientId: String): Int = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("clientId", clientId)
        val requestBuilder = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/tasks/reconcile")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")

        FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.awaitResult()?.token?.let { token ->
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = JSONObject(text.ifBlank { "{}" })
            if (!response.isSuccessful || json.optBoolean("success") == false) {
                error(json.optString("message", "Could not reconcile tasks."))
            }
            json.optInt("reconciled", 0)
        }
    }

    suspend fun getImageTask(baseUrl: String, clientId: String, taskId: String): OneImageTask? = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/tasks/$clientId")
            .get()

        FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.awaitResult()?.token?.let { token ->
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Could not load task status.")
            val tasks = JSONObject(text.ifBlank { "{}" }).optJSONArray("tasks") ?: return@withContext null
            for (index in 0 until tasks.length()) {
                val taskJson = tasks.optJSONObject(index) ?: continue
                if (taskJson.optString("id") == taskId) return@withContext parseTask(taskJson)
            }
            null
        }
    }

    suspend fun bootstrapAccountProfile(
        baseUrl: String,
        legalAcceptanceMethod: String? = null
    ): OneImageAccountProfile = withContext(Dispatchers.IO) {
        val payload = JSONObject()
        if (!legalAcceptanceMethod.isNullOrBlank()) {
            payload.put("legalAcceptance", legalAcceptancePayload(legalAcceptanceMethod))
        }

        val requestBuilder = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/account/bootstrap")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")

        val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.awaitResult()?.token
            ?: error("Please sign in again.")
        requestBuilder.addHeader("Authorization", "Bearer $token")

        client.newCall(requestBuilder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = JSONObject(text.ifBlank { "{}" })
            if (!response.isSuccessful || json.optBoolean("success") == false) {
                error(json.optString("message", "Could not load account profile."))
            }
            parseAccountProfile(json.getJSONObject("profile"))
        }
    }

    suspend fun acceptLegalAgreements(
        baseUrl: String,
        method: String = "account_gate"
    ) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("legalAcceptance", legalAcceptancePayload(method))

        val requestBuilder = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/account/legal-acceptance")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")

        val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.awaitResult()?.token
            ?: error("Please sign in again.")
        requestBuilder.addHeader("Authorization", "Bearer $token")

        client.newCall(requestBuilder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = JSONObject(text.ifBlank { "{}" })
            if (!response.isSuccessful || json.optBoolean("success") == false) {
                error(json.optString("message", "Could not accept Privacy & Terms."))
            }
        }
    }

    suspend fun registerNotificationToken(
        baseUrl: String,
        token: String,
        appVersion: String,
        uidHint: String? = null
    ) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("token", token)
            .put("appVersion", appVersion)
        if (!uidHint.isNullOrBlank()) payload.put("uidHint", uidHint)

        val requestBuilder = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/mobile/notification-token")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")

        val authToken = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.awaitResult()?.token
            ?: return@withContext
        requestBuilder.addHeader("Authorization", "Bearer $authToken")

        client.newCall(requestBuilder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = JSONObject(text.ifBlank { "{}" })
            if (!response.isSuccessful || json.optBoolean("success") == false) {
                error(json.optString("message", "Could not register notification token."))
            }
        }
    }

    suspend fun markNotificationsSeen(
        baseUrl: String,
        notificationIds: List<String>
    ) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("notificationIds", JSONArray(notificationIds))

        val requestBuilder = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/mobile/notifications/mark-seen")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")

        val authToken = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.awaitResult()?.token
            ?: return@withContext
        requestBuilder.addHeader("Authorization", "Bearer $authToken")

        client.newCall(requestBuilder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = JSONObject(text.ifBlank { "{}" })
            if (!response.isSuccessful || json.optBoolean("success") == false) {
                error(json.optString("message", "Could not update notifications."))
            }
        }
    }

    fun parseTask(json: JSONObject): OneImageTask {
        val resultsJson = json.optJSONArray("results")
        val results = buildList {
            if (resultsJson != null) {
                for (index in 0 until resultsJson.length()) {
                    val result = resultsJson.optJSONObject(index) ?: continue
                    val url = result.optString("url")
                    if (url.startsWith("http") || url.startsWith("file:") || url.startsWith("content:") || url.startsWith("webrtc://")) {
                        add(
                            OneImageTaskResult(
                                label = result.optString("label", "Result"),
                                url = url,
                                filename = result.optString("filename"),
                                size = result.optLong("size", 0L)
                            )
                        )
                    }
                }
            }
        }
        val params = json.optJSONObject("params")
        val createdAtMs = when {
            json.has("createdAtMs") -> json.optLong("createdAtMs", 0L)
            json.has("createdAt") -> json.optLong("createdAt", 0L)
            else -> 0L
        }
        return LocalTaskResultStore.overlayTask(
            OneImageTask(
            id = json.optString("id"),
            type = json.optString("type", "image"),
            status = json.optString("status"),
            statusDetails = json.optString("status_details").ifBlank { null },
            error = json.optString("error").ifBlank { null },
            progressValue = json.optInt("progress_value", 0),
            progressMax = json.optInt("progress_max", 0),
            prompt = params?.optString("prompt")?.ifBlank { json.optString("prompt").ifBlank { null } }
                ?: json.optString("prompt").ifBlank { null },
            isLightning = params?.optBoolean("isLightning", true) ?: true,
            createdAtMs = createdAtMs,
            useWebRTC = json.optBoolean("useWebRTC", false),
            resultRestoreUnavailable = json.optBoolean("resultRestoreUnavailable", false),
            results = results
        )
        )
    }

    private suspend fun postGeneration(
        baseUrl: String,
        path: String,
        payload: JSONObject,
        fallbackMessage: String
    ): String = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url("${baseUrl.trimEnd('/')}$path")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")

        FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.awaitResult()?.token?.let { token ->
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = parseJsonObjectOrNull(text)
            if (!response.isSuccessful || json?.optBoolean("success") == false) {
                error(
                    json?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: nonJsonApiResponseMessage(response.code, path, text, fallbackMessage)
                )
            }
            json?.optString("taskId")?.takeIf { it.isNotBlank() }
                ?: error(nonJsonApiResponseMessage(response.code, path, text, fallbackMessage))
        }
    }

    private fun parseJsonObjectOrNull(text: String): JSONObject? {
        val trimmed = text.trimStart()
        if (!trimmed.startsWith("{")) return null
        return runCatching { JSONObject(trimmed) }.getOrNull()
    }

    private fun nonJsonApiResponseMessage(
        responseCode: Int,
        path: String,
        text: String,
        fallbackMessage: String
    ): String {
        val trimmed = text.trimStart()
        return when {
            trimmed.startsWith("<!DOCTYPE", ignoreCase = true) || trimmed.startsWith("<html", ignoreCase = true) ->
                "The server returned a webpage instead of the API response for $path. Please try again after the latest server update is live."
            responseCode >= 500 ->
                "The server had a temporary problem. Please try again in a moment."
            else -> fallbackMessage
        }
    }

    private fun parseAccountProfile(json: JSONObject): OneImageAccountProfile {
        val credits = when {
            json.has("credits") -> json.optLong("credits", 0)
            json.has("tokenBalance") -> json.optLong("tokenBalance", 0)
            else -> 0
        }
        return OneImageAccountProfile(
            uid = json.optString("uid"),
            email = json.optString("email").ifBlank { null },
            displayName = json.optString("displayName").ifBlank { null },
            credits = credits,
            tokenBalance = if (json.has("tokenBalance")) json.optLong("tokenBalance", 0) else null,
            role = json.optString("role").ifBlank { null },
            subscriptionStatus = json.optString("subscriptionStatus").ifBlank { null },
            subscriptionPlan = json.optString("subscriptionPlan").ifBlank { null },
            isAdmin = json.optBoolean("isAdmin", false),
            admin = json.optBoolean("admin", false),
            acceptedTermsVersion = json.optString("acceptedTermsVersion").ifBlank { null },
            acceptedPrivacyVersion = json.optString("acceptedPrivacyVersion").ifBlank { null }
        )
    }

    private fun legalAcceptancePayload(method: String): JSONObject {
        return JSONObject()
            .put("accepted", true)
            .put("termsVersion", CURRENT_TERMS_VERSION)
            .put("privacyVersion", CURRENT_PRIVACY_VERSION)
            .put("method", method)
    }

    private fun getDisplayName(contentResolver: ContentResolver, uri: Uri): String? {
        return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }

    private suspend fun postTaskAction(
        baseUrl: String,
        path: String,
        clientId: String,
        taskId: String,
        fallbackMessage: String
    ) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("clientId", clientId)
            .put("taskId", taskId)

        val requestBuilder = Request.Builder()
            .url("${baseUrl.trimEnd('/')}$path")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")

        FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.awaitResult()?.token?.let { token ->
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = JSONObject(text.ifBlank { "{}" })
            if (!response.isSuccessful || json.optBoolean("success") == false) {
                error(json.optString("message", fallbackMessage))
            }
        }
    }
}

data class KeyframeWorkflowInput(
    val fileInfo: OneImageFileInfo,
    val prompt: String,
    val durationFrames: Int
)
suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
}
