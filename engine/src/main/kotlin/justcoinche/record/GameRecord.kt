package justcoinche.record

import justcoinche.engine.Game
import justcoinche.engine.History
import justcoinche.engine.Round
import justcoinche.engine.Rules
import justcoinche.engine.Seat
import justcoinche.engine.parseSeat
import justcoinche.engine.letter

/**
 * The record of a whole game, as lines of text: a header, then one
 * [History] line per finished deal (redeals included), with the player's
 * signals in between. For example:
 *
 * ```
 * partie v1 | debut 2026-11-20T17:30:05 | premier-donneur O | regles pts-contrat,...,cible-1000
 * v1 | donneur O | regles ... | score NS 90 EO 0
 * signal | donne 2 | coup 17 | note Est aurait dû couper
 * v1 | donneur S | regles ... | score NS 0 EO 160
 * ```
 *
 * Reading a record replays every deal through [Game], so a record that
 * breaks a rule or disagrees with the scores is rejected.
 */
object GameRecord {

    const val VERSION = "partie v1"
    private const val SEPARATOR = " | "
    private const val SIGNAL = "signal"

    fun header(rules: Rules, firstDealer: Seat, startedAt: String): String = listOf(
        VERSION,
        "debut $startedAt",
        "premier-donneur ${firstDealer.letter}",
        "regles ${History.rulesNotation(rules)}",
    ).joinToString(SEPARATOR)

    /**
     * A signal: the player flags the current move as worth a look, for
     * instance a computer player's odd card. It names the deal (counting from
     * 1, redeals included) and the number of bids and cards played in it so far.
     */
    fun signal(game: Game, note: String): String {
        val round = game.currentRound ?: game.results.lastOrNull()?.round
        val deal = game.results.size + if (game.currentRound != null) 1 else 0
        val move = round?.let { it.auction.calls.size + cardsPlayed(it) } ?: 0
        // A note stays on its line and cannot be mistaken for a field separator.
        val text = note.replace(Regex("\\s+"), " ").replace("|", "/").trim()
        return listOf(SIGNAL, "donne $deal", "coup $move", "note $text").joinToString(SEPARATOR)
    }

    /**
     * Rebuilds a game from its record. [inProgress] is the line of the deal
     * being played, if any (see [History.write]).
     */
    fun read(lines: List<String>, inProgress: String? = null): Game {
        val header = lines.firstOrNull()?.split(SEPARATOR)
        require(header != null && header.first() == VERSION) { "Not a $VERSION record" }
        val fields = header.drop(1).associate { it.substringBefore(' ') to it.substringAfter(' ') }
        fun field(name: String): String = fields[name] ?: throw IllegalArgumentException("Missing field '$name'")

        var game = Game.start(History.parseRules(field("regles")), parseSeat(field("premier-donneur")))
        lines.drop(1).filter { it.isNotBlank() && !it.startsWith(SIGNAL) }.forEach { line ->
            game = game.replay(History.read(line))
            require(game.results.last().historyLine == line) { "Deal ${game.results.size} does not match: $line" }
        }
        inProgress?.let { line ->
            val round = History.read(line)
            require(round.toAct != null) { "The deal in progress is already finished: $line" }
            game = game.replay(round)
        }
        return game
    }

    /** Plays [round]'s bids and cards on this game, as its next deal. */
    private fun Game.replay(round: Round): Game {
        require(round.dealer == dealer && round.rules == rules) { "Deal out of order or under other rules" }
        var game = deal(round.deal)
        round.auction.calls.forEach { game = game.call(it.seat, it.bid) }
        round.cardPlay?.let { play -> (play.tricks + play.currentTrick).forEach { trick ->
            trick.cards.forEach { game = game.play(it) }
        } }
        return game
    }

    private fun cardsPlayed(round: Round): Int =
        round.cardPlay?.let { it.tricks.size * Seat.entries.size + it.currentTrick.cards.size } ?: 0
}
