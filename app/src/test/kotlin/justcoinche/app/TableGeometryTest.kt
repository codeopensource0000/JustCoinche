package justcoinche.app

import androidx.compose.ui.unit.dp
import justcoinche.engine.Seat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The table cannot be looked at from a test, but its layout can be checked:
 * on phone screens from small to large, in landscape, nothing may leave the
 * screen and no two elements may overlap.
 */
class TableGeometryTest {

    /**
     * Landscape screens in dp, after the system bars and the camera cutout:
     * small phones, common phones, and the Fairphone 6 (about 785 × 325 with
     * its status bar shown, 790 × 365 in full screen).
     */
    private val screens = listOf(
        640.dp to 320.dp,
        785.dp to 325.dp,
        790.dp to 365.dp,
        640.dp to 360.dp,
        732.dp to 360.dp,
        780.dp to 390.dp,
        850.dp to 412.dp,
        900.dp to 425.dp,
    )

    private fun TableGeometry.elements(): Map<String, Area> = buildMap {
        put("hand", handArea)
        put("trick", trickArea)
        put("menu", menuButton)
        put("score", scoreLine)
        Seat.entries.forEach { seat -> put("name $seat", nameTag(seat)) }
    }

    @Test
    fun `on every screen nothing leaves the table and nothing overlaps`() {
        screens.forEach { (width, height) ->
            val geometry = TableGeometry(width, height)
            val elements = geometry.elements()
            elements.forEach { (name, area) ->
                assertTrue("$name leaves the $width × $height screen: $area", area.isInside(width, height))
            }
            elements.entries.forEachIndexed { i, (name, area) ->
                elements.entries.drop(i + 1).forEach { (other, otherArea) ->
                    assertFalse("$name overlaps $other on $width × $height", area.overlaps(otherArea))
                }
            }
        }
    }

    @Test
    fun `the middle leaves room for the bidding panel, and the trick's cards are big`() {
        screens.forEach { (width, height) ->
            val geometry = TableGeometry(width, height)
            val middle = geometry.middle
            // The bidding panel: six 48 dp keys and three rows, about 330 × 140 dp, with a margin.
            val roomy = middle.width >= 380.dp && middle.height >= 140.dp
            assertTrue("middle too small on $width × $height: $middle", roomy)
            // The trick's cards are clear, but leave the felt room: from a quarter to a third of the height.
            assertTrue(geometry.trickCard.height >= height * 0.25f && geometry.trickCard.height <= height / 3)
        }
    }

    @Test
    fun `hand cards are read comfortably and keep their order from left to right`() {
        screens.forEach { (width, height) ->
            val geometry = TableGeometry(width, height)
            // The hand takes under a third of the screen's height, lifted cards included.
            assertTrue("hand too tall on $width × $height", geometry.handArea.height <= height * 0.3f)
            assertTrue(geometry.handCard.height >= 100.dp)
            val slots = (0 until 8).map { geometry.handSlot(it, 8) }
            slots.zipWithNext { a, b -> assertTrue(b.left > a.left) }
            // The cards are not squeezed: each shows well over half its face.
            slots.zipWithNext { a, b -> assertTrue(b.left - a.left >= geometry.handCard.width * 0.65f) }
        }
    }

    @Test
    fun `fewer cards stay centred under the trick`() {
        val geometry = TableGeometry(850.dp, 412.dp)
        listOf(1, 3, 8).forEach { count ->
            val first = geometry.handSlot(0, count)
            val last = geometry.handSlot(count - 1, count)
            assertEquals(geometry.centerX.value, ((first.left + last.right) / 2).value, 0.01f)
        }
    }
}
