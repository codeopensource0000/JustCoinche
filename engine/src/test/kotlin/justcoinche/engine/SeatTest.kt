package justcoinche.engine

import justcoinche.engine.Seat.EAST
import justcoinche.engine.Seat.NORTH
import justcoinche.engine.Seat.SOUTH
import justcoinche.engine.Seat.WEST
import kotlin.test.Test
import kotlin.test.assertEquals

class SeatTest {

    @Test
    fun `play goes anticlockwise from south to east, north, west and back`() {
        assertEquals(listOf(EAST, NORTH, WEST, SOUTH), listOf(SOUTH, EAST, NORTH, WEST).map { it.next })
    }

    @Test
    fun `partners sit opposite and share a team`() {
        assertEquals(NORTH, SOUTH.partner)
        assertEquals(WEST, EAST.partner)
        Seat.entries.forEach { seat ->
            assertEquals(seat.team, seat.partner.team)
            assertEquals(seat, seat.partner.partner)
        }
        assertEquals(Team.NORTH_SOUTH, SOUTH.team)
        assertEquals(Team.EAST_WEST, EAST.team)
    }
}
