package com.blockme.ai

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

data class DetectedPiece(
    val cells: List<Cell>,
    val centerX: Float,
    val centerY: Float,
    val color: Int
)

data class PieceScanResult(
    val pieces: List<DetectedPiece>,
    val confidence: Float
)

object PieceDetection {

    private const val MIN_COMPONENT_PIXELS = 20

    fun scan(bitmap: Bitmap): PieceScanResult? {
        if (bitmap.width < 100 || bitmap.height < 200) {
            return null
        }

        val width = bitmap.width
        val height = bitmap.height

        // বোর্ডের নিচের অংশে থাকা তিনটি ব্লক খুঁজবে।
        val startY = (height * 0.66f).toInt()
        val endY = (height * 0.94f).toInt()

        val step = 2
        val maskWidth = (width + step - 1) / step
        val maskHeight = (endY - startY + step - 1) / step

        if (maskWidth <= 0 || maskHeight <= 0) {
            return null
        }

        val mask = BooleanArray(maskWidth * maskHeight)

        for (my in 0 until maskHeight) {
            for (mx in 0 until maskWidth) {
                val x = mx * step
                val y = startY + my * step

                if (x >= width || y >= endY) continue

                val pixel = bitmap.getPixel(x, y)

                mask[my * maskWidth + mx] =
                    isBlockColor(pixel)
            }
        }

        val visited = BooleanArray(mask.size)
        val components = mutableListOf<List<Int>>()

        for (y in 0 until maskHeight) {
            for (x in 0 until maskWidth) {

                val index = y * maskWidth + x

                if (!mask[index] || visited[index]) {
                    continue
                }

                val component = mutableListOf<Int>()
                val queue = ArrayDeque<Int>()

                queue.add(index)
                visited[index] = true

                while (queue.isNotEmpty()) {
                    val current = queue.removeFirst()
                    component.add(current)

                    val cx = current % maskWidth
                    val cy = current / maskWidth

                    val neighbors = intArrayOf(
                        if (cx > 0) current - 1 else -1,
                        if (cx < maskWidth - 1) current + 1 else -1,
                        if (cy > 0) current - maskWidth else -1,
                        if (cy < maskHeight - 1)
                            current + maskWidth else -1
                    )

                    for (next in neighbors) {
                        if (next >= 0 &&
                            mask[next] &&
                            !visited[next]
                        ) {
                            visited[next] = true
                            queue.add(next)
                        }
                    }
                }

                if (component.size >= MIN_COMPONENT_PIXELS) {
                    components.add(component)
                }
            }
        }

        val detected = components
            .mapNotNull { component ->
                createPiece(
                    bitmap,
                    component,
                    maskWidth,
                    startY,
                    step
                )
            }
            .sortedBy { it.centerX }

        if (detected.size != 3) {
            return null
        }

        return PieceScanResult(
            pieces = detected,
            confidence = 1f
        )
    }

    private fun isBlockColor(pixel: Int): Boolean {
        val red = Color.red(pixel)
        val green = Color.green(pixel)
        val blue = Color.blue(pixel)

        val brightness = max(red, max(green, blue))
        val saturation =
            brightness - min(red, min(green, blue))

        return brightness > 120 && saturation > 65
    }

    private fun createPiece(
        bitmap: Bitmap,
        component: List<Int>,
        maskWidth: Int,
        startY: Int,
        step: Int
    ): DetectedPiece? {

        val xs = component.map { it % maskWidth }
        val ys = component.map { it / maskWidth }

        val minX = xs.minOrNull() ?: return null
        val maxX = xs.maxOrNull() ?: return null
        val minY = ys.minOrNull() ?: return null
        val maxY = ys.maxOrNull() ?: return null

        val cellSize = estimateCellSize(
            maxX - minX + 1,
            maxY - minY + 1,
            step
        )

        if (cellSize <= 0f) return null

        val rawCells = mutableListOf<Pair<Int, Int>>()

        for (y in minY..maxY) {
            for (x in minX..maxX) {

                val occupied = component.any { index ->
                    index % maskWidth == x &&
                        index / maskWidth == y
                }

                if (occupied) {
                    rawCells.add(x to y)
                }
            }
        }

        if (rawCells.isEmpty()) return null

        val cellGroups = mutableSetOf<Pair<Int, Int>>()

        for ((x, y) in rawCells) {
            val col = ((x - minX) * step / cellSize).toInt()
            val row = ((y - minY) * step / cellSize).toInt()
            cellGroups.add(row to col)
        }

        val minRow = cellGroups.minOf { it.first }
        val minCol = cellGroups.minOf { it.second }

        val cells = cellGroups.map { (row, col) ->
            Cell(row - minRow, col - minCol)
        }.distinct().sortedWith(
            compareBy<Cell> { it.row }.thenBy { it.col }
        )

        val centerX =
            ((minX + maxX) / 2f) * step

        val centerY =
            startY + ((minY + maxY) / 2f) * step

        val color = bitmap.getPixel(
            (centerX.toInt()).coerceIn(0, bitmap.width - 1),
            (centerY.toInt()).coerceIn(0, bitmap.height - 1)
        )

        return DetectedPiece(
            cells = cells,
            centerX = centerX,
            centerY = centerY,
            color = color
        )
    }

    private fun estimateCellSize(
        width: Int,
        height: Int,
        step: Int
    ): Float {
        return max(width, height).toFloat() * step / 4f
    }
}
