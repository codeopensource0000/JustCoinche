package justcoinche.ai

import justcoinche.arena.duplicateMatch
import justcoinche.engine.Rules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AiCatalogTest {

    @Test
    fun `ids are unique and every AI is found by its id`() {
        val ids = AiCatalog.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertEquals(it, AiCatalog[it].id) }
        assertFailsWith<IllegalArgumentException> { AiCatalog["nope"] }
    }

    @Test
    fun `a cheating AI is never offered as an opponent`() {
        assertTrue(AiCatalog.playable.none { it.cheats })
    }

    @Test
    fun `every AI of the catalog plays whole deals legally against the beginner`() {
        // The arena replays each deal through the engine, which rejects any illegal bid or card.
        AiCatalog.entries.forEach { entry ->
            duplicateMatch(
                teamA = { seat -> entry.create(seat, 1) },
                teamB = { seat -> AiCatalog["ia1"].create(seat, 1) },
                deals = DEALS_PER_AI,
                seed = 5,
                rules = Rules(),
            )
        }
    }

    private companion object {
        const val DEALS_PER_AI = 6
    }
}
