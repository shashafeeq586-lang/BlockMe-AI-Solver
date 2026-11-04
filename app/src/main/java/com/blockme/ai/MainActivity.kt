
package com.blockme.ai

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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

    companion object {
        const val ACTION_SCAN_RESULT = "com.blockme.ai.SCAN_RESULT"
    }

    private var captureApproved by mutableStateOf(false)
    private var occupied by mutableIntStateOf(0)
    private var empty by mutableIntStateOf(64)
    private var confidence by mutableFloatStateOf(0f)
    private var scanMessage by mutableStateOf("Waiting for board scan...")
    private var boardData by mutableStateOf("")
    private var pieceCount by mutableIntStateOf(0)
    private var piecesData by mutableStateOf("")
    private var pieceConfidence by mutableFloatStateOf(0f)

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_SCAN_RESULT) return

            occupied = intent.getIntExtra("occupied", -1)
            empty = intent.getIntExtra("empty", -1)
            confidence = intent.getFloatExtra("confidence", 0f)
            boardData = intent.getStringExtra("board") ?: ""

            pieceCount = intent.getIntExtra("pieceCount", 0)
            piecesData = intent.getStringExtra("pieces") ?: ""
            pieceConfidence = intent.getFloatExtra("pieceConfidence", 0f)

            scanMessage = "Scan result received"
        }
    }

    private val screenCaptureLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val serviceIntent =
                    Intent(this, ScreenCaptureService::class.java).apply {
                        putExtra("resultCode", result.resultCode)
                        putExtra("data", result.data)
                    }

                ContextCompat.startForegroundService(this, serviceIntent)
                captureApproved = true
                scanMessage = "Scanning screen..."
            } else {
                captureApproved = false
                scanMessage = "Screen capture permission denied"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ContextCompat.registerReceiver(
            this,
            scanReceiver,
            IntentFilter(ACTION_SCAN_RESULT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

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
                        scanMessage = "Screen capture stopped"
                    },
                    isRunning = captureApproved,
                    occupied = occupied,
                    empty = empty,
                    confidence = confidence,
                    scanMessage = scanMessage,
                    boardData = boardData,
                    pieceCount = pieceCount,
                    piecesData = piecesData,
                    pieceConfidence = pieceConfidence
                )
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(scanReceiver)
        super.onDestroy()
    }
}

@Composable
fun BlockMeHome(
    onStart: () -> Unit,
    onStop: () -> Unit,
    isRunning: Boolean,
    occupied: Int,
    empty: Int,
    confidence: Float,
    scanMessage: String,
    boardData: String,
    pieceCount: Int,
    piecesData: String,
    pieceConfidence: Float
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
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp)
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                Text("👑", fontSize = 55.sp)
                Text(
                    "BlockMe",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    "AI Solver",
                    fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFFE082)
                )
            }

            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(78.dp),
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = green
                )
            ) {
                Text(
                    "▶   START AI",
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }

            Button(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(78.dp),
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = red
                )
            ) {
                Text(
                    "■   STOP AI",
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                color = Color(0xE8172535),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(14.dp)
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        "SCAN RESULT",
                        color = Color(0xFFFFE082),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )

                    Text(
                        scanMessage,
                        color = Color.White,
                        fontSize = 14.sp
                    )

                    Text(
                        "Occupied: $occupied / 64",
                        color = Color.White,
                        fontSize = 17.sp
                    )

                    Text(
                        "Empty: $empty / 64",
                        color = Color.White,
                        fontSize = 17.sp
                    )

                    Text(
                        "Board confidence: ${"%.1f".format(confidence * 100)}%",
                        color = Color.White,
                        fontSize = 15.sp
                    )

                    if (boardData.length == 64) {
                        Text(
                            "Detected 8 × 8 Board",
                            color = Color(0xFF80FF80),
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            boardData.chunked(8).joinToString("\n") {
                                it.map { cell ->
                                    if (cell == '1') "■" else "□"
                                }.joinToString(" ")
                            },
                            color = Color.White,
                            fontSize = 17.sp,
                            lineHeight = 22.sp
                        )
                    }

                    HorizontalDivider(color = Color.Gray)

                    Text(
                        "PIECE DETECTION",
                        color = Color(0xFFFFE082),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black
                    )

                    Text(
                        "Detected pieces: $pieceCount / 3",
                        color = Color.White,
                        fontSize = 17.sp
                    )

                    Text(
                        "Piece confidence: ${"%.1f".format(pieceConfidence * 100)}%",
                        color = Color.White,
                        fontSize = 15.sp
                    )

                    if (piecesData.isNotBlank()) {
                        val pieces = piecesData.split("|")

                        pieces.forEachIndexed { index, piece ->
                            val cells = piece.split(";")
                                .mapNotNull { value ->
                                    val parts = value.split(",")
                                    if (parts.size != 2) null
                                    else {
                                        val row = parts[0].toIntOrNull()
                                        val col = parts[1].toIntOrNull()
                                        if (row == null || col == null) null
                                        else Cell(row, col)
                                    }
                                }

                            Text(
                                "Piece ${index + 1}: ${cells.size} cells",
                                color = Color(0xFF80FF80),
                                fontWeight = FontWeight.Bold
                            )

                            if (cells.isNotEmpty()) {
                                val rows = (cells.maxOf { it.row } + 1)
                                    .coerceAtMost(8)
                                val cols = (cells.maxOf { it.col } + 1)
                                    .coerceAtMost(8)

                                Column {
                                    for (r in 0 until rows) {
                                        Text(
                                            (0 until cols).joinToString(" ") { c ->
                                                if (cells.any {
                                                    it.row == r && it.col == c
                                                }) "■" else "·"
                                            },
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            lineHeight = 21.sp
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            "Waiting for three pieces...",
                            color = Color.LightGray,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Text(
                if (isRunning) "Screen Capture Started"
                else "BlockMe AI Solver",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
