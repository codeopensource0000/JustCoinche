package justcoinche.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TimingTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `each speed governs its own durations`() {
        val normal = Timing()
        val fastCards = Timing(cards = Pace.FAST)
        assertTrue(fastCards.playMs < normal.playMs && fastCards.botPauseMs < normal.botPauseMs)
        assertEquals(normal.gatherMs, fastCards.gatherMs)
        val slowTricks = Timing(tricks = Pace.SLOW)
        assertTrue(slowTricks.gatherDelayMs > normal.gatherDelayMs && slowTricks.gatherMs > normal.gatherMs)
        assertEquals(normal.playMs, slowTricks.playMs)
    }

    @Test
    fun `the next card waits until the trick is gathered`() {
        Pace.entries.forEach { pace ->
            val timing = Timing(tricks = pace)
            assertTrue(timing.trickPauseMs >= timing.gatherDelayMs + timing.gatherMs)
        }
    }

    @Test
    fun `the speeds are written and read back, and start normal`() {
        val file = File(folder.root, "options/rythme.txt")
        assertEquals(Timing(), TimingOptions(file).read())
        TimingOptions(file).write(Timing(cards = Pace.VERY_FAST, tricks = Pace.SLOW))
        assertEquals(Timing(cards = Pace.VERY_FAST, tricks = Pace.SLOW), TimingOptions(file).read())
        file.writeText("cartes supersonique\n")
        assertEquals(Timing(), TimingOptions(file).read())
    }
}
