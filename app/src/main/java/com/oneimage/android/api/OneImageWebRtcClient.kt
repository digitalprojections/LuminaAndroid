package com.oneimage.android.api

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class WebRtcReceivedFile(
    val fileId: String,
    val filename: String,
    val mimeType: String,
    val size: Long,
    val url: String,
    val taskId: String?,
    val label: String?
)

class OneImageWebRtcClient(
    private val context: Context,
    private val clientId: String,
    private val onStatus: (String) -> Unit,
    private val onFileReceived: (WebRtcReceivedFile) -> Unit
) {
    private val transferOwnerId = UUID.randomUUID().toString()
    private val firestore = FirebaseFirestore.getInstance()
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private var sessionId: String? = null
    private var answerListener: ListenerRegistration? = null
    private var candidateListener: ListenerRegistration? = null
    private val inputFiles = mutableMapOf<String, Pair<Uri, OneImageFileInfo>>()
    private val incomingFiles = mutableMapOf<String, IncomingFile>()
    private val pendingCandidates = mutableListOf<IceCandidate>()
    private val sendScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sendMutex = Mutex()
    private var isOfferWritten = false
    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .createInitializationOptions()
        )
    }

    fun setInputImage(uri: Uri, fileInfo: OneImageFileInfo) {
        inputFiles["inputImage"] = uri to fileInfo
    }

    fun setInputFiles(files: Map<String, Pair<Uri, OneImageFileInfo>>) {
        inputFiles.clear()
        inputFiles.putAll(files)
    }

    fun isOpen(): Boolean = dataChannel?.state() == DataChannel.State.OPEN

    fun requestTaskResults(taskId: String, taskType: String = "image"): Boolean {
        val channel = dataChannel ?: return false
        if (channel.state() != DataChannel.State.OPEN) return false
        sendJson(
            channel,
            JSONObject()
                .put("type", "request_task_results")
                .put("taskId", taskId)
                .put("taskType", taskType)
        )
        return true
    }

    suspend fun connect(timeoutMs: Long = 15_000): Boolean = withContext(Dispatchers.Main) {
        if (dataChannel?.state() == DataChannel.State.OPEN) return@withContext true
        close()

        onStatus("Opening direct transfer...")
        val factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        val iceServer = PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        val id = UUID.randomUUID().toString()
        sessionId = id

        val pc = factory.createPeerConnection(
            PeerConnection.RTCConfiguration(listOf(iceServer)),
            object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    onStatus("Direct transfer: ${state?.name?.lowercase() ?: "checking"}")
                }
                override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit
                override fun onIceCandidate(candidate: IceCandidate?) {
                    if (candidate == null) return
                    synchronized(this@OneImageWebRtcClient) {
                        if (isOfferWritten) {
                            sendCandidate(id, candidate)
                        } else {
                            pendingCandidates.add(candidate)
                        }
                    }
                }
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
                override fun onAddStream(stream: org.webrtc.MediaStream?) = Unit
                override fun onRemoveStream(stream: org.webrtc.MediaStream?) = Unit
                override fun onDataChannel(channel: DataChannel?) = Unit
                override fun onRenegotiationNeeded() = Unit
                override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) = Unit
            }
        ) ?: return@withContext false

        peerConnection = pc
        dataChannel = pc.createDataChannel("files", DataChannel.Init()).also { channel ->
            channel.registerObserver(object : DataChannel.Observer {
                override fun onBufferedAmountChange(previousAmount: Long) = Unit
                override fun onStateChange() {
                    if (channel.state() == DataChannel.State.OPEN) onStatus("Direct transfer connected")
                }
                override fun onMessage(buffer: DataChannel.Buffer?) {
                    if (buffer == null) return
                    handleDataChannelMessage(buffer)
                }
            })
        }

        val offer = pc.createOfferSuspend()
        pc.setLocalDescriptionSuspend(offer)
        firestore.collection("webrtc_signals").document(id).set(
            mapOf(
                "offer" to (offer.description ?: ""),
                "clientId" to clientId,
                "recipientId" to "local-agent",
                "status" to "pending",
                "createdAt" to Timestamp.now()
            )
        ).awaitResult()

        synchronized(this@OneImageWebRtcClient) {
            isOfferWritten = true
            pendingCandidates.forEach { sendCandidate(id, it) }
            pendingCandidates.clear()
        }

        answerListener = firestore.collection("webrtc_signals").document(id)
            .addSnapshotListener { snapshot, _ ->
                val answer = snapshot?.getString("answer")
                if (!answer.isNullOrBlank() && pc.remoteDescription == null) {
                    pc.setRemoteDescription(
                        LoggingSdpObserver("setRemoteDescription"),
                        SessionDescription(SessionDescription.Type.ANSWER, answer)
                    )
                }
            }

        candidateListener = firestore.collection("webrtc_signals")
            .document(id)
            .collection("candidates")
            .whereEqualTo("sender", "agent")
            .addSnapshotListener { snapshot, _ ->
                snapshot?.documentChanges?.forEach { change ->
                    val candidate = change.document.getString("candidate") ?: return@forEach
                    val mid = change.document.getString("mid") ?: ""
                    pc.addIceCandidate(IceCandidate(mid, 0, candidate))
                }
            }

        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < timeoutMs) {
            if (dataChannel?.state() == DataChannel.State.OPEN) return@withContext true
            kotlinx.coroutines.delay(100)
        }
        false
    }

    fun close() {
        answerListener?.remove()
        candidateListener?.remove()
        answerListener = null
        candidateListener = null
        dataChannel?.close()
        peerConnection?.close()
        dataChannel = null
        peerConnection = null
        sessionId = null
        incomingFiles.clear()
        WebRtcTransferProgressStore.clearOwner(transferOwnerId)
        synchronized(this) {
            pendingCandidates.clear()
            isOfferWritten = false
        }
    }

    private fun sendCandidate(id: String, candidate: IceCandidate) {
        firestore.collection("webrtc_signals")
            .document(id)
            .collection("candidates")
            .add(
                mapOf(
                    "candidate" to candidate.sdp,
                    "mid" to (candidate.sdpMid ?: ""),
                    "sender" to "client",
                    "createdAt" to Timestamp.now()
                )
            )
    }

    private fun handleDataChannelMessage(buffer: DataChannel.Buffer) {
        val bytes = ByteArray(buffer.data.remaining())
        buffer.data.get(bytes)
        if (buffer.binary) {
            handleIncomingBinary(bytes)
            return
        }

        val text = bytes.toString(Charsets.UTF_8)
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (json.optString("type")) {
            "request_file" -> sendSelectedFile(json.optString("fileId", "inputImage"))
            "request_files" -> {
                val files = json.optJSONArray("files")
                if (files != null) {
                    for (index in 0 until files.length()) {
                        val file = files.optJSONObject(index) ?: continue
                        sendSelectedFile(file.optString("id", "inputImage"))
                    }
                }
            }
            "file_start" -> {
                val fileId = json.getString("fileId")
                val wireSize = json.optLong("size", 0L)
                val filename = json.optString("filename", "result")
                val taskId = json.optString("taskId").ifBlank { null }
                val nowMs = SystemClock.elapsedRealtime()
                incomingFiles[fileId] = IncomingFile(
                    fileId = fileId,
                    filename = filename,
                    mimeType = json.optString("mimetype", "application/octet-stream"),
                    size = wireSize,
                    originalSize = json.optLong("originalSize", wireSize),
                    taskId = taskId,
                    label = json.optString("label").ifBlank { null },
                    checksum = json.optString("checksum").ifBlank { null },
                    lastProgressAtMs = nowMs
                )
                WebRtcTransferProgressStore.start(transferOwnerId, fileId, filename, wireSize, taskId, nowMs)
            }
            "file_end" -> {
                val fileId = json.optString("fileId")
                val incoming = incomingFiles[fileId] ?: return
                incoming.endReceived = true
                finalizeIncomingFile(fileId)
            }
            "file_nack" -> onStatus(json.optString("message", "File transfer was rejected"))
            "task_results_complete" -> onStatus("Restored ${json.optInt("count", 0)} result file(s)")
            "task_results_unavailable" -> onStatus(json.optString("message", "Stored results are unavailable"))
        }
    }

    private fun sendSelectedFile(requestedFileId: String) {
        val channel = dataChannel ?: return
        val entry = inputFiles[requestedFileId] ?: inputFiles.values.firstOrNull() ?: return
        val uri = entry.first
        val info = entry.second
        sendScope.launch {
            try {
                sendMutex.withLock {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withLock
                    sendJson(
                        channel,
                        JSONObject()
                            .put("type", "file_start")
                            .put("fileId", requestedFileId)
                            .put("filename", info.filename)
                            .put("size", bytes.size)
                            .put("mimetype", info.mimeType)
                    )
                    var offset = 0
                    while (offset < bytes.size) {
                        val end = minOf(offset + WEBRTC_CHUNK_SIZE, bytes.size)
                        channel.send(DataChannel.Buffer(ByteBuffer.wrap(bytes, offset, end - offset), true))
                        offset = end
                    }
                    sendJson(
                        channel,
                        JSONObject()
                            .put("type", "file_end")
                            .put("fileId", requestedFileId)
                            .put("filename", info.filename)
                    )
                }
            } catch (e: Exception) {
                onStatus("File transfer failed: ${e.message}")
            }
        }
    }

    private fun sendJson(channel: DataChannel, json: JSONObject) {
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        channel.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), false))
    }

    private fun handleIncomingBinary(bytes: ByteArray) {
        val framed = parseFramedBinary(bytes)
        if (framed?.error != null) {
            onStatus(framed.error)
            WebRtcTransferProgressStore.fail(
                transferOwnerId,
                framed.fileId,
                framed.error,
                SystemClock.elapsedRealtime()
            )
            incomingFiles.remove(framed.fileId)
            return
        }
        val fileId = framed?.fileId ?: incomingFiles.keys.lastOrNull() ?: return
        val file = incomingFiles[fileId] ?: return

        val chunk = framed?.chunk ?: bytes
        val offset = framed?.offset
        val received = file.bytes.size()

        if (offset != null) {
            if (offset < received && offset + chunk.size <= received) return
            if (offset != received) {
                val message = "Restore failed for ${file.filename}: chunk order was invalid"
                onStatus(message)
                incomingFiles.remove(fileId)
                WebRtcTransferProgressStore.fail(transferOwnerId, fileId, message, SystemClock.elapsedRealtime())
                return
            }
        }

        val remaining = (file.size - received).coerceAtLeast(0L).toInt()
        if (remaining <= 0) {
            if (file.endReceived) finalizeIncomingFile(fileId)
            return
        }

        val acceptedChunk = if (chunk.size > remaining) chunk.copyOf(remaining) else chunk
        file.bytes.write(acceptedChunk)
        reportIncomingProgress(file)
        if (file.endReceived || file.bytes.size().toLong() >= file.size) {
            finalizeIncomingFile(fileId)
        }
    }

    private fun finalizeIncomingFile(fileId: String) {
        val incoming = incomingFiles.remove(fileId) ?: return
        val wireBytes = incoming.bytes.toByteArray()
        if (wireBytes.size.toLong() < incoming.size) {
            incomingFiles[fileId] = incoming
            return
        }

        WebRtcTransferProgressStore.verifying(
            transferOwnerId,
            fileId,
            incoming.size,
            SystemClock.elapsedRealtime()
        )

        val expectedWireSize = incoming.size.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val trimmedWireBytes = if (wireBytes.size > expectedWireSize) {
            wireBytes.copyOf(expectedWireSize)
        } else {
            wireBytes
        }
        val expectedOriginalSize = incoming.originalSize
            .coerceAtLeast(0L)
            .coerceAtMost(trimmedWireBytes.size.toLong())
            .toInt()
        val fileBytes = if (expectedOriginalSize in 1 until trimmedWireBytes.size) {
            trimmedWireBytes.copyOf(expectedOriginalSize)
        } else {
            trimmedWireBytes
        }
        val actualChecksum = incoming.checksum?.let { sha256(fileBytes) }
        if (incoming.checksum != null && actualChecksum != null && !incoming.checksum.equals(actualChecksum, ignoreCase = true)) {
            val message = "Restore failed for ${incoming.filename}: checksum mismatch"
            onStatus(message)
            WebRtcTransferProgressStore.fail(transferOwnerId, fileId, message, SystemClock.elapsedRealtime())
            return
        }

        val safeName = incoming.filename.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val directory = File(context.cacheDir, "oneimage-results").apply { mkdirs() }
        val file = File(directory, "${incoming.fileId}-$safeName")
        file.writeBytes(fileBytes)
        
        dataChannel?.let { channel ->
            sendJson(
                channel,
                JSONObject()
                    .put("type", "file_ack")
                    .put("fileId", incoming.fileId)
                    .put("filename", incoming.filename)
                    .put("size", fileBytes.size)
                    .put("wireSize", trimmedWireBytes.size)
            )
        }
        onFileReceived(
            WebRtcReceivedFile(
                fileId = incoming.fileId,
                filename = incoming.filename,
                mimeType = incoming.mimeType,
                size = file.length(),
                url = file.toURI().toString(),
                taskId = incoming.taskId,
                label = incoming.label
            )
        )
        WebRtcTransferProgressStore.complete(transferOwnerId, fileId, SystemClock.elapsedRealtime())
    }

    private fun reportIncomingProgress(file: IncomingFile) {
        val received = file.bytes.size().toLong()
        val percent = if (file.size <= 0L) 0 else ((received * 100L) / file.size).toInt().coerceIn(0, 100)
        val nowMs = SystemClock.elapsedRealtime()
        if (received < file.size && percent == file.lastProgressPercent && nowMs - file.lastProgressAtMs < PROGRESS_UPDATE_INTERVAL_MS) {
            return
        }
        file.lastProgressPercent = percent
        file.lastProgressAtMs = nowMs
        WebRtcTransferProgressStore.receiving(transferOwnerId, file.fileId, received, file.size, nowMs)
    }

    private fun parseFramedBinary(bytes: ByteArray): FramedChunk? {
        if (bytes.size < FRAMED_BINARY_MIN_HEADER_BYTES) return null
        val magic = bytes.copyOfRange(0, 4).toString(Charsets.UTF_8)
        if (magic != FRAMED_BINARY_MAGIC) return null
        val fileIdLength = bytes[4].toInt() and 0xff
        val headerLength = FRAMED_BINARY_MIN_HEADER_BYTES + fileIdLength
        if (fileIdLength <= 0 || bytes.size < headerLength) return null
        val view = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val offset = view.getInt(5)
        val chunkSize = view.getInt(9)
        val fileId = bytes.copyOfRange(FRAMED_BINARY_MIN_HEADER_BYTES, headerLength).toString(Charsets.UTF_8)
        val chunk = bytes.copyOfRange(headerLength, bytes.size)
        if (chunk.size != chunkSize) {
            return FramedChunk(
                fileId = fileId,
                offset = offset,
                chunk = chunk,
                error = "Restore failed for $fileId: invalid chunk size ${chunk.size}/$chunkSize"
            )
        }
        return FramedChunk(fileId = fileId, offset = offset, chunk = chunk)
    }

    private data class IncomingFile(
        val fileId: String,
        val filename: String,
        val mimeType: String,
        val size: Long,
        val originalSize: Long,
        val taskId: String?,
        val label: String?,
        val checksum: String?,
        var endReceived: Boolean = false,
        var lastProgressPercent: Int = 0,
        var lastProgressAtMs: Long = 0L,
        val bytes: ByteArrayOutputStream = ByteArrayOutputStream()
    )

    private data class FramedChunk(
        val fileId: String,
        val offset: Int,
        val chunk: ByteArray,
        val error: String? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as FramedChunk

            if (offset != other.offset) return false
            if (fileId != other.fileId) return false
            if (!chunk.contentEquals(other.chunk)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = offset
            result = 31 * result + fileId.hashCode()
            result = 31 * result + chunk.contentHashCode()
            return result
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private class LoggingSdpObserver(private val label: String) : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription?) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) {
            android.util.Log.w("OneImageWebRTC", "$label create failed: $error")
        }
        override fun onSetFailure(error: String?) {
            android.util.Log.w("OneImageWebRTC", "$label set failed: $error")
        }
    }

    companion object {
        private const val WEBRTC_CHUNK_SIZE = 16 * 1024
        private const val PROGRESS_UPDATE_INTERVAL_MS = 250L
        private const val FRAMED_BINARY_MAGIC = "OWB5"
        private const val FRAMED_BINARY_MIN_HEADER_BYTES = 13
    }
}

private suspend fun PeerConnection.createOfferSuspend(): SessionDescription =
    suspendCancellableCoroutine { continuation ->
        createOffer(
            object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription?) {
                    if (description == null) continuation.resumeWithException(IllegalStateException("Empty WebRTC offer"))
                    else continuation.resume(description)
                }
                override fun onSetSuccess() = Unit
                override fun onCreateFailure(error: String?) {
                    continuation.resumeWithException(IllegalStateException(error ?: "Could not create WebRTC offer"))
                }
                override fun onSetFailure(error: String?) = Unit
            },
            MediaConstraints()
        )
    }

private suspend fun PeerConnection.setLocalDescriptionSuspend(description: SessionDescription) =
    suspendCancellableCoroutine { continuation ->
        setLocalDescription(
            object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription?) = Unit
                override fun onSetSuccess() {
                    continuation.resume(Unit)
                }
                override fun onCreateFailure(error: String?) = Unit
                override fun onSetFailure(error: String?) {
                    continuation.resumeWithException(IllegalStateException(error ?: "Could not set WebRTC offer"))
                }
            },
            description
        )
    }
