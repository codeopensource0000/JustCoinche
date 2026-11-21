package justcoinche.app

import justcoinche.ai.AiCatalog
import justcoinche.engine.Player
import justcoinche.engine.Seat
import java.io.File

/** Which computer players sit at the table: North, the partner, and the two opponents, by catalog id. */
internal data class AiChoice(val partner: String = DEFAULT_AI, val opponents: String = DEFAULT_AI) {

    /** The catalog id of the computer player at [seat]. */
    fun idAt(seat: Seat): String = if (seat == HUMAN.partner) partner else opponents

    /** The id at each computer seat, as the game record names them. */
    val bySeat: Map<Seat, String> get() = Seat.entries.filter { it != HUMAN }.associateWith { idAt(it) }

    /** Fresh players for the three computer seats; [seed] feeds those that draw at random. */
    fun players(seed: Long): Map<Seat, Player> = bySeat.mapValues { (seat, id) -> AiCatalog[id].create(seat, seed) }
}

/** The computer player chosen until the player picks another one: the best honest one measured, quick setting. */
internal const val DEFAULT_AI = "ia5-rapide"

/**
 * Keeps the [AiChoice] in a small text file, one line per role
 * ("partenaire ia2", "adversaires ia5"). An id the catalog no longer knows,
 * or one that sees the hidden cards, falls back to the default.
 */
internal class AiOptions(private val file: File) {

    fun read(): AiChoice {
        val lines = if (file.exists()) file.readLines() else emptyList()
        val fields = lines.associate { it.substringBefore(' ') to it.substringAfter(' ') }
        fun known(id: String?) = id?.takeIf { wanted -> AiCatalog.playable.any { it.id == wanted } } ?: DEFAULT_AI
        return AiChoice(partner = known(fields[PARTNER]), opponents = known(fields[OPPONENTS]))
    }

    fun write(choice: AiChoice) {
        file.parentFile?.mkdirs()
        file.writeText("$PARTNER ${choice.partner}\n$OPPONENTS ${choice.opponents}\n")
    }

    private companion object {
        const val PARTNER = "partenaire"
        const val OPPONENTS = "adversaires"
    }
}
