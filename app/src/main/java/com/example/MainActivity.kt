package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.ui.theme.LuminaTheme
import com.example.ui.Screen
import com.example.ui.auth.LoginScreen
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.imagegen.ImageGenScreen
import com.example.ui.datasync.DataSyncScreen
import com.example.ui.billing.BillingScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.legal.LegalScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
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
                composable<Screen.ImageGen> { ImageGenScreen(onBack = { navController.popBackStack() }) }
                composable<Screen.DataSync> { DataSyncScreen(onBack = { navController.popBackStack() }) }
                composable<Screen.Billing> { BillingScreen(onBack = { navController.popBackStack() }) }
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
