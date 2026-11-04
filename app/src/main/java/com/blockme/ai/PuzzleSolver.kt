package com.blockme.ai

data class Cell(val row: Int, val col: Int)

data class Piece(val cells: List<Cell>)

data class Move(
    val pieceIndex: Int,
    val row: Int,
    val col: Int,
    val rotation: Int,
    val linesCleared: Int
)

data class SolveResult(
    val moves: List<Move>,
    val score: Int
)

object PuzzleSolver {

    private const val SIZE = 8
    private const val BEAM_WIDTH = 250

    private data class State(
        val board: Array<BooleanArray>,
        val moves: List<Move>,
        val score: Int,
        val remaining: List<Int>,
        val totalCleared: Int,
        val combo: Int
    )

    fun solve(
        inputBoard: List<List<Boolean>>,
        pieces: List<Piece>
    ): SolveResult? {

        require(inputBoard.size == SIZE)
        require(inputBoard.all { it.size == SIZE })
        require(pieces.isNotEmpty())
        require(pieces.all { it.cells.isNotEmpty() })

        val initialBoard = Array(SIZE) { r ->
            BooleanArray(SIZE) { c -> inputBoard[r][c] }
        }

        var beam = listOf(
            State(
                board = initialBoard,
                moves = emptyList(),
                score = 0,
                remaining = pieces.indices.toList(),
                totalCleared = 0,
                combo = 0
            )
        )

        var best = beam.first()

        repeat(pieces.size) {

            val next = mutableListOf<State>()

            for (state in beam) {

                for (pieceIndex in state.remaining) {

                    val shapes = rotations(pieces[pieceIndex])

                    for ((rotation, shape) in shapes.withIndex()) {

                        for (r in 0 until SIZE) {
                            for (c in 0 until SIZE) {

                                if (!canPlace(
                                        state.board,
                                        shape,
                                        r,
                                        c
                                    )
                                ) continue

                                val newBoard = copyBoard(state.board)

                                place(newBoard, shape, r, c)

                                val cleared = clearLines(newBoard)

                                val newCombo =
                                    if (cleared > 0) state.combo + 1
                                    else 0

                                val move = Move(
                                    pieceIndex = pieceIndex,
                                    row = r,
                                    col = c,
                                    rotation = rotation * 90,
                                    linesCleared = cleared
                                )

                                val newRemaining =
                                    state.remaining - pieceIndex

                                val totalCleared =
                                    state.totalCleared + cleared

                                val strategicScore =
                                    totalCleared * 100000 +
                                    newCombo * 25000 +
                                    newRemaining.size * 100 +
                                    evaluateBoard(
                                        newBoard,
                                        pieces,
                                        newRemaining
                                    )

                                next.add(
                                    State(
                                        board = newBoard,
                                        moves = state.moves + move,
                                        score = strategicScore,
                                        remaining = newRemaining,
                                        totalCleared = totalCleared,
                                        combo = newCombo
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (next.isEmpty()) {
                break
            }

            beam = next
                .sortedByDescending { it.score }
                .take(BEAM_WIDTH)

            val candidate = beam.maxByOrNull { it.score }

            if (candidate != null &&
                candidate.moves.size > best.moves.size
            ) {
                best = candidate
            } else if (candidate != null &&
                candidate.moves.size == best.moves.size &&
                candidate.score > best.score
            ) {
                best = candidate
            }
        }

        val finalBest = beam.maxByOrNull { it.score }

        if (finalBest != null &&
            (finalBest.moves.size > best.moves.size ||
            (finalBest.moves.size == best.moves.size &&
             finalBest.score > best.score))
        ) {
            best = finalBest
        }

        return if (best.moves.isEmpty()) null
        else SolveResult(best.moves, best.score)
    }

    private fun rotations(piece: Piece): List<List<Cell>> {

        val result = mutableListOf<List<Cell>>()
        var current = normalize(piece.cells)

        repeat(4) {

            if (current !in result) {
                result.add(current)
            }

            current = normalize(
                current.map {
                    Cell(it.col, -it.row)
                }
            )
        }

        return result
    }

    private fun normalize(cells: List<Cell>): List<Cell> {

        val minRow = cells.minOf { it.row }
        val minCol = cells.minOf { it.col }

        return cells
            .map {
                Cell(it.row - minRow, it.col - minCol)
            }
            .distinct()
            .sortedWith(
                compareBy<Cell> { it.row }
                    .thenBy { it.col }
            )
    }

    private fun canPlace(
        board: Array<BooleanArray>,
        shape: List<Cell>,
        row: Int,
        col: Int
    ): Boolean {

        return shape.all {

            val r = row + it.row
            val c = col + it.col

            r in 0 until SIZE &&
            c in 0 until SIZE &&
            !board[r][c]
        }
    }

    private fun place(
        board: Array<BooleanArray>,
        shape: List<Cell>,
        row: Int,
        col: Int
    ) {

        for (cell in shape) {
            board[row + cell.row][col + cell.col] = true
        }
    }

    private fun clearLines(
        board: Array<BooleanArray>
    ): Int {

        val fullRows = (0 until SIZE).filter { r ->
            board[r].all { it }
        }

        val fullCols = (0 until SIZE).filter { c ->
            (0 until SIZE).all { r ->
                board[r][c]
            }
        }

        for (r in fullRows) {
            for (c in 0 until SIZE) {
                board[r][c] = false
            }
        }

        for (c in fullCols) {
            for (r in 0 until SIZE) {
                board[r][c] = false
            }
        }

        return fullRows.size + fullCols.size
    }

    private fun evaluateBoard(
        board: Array<BooleanArray>,
        pieces: List<Piece>,
        remaining: List<Int>
    ): Int {

        var occupied = 0
        var completedPotential = 0
        var emptySpaces = 0

        for (r in 0 until SIZE) {

            val filled = board[r].count { it }

            occupied += filled
            emptySpaces += SIZE - filled

            if (filled == SIZE - 1) {
                completedPotential += 15
            }
        }

        for (c in 0 until SIZE) {

            var filled = 0

            for (r in 0 until SIZE) {
                if (board[r][c]) filled++
            }

            if (filled == SIZE - 1) {
                completedPotential += 15
            }
        }

        var roughness = 0

        for (r in 0 until SIZE - 1) {
            for (c in 0 until SIZE) {
                if (board[r][c] != board[r + 1][c]) {
                    roughness++
                }
            }
        }

        // Reserve enough open space for the largest remaining piece.
        val largestPieceSize = remaining
            .map { pieces[it].cells.size }
            .maxOrNull() ?: 0

        var largestEmptyArea = 0

        for (r in 0 until SIZE) {
            for (c in 0 until SIZE) {

                if (!board[r][c]) {

                    var area = 0

                    for (rr in r until SIZE) {
                        for (cc in c until SIZE) {
                            if (!board[rr][cc]) area++
                            else break
                        }
                    }

                    largestEmptyArea =
                        maxOf(largestEmptyArea, area)
                }
            }
        }

        val spacePenalty =
            if (largestEmptyArea < largestPieceSize)
                (largestPieceSize - largestEmptyArea) * 30
            else 0

        return emptySpaces * 2 +
                completedPotential * 40 -
                occupied * 2 -
                roughness * 3 -
                spacePenalty
    }

    private fun copyBoard(
        board: Array<BooleanArray>
    ): Array<BooleanArray> {

        return Array(SIZE) {
            board[it].clone()
        }
    }
}
