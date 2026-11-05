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

    private data class RotatedShape(
        val degrees: Int,
        val cells: List<Cell>
    )

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
        require(pieces.size in 1..3)
        require(pieces.all { it.cells.isNotEmpty() })

        val initialBoard = Array(SIZE) { row ->
            BooleanArray(SIZE) { col ->
                inputBoard[row][col]
            }
        }

        val shapeOptions = pieces.map { listOf(RotatedShape(0, normalize(it.cells))) }

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

                    for (rotated in shapeOptions[pieceIndex]) {

                        val shape = rotated.cells
                        val maxRow = shape.maxOf { it.row }
                        val maxCol = shape.maxOf { it.col }

                        for (row in 0..(SIZE - 1 - maxRow)) {
                            for (col in 0..(SIZE - 1 - maxCol)) {

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
                                    state.remaining.filter {
                                        it != pieceIndex
                                    }

                                // বড় Piece আগে বসালে সামান্য অগ্রাধিকার।
                                val orderBonus =
                                    state.orderBonus +
                                    pieces[pieceIndex].cells.size *
                                    (pieces.size - depth) * 20

                                val move = Move(
                                    pieceIndex = pieceIndex,
                                    row = row,
                                    col = col,
                                    rotation = rotated.degrees,
                                    linesCleared = cleared
                                )

                                next.add(
                                    State(
                                        board = newBoard,
                                        moves = state.moves + move,
                                        remaining = newRemaining,
                                        totalCleared =
                                            state.totalCleared + cleared,
                                        combo = newCombo,
                                        orderBonus = orderBonus
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (next.isEmpty()) break

            beam = next
                .sortedByDescending {
                    finalScore(it, pieces)
                }
                .take(BEAM_WIDTH)

            for (candidate in beam) {
                val previous = best

                if (
                    previous == null ||
                    isBetter(candidate, previous, pieces)
                ) {
                    best = candidate
                }
            }
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

    private fun rotations(piece: Piece): List<RotatedShape> {

        val result = mutableListOf<RotatedShape>()
        var current = normalize(piece.cells)

        repeat(4) { turn ->
            if (result.none { it.cells == current }) {
                result.add(
                    RotatedShape(
                        degrees = turn * 90,
                        cells = current
                    )
                )
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
                Cell(
                    it.row - minRow,
                    it.col - minCol
                )
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

        return shape.all { cell ->
            val r = row + cell.row
            val c = col + cell.col

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
                almostComplete++
            }
        }

        for (col in 0 until SIZE) {
            var filled = 0

            for (row in 0 until SIZE) {
                if (board[row][col]) filled++
            }

            if (filled == SIZE - 1) {
                almostComplete++
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
