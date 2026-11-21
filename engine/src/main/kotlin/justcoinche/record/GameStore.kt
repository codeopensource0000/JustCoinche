package justcoinche.record

import justcoinche.engine.Game
import justcoinche.engine.History
import justcoinche.engine.Rules
import justcoinche.engine.Seat
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Keeps games on disk, in [dir]:
 * - one record per game ("partie-2026-11-20_17-30-05.txt", see [GameRecord]),
 *   only ever appended to, so that a finished deal is never lost;
 * - "en-cours.txt": the name of the game being played and the line of its
 *   deal in progress, rewritten after each move, so that the game can be
 *   resumed at the very move where it stopped.
 *
 * Plain files rather than a database: the records are meant to be read by a
 * human and by analysis tools, and need no query.
 */
class GameStore(private val dir: File) {

    private var record: File? = null
    private var dealsWritten = 0

    /** The game being played when the store was last used, or null if there is none or it is over. */
    fun resume(): Game? {
        val pointer = File(dir, CURRENT).takeIf { it.exists() }?.readLines()
        val file = pointer?.firstOrNull()?.let { File(dir, it) }?.takeIf { it.exists() }
        val game = file?.let { read(it.readLines(), pointer.getOrNull(1)) }?.takeUnless { it.isOver }
        if (game != null) {
            record = file
            dealsWritten = game.results.size
        }
        return game
    }

    private fun read(lines: List<String>, inProgress: String?): Game =
        try {
            GameRecord.read(lines, inProgress)
        } catch (stale: IllegalArgumentException) {
            // The deal in progress may be one already appended to the record when
            // the app stopped between the two writes: the finished deals still stand.
            GameRecord.read(lines).takeIf { inProgress != null } ?: throw stale
        }

    /** Starts a new game, with a new record naming the computer [players] by seat. */
    fun start(
        rules: Rules,
        firstDealer: Seat,
        startedAt: LocalDateTime = LocalDateTime.now(),
        players: Map<Seat, String> = emptyMap(),
    ): Game {
        dir.mkdirs()
        val stamp = startedAt.withNano(0)
        var file = File(dir, "partie-${stamp.format(FILE_STAMP)}.txt")
        var suffix = 1
        while (file.exists()) file = File(dir, "partie-${stamp.format(FILE_STAMP)}-${suffix++}.txt")
        file.writeText(GameRecord.header(rules, firstDealer, stamp.toString(), players) + "\n")
        record = file
        dealsWritten = 0
        return Game.start(rules, firstDealer).also { save(it) }
    }

    /** Brings the files up to date with [game]: call it after every move. */
    fun save(game: Game) {
        val file = checkNotNull(record) { "No game started or resumed" }
        game.results.drop(dealsWritten).forEach { file.appendText(it.historyLine + "\n") }
        dealsWritten = game.results.size
        val inProgress = game.currentRound?.let { History.write(it) }
        // Written aside then renamed, so that a crash never leaves half a file.
        val temp = File(dir, "$CURRENT.tmp")
        temp.writeText(listOfNotNull(file.name, inProgress).joinToString("\n"))
        check(temp.renameTo(File(dir, CURRENT))) { "Could not save the game in progress" }
    }

    /** Adds the player's signal on the current move to the record. */
    fun signal(game: Game, note: String) {
        checkNotNull(record) { "No game started or resumed" }.appendText(GameRecord.signal(game, note) + "\n")
    }

    /** Every game record, oldest first. */
    fun records(): List<File> =
        dir.listFiles { file -> file.name.startsWith("partie-") }.orEmpty().sortedBy { it.name }

    private companion object {
        const val CURRENT = "en-cours.txt"
        val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    }
}
