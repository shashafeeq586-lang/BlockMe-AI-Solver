package com.blockme.ai

import android.graphics.Bitmap
import android.graphics.Color

data class BoardScanResult(
    val board: List<List<Boolean>>,
    val occupiedCount: Int,
    val emptyCount: Int,
    val confidence: Float
)

object BoardDetection {

    private const val SIZE = 8

    // Board position calibrated from the supplied game screenshot.
    private const val LEFT_RATIO = 0.0615f
    private const val TOP_RATIO = 0.1191f
    private const val WIDTH_RATIO = 0.874f

    fun scan(bitmap: Bitmap): BoardScanResult? {

        if (bitmap.width < 100 || bitmap.height < 100) {
            return null
        }

        val left = (bitmap.width * LEFT_RATIO).toInt()
        val top = (bitmap.height * TOP_RATIO).toInt()
        val boardSize = (bitmap.width * WIDTH_RATIO).toInt()

        if (left + boardSize > bitmap.width ||
            top + boardSize > bitmap.height
        ) {
            return null
        }

        val cellSize = boardSize.toFloat() / SIZE

        val board = mutableListOf<List<Boolean>>()
        var occupiedCount = 0
        var confidenceTotal = 0f

        for (row in 0 until SIZE) {

            val rowData = mutableListOf<Boolean>()

            for (col in 0 until SIZE) {

                val centerX =
                    left + (col + 0.5f) * cellSize

                val centerY =
                    top + (row + 0.5f) * cellSize

                val result = sampleCell(
                    bitmap,
                    centerX.toInt(),
                    centerY.toInt(),
                    cellSize
                )

                rowData.add(result.first)

                if (result.first) {
                    occupiedCount++
                }

                confidenceTotal += result.second
            }

            board.add(rowData)
        }

        return BoardScanResult(
            board = board,
            occupiedCount = occupiedCount,
            emptyCount = SIZE * SIZE - occupiedCount,
            confidence = confidenceTotal / 64f
        )
    }

    private fun sampleCell(
        bitmap: Bitmap,
        centerX: Int,
        centerY: Int,
        cellSize: Float
    ): Pair<Boolean, Float> {

        val radius = (cellSize * 0.18f).toInt()
        var brightSamples = 0
        var totalSamples = 0

        for (dy in -radius..radius step 4) {
            for (dx in -radius..radius step 4) {

                val x = centerX + dx
                val y = centerY + dy

                if (x !in 0 until bitmap.width ||
                    y !in 0 until bitmap.height
                ) continue

                val pixel = bitmap.getPixel(x, y)

                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)

                val brightness = maxOf(red, green, blue)
                val saturation =
                    brightness - minOf(red, green, blue)

                if (brightness > 85 && saturation > 35) {
                    brightSamples++
                }

                totalSamples++
            }
        }

        if (totalSamples == 0) {
            return false to 0f
        }

        val ratio = brightSamples.toFloat() / totalSamples

        val occupied = ratio > 0.25f

        val confidence = if (occupied) {
            ratio.coerceIn(0f, 1f)
        } else {
            (1f - ratio).coerceIn(0f, 1f)
        }

        return occupied to confidence
    }
}
