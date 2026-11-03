
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
    private const val BEAM_WIDTH = 100

    private data class State(
        val board: Array<BooleanArray>,
        val moves: List<Move>,
        val score: Int,
        val remaining: List<Int>
    )

    fun solve(
        inputBoard: List<List<Boolean>>,
        pieces: List<Piece>
    ): SolveResult? {

        require(inputBoard.size == SIZE)
        require(inputBoard.all { it.size == SIZE })
        require(pieces.isNotEmpty())

        val initialBoard = Array(SIZE) { r ->
            BooleanArray(SIZE) { c -> inputBoard[r][c] }
        }

        var beam = listOf(
            State(
                initialBoard,
                emptyList(),
                0,
                pieces.indices.toList()
            )
        )

        repeat(pieces.size) {
            val next = mutableListOf<State>()

            for (state in beam) {
                for (pieceIndex in state.remaining) {
                    val piece = pieces[pieceIndex]

                    for ((rotation, shape) in rotations(piece).withIndex()) {
                        for (r in 0 until SIZE) {
                            for (c in 0 until SIZE) {

                                if (!canPlace(state.board, shape, r, c)) {
                                    continue
                                }

                                val newBoard = copyBoard(state.board)
                                place(newBoard, shape, r, c)

                                val cleared = clearLines(newBoard)
                                val newRemaining =
                                    state.remaining - pieceIndex

                                val move = Move(
                                    pieceIndex,
                                    r,
                                    c,
                                    rotation * 90,
                                    cleared
                                )

                                val newScore =
                                    state.score +
                                    cleared * 1000 +
                                    evaluateBoard(newBoard)

                                next.add(
                                    State(
                                        newBoard,
                                        state.moves + move,
                                        newScore,
                                        newRemaining
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (next.isEmpty()) {
                return beam.maxByOrNull { it.score }?.let {
                    SolveResult(it.moves, it.score)
                }
            }

            beam = next
                .sortedByDescending { it.score }
                .take(BEAM_WIDTH)
        }

        val best = beam.maxByOrNull { it.score } ?: return null

        return SolveResult(best.moves, best.score)
    }

    private fun rotations(piece: Piece): List<List<Cell>> {
        val result = mutableListOf<List<Cell>>()
        var current = normalize(piece.cells)

        repeat(4) {
            if (current !in result) result.add(current)

            current = normalize(
                current.map { Cell(it.col, -it.row) }
            )
        }

        return result
    }

    private fun normalize(cells: List<Cell>): List<Cell> {
        val minRow = cells.minOf { it.row }
        val minCol = cells.minOf { it.col }

        return cells
            .map { Cell(it.row - minRow, it.col - minCol) }
            .sortedWith(compareBy<Cell> { it.row }.thenBy { it.col })
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

    private fun clearLines(board: Array<BooleanArray>): Int {
        val fullRows = (0 until SIZE).filter { r ->
            board[r].all { it }
        }

        val fullCols = (0 until SIZE).filter { c ->
            (0 until SIZE).all { r -> board[r][c] }
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

    private fun evaluateBoard(board: Array<BooleanArray>): Int {
        var occupied = 0
        var empty = 0
        var roughness = 0

        for (r in 0 until SIZE) {
            for (c in 0 until SIZE) {
                if (board[r][c]) occupied++ else empty++
            }
        }

        for (r in 0 until SIZE - 1) {
            for (c in 0 until SIZE) {
                if (board[r][c] != board[r + 1][c]) {
                    roughness++
                }
            }
        }

        return empty * 2 - occupied * 2 - roughness
    }

    private fun copyBoard(
        board: Array<BooleanArray>
    ): Array<BooleanArray> {
        return Array(SIZE) { board[it].clone() }
    }
}
