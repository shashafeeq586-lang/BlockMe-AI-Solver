
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

data class SolveResult(val moves: List<Move>, val score: Int)

object PuzzleSolver {

    private const val SIZE = 8
    private const val BEAM_WIDTH = 350

    private data class State(
        val board: Array<BooleanArray>,
        val moves: List<Move>,
        val remaining: List<Int>,
        val totalCleared: Int,
        val combo: Int,
        val orderBonus: Int
    )

    fun solve(
        inputBoard: List<List<Boolean>>,
        pieces: List<Piece>
    ): SolveResult? {

        require(inputBoard.size == SIZE)
        require(inputBoard.all { it.size == SIZE })
        require(pieces.size == 3)
        require(pieces.all { it.cells.isNotEmpty() })

        val initialBoard = Array(SIZE) { r ->
            BooleanArray(SIZE) { c -> inputBoard[r][c] }
        }

        var beam = listOf(
            State(
                board = initialBoard,
                moves = emptyList(),
                remaining = pieces.indices.toList(),
                totalCleared = 0,
                combo = 0,
                orderBonus = 0
            )
        )

        var best: State? = null

        for (depth in pieces.indices) {
            val next = mutableListOf<State>()

            for (state in beam) {
                for (pieceIndex in state.remaining) {

                    val shapes = rotations(pieces[pieceIndex])

                    for ((rotation, shape) in shapes.withIndex()) {
                        for (row in 0 until SIZE) {
                            for (col in 0 until SIZE) {

                                if (!canPlace(
                                        state.board,
                                        shape,
                                        row,
                                        col
                                    )
                                ) continue

                                val newBoard = copyBoard(state.board)

                                place(newBoard, shape, row, col)

                                val cleared = clearLines(newBoard)

                                val newCombo =
                                    if (cleared > 0) state.combo + 1
                                    else 0

                                val newRemaining =
                                    state.remaining - pieceIndex

                                val largestSize =
                                    pieces.maxOf { it.cells.size }

                                val orderBonus =
                                    state.orderBonus +
                                    (
                                        pieces[pieceIndex].cells.size *
                                        (pieces.size - depth) * 20
                                    )

                                val move = Move(
                                    pieceIndex = pieceIndex,
                                    row = row,
                                    col = col,
                                    rotation = rotation * 90,
                                    linesCleared = cleared
                                )

                                val candidate = State(
                                    board = newBoard,
                                    moves = state.moves + move,
                                    remaining = newRemaining,
                                    totalCleared =
                                        state.totalCleared + cleared,
                                    combo = newCombo,
                                    orderBonus = orderBonus
                                )

                                next.add(candidate)
                            }
                        }
                    }
                }
            }

            if (next.isEmpty()) break

            beam = next
                .sortedByDescending {
                    evaluate(it, pieces)
                }
                .take(BEAM_WIDTH)

            val candidate = beam.maxByOrNull {
                finalScore(it, pieces)
            }

            if (
                candidate != null &&
                (
                    best == null ||
                    isBetter(candidate, best!!, pieces)
                )
            ) {
                best = candidate
            }
        }

        val finalCandidate = beam.maxByOrNull {
            finalScore(it, pieces)
        }

        if (
            finalCandidate != null &&
            (
                best == null ||
                isBetter(finalCandidate, best!!, pieces)
            )
        ) {
            best = finalCandidate
        }

        val result = best ?: return null

        if (result.moves.isEmpty()) return null

        return SolveResult(
            moves = result.moves,
            score = finalScore(result, pieces)
        )
    }

    private fun isBetter(
        a: State,
        b: State,
        pieces: List<Piece>
    ): Boolean {

        if (a.moves.size != b.moves.size) {
            return a.moves.size > b.moves.size
        }

        if (a.totalCleared != b.totalCleared) {
            return a.totalCleared > b.totalCleared
        }

        if (a.combo != b.combo) {
            return a.combo > b.combo
        }

        return finalScore(a, pieces) > finalScore(b, pieces)
    }

    private fun finalScore(
        state: State,
        pieces: List<Piece>
    ): Int {
        return state.moves.size * 100000 +
            state.totalCleared * 50000 +
            state.combo * 10000 +
            state.orderBonus +
            evaluateBoard(state.board)
    }

    private fun evaluate(
        state: State,
        pieces: List<Piece>
    ): Int {
        return finalScore(state, pieces)
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

        val fullRows = (0 until SIZE).filter { row ->
            board[row].all { it }
        }

        val fullCols = (0 until SIZE).filter { col ->
            (0 until SIZE).all { row ->
                board[row][col]
            }
        }

        for (row in fullRows) {
            for (col in 0 until SIZE) {
                board[row][col] = false
            }
        }

        for (col in fullCols) {
            for (row in 0 until SIZE) {
                board[row][col] = false
            }
        }

        return fullRows.size + fullCols.size
    }

    private fun evaluateBoard(
        board: Array<BooleanArray>
    ): Int {

        var occupied = 0
        var almostComplete = 0
        var roughness = 0

        for (row in 0 until SIZE) {
            val filled = board[row].count { it }
            occupied += filled

            if (filled == SIZE - 1) {
                almostComplete += 1
            }
        }

        for (col in 0 until SIZE) {
            var filled = 0

            for (row in 0 until SIZE) {
                if (board[row][col]) filled++
            }

            if (filled == SIZE - 1) {
                almostComplete += 1
            }
        }

        for (row in 0 until SIZE - 1) {
            for (col in 0 until SIZE) {
                if (board[row][col] != board[row + 1][col]) {
                    roughness++
                }
            }
        }

        var emptyTwoByTwo = 0

        for (row in 0 until SIZE - 1) {
            for (col in 0 until SIZE - 1) {
                if (
                    !board[row][col] &&
                    !board[row + 1][col] &&
                    !board[row][col + 1] &&
                    !board[row + 1][col + 1]
                ) {
                    emptyTwoByTwo++
                }
            }
        }

        return almostComplete * 400 +
            emptyTwoByTwo * 8 -
            occupied * 3 -
            roughness * 2
    }

    private fun copyBoard(
        board: Array<BooleanArray>
    ): Array<BooleanArray> {
        return Array(SIZE) {
            board[it].clone()
        }
    }
}
