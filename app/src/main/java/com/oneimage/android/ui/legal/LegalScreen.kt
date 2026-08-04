package com.oneimage.android.ui.legal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oneimage.android.R
import com.oneimage.android.api.OneImageApi
import com.oneimage.android.ui.shared.IndependentServiceNotice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy & Terms", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            item {
                IndependentServiceNotice()
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "GenStudio uses one shared legal document set for web and Android. The hosted pages below are the authoritative agreements for this app.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
            item {
                Text(text = "Privacy Policy", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.privacy_policy_summary),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { uriHandler.openUri(OneImageApi.PRIVACY_POLICY_URL) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open full Privacy Policy")
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
            item {
                Text(text = "Terms of Service", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.terms_of_service_summary),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { uriHandler.openUri(OneImageApi.TERMS_OF_SERVICE_URL) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open full Terms of Service")
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
            item {
                Text(text = "Account and Data Deletion", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "You can request account deletion in the app or from the external deletion page.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { uriHandler.openUri(OneImageApi.ACCOUNT_DELETION_URL) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open deletion instructions")
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
            item {
                Text(text = "Commercial Disclosure", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Review payment timing, delivery, cancellation, refunds, and operating environment details before purchase.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { uriHandler.openUri(OneImageApi.COMMERCIAL_DISCLOSURE_URL) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open commercial disclosure")
                }
            }
        }
    }
}



