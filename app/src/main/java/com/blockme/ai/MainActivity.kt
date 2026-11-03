package com.blockme.ai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val screenCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra("resultCode", result.resultCode)
                    putExtra("data", result.data)
                }

                ContextCompat.startForegroundService(this, serviceIntent)
                captureApproved = true
            } else {
                captureApproved = false
            }
        }

    private var captureApproved by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                BlockMeHome(
                    onStart = {
                        val manager = getSystemService(
                            Context.MEDIA_PROJECTION_SERVICE
                        ) as MediaProjectionManager

                        screenCaptureLauncher.launch(
                            manager.createScreenCaptureIntent()
                        )
                    },
                    onStop = {
                        stopService(
                            Intent(this, ScreenCaptureService::class.java)
                        )
                        captureApproved = false
                    },
                    isRunning = captureApproved
                )
            }
        }
    }
}

@Composable
fun BlockMeHome(
    onStart: () -> Unit,
    onStop: () -> Unit,
    isRunning: Boolean
) {
    val green = Color(0xFF20D900)
    val red = Color(0xFFFF2525)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0879E8),
                        Color(0xFF53D9FF),
                        Color(0xFF087C39),
                        Color(0xFF063E24)
                    )
                )
            )
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopEnd
            ) {
                Surface(
                    color = Color(0xFF071C4B),
                    shape = RoundedCornerShape(30.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        2.dp, Color(0xFFFFD54F)
                    )
                ) {
                    Text(
                        "♛  SIAM",
                        modifier = Modifier.padding(
                            horizontal = 22.dp,
                            vertical = 9.dp
                        ),
                        color = Color.White,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("👑", fontSize = 65.sp)
                Text(
                    "BlockMe",
                    fontSize = 43.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    "AI Solver",
                    fontSize = 31.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFFE082)
                )
                Text(
                    "◆  ◆  ◆  ◆",
                    color = Color(0xFFFFD740),
                    fontSize = 23.sp
                )
            }

            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = green
                ),
                elevation = ButtonDefaults.buttonElevation(8.dp)
            ) {
                Text(
                    "▶   START AI",
                    fontSize = 29.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }

            Button(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = red
                ),
                elevation = ButtonDefaults.buttonElevation(8.dp)
            ) {
                Text(
                    "■   STOP AI",
                    fontSize = 29.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }

            Text(
                if (isRunning) "Screen Capture Started" else "BlockMe AI Solver",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
