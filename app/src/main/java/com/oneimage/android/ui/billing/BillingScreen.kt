package com.oneimage.android.ui.billing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oneimage.android.ui.account.AccountViewModel
import com.oneimage.android.ui.shared.IndependentServiceNotice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingScreen(
    onBack: () -> Unit,
    accountViewModel: AccountViewModel = viewModel()
) {
    val accountState by accountViewModel.uiState.collectAsState()
    val profile = accountState.profile
    val isAdmin = profile?.hasUnlimitedAccess == true
    val currentPlan = profile?.planLabel ?: "none"
    val currentStatus = profile?.statusLabel ?: "unpaid"
    var serviceModelAccepted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        accountViewModel.loadProfile()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Billing & Plans", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Text(
                    text = "Professional Membership",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Manage your creator account",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Current Account",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = profile?.creditBalanceText ?: if (accountState.isLoading) "Syncing..." else "0",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$currentPlan plan - $currentStatus status",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (profile?.role == "admin" || profile?.admin == true || profile?.isAdmin == true) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Admin access",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (accountState.error != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = accountState.error ?: "",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            item {
                if (isAdmin) {
                    SubscriptionCard(
                        title = "Admin",
                        price = "Unlimited",
                        period = "",
                        features = listOf(
                            "Unlimited generation access",
                            "Comped account status",
                            "No credit deduction for tasks",
                            "Full service access"
                        ),
                        isCurrent = true,
                        highlight = true
                    )
                }
            }

            item {
                SubscriptionCard(
                    title = "Pro Plan",
                    price = "$12.00",
                    period = "/mo",
                    features = listOf(
                        "Unlimited HD creations",
                        "Priority RTC Channel Access",
                        "Advanced Style Controls",
                        "Zero Ads Experience"
                    ),
                    isCurrent = currentPlan.equals("pro", ignoreCase = true) && !isAdmin,
                    highlight = !isAdmin
                )
            }

            item {
                SubscriptionCard(
                    title = "Basic",
                    price = "$0.00",
                    period = "/mo",
                    features = listOf(
                        "10 Generations / day",
                        "Standard RTC Channel",
                        "Community Discord Access"
                    ),
                    isCurrent = (currentPlan.equals("none", ignoreCase = true) || currentPlan.equals("basic", ignoreCase = true)) && !isAdmin,
                    highlight = false
                )
            }

            item {
                IndependentServiceNotice(
                    checked = serviceModelAccepted,
                    onCheckedChange = { serviceModelAccepted = it }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { },
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 12.dp)
                ) {
                    Text(
                        when {
                            isAdmin -> "Admin Access Active"
                            !serviceModelAccepted -> "Confirm Service Model"
                            else -> "Checkout Coming Soon"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CreditCard, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (serviceModelAccepted && !isAdmin) {
                            "Stripe checkout is not enabled in this Android build yet."
                        } else {
                            "Secure checkout will open here when payment setup is enabled."
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SubscriptionCard(
    title: String,
    price: String,
    period: String,
    features: List<String>,
    isCurrent: Boolean,
    highlight: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isCurrent) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "ACTIVE",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(vertical = 12.dp)) {
                Text(text = price, fontWeight = FontWeight.Bold, fontSize = 36.sp)
                Text(text = period, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            }
            
            Divider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 16.dp))
            
            features.forEach { feature ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = feature, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}


