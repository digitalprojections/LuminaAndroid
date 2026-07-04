package com.oneimage.android.ui.datasync

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSyncScreen(onBack: () -> Unit) {
    var isStreaming by remember { mutableStateOf(false) }
    val logs = remember { mutableStateListOf<String>() }

    LaunchedEffect(isStreaming) {
        if (isStreaming) {
            logs.add(0, "[SYSTEM] Establishing RTC Peer Connection...")
            kotlinx.coroutines.delay(800)
            logs.add(0, "[SYSTEM] ICE Candidate Exchange started")
            kotlinx.coroutines.delay(500)
            logs.add(0, "[DATA] DataChannel: 'OneImageSync' OPENED")
            logs.add(0, "[STATS] Latency: 42ms | Jitter: 4ms")
        } else {
            if (logs.isNotEmpty()) logs.add(0, "[SYSTEM] Connection Closed.")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data Stream Monitor", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Stream Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { if (isStreaming) 0.65f else 0f },
                            modifier = Modifier.size(140.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 8.dp,
                            trackColor = MaterialTheme.colorScheme.outline
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isStreaming) "8.4 Mbps" else "0.0 Mbps",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "THROUGHPUT",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem("LATENCY", "42ms")
                        StatItem("LOSS", "0.02%")
                        StatItem("BUFFER", "128kb")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Console / Log Area
            Text(
                "SYSTEM CONSOLE",
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                letterSpacing = 1.sp
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF121212)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    reverseLayout = false
                ) {
                    items(logs) { log ->
                        Text(
                            text = log,
                            fontSize = 12.sp,
                            color = if (log.contains("[ERROR]")) Color.Red else if (log.contains("[DATA]")) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Control Button
            Button(
                onClick = { isStreaming = !isStreaming },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isStreaming) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    contentColor = if (isStreaming) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(if (isStreaming) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isStreaming) "DISCONNECT STREAM" else "INITIALIZE DATA CHANNEL", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
