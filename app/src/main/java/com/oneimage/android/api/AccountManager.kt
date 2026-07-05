package com.oneimage.android.api

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AccountManager {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _profileFlow = MutableStateFlow<OneImageAccountProfile?>(null)
    val profileFlow: StateFlow<OneImageAccountProfile?> = _profileFlow

    private var profileListener: ListenerRegistration? = null
    private var isInitialized = false

    fun initialize() {
        if (isInitialized) return
        isInitialized = true

        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user == null) {
                detachListeners()
                _profileFlow.value = null
            } else {
                listenToProfile(user.uid)
            }
        }
    }

    private fun listenToProfile(uid: String) {
        profileListener?.remove()
        profileListener = firestore.collection("users").document(uid).addSnapshotListener { snapshot, e ->
            if (e != null) return@addSnapshotListener
            if (snapshot != null && snapshot.exists()) {
                val data = snapshot.data ?: return@addSnapshotListener
                _profileFlow.value = OneImageAccountProfile.fromFirestoreMap(uid, data)
            } else {
                _profileFlow.value = null
            }
        }
    }

    private fun detachListeners() {
        profileListener?.remove()
        profileListener = null
    }
}
