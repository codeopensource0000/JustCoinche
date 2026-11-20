package justcoinche.record

import justcoinche.arena.RulePlayer
import justcoinche.arena.playGame
import justcoinche.arena.playTurn
import justcoinche.engine.Bid
import justcoinche.engine.Deal
import justcoinche.engine.Game
import justcoinche.engine.History
import justcoinche.engine.Rules
import justcoinche.engine.Seat
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.LocalDateTime
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameStoreTest {

    @TempDir
    lateinit var dir: File

    private val players = Seat.entries.associateWith { RulePlayer() }
    private val noon = LocalDateTime.of(2026, 11, 20, 12, 0, 0)

    /** Everything that defines a game's state, as text. */
    private fun Game.fingerprint(): List<String> =
        results.map { it.historyLine } + listOfNotNull(currentRound?.let { History.write(it) }) +
            "$scores $dealer"

    @Test
    fun `a game saved after every move resumes at any move, and its record replays it whole`() {
        val random = Random(3)
        val store = GameStore(dir)
        var resumes = 0
        val game = playGame(store.start(Rules(), Seat.WEST, noon), players, random) { game ->
            store.save(game)
            // Resuming after a sample of moves, as if the app had been closed there.
            if (!game.isOver && random.nextInt(25) == 0) {
                assertEquals(game.fingerprint(), checkNotNull(GameStore(dir).resume()).fingerprint())
                resumes++
            }
        }
        assertTrue(game.isOver)
        assertTrue(resumes > 10, "only $resumes resumes checked")

        val record = store.records().single().readLines()
        assertTrue(record.first().startsWith("partie v1 | debut 2026-11-20T12:00 | premier-donneur O | regles "))
        assertEquals(game.fingerprint(), GameRecord.read(record).fingerprint())
        // A finished game is not resumed: the app starts a new one.
        assertNull(GameStore(dir).resume())
    }

    @Test
    fun `a signal names the deal and the move, and reading the record skips it`() {
        val store = GameStore(dir)
        var game = store.start(Rules(), Seat.WEST, noon)
        game = game.deal(Deal.random(Random(1)))
        game = game.call(Seat.SOUTH, Bid.Pass).call(Seat.EAST, Bid.Pass)
        store.save(game)
        store.signal(game, "  Est aurait\ndû | couper ")

        val record = store.records().single().readLines()
        assertEquals("signal | donne 1 | coup 2 | note Est aurait dû / couper", record.last())
        assertEquals(game.fingerprint(), checkNotNull(GameStore(dir).resume()).fingerprint())
    }

    @Test
    fun `a deal already recorded when the app stopped is not replayed twice`() {
        val random = Random(4)
        val store = GameStore(dir)
        var game = store.start(Rules(), Seat.WEST, noon)
        var before = game
        // Play until the first deal ends, remembering the save made just before its last move.
        while (game.results.isEmpty()) {
            store.save(game)
            before = game
            game = if (game.currentRound == null) game.deal(Deal.random(random)) else game.playTurn(players)
        }
        store.save(game)
        // The app stops between the two writes: the record has the deal, the
        // file of the game in progress still holds its last move but one.
        val inProgress = checkNotNull(before.currentRound).let { History.write(it) }
        File(dir, "en-cours.txt").writeText(store.records().single().name + "\n" + inProgress)

        assertEquals(game.fingerprint(), checkNotNull(GameStore(dir).resume()).fingerprint())
    }

    @Test
    fun `a record that disagrees with the rules is rejected`() {
        val store = GameStore(dir)
        playGame(store.start(Rules(), Seat.WEST, noon), players, Random(5)) { store.save(it) }
        val record = store.records().single().readLines()
        val tampered = record.map { it.replace("score NS ", "score NS 1") }
        assertFailsWith<IllegalArgumentException> { GameRecord.read(tampered) }
        assertFailsWith<IllegalArgumentException> { GameRecord.read(listOf("partie v9")) }
    }

    @Test
    fun `two games started in the same second get two records`() {
        val store = GameStore(dir)
        store.start(Rules(), Seat.WEST, noon)
        store.start(Rules(), Seat.WEST, noon)
        val names = store.records().map { it.name }
        assertEquals(2, names.size)
        assertNotEquals(names[0], names[1])
    }
}
