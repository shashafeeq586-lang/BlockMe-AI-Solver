package com.blockme.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                SolverScreen()
            }
        }
    }
}

@Composable
fun SolverScreen() {

    var status by remember {
        mutableStateOf("AI Solver প্রস্তুত")
    }

    var isRunning by remember {
        mutableStateOf(false)
    }

    val features = listOf(
        "8 × 8 Board Detection",
        "76 Block Shape Database",
        "Four Direction Rotation",
        "Multi-Line Clear Strategy",
        "Large Block Space Reservation",
        "Automatic Move Planning",
        "Move Verification"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("BlockMe AI Solver")
                }
            )
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            item {
                Text(
                    text = status,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            item {
                Button(
                    onClick = {
                        status = "Screen detection এখনো যুক্ত করা হয়নি"
                        isRunning = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start AI Solver")
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        status = "AI Solver বন্ধ"
                        isRunning = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Stop AI")
                }
            }

            item {
                Text(
                    text = "AI Features",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            items(features) { feature ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "✓  $feature",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            item {
                Text(
                    text = "পরবর্তী ধাপে Board Scanner ও Solver Engine যুক্ত হবে।",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
