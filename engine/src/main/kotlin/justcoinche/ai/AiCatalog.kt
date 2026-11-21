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
        AiEntry(
            "ia2",
            "Règles+",
            "Petit jeu aux enchères (barème As, 9, Valet), coinche simple, appel direct au jeu de la carte.",
        ) { _, _ -> RulesPlusPlayer() },
        AiEntry(
            "ia3",
            "Oracle (triche)",
            "Voit toutes les cartes et joue le coup exact. Sert de mesure, jamais d'adversaire.",
            cheats = true,
        ) { _, _ -> OraclePlayer() },
    ) + experts(
        "ia4",
        "Expert PIMC",
        "Tire des donnes compatibles avec le jeu, les résout cartes ouvertes et joue la meilleure carte " +
            "en moyenne. Enchères de Règles+.",
        Insight.NONE,
    )

    /** One expert at each effort: "ia4" thinks normally, "ia4-rapide" and "ia4-fort" less and more. */
    private fun experts(id: String, name: String, description: String, insight: Insight) =
        Effort.entries.map { effort ->
            AiEntry(
                if (effort == Effort.NORMAL) id else "$id-${effort.label}",
                "$name (${effort.label})",
                "$description ${effort.samples} donnes tirées par coup.",
            ) { _, seed -> PimcPlayer(effort, seed, insight = insight) }
        }

    /** The players a human may face in the app: every honest one. */
    val playable: List<AiEntry> get() = entries.filter { !it.cheats }

    operator fun get(id: String): AiEntry =
        entries.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Unknown AI '$id'")

    private const val SEATS = 4
}
