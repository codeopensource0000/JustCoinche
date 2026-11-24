package justcoinche.app

import java.io.File
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** A speed the player can choose; [factor] stretches or shortens every duration it governs. */
internal enum class Pace(val label: String, val factor: Float) {
    SLOW("Lent", 1.6f),
    NORMAL("Normal", 1f),
    FAST("Rapide", 0.6f),
    VERY_FAST("Très rapide", 0.35f),
}

/**
 * How long things take on the table, in milliseconds. Two speeds are chosen
 * in the menu: [cards] for the cards that move (played, dealt, the pause
 * between two computer players), [tricks] for a finished trick (how long it
 * stays in view, how fast it is gathered).
 */
internal data class Timing(val cards: Pace = Pace.NORMAL, val tricks: Pace = Pace.NORMAL) {
    val playMs: Int get() = ms(PLAY_MS, cards)
    val fadeInMs: Int get() = ms(FADE_IN_MS, cards)
    val closeGapMs: Int get() = ms(CLOSE_GAP_MS, cards)
    val riseMs: Int get() = ms(RISE_MS, cards)
    val dealStaggerMs: Long get() = (DEAL_STAGGER_MS * cards.factor).roundToLong()
    val botPauseMs: Long get() = (BOT_PAUSE_MS * cards.factor).roundToLong()

    /** How long a finished trick stays in view before it is gathered. */
    val gatherDelayMs: Long get() = (GATHER_DELAY_MS * tricks.factor).roundToLong()
    val gatherMs: Int get() = ms(GATHER_MS, tricks)

    /** The wait after a trick before the next card: the trick is gathered meanwhile. */
    val trickPauseMs: Long get() = gatherDelayMs + gatherMs + AFTER_GATHER_MS

    private fun ms(base: Int, pace: Pace): Int = (base * pace.factor).roundToInt()

    private companion object {
        const val PLAY_MS = 480
        const val FADE_IN_MS = 220
        const val CLOSE_GAP_MS = 420
        const val RISE_MS = 380
        const val DEAL_STAGGER_MS = 70
        const val BOT_PAUSE_MS = 800
        const val GATHER_DELAY_MS = 900
        const val GATHER_MS = 520
        const val AFTER_GATHER_MS = 280
    }
}

/**
 * Keeps the [Timing] in a small text file ("cartes rapide", "plis lent").
 * A missing or unknown speed is the normal one.
 */
internal class TimingOptions(private val file: File) {

    fun read(): Timing {
        val lines = if (file.exists()) file.readLines() else emptyList()
        val fields = lines.associate { it.substringBefore(' ') to it.substringAfter(' ') }
        fun pace(key: String) = Pace.entries.firstOrNull { it.name.lowercase() == fields[key] } ?: Pace.NORMAL
        return Timing(cards = pace(CARDS), tricks = pace(TRICKS))
    }

    fun write(timing: Timing) {
        file.parentFile?.mkdirs()
        file.writeText("$CARDS ${timing.cards.name.lowercase()}\n$TRICKS ${timing.tricks.name.lowercase()}\n")
    }

    private companion object {
        const val CARDS = "cartes"
        const val TRICKS = "plis"
    }
}
