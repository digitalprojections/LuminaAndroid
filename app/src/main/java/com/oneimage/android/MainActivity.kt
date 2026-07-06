package com.oneimage.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.oneimage.android.ui.Screen
import com.oneimage.android.ui.auth.LoginScreen
import com.oneimage.android.ui.billing.BillingScreen
import com.oneimage.android.ui.dashboard.DashboardScreen
import com.oneimage.android.ui.datasync.DataSyncScreen
import com.oneimage.android.ui.imagegen.ImageGenScreen
import com.oneimage.android.ui.legal.LegalScreen
import com.oneimage.android.ui.lipsync.LipSyncScreen
import com.oneimage.android.ui.meshmodel.MeshModelScreen
import com.oneimage.android.ui.settings.SettingsScreen
import com.oneimage.android.ui.support.SupportScreen
import com.oneimage.android.ui.theme.LuminaTheme
import com.oneimage.android.ui.videogen.VideoGenScreen
import com.oneimage.android.ui.workflow.WorkflowScreen
import com.oneimage.android.ui.workflow.WorkflowSpecs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.oneimage.android.api.AccountManager.initialize()
        enableEdgeToEdge()
        setContent {
            LuminaTheme {
                val navController = rememberNavController()
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    NavHost(navController = navController, startDestination = Screen.Login) {
                        composable<Screen.Login> {
                            LoginScreen(onLoginSuccess = {
                                navController.navigate(Screen.Dashboard) {
                                    popUpTo(Screen.Login) { inclusive = true }
                                }
                            })
                        }
                        composable<Screen.Dashboard> { DashboardScreen(navController) }
                        composable<Screen.ImageGen> { ImageGenScreen(onBack = { goBackToDashboard(navController) }) }
                        composable<Screen.VideoGen> { VideoGenScreen(onBack = { goBackToDashboard(navController) }) }
                        composable<Screen.LipSync> { LipSyncScreen(onBack = { goBackToDashboard(navController) }) }
                        composable<Screen.CharacterReplacement> { WorkflowScreen(WorkflowSpecs.CharacterReplacement) { goBackToDashboard(navController) } }
                        composable<Screen.StoryImages> { WorkflowScreen(WorkflowSpecs.StoryImages) { goBackToDashboard(navController) } }
                        composable<Screen.MeshModel> { MeshModelScreen(onBack = { goBackToDashboard(navController) }) }
                        composable<Screen.GameAssetUpscaler> { WorkflowScreen(WorkflowSpecs.GameAssetUpscaler) { goBackToDashboard(navController) } }
                        composable<Screen.VideoDescription> { WorkflowScreen(WorkflowSpecs.VideoDescription) { goBackToDashboard(navController) } }
                        composable<Screen.Keyframes> { WorkflowScreen(WorkflowSpecs.Keyframes) { goBackToDashboard(navController) } }
                        composable<Screen.DataSync> { DataSyncScreen(onBack = { navController.popBackStack() }) }
                        composable<Screen.Billing> { BillingScreen(onBack = { navController.popBackStack() }) }
                        composable<Screen.Support> { SupportScreen(onBack = { navController.popBackStack() }) }
                        composable<Screen.Settings> {
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
                                onLogout = {
                                    navController.navigate(Screen.Login) {
                                        popUpTo(Screen.Dashboard) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable<Screen.Legal> { LegalScreen(onBack = { navController.popBackStack() }) }
                    }
                }
            }
        }
    }
}

private fun goBackToDashboard(navController: androidx.navigation.NavHostController) {
    if (!navController.popBackStack()) {
        navController.navigate(Screen.Dashboard) { launchSingleTop = true }
    }
}
