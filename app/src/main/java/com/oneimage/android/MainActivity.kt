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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import androidx.navigation.toRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.oneimage.android.notifications.MobileNotificationManager
import com.oneimage.android.ui.Screen
import com.oneimage.android.ui.account.EarnCreditsScreen
import com.oneimage.android.ui.auth.LoginScreen
import com.oneimage.android.ui.dashboard.DashboardScreen
import com.oneimage.android.ui.datasync.DataSyncScreen
import com.oneimage.android.ui.imagegen.ImageGenScreen
import com.oneimage.android.ui.legal.LegalScreen
import com.oneimage.android.ui.lipsync.LipSyncScreen
import com.oneimage.android.ui.meshmodel.MeshModelScreen
import com.oneimage.android.ui.onboarding.OnboardingPreferences
import com.oneimage.android.ui.onboarding.OnboardingScreen
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
import kotlinx.coroutines.launch

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
                val lifecycleOwner = LocalLifecycleOwner.current
                val scope = rememberCoroutineScope()
                val navController = rememberNavController()
                val notificationState = rememberAppNotificationState()
                val auth = remember { FirebaseAuth.getInstance() }
                var signedInUid by remember { mutableStateOf(auth.currentUser?.uid) }
                var pushNotificationsEnabled by remember {
                    mutableStateOf(MobileNotificationManager.areNotificationsEnabled(context))
                }
                val refreshPushNotificationState = {
                    pushNotificationsEnabled = MobileNotificationManager.areNotificationsEnabled(context)
                }
                val registerNotificationTokenIfAllowed = {
                    refreshPushNotificationState()
                    if (pushNotificationsEnabled) {
                        scope.launch {
                            runCatching { MobileNotificationManager.registerCurrentToken(context) }
                        }
                    }
                }
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { registerNotificationTokenIfAllowed() }

                DisposableEffect(auth) {
                    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                        signedInUid = firebaseAuth.currentUser?.uid
                    }
                    auth.addAuthStateListener(listener)
                    onDispose { auth.removeAuthStateListener(listener) }
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            refreshPushNotificationState()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                LaunchedEffect(signedInUid, pushNotificationsEnabled) {
                    if (signedInUid.isNullOrBlank()) return@LaunchedEffect
                    if (pushNotificationsEnabled) runCatching { MobileNotificationManager.registerCurrentToken(context) }
                }

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        TaskNotificationObserver(notificationState)
                        NavHost(navController = navController, startDestination = Screen.Login) {
                            composable<Screen.Login> {
                                LoginScreen(
                                    onLoginSuccess = {
                                        val accountId = auth.currentUser?.uid.orEmpty()
                                        val destination = if (OnboardingPreferences.isComplete(context, accountId)) {
                                            Screen.Dashboard
                                        } else {
                                            Screen.Onboarding
                                        }
                                        navController.navigate(destination) {
                                            popUpTo(Screen.Login) { inclusive = true }
                                        }
                                    },
                                    onLegalClick = { navController.navigate(Screen.Legal) }
                                )
                            }
                            composable<Screen.Onboarding> {
                                OnboardingScreen(
                                    onComplete = {
                                        auth.currentUser?.uid?.let { accountId ->
                                            OnboardingPreferences.markComplete(context, accountId)
                                        }
                                        navController.navigate(Screen.Dashboard) {
                                            popUpTo(Screen.Onboarding) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }
                            composable<Screen.Dashboard> { DashboardScreen(navController) }
                            composable<Screen.EarnCredits> {
                                EarnCreditsScreen(onBack = { navController.popBackStack() })
                            }
                            composable<Screen.ImageGen> {
                                ImageGenScreen(
                                    onBack = { goBackToDashboard(navController) },
                                    onHistory = { navController.navigate(Screen.History(SharedHistorySpecs.Image.workflowKey)) },
                                    onCreditsClick = { navController.navigate(Screen.EarnCredits) }
                                )
                            }
                            composable<Screen.VideoGen> {
                                VideoGenScreen(
                                    onBack = { goBackToDashboard(navController) },
                                    onHistory = { navController.navigate(Screen.History(SharedHistorySpecs.Video.workflowKey)) },
                                    onCreditsClick = { navController.navigate(Screen.EarnCredits) }
                                )
                            }
                            composable<Screen.SingleI2V> {
                                WorkflowScreen(
                                    spec = WorkflowSpecs.SingleI2V,
                                    onBack = { goBackToDashboard(navController) },
                                    onHistory = { navController.navigate(Screen.History(SharedHistorySpecs.SingleI2V.workflowKey)) },
                                    onCreditsClick = { navController.navigate(Screen.EarnCredits) }
                                )
                            }
                            composable<Screen.LipSync> {
                                LipSyncScreen(
                                    onBack = { goBackToDashboard(navController) },
                                    onHistory = { navController.navigate(Screen.History(SharedHistorySpecs.LipSync.workflowKey)) },
                                    onCreditsClick = { navController.navigate(Screen.EarnCredits) }
                                )
                            }
                            composable<Screen.CharacterReplacement> {
                                WorkflowScreen(
                                    spec = WorkflowSpecs.CharacterReplacement,
                                    onBack = { goBackToDashboard(navController) },
                                    onHistory = { navController.navigate(Screen.History(SharedHistorySpecs.CharacterReplacement.workflowKey)) },
                                    onCreditsClick = { navController.navigate(Screen.EarnCredits) }
                                )
                            }
                            composable<Screen.StoryImages> {
                                WorkflowScreen(
                                    spec = WorkflowSpecs.StoryImages,
                                    onBack = { goBackToDashboard(navController) },
                                    onHistory = { navController.navigate(Screen.History(SharedHistorySpecs.StoryImages.workflowKey)) },
                                    onCreditsClick = { navController.navigate(Screen.EarnCredits) }
                                )
                            }
                            composable<Screen.RefRestyle> {
                                WorkflowScreen(
                                    spec = WorkflowSpecs.RefRestyle,
                                    onBack = { goBackToDashboard(navController) },
                                    onHistory = { navController.navigate(Screen.History(SharedHistorySpecs.RefRestyle.workflowKey)) },
                                    onCreditsClick = { navController.navigate(Screen.EarnCredits) }
                                )
                            }
                            composable<Screen.MeshModel> {
                                MeshModelScreen(
                                    onBack = { goBackToDashboard(navController) },
                                    onHistory = { navController.navigate(Screen.History(SharedHistorySpecs.MeshModel.workflowKey)) },
                                    onCreditsClick = { navController.navigate(Screen.EarnCredits) }
                                )
                            }
                            composable<Screen.SoundEffects> {
                                WorkflowScreen(spec = WorkflowSpecs.SoundEffects,
                                    onBack = { navController.popBackStack() },
                                    onHistory = { navController.navigate(Screen.History(SharedHistorySpecs.SoundEffects.workflowKey)) },
                                    onCreditsClick = { navController.navigate(Screen.EarnCredits) })
                            }
                            composable<Screen.GameAssetUpscaler> {
                                WorkflowScreen(
                                    spec = WorkflowSpecs.GameAssetUpscaler,
                                    onBack = { goBackToDashboard(navController) },
                                    onHistory = { navController.navigate(Screen.History(SharedHistorySpecs.GameAssetUpscaler.workflowKey)) },
                                    onCreditsClick = { navController.navigate(Screen.EarnCredits) }
                                )
                            }
                            composable<Screen.Keyframes> {
                                WorkflowScreen(
                                    spec = WorkflowSpecs.Keyframes,
                                    onBack = { goBackToDashboard(navController) },
                                    onHistory = { navController.navigate(Screen.History(SharedHistorySpecs.Keyframes.workflowKey)) },
                                    onCreditsClick = { navController.navigate(Screen.EarnCredits) }
                                )
                            }
                            composable<Screen.DataSync> { DataSyncScreen(onBack = { navController.popBackStack() }) }
                            composable<Screen.Support> { SupportScreen(onBack = { navController.popBackStack() }) }
                            composable<Screen.Settings> {
                                SettingsScreen(
                                    onBack = { navController.popBackStack() },
                                    onLegalClick = { navController.navigate(Screen.Legal) },
                                    onEarnCreditsClick = { navController.navigate(Screen.EarnCredits) },
                                    darkModeEnabled = darkModeEnabled,
                                    onDarkModeChanged = { darkModeEnabled = it },
                                    pushNotificationsEnabled = pushNotificationsEnabled,
                                    pushNotificationsStatus = if (pushNotificationsEnabled) {
                                        "Enabled"
                                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        "Off. Turn on to allow alerts."
                                    } else {
                                        "Off in Android settings"
                                    },
                                    onPushNotificationsChanged = { enabled ->
                                        if (enabled) {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                            ) {
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            } else {
                                                refreshPushNotificationState()
                                                if (pushNotificationsEnabled) {
                                                    registerNotificationTokenIfAllowed()
                                                } else {
                                                    MobileNotificationManager.openNotificationSettings(context)
                                                }
                                            }
                                        } else {
                                            MobileNotificationManager.openNotificationSettings(context)
                                        }
                                    },
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
