package com.blockme.ai

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

data class DetectedPiece(
    val cells: List<Cell>,
    val centerX: Float,
    val centerY: Float,
    val color: Int,
    val tileSize: Float = 0f,
    val touchX: Float = centerX,
    val touchY: Float = centerY,
    val anchorRow: Int = 0,
    val anchorCol: Int = 0
)

data class PieceScanResult(
    val pieces: List<DetectedPiece>,
    val confidence: Float
)

object PieceDetection {

    private const val TILE_RATIO = 0.041f

    private data class Slot(
        val centerX: Float,
        val top: Float,
        val bottom: Float
    )

    private data class GridResult(
        val cells: List<Cell>,
        val confidence: Float,
        val color: Int,
        val centerX: Float,
        val centerY: Float,
        val touchX: Float,
        val touchY: Float,
        val anchorRow: Int,
        val anchorCol: Int
    )

    fun scan(bitmap: Bitmap): PieceScanResult? {
        if (bitmap.width < 100 || bitmap.height < 200) return null

        val tile = bitmap.width * TILE_RATIO

        val slots = listOf(
            Slot(0.175f, 0.70f, 0.83f),
            Slot(0.501f, 0.70f, 0.83f),
            Slot(0.828f, 0.70f, 0.83f)
        )

        val pieces = mutableListOf<DetectedPiece>()
        var confidenceTotal = 0f

        for (slot in slots) {
            val centerX = slot.centerX * bitmap.width
            val top = slot.top * bitmap.height
            val bottom = slot.bottom * bitmap.height

            val result = findGrid(
                bitmap,
                centerX,
                top,
                bottom,
                tile
            ) ?: continue

            if (result.cells.isEmpty()) continue

            pieces.add(
                DetectedPiece(
                    cells = result.cells,
                    centerX = result.centerX,
                    centerY = result.centerY,
                    color = result.color,
                    tileSize = tile,
                    touchX = result.touchX,
                    touchY = result.touchY,
                    anchorRow = result.anchorRow,
                    anchorCol = result.anchorCol
                )
            )

            confidenceTotal += result.confidence
        }

        return PieceScanResult(
            pieces = pieces,
            confidence = if (pieces.isEmpty()) 0f else confidenceTotal / pieces.size
        )
    }

    private fun findGrid(
        bitmap: Bitmap,
        centerX: Float,
        top: Float,
        bottom: Float,
        tile: Float
    ): GridResult? {

        var bestCells = emptyList<Cell>()
        var bestScore = 0f
        var bestColor = Color.TRANSPARENT
        var bestCenterX = centerX
        var bestCenterY = (top + bottom) / 2f
        var bestTouchX = centerX
        var bestTouchY = (top + bottom) / 2f
        var bestAnchorRow = 0
        var bestAnchorCol = 0

        val startX = centerX - tile * 2.5f

        var offsetY = 0

        while (offsetY < tile.toInt()) {
            var offsetX = 0

            while (offsetX < tile.toInt()) {
                val found = mutableListOf<Cell>()
                var score = 0f
                var colorTotal = 0L
                var colorSamples = 0
                var sumX = 0f
                var sumY = 0f

                for (row in 0 until 6) {
                    val cellY =
                        top + offsetY + (row + 0.5f) * tile

                    if (cellY > bottom) continue

                    for (col in 0 until 5) {
                        val cellX =
                            startX + offsetX + (col + 0.5f) * tile

                        val ratio = sampleCell(
                            bitmap,
                            cellX,
                            cellY,
                            tile
                        )

                        if (ratio.first > 0.38f) {
                            found.add(Cell(row, col))
                            score += ratio.first
                            colorTotal += ratio.second.toLong()
                            colorSamples++
                            sumX += cellX
                            sumY += cellY
                        }
                    }
                }

                if (found.size in 1..8 && score > bestScore) {
                    bestScore = score
                    bestCells = found
                    bestCenterX = sumX / found.size
                    bestCenterY = sumY / found.size
                    val first = found.first()
                    bestTouchX = startX + offsetX + (first.col + 0.5f) * tile
                    bestTouchY = top + offsetY + (first.row + 0.5f) * tile
                    bestAnchorRow = first.row - found.minOf { it.row }
                    bestAnchorCol = first.col - found.minOf { it.col }

                    bestColor = if (colorSamples > 0) {
                        Color.rgb(
                            ((colorTotal / colorSamples) shr 16).toInt(),
                            ((colorTotal / colorSamples) shr 8 and 255).toInt(),
                            (colorTotal / colorSamples and 255).toInt()
                        )
                    } else {
                        Color.TRANSPARENT
                    }
                }

                offsetX += 2
            }

            offsetY += 2
        }

        if (bestCells.isEmpty()) return null

        val minRow = bestCells.minOf { it.row }
        val minCol = bestCells.minOf { it.col }

        val normalized = bestCells.map {
            Cell(it.row - minRow, it.col - minCol)
        }.distinct().sortedWith(
            compareBy<Cell> { it.row }.thenBy { it.col }
        )

        return GridResult(
            cells = normalized,
            confidence = (bestScore / normalized.size).coerceIn(0f, 1f),
            color = bestColor,
            centerX = bestCenterX,
            centerY = bestCenterY,
            touchX = bestTouchX,
            touchY = bestTouchY,
            anchorRow = bestAnchorRow,
            anchorCol = bestAnchorCol
        )
    }

    private fun sampleCell(
        bitmap: Bitmap,
        centerX: Float,
        centerY: Float,
        tile: Float
    ): Pair<Float, Int> {

        val radius = (tile * 0.28f).toInt()
        var colored = 0
        var total = 0
        var redTotal = 0L
        var greenTotal = 0L
        var blueTotal = 0L

        for (dy in -radius..radius step 2) {
            for (dx in -radius..radius step 2) {
                val x = centerX.toInt() + dx
                val y = centerY.toInt() + dy

                if (
                    x !in 0 until bitmap.width ||
                    y !in 0 until bitmap.height
                ) continue

                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                val brightness = max(r, max(g, b))
                val saturation = brightness - min(r, min(g, b))

                if (brightness > 175 && saturation > 70) {
                    colored++
                    redTotal += r
                    greenTotal += g
                    blueTotal += b
                }

                total++
            }
        }

        if (total == 0 || colored == 0) return 0f to 0

        val averageColor = Color.rgb(
            (redTotal / colored).toInt(),
            (greenTotal / colored).toInt(),
            (blueTotal / colored).toInt()
        )

        return colored.toFloat() / total to averageColor
    }
}
