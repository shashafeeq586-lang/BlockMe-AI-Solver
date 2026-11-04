
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
        private const val ACTION_COOLDOWN = 1200L

        // BoardDetection-এর একই বোর্ড অবস্থান
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
    private var lastActionSignature = ""
    private var isStopping = false

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

            var aiStatus = "Waiting for board and three pieces"

            if (
                boardResult != null &&
                pieceResult != null &&
                pieceResult.pieces.size == 3
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

        // একই স্ক্রিনে একই চাল বারবার পাঠানো যাবে না।
        if (signature == lastActionSignature) {
            return "Waiting for the previous move to update the board"
        }

        val now = System.currentTimeMillis()

        if (now - lastActionTime < ACTION_COOLDOWN) {
            return "Waiting for the previous action"
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

        // এই সংস্করণে Rotation করা হচ্ছে না।
        // ভুল Rotation Gesture পাঠানো থেকে বিরত রাখা হচ্ছে।
        if (move.rotation != 0) {
            return "AI needs ${move.rotation} degree rotation; waiting for rotation support"
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

        val minRow = selectedPiece.cells.minOf { it.row }
        val maxRow = selectedPiece.cells.maxOf { it.row }
        val minCol = selectedPiece.cells.minOf { it.col }
        val maxCol = selectedPiece.cells.maxOf { it.col }

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

        // গন্তব্য বোর্ডের বাইরে হলে কোনো Gesture পাঠানো হবে না।
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

        // একটি মাত্র Drag পাঠানো হচ্ছে।
        val accepted = GameTouchController.swipe(
            startX = sourceX,
            startY = sourceY,
            endX = destinationX,
            endY = destinationY,
            duration = 650L
        )

        if (!accepted) {
            return "Drag gesture was not accepted"
        }

        lastActionSignature = signature
        lastActionTime = now

        Log.i(
            TAG,
            "Drag sent: piece=${move.pieceIndex}, " +
                "row=${move.row}, col=${move.col}, " +
                "rotation=${move.rotation}, " +
                "source=($sourceX,$sourceY), " +
                "destination=($destinationX,$destinationY)"
        )

        return "AI drag sent; waiting for board update"
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
