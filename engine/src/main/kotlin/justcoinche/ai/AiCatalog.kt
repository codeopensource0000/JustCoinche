package justcoinche.ai

import justcoinche.arena.RandomPlayer
import justcoinche.arena.RulePlayer
import justcoinche.engine.Player
import justcoinche.engine.Seat
import kotlin.random.Random

/**
 * One computer player the arena and the app can pick by its [id].
 *
 * [create] makes the player for a seat; [seed] feeds any randomness it uses,
 * so that a match can be replayed exactly. [cheats] marks a player that sees
 * the hidden cards: a yardstick for measures, never an opponent in the app.
 */
class AiEntry(
    val id: String,
    val name: String,
    val description: String,
    val cheats: Boolean = false,
    val create: (seat: Seat, seed: Long) -> Player,
)

/** Every computer player, from the weakest to the strongest. */
object AiCatalog {

    val entries: List<AiEntry> = listOf(
        AiEntry("ia0", "Aléatoire", "Joue une carte légale au hasard ; passe presque toujours.") { seat, seed ->
            RandomPlayer(Random(seed * SEATS + seat.ordinal))
        },
        AiEntry("ia1", "Débutant", "Réflexes simples : annonce avec 3 atouts dont le Valet ou le 9.") { _, _ ->
            RulePlayer()
        },
    )

    /** The players a human may face in the app: every honest one. */
    val playable: List<AiEntry> get() = entries.filter { !it.cheats }

    operator fun get(id: String): AiEntry =
        entries.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Unknown AI '$id'")

    private const val SEATS = 4
}
