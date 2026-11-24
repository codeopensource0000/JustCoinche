package justcoinche.app

import code.opensource0000.justcoinche.R
import justcoinche.engine.Card
import justcoinche.engine.Rank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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
    fun `every drawing has the size the corner letters are placed for`() {
        Card.deck.forEach { card ->
            // Unit tests run from the app module's folder.
            val xml = File("src/main/res/drawable/${drawingName(card)}.xml").readText()
            assertTrue(card.toString(), "android:viewportWidth=\"$DECK_WIDTH\"" in xml)
            assertTrue(card.toString(), "android:viewportHeight=\"$DECK_HEIGHT\"" in xml)
        }
    }

    @Test
    fun `every rank has its corner letter`() {
        assertEquals(Rank.entries.toSet(), CORNER_INDICES.keys)
    }
}
