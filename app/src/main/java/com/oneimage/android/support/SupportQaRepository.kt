package com.oneimage.android.support

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class SupportQaRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun observeAnswers(): Flow<SupportQaResult> = callbackFlow {
        var registration: ListenerRegistration? = null
        registration = firestore.collection(COLLECTION)
            .orderBy("sortOrder", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(SupportQaResult(answers = starterAnswers, error = error.message, usingFallback = true))
                    return@addSnapshotListener
                }

                val answers = snapshot?.documents
                    ?.mapNotNull(SupportAnswer::fromDocument)
                    ?.filter { it.active }
                    .orEmpty()

                if (answers.isEmpty()) {
                    trySend(SupportQaResult(answers = starterAnswers, usingFallback = true))
                } else {
                    trySend(SupportQaResult(answers = answers))
                }
            }

        awaitClose { registration?.remove() }
    }

    companion object {
        const val COLLECTION = "support_qa"
    }
}

data class SupportQaResult(
    val answers: List<SupportAnswer>,
    val error: String? = null,
    val usingFallback: Boolean = false
)

val starterAnswers = listOf(
    qa("account-google-sign-in", "Account", 101, "How do I sign in?", "Use Google sign-in from the login screen.", "OneStudio uses Google sign-in. Choose the Google account you want tied to your creator profile. If sign-in loops or closes without success, switch networks and try again, then report the exact message shown on screen.", "login", "google", "auth", "account"),
    qa("account-profile-sync", "Account", 102, "Why does my profile say syncing?", "The app is waiting for your Firebase account profile to load.", "The Settings and account screens read your user profile from Firebase. If role, plan, status, or credits show Syncing, keep the app open briefly and confirm you are online. If it stays stuck, support needs the signed-in email and approximate time.", "profile", "syncing", "settings", "firebase"),
    qa("account-logout", "Account", 103, "How do I switch accounts?", "Open Settings and use Logout, then sign in with the other Google account.", "Your credits, plan, and history are tied to the signed-in account. To switch, open Settings, tap Logout, and sign in again with the intended Google account.", "logout", "switch", "email"),

    qa("account-service-model", "Account", 201, "Why does the app use credits?", "Generation uses metered compute, storage, and service maintenance.", "OneStudio is an independent creative service. Image, video, and 3D generation consume metered resources, so credits are required to run tasks. Android reads the same shared account access and credit balance that your web workspace uses.", "credits", "service model", "compute"),
    qa("account-plan-status", "Account", 202, "What plan information is shown in the app?", "Settings shows the current role, plan, status, and credits from your shared account.", "The Android app reads your existing account role, plan, status, and credits from the shared OneStudio profile. It does not manage purchases locally. If your access changes on the web, the updated account state should appear here after sync.", "plan", "status", "credits", "account"),
    qa("account-credit-usage", "Account", 203, "Why were credits used for my generation?", "Credits are reserved when a workflow starts and are tied to the selected tool.", "Credits are used when you start a generation workflow. If a result fails before the service accepts the task, the app should not treat it as completed. Check workflow history first, then contact support with the workflow name and approximate time if the balance looks wrong.", "credits", "balance", "generation", "usage"),

    qa("workflow-image-generation", "Workflows", 301, "What does Image Generation create?", "Image Generation creates consistent character views from a prompt.", "Use Image Generation when you want consistent character views or angles. Provide a clear prompt with the subject, style, clothing, camera angle, and any important visual details.", "image generation", "prompt", "character"),
    qa("workflow-video-generation", "Workflows", 302, "What does Video Generation need?", "Video Generation uses start and end frames plus a prompt.", "Use Video Generation for a short video from two images. Pick strong start and end frames, then describe the motion clearly. Credits scale with the selected duration.", "video", "start frame", "end frame", "duration"),
    qa("workflow-lipsync", "Workflows", 303, "How does LipSync work?", "LipSync uses an image, an audio segment, and a prompt or style description.", "Choose the image and audio, then set the audio start and clip duration. The app validates that the selected segment fits inside the uploaded audio. Credits are calculated from the selected duration.", "lipsync", "audio", "duration", "prompt"),
    qa("workflow-keyframes", "Workflows", 304, "How do Keyframes work?", "Keyframes create a longer video from selected key images.", "Keyframes supports multiple images, each with optional prompt notes and timing to the next frame. Use it when you need more guided motion than a simple two-frame video.", "keyframes", "video", "motion", "frames"),
    qa("workflow-story-images", "Workflows", 305, "How should I format Story Images?", "Use one story paragraph per generated image.", "Story Images turns paragraphs into image panels. Separate paragraphs with blank lines. Each paragraph should stay under 512 characters, and you can add optional style guidance and an aspect ratio.", "story images", "paragraph", "aspect ratio", "512"),
    qa("workflow-character-replacement", "Workflows", 306, "What files does Character Replacement need?", "It needs a source video and a character reference image.", "Character Replacement changes a character in a short clip while preserving motion, pose, timing, and scene. Use a short source video and a clear reference image of the replacement identity or character.", "character replacement", "video", "reference image"),
    qa("workflow-game-mesh", "Workflows", 307, "What does Game Mesh create?", "Game Mesh drafts a 3D model from one image.", "Use Game Mesh when you have a clear image of an object or asset and want a draft 3D model. A clean, centered source image gives better results than a busy scene.", "game mesh", "3d", "model", "image"),
    qa("workflow-upscaler", "Workflows", 308, "What is Game Asset Upscaler for?", "It cleans and enlarges sprites, icons, tiles, and UI assets.", "Game Asset Upscaler is for improving smaller game art. The default direction asks for high-definition game asset style and avoids pixel art unless you explicitly want a different look.", "upscaler", "game asset", "sprite", "icon"),
    qa("workflow-single-i2v", "Workflows", 309, "What does Single I2V create?", "It turns one image into a short motion clip.", "Upload one image, describe the motion or camera direction, choose a duration from 3 to 10 seconds, then use the prepared image size or select an output aspect ratio.", "single i2v", "image to video", "motion", "duration"),
    qa("workflow-stuck", "Workflows", 310, "What should I do if a generation is stuck?", "Open workflow history, wait for the current status to refresh, then retry only if no task is active.", "Long running video and image workflows can take several minutes. Keep the app open long enough for the current status to refresh. If the task still does not update, capture the workflow name, prompt, and time.", "stuck", "pending", "running", "task"),
    qa("workflow-quality", "Workflows", 311, "How can I improve output quality?", "Use direct prompts, strong source images, and avoid mixing too many style goals in one request.", "Quality depends heavily on the source image and prompt. Use one clear subject, describe the intended motion or style directly, and avoid conflicting instructions. For multi-image workflows, use images with matching lighting and framing.", "quality", "prompt", "source image"),

    qa("results-history", "Results", 401, "Where can I find previous generations?", "Each workflow screen has a recent generation or history section.", "Workflow screens listen to your Firebase task history and show recent tasks for the signed-in account. Open the same workflow type to review its recent results and status.", "history", "recent", "tasks", "results"),
    qa("results-restore", "Results", 402, "What does Restore mean?", "Restore asks the local WebRTC transport to fetch a result again when possible.", "Some results may be delivered through WebRTC. When a result has a restore option, keep the app online and use Restore from the workflow result card or history item. If restore is unavailable, report the task id or workflow details.", "restore", "webrtc", "result"),
    qa("results-files", "Results", 403, "Why do some results show previews and others show links?", "Image and playable video results can render inline; other files may show as saved links.", "The app previews common image files and playable video results. Other result types may show the saved URL or local file reference instead of an inline preview.", "preview", "image", "video", "file"),

    qa("privacy-uploads", "Privacy", 501, "What happens to files I upload?", "Uploads are used to run the selected workflow and show the result back in the app.", "Files are sent as needed for the selected workflow. Avoid uploading private or sensitive material unless you are comfortable using it for generation. For account-specific deletion or export questions, contact support from the signed-in account.", "privacy", "upload", "file", "data"),
    qa("privacy-policy", "Privacy", 502, "Does OneStudio sell personal information?", "The in-app privacy summary says personal information is not sold to third parties.", "The Privacy & Terms screen says OneStudio uses data to provide image, video, and asset generation features and does not sell personal information to third parties.", "privacy policy", "personal information"),
    qa("terms-content-rules", "Privacy", 503, "What content is not allowed?", "The app terms prohibit harmful, illegal, or NSFW generation use.", "The Terms of Use summary says users agree not to use generation services to create harmful, illegal, or NSFW content.", "terms", "nsfw", "illegal", "harmful"),

    qa("support-flow", "Support", 601, "How does support escalation work?", "Support starts with Firebase QA, then can move to AI, then live WebRTC chat later.", "The current support layer is a Firebase QA database. The next layer can use AI chat over the same knowledge base. Later, unresolved issues can escalate to a real person through a WebRTC live support session.", "support", "ai", "live", "webrtc"),
    qa("support-live-agent", "Support", 602, "When can I talk to a real person?", "Live support is planned after the QA and AI assistant layers are stable.", "Live person chat is the third support layer. It should be used after the QA database and AI assistant cannot resolve the issue, so the human receives the question context instead of starting from zero.", "live agent", "human", "chat")
)

private fun qa(
    id: String,
    category: String,
    sortOrder: Long,
    question: String,
    shortAnswer: String,
    answer: String,
    vararg keywords: String
) = SupportAnswer(
    id = id,
    question = question,
    shortAnswer = shortAnswer,
    answer = answer,
    category = category,
    keywords = keywords.toList(),
    workflows = inferredWorkflows(id),
    sortOrder = sortOrder,
    active = true
)
