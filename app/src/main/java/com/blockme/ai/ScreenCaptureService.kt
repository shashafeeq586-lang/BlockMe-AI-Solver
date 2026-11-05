
package com.blockme.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.nio.ByteBuffer

class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "BlockMeAI"
        private const val CHANNEL_ID = "blockme_capture"
        private const val NOTIFICATION_ID = 101

        private const val SCAN_INTERVAL = 500L
        private const val ACTION_COOLDOWN = 1500L
        private const val MOVE_TIMEOUT = 6000L

        private const val BOARD_LEFT_RATIO = 0.0649f
        private const val BOARD_TOP_RATIO = 0.2556f
        private const val BOARD_WIDTH_RATIO = 0.8745f
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val handler = Handler(Looper.getMainLooper())

    private var lastScanTime = 0L
    private var lastActionTime = 0L

    @Volatile
    private var pendingSignature = ""

    @Volatile
    private var pendingGesture = false

    @Volatile
    private var gestureResult: GestureResult? = null

    @Volatile
    private var isStopping = false

    private var pendingSince = 0L

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        val resultCode = intent?.getIntExtra("resultCode", 0) ?: 0

        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra("data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("data")
        }

        if (resultCode == 0 || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("BlockMe AI Solver")
            .setContentText("AI scanning and move planning")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()

        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )

        try {
            val manager = getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

            mediaProjection = manager.getMediaProjection(resultCode, data)

            val metrics = DisplayMetrics()

            @Suppress("DEPRECATION")
            (getSystemService(WINDOW_SERVICE) as WindowManager)
                .defaultDisplay.getRealMetrics(metrics)

            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            imageReader = ImageReader.newInstance(
                width,
                height,
                PixelFormat.RGBA_8888,
                2
            )

            imageReader?.setOnImageAvailableListener({ reader ->

                val image = try {
                    reader.acquireLatestImage()
                } catch (e: Exception) {
                    Log.e(TAG, "Image acquisition failed", e)
                    null
                }

                if (image != null) {
                    try {
                        val now = System.currentTimeMillis()

                        if (now - lastScanTime >= SCAN_INTERVAL) {
                            lastScanTime = now
                            scanImage(image)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Scanning failed", e)
                    } finally {
                        image.close()
                    }
                }
            }, handler)

            mediaProjection?.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        Log.i(TAG, "Screen capture stopped")
                        stopSelf()
                    }
                },
                handler
            )

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "BlockMeScreenCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                handler
            )

            Log.i(TAG, "Screen capture started: ${width}x${height}")

        } catch (e: Exception) {
            Log.e(TAG, "Could not start screen capture", e)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun scanImage(image: Image) {
        val bitmap = imageToBitmap(image) ?: return

        try {
            val boardResult = BoardDetection.scan(bitmap)
            val pieceResult = PieceDetection.scan(bitmap)

            val boardString = boardResult?.board?.joinToString("") { row ->
                row.joinToString("") { cell ->
                    if (cell) "1" else "0"
                }
            } ?: ""

            val piecesString = pieceResult?.pieces?.joinToString("|") { piece ->
                piece.cells.joinToString(";") { cell ->
                    "${cell.row},${cell.col}"
                }
            } ?: ""

            var aiStatus = "Waiting for board and pieces"

            if (
                boardResult != null &&
                pieceResult != null &&
                pieceResult.pieces.isNotEmpty()
            ) {
                aiStatus = processAiMove(
                    bitmap = bitmap,
                    board = boardResult.board,
                    detectedPieces = pieceResult.pieces,
                    boardString = boardString,
                    piecesString = piecesString
                )
            }

            val update = Intent(
                MainActivity.ACTION_SCAN_RESULT
            ).apply {
                setPackage(packageName)

                putExtra("occupied", boardResult?.occupiedCount ?: -1)
                putExtra("empty", boardResult?.emptyCount ?: -1)
                putExtra("confidence", boardResult?.confidence ?: 0f)
                putExtra("board", boardString)

                putExtra("pieceCount", pieceResult?.pieces?.size ?: 0)
                putExtra("pieces", piecesString)
                putExtra("pieceConfidence", pieceResult?.confidence ?: 0f)

                putExtra("aiStatus", aiStatus)
            }

            sendBroadcast(update)

            Log.i(
                TAG,
                "Board: ${boardResult?.occupiedCount ?: "Not detected"}, " +
                    "Pieces: ${pieceResult?.pieces?.size ?: 0}, " +
                    "AI: $aiStatus"
            )

        } catch (e: Exception) {
            Log.e(TAG, "Detection failed", e)
        } finally {
            bitmap.recycle()
        }
    }

    private fun processAiMove(
        bitmap: Bitmap,
        board: List<List<Boolean>>,
        detectedPieces: List<DetectedPiece>,
        boardString: String,
        piecesString: String
    ): String {

        val signature = "$boardString#$piecesString"
        val now = System.currentTimeMillis()

        // আগের Gesture এখনো চলছে।
        if (pendingGesture) {
            return "AI drag is in progress"
        }

        // Gesture শেষ হয়েছে কি না এবং বোর্ড আপডেট হয়েছে কি না।
        if (pendingSignature.isNotEmpty()) {

            val result = gestureResult

            if (result == GestureResult.CANCELLED ||
                result == GestureResult.REJECTED
            ) {
                Log.w(TAG, "Previous gesture failed: $result")

                pendingSignature = ""
                gestureResult = null
                pendingSince = 0L

                return "Previous drag failed; preparing to retry"
            }

            if (signature != pendingSignature) {
                Log.i(TAG, "Board changed after drag")

                pendingSignature = ""
                gestureResult = null
                pendingSince = 0L
            } else {
                if (now - pendingSince < MOVE_TIMEOUT) {
                    return when (result) {
                        GestureResult.COMPLETED ->
                            "Drag completed; verifying board update"

                        else ->
                            "Waiting for drag completion"
                    }
                }

                Log.w(TAG, "Board did not change after drag")

                pendingSignature = ""
                gestureResult = null
                pendingSince = 0L
            }
        }

        if (now - lastActionTime < ACTION_COOLDOWN) {
            return "Waiting for action cooldown"
        }

        if (!GameTouchController.isConnected()) {
            return "Enable BlockMe AI Touch Control in Accessibility settings"
        }

        val solverPieces = detectedPieces.map {
            Piece(it.cells)
        }

        val solution = try {
            PuzzleSolver.solve(board, solverPieces)
        } catch (e: Exception) {
            Log.e(TAG, "Puzzle solving failed", e)
            null
        } ?: return "No legal move found"

        val move = solution.moves.firstOrNull()
            ?: return "No move available"

        if (move.pieceIndex !in detectedPieces.indices) {
            return "Invalid piece selected by solver"
        }

        // Rotation support এখনো যাচাই করা হয়নি।
        if (move.rotation != 0) {
            return "AI needs ${move.rotation} degree rotation; rotation support pending"
        }

        val selectedPiece = detectedPieces[move.pieceIndex]

        if (
            selectedPiece.cells.isEmpty() ||
            selectedPiece.tileSize <= 0f
        ) {
            return "Selected piece coordinates are invalid"
        }

        val sourceX = selectedPiece.centerX
        val sourceY = selectedPiece.centerY

        val averageRow = selectedPiece.cells
            .map { it.row }
            .average()
            .toFloat()

        val averageCol = selectedPiece.cells
            .map { it.col }
            .average()
            .toFloat()

        val boardLeft = bitmap.width * BOARD_LEFT_RATIO
        val boardTop = bitmap.height * BOARD_TOP_RATIO
        val boardSize = bitmap.width * BOARD_WIDTH_RATIO
        val cellSize = boardSize / 8f

        val boardRight = boardLeft + boardSize
        val boardBottom = boardTop + boardSize

        val destinationX =
            boardLeft + (move.col + averageCol + 0.5f) * cellSize

        val destinationY =
            boardTop + (move.row + averageRow + 0.5f) * cellSize

        if (
            destinationX !in boardLeft..boardRight ||
            destinationY !in boardTop..boardBottom
        ) {
            return "AI destination is outside the board"
        }

        if (
            sourceX !in 0f..bitmap.width.toFloat() ||
            sourceY !in 0f..bitmap.height.toFloat()
        ) {
            return "Piece source position is outside the screen"
        }

        // নতুন Drag শুরু করার আগে pending state সেট করা।
        pendingSignature = signature
        pendingGesture = true
        gestureResult = null
        pendingSince = now
        lastActionTime = now

        val accepted = GameTouchController.swipeWithCallback(
            startX = sourceX,
            startY = sourceY,
            endX = destinationX,
            endY = destinationY,
            duration = 650L
        ) { result ->

            handler.post {
                pendingGesture = false
                gestureResult = result

                Log.i(TAG, "Gesture callback: $result")

                if (result != GestureResult.COMPLETED) {
                    Log.w(TAG, "Drag did not complete successfully")
                }
            }
        }

        if (!accepted) {
            pendingGesture = false
            pendingSignature = ""
            gestureResult = GestureResult.REJECTED
            pendingSince = 0L

            return "Drag gesture was rejected"
        }

        Log.i(
            TAG,
            "Drag dispatched: piece=${move.pieceIndex}, " +
                "row=${move.row}, col=${move.col}, " +
                "rotation=${move.rotation}, " +
                "source=($sourceX,$sourceY), " +
                "destination=($destinationX,$destinationY)"
        )

        return "AI drag dispatched; waiting for completion and board update"
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null

        val buffer: ByteBuffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride

        if (pixelStride <= 0 || rowStride <= 0) return null

        val paddedWidth = rowStride / pixelStride

        if (paddedWidth < image.width) return null

        val paddedBitmap = Bitmap.createBitmap(
            paddedWidth,
            image.height,
            Bitmap.Config.ARGB_8888
        )

        return try {
            buffer.rewind()
            paddedBitmap.copyPixelsFromBuffer(buffer)

            if (paddedWidth == image.width) {
                paddedBitmap
            } else {
                val cropped = Bitmap.createBitmap(
                    paddedBitmap,
                    0,
                    0,
                    image.width,
                    image.height
                )

                paddedBitmap.recycle()
                cropped
            }
        } catch (e: Exception) {
            Log.e(TAG, "Image conversion failed", e)
            paddedBitmap.recycle()
            null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "BlockMe Screen Capture",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager = getSystemService(
                NotificationManager::class.java
            )

            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        if (!isStopping) {
            isStopping = true

            imageReader?.setOnImageAvailableListener(null, null)

            virtualDisplay?.release()
            imageReader?.close()
            mediaProjection?.stop()

            virtualDisplay = null
            imageReader = null
            mediaProjection = null
        }

        super.onDestroy()

        Log.i(TAG, "BlockMe AI service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
