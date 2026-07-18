package com.oneimage.android.api

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.ceil
import kotlin.math.roundToInt

data class WorkflowPricingConfig(
    val oneImageLightning: Int = 30,
    val oneImageQuality: Int = 50,
    val oneVideoPerSecond: Int = 4,
    val singleI2VFlat: Int = 12,
    val oneMotionPerSecond: Int = 6,
    val oneMotionMinimum: Int = 30,
    val oneMotionExtraKeyframe: Int = 2,
    val videoDescriptionFlat: Int = 10,
    val oneLipSyncPerSecond: Int = 4,
    val characterReplacementPerSecond: Int = 4,
    val qwenImageEditFlat: Int = 30,
    val refRestyleFlat: Int = 24,
    val meshModelFlat: Int = 50,
    val gameAssetUpscalerFlat: Int = 30
) {
    companion object {
        fun fromFirestoreMap(data: Map<String, Any>?): WorkflowPricingConfig {
            val defaults = WorkflowPricingConfig()
            return WorkflowPricingConfig(
                oneImageLightning = number(data, "oneImageLightning", defaults.oneImageLightning),
                oneImageQuality = number(data, "oneImageQuality", defaults.oneImageQuality),
                oneVideoPerSecond = number(data, "oneVideoPerSecond", defaults.oneVideoPerSecond),
                singleI2VFlat = number(data, "singleI2VFlat", defaults.singleI2VFlat),
                oneMotionPerSecond = number(data, "oneMotionPerSecond", defaults.oneMotionPerSecond),
                oneMotionMinimum = number(data, "oneMotionMinimum", defaults.oneMotionMinimum),
                oneMotionExtraKeyframe = number(data, "oneMotionExtraKeyframe", defaults.oneMotionExtraKeyframe),
                videoDescriptionFlat = number(data, "videoDescriptionFlat", defaults.videoDescriptionFlat),
                oneLipSyncPerSecond = number(data, "oneLipSyncPerSecond", defaults.oneLipSyncPerSecond),
                characterReplacementPerSecond = number(data, "characterReplacementPerSecond", defaults.characterReplacementPerSecond),
                qwenImageEditFlat = number(data, "qwenImageEditFlat", defaults.qwenImageEditFlat),
                refRestyleFlat = number(data, "refRestyleFlat", defaults.refRestyleFlat),
                meshModelFlat = number(data, "meshModelFlat", defaults.meshModelFlat),
                gameAssetUpscalerFlat = number(data, "gameAssetUpscalerFlat", defaults.gameAssetUpscalerFlat)
            )
        }

        private fun number(data: Map<String, Any>?, key: String, fallback: Int): Int {
            val raw = data?.get(key) as? Number ?: return fallback
            return raw.toDouble().roundToInt().coerceAtLeast(0)
        }
    }
}

object WorkflowPricingRepository {
    private const val TAG = "WorkflowPricingRepo"

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val _pricingFlow = MutableStateFlow(WorkflowPricingConfig())
    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var pricingListener: ListenerRegistration? = null

    val pricingFlow: StateFlow<WorkflowPricingConfig> = _pricingFlow

    fun start() {
        if (authListener != null) {
            return
        }

        authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser == null) {
                detachPricingListener()
                _pricingFlow.value = WorkflowPricingConfig()
            } else {
                attachPricingListener()
            }
        }
        auth.addAuthStateListener(authListener!!)

        if (auth.currentUser != null) {
            attachPricingListener()
        }
    }

    private fun attachPricingListener() {
        if (pricingListener != null) {
            return
        }

        pricingListener = firestore.collection("app_config")
            .document("workflow_pricing")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Falling back to default workflow pricing.", error)
                    _pricingFlow.value = WorkflowPricingConfig()
                    return@addSnapshotListener
                }

                _pricingFlow.value = WorkflowPricingConfig.fromFirestoreMap(snapshot?.data)
            }
    }

    private fun detachPricingListener() {
        pricingListener?.remove()
        pricingListener = null
    }
}

fun WorkflowPricingConfig.oneImageCredits(isLightning: Boolean): Int =
    if (isLightning) oneImageLightning else oneImageQuality

fun WorkflowPricingConfig.oneVideoCredits(durationSeconds: Int): Int =
    durationSeconds.coerceAtLeast(1) * oneVideoPerSecond

fun WorkflowPricingConfig.lipSyncCredits(durationSeconds: Float): Int =
    ceil(durationSeconds.coerceAtLeast(0.1f).toDouble()).toInt().coerceAtLeast(1) * oneLipSyncPerSecond

fun WorkflowPricingConfig.characterReplacementCredits(durationSeconds: Float): Int =
    ceil(durationSeconds.coerceAtLeast(0.1f).toDouble()).toInt().coerceAtLeast(1) * characterReplacementPerSecond
