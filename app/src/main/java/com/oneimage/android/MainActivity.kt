package com.oneimage.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import androidx.navigation.toRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.oneimage.android.notifications.MobileNotificationManager
import com.oneimage.android.ui.Screen
import com.oneimage.android.ui.auth.LoginScreen
import com.oneimage.android.ui.dashboard.DashboardScreen
import com.oneimage.android.ui.datasync.DataSyncScreen
import com.oneimage.android.ui.imagegen.ImageGenScreen
import com.oneimage.android.ui.legal.LegalScreen
import com.oneimage.android.ui.lipsync.LipSyncScreen
import com.oneimage.android.ui.meshmodel.MeshModelScreen
import com.oneimage.android.ui.settings.SettingsScreen
import com.oneimage.android.ui.shared.AppNotificationHost
import com.oneimage.android.ui.shared.SharedHistoryScreen
import com.oneimage.android.ui.shared.SharedHistorySpecs
import com.oneimage.android.ui.shared.TaskNotificationObserver
import com.oneimage.android.ui.support.SupportScreen
import com.oneimage.android.ui.shared.WebRtcTransferProgressOverlay
import com.oneimage.android.ui.shared.rememberAppNotificationState
import com.oneimage.android.ui.theme.LuminaTheme
import com.oneimage.android.ui.theme.ThemePreferences
import com.oneimage.android.ui.videogen.VideoGenScreen
import com.oneimage.android.ui.workflow.WorkflowScreen
import com.oneimage.android.ui.workflow.WorkflowSpecs
import androidx.compose.foundation.isSystemInDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.oneimage.android.api.AccountManager.initialize()
        enableEdgeToEdge()
        setContent {
            val systemDarkTheme = isSystemInDarkTheme()
            var darkModeEnabled by rememberSaveable {
                mutableStateOf(ThemePreferences.getDarkModeEnabled(applicationContext, systemDarkTheme))
            }

            LaunchedEffect(darkModeEnabled) {
                ThemePreferences.setDarkModeEnabled(applicationContext, darkModeEnabled)
            }

            LuminaTheme(darkTheme = darkModeEnabled) {
                val context = LocalContext.current
                val navController = rememberNavController()
                val notificationState = rememberAppNotificationState()
                val auth = remember { FirebaseAuth.getInstance() }
                var signedInUid by remember { mutableStateOf(auth.currentUser?.uid) }
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { }

                DisposableEffect(auth) {
                    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                        signedInUid = firebaseAuth.currentUser?.uid
                    }
                    auth.addAuthStateListener(listener)
                    onDispose { auth.removeAuthStateListener(listener) }
                }

                LaunchedEffect(signedInUid) {
                    if (signedInUid.isNullOrBlank()) return@LaunchedEffect
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    runCatching { MobileNotificationManager.registerCurrentToken(context) }
                }

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        TaskNotificationObserver(notificationState)
                        NavHost(navController = navController, startDestination = Screen.Login) {
                            composable<Screen.Login> {
                                LoginScreen(
                                    onLoginSuccess = {
                                        navController.navigate(Screen.Dashboard) {
                                            popUpTo(Screen.Login) { inclusive = true }
                                        }
                                    },
                                    onLegalClick = { navController.navigate(Screen.Legal) }
                                )
                            }
                            composable<Screen.Dashboard> { DashboardScreen(navController) }
                            composable<Screen.ImageGen> {
                                ImageGenScreen(
                                    onBack = { goBackToDashboard(navController) },
                                    onHistory = { navController.navigate(Screen.History(SharedHistorySpecs.Image.workflowKey)) }
                                )
                            }
                            composable<Screen.VideoGen> {
                                VideoGenScreen(
                                    onBack = { goBackToDashboard(navController) },
                                    onHistory = { navController.navigate(Screen.History(SharedHistorySpecs.Video.workflowKey)) }
                                )
                            }
                            composable<Screen.SingleI2V> {
                                WorkflowScreen(
                                    spec = WorkflowSpecs.SingleI2V,
                                    onBack = { goBackToDashboard(navController) },
                                    onHistory = { navController.navigate(Screen.History(SharedHistorySpecs.SingleI2V.workflowKey)) }
                                )
                            }
                            composable<Screen.LipSync> {
                                LipSyncScreen(
                                    onBack = { goBackToDashboard(navController) },
                                    onHistory = { navController.navigate(Screen.History(SharedHistorySpecs.LipSync.workflowKey)) }
                                )
                            }
                            composable<Screen.CharacterReplacement> {
                                WorkflowScreen(
                                    spec = WorkflowSpecs.CharacterReplacement,
                                    onBack = { goBackToDashboard(navController) },
                                    onHistory = { navController.navigate(Screen.History(SharedHistorySpecs.CharacterReplacement.workflowKey)) }
                                )
                            }
                            composable<Screen.StoryImages> {
                                WorkflowScreen(
                                    spec = WorkflowSpecs.StoryImages,
                                    onBack = { goBackToDashboard(navController) },
                                    onHistory = { navController.navigate(Screen.History(SharedHistorySpecs.StoryImages.workflowKey)) }
                                )
                            }
                            composable<Screen.MeshModel> {
                                MeshModelScreen(
                                    onBack = { goBackToDashboard(navController) },
                                    onHistory = { navController.navigate(Screen.History(SharedHistorySpecs.MeshModel.workflowKey)) }
                                )
                            }
                            composable<Screen.GameAssetUpscaler> {
                                WorkflowScreen(
                                    spec = WorkflowSpecs.GameAssetUpscaler,
                                    onBack = { goBackToDashboard(navController) },
                                    onHistory = { navController.navigate(Screen.History(SharedHistorySpecs.GameAssetUpscaler.workflowKey)) }
                                )
                            }
                            composable<Screen.Keyframes> {
                                WorkflowScreen(
                                    spec = WorkflowSpecs.Keyframes,
                                    onBack = { goBackToDashboard(navController) },
                                    onHistory = { navController.navigate(Screen.History(SharedHistorySpecs.Keyframes.workflowKey)) }
                                )
                            }
                            composable<Screen.DataSync> { DataSyncScreen(onBack = { navController.popBackStack() }) }
                            composable<Screen.Support> { SupportScreen(onBack = { navController.popBackStack() }) }
                            composable<Screen.Settings> {
                                SettingsScreen(
                                    onBack = { navController.popBackStack() },
                                    darkModeEnabled = darkModeEnabled,
                                    onDarkModeChanged = { darkModeEnabled = it },
                                    onLogout = {
                                        navController.navigate(Screen.Login) {
                                            popUpTo(Screen.Dashboard) { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable<Screen.Legal> { LegalScreen(onBack = { navController.popBackStack() }) }
                            composable<Screen.History> { backStackEntry ->
                                SharedHistorySpecs.fromWorkflowKey(backStackEntry.toRoute<Screen.History>().workflowKey)?.let { spec ->
                                    SharedHistoryScreen(spec = spec, onBack = { navController.popBackStack() })
                                }
                            }
                        }
                        AppNotificationHost(
                            state = notificationState,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                        WebRtcTransferProgressOverlay(modifier = Modifier.align(Alignment.BottomCenter))
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
