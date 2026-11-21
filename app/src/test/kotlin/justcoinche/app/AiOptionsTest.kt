package justcoinche.app

import justcoinche.ai.AiCatalog
import justcoinche.engine.Seat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AiOptionsTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `the choice is written and read back, and starts with the default`() {
        val options = AiOptions(File(folder.root, "options/ia.txt"))
        assertEquals(AiChoice(DEFAULT_AI, DEFAULT_AI), options.read())
        options.write(AiChoice(partner = "ia2", opponents = "ia5-rapide"))
        assertEquals(AiChoice(partner = "ia2", opponents = "ia5-rapide"), AiOptions(options.file()).read())
    }

    @Test
    fun `an unknown or cheating player falls back to the default`() {
        val file = folder.newFile("ia.txt")
        file.writeText("partenaire ia3\nadversaires ia99\n")
        assertEquals(AiChoice(DEFAULT_AI, DEFAULT_AI), AiOptions(file).read())
    }

    @Test
    fun `the partner sits North and the opponents East and West`() {
        val choice = AiChoice(partner = "ia1", opponents = "ia4-rapide")
        assertEquals(mapOf(Seat.EAST to "ia4-rapide", Seat.NORTH to "ia1", Seat.WEST to "ia4-rapide"), choice.bySeat)
        assertEquals(setOf(Seat.EAST, Seat.NORTH, Seat.WEST), choice.players(seed = 1).keys)
        assertTrue(DEFAULT_AI in AiCatalog.playable.map { it.id })
    }

    private fun AiOptions.file(): File = File(folder.root, "options/ia.txt").also { assertTrue(it.exists()) }
}
