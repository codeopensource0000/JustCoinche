package justcoinche.app

import code.opensource0000.justcoinche.R
import justcoinche.engine.Card
import justcoinche.engine.Rank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

class DeckArtTest {

    /** The file name of a card's drawing, for example "card_hearts_jack". */
    private fun drawingName(card: Card): String {
        val rank = when (card.rank) {
            Rank.SEVEN -> "7"
            Rank.EIGHT -> "8"
            Rank.NINE -> "9"
            Rank.TEN -> "10"
            else -> card.rank.name.lowercase()
        }
        return "card_${card.suit.name.lowercase()}_$rank"
    }

    @Test
    fun `every card shows its own drawing`() {
        Card.deck.forEach { card ->
            val name = drawingName(card)
            // R.drawable holds one number per drawing, named after its file.
            val expected = R.drawable::class.java.getField(name).getInt(null)
            assertEquals(name, expected, faceOf(card))
        }
    }

    @Test
    fun `every drawing has the shape the corner letters are placed for`() {
        Card.deck.forEach { card ->
            // Unit tests run from the app module's folder.
            val vector = File("src/main/res/drawable/${drawingName(card)}.xml")
            if (vector.exists()) {
                val xml = vector.readText()
                assertTrue(card.toString(), "android:viewportWidth=\"$DECK_WIDTH\"" in xml)
                assertTrue(card.toString(), "android:viewportHeight=\"$DECK_HEIGHT\"" in xml)
            } else {
                // The court cards are images: see third_party/svg-cards/README.md.
                val image = ImageIO.read(File("src/main/res/drawable-nodpi/${drawingName(card)}.png"))
                assertEquals(card.toString(), DECK_HEIGHT / DECK_WIDTH, image.height.toFloat() / image.width, 0.005f)
            }
        }
    }

    @Test
    fun `no drawing holds a path too long for the compiled resources`() {
        // A longer string is silently replaced by "STRING_TOO_LARGE" in the app, which crashes when drawn.
        val longest = File("src/main/res/drawable").listFiles()!!.flatMap { file ->
            Regex("android:pathData=\"([^\"]*)\"").findAll(file.readText()).map { it.groupValues[1].length }
        }.max()
        assertTrue("longest path: $longest characters", longest <= Short.MAX_VALUE)
    }

    @Test
    fun `every rank has its corner letter`() {
        assertEquals(Rank.entries.toSet(), CORNER_INDICES.keys)
    }
}
