package com.example.ui

import kotlinx.serialization.Serializable

sealed class Screen {
    @Serializable
    object Login : Screen()
    
    @Serializable
    object Dashboard : Screen()
    
    @Serializable
    object ImageGen : Screen()
    
    @Serializable
    object DataSync : Screen()
    
    @Serializable
    object Billing : Screen()
    
    @Serializable
    object Settings : Screen()
    
    @Serializable
    object Legal : Screen()
}
