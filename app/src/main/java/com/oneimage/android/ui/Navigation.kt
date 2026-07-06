package com.oneimage.android.ui

import kotlinx.serialization.Serializable

sealed class Screen {
    @Serializable
    object Login : Screen()

    @Serializable
    object Dashboard : Screen()

    @Serializable
    object ImageGen : Screen()

    @Serializable
    object VideoGen : Screen()

    @Serializable
    object LipSync : Screen()

    @Serializable
    object CharacterReplacement : Screen()

    @Serializable
    object StoryImages : Screen()

    @Serializable
    object MeshModel : Screen()

    @Serializable
    object GameAssetUpscaler : Screen()

    @Serializable
    object VideoDescription : Screen()

    @Serializable
    object Keyframes : Screen()

    @Serializable
    object DataSync : Screen()

    @Serializable
    object Billing : Screen()

    @Serializable
    object Settings : Screen()

    @Serializable
    object Support : Screen()

    @Serializable
    object Legal : Screen()
}
