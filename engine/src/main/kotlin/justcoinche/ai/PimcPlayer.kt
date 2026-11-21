package justcoinche.ai

import justcoinche.engine.Bid
import justcoinche.engine.Card
import justcoinche.engine.CardSet
import justcoinche.engine.Contract
import justcoinche.engine.Player
import justcoinche.engine.PlayerView
import kotlin.random.Random

/** How hard an expert player thinks: deals drawn per card, and a time limit per decision. */
enum class Effort(val samples: Int, val maxMillis: Long, val label: String) {
    QUICK(16, 1_000, "rapide"),
    NORMAL(48, 2_500, "normal"),
    STRONG(128, 6_000, "fort"),
}

/** What an expert player reads from the table, besides the cards played and the suits not followed. */
enum class Insight {
    /** Nothing more (ia4). */
    NONE,

    /** The auction, with the petit jeu convention (ia5). */
    AUCTION,

    /** The auction and the direct calls of the card play (ia6). */
    AUCTION_AND_CALLS,
}

/**
 * "Expert PIMC" (ia4 to ia6): the card play of the bridge programs (GIB). For each
 * decision it draws deals of the hidden cards that agree with the play so
 * far ([HiddenCards]), scores every legal card on each of them with all the
 * cards open ([CardJudge]), and plays the card with the best mean score.
 * Bids like ia2. With an [insight], the drawn deals also follow what the
 * auction and the calls tell ([Reading]): each one is kept with the
 * probability its reading gives.
 *
 * Its known weakness, shared with the bridge programs: on each drawn deal it
 * plays as if it would then see all the cards, so it does not value keeping
 * options open or hiding information.
 *
 * The draws come from [seed] and what the player sees, so a decision is the
 * same every time the same position is met, unless the time limit cuts it
 * short; [timeLimited] false removes the limit, for tests.
 */
class PimcPlayer(
    private val effort: Effort,
    private val seed: Long,
    private val timeLimited: Boolean = true,
    private val insight: Insight = Insight.NONE,
) : Player {
    private var judge: CardJudge? = null
    private var judged: Contract? = null

    override fun bid(view: PlayerView): Bid = PetitJeu.bid(view)

    override fun play(view: PlayerView): Card {
        val cards = view.playableCards.toList()
        if (cards.size == 1) return cards.single()
        val contract = checkNotNull(view.contract)
        val trick = checkNotNull(view.currentTrick)
        val judge = judgeFor(contract, view)
        val hidden = HiddenCards(view)
        val readings = when (insight) {
            Insight.NONE -> emptyList()
            Insight.AUCTION -> listOf(AuctionReading(view))
            Insight.AUCTION_AND_CALLS -> listOf(AuctionReading(view), CallReading(view))
        }
        val random = Random(seed xor positionKey(view))
        val totals = cards.associateWithTo(LinkedHashMap()) { 0.0 }
        val deadline = System.nanoTime() + effort.maxMillis * NANOS_PER_MILLI
        // A few deals at least, then as many as planned while time allows.
        val inTime = { drawn: Int -> drawn < MIN_SAMPLES || !timeLimited || System.nanoTime() < deadline }
        var drawn = 0
        while (drawn < effort.samples && inTime(drawn)) {
            val hands = draw(hidden, readings, random) ?: break
            judge.judge(hands, view.tricks, trick, hidden.beloteTeam(hands)).forEach { (card, score) ->
                totals[card] = totals.getValue(card) + score
            }
            drawn++
        }
        return if (drawn == 0) RulesPlusPlay(view).choose() else bestOf(totals, view)
    }

    /**
     * A drawn deal kept with the probability its readings give it. If none
     * is kept after [MAX_CANDIDATES] tries, the deal that explained the table
     * best: the convention cannot explain everything a player does.
     */
    private fun draw(hidden: HiddenCards, readings: List<Reading>, random: Random): List<CardSet>? {
        var kept: List<CardSet>? = null
        var best: List<CardSet>? = null
        var bestWeight = -1.0
        var tries = 0
        while (kept == null && tries < MAX_CANDIDATES) {
            val hands = hidden.sample(random) ?: break
            val initial = hidden.initialHands(hands)
            val weight = readings.fold(1.0) { product, reading -> product * reading.weight(initial) }
            if (random.nextDouble() < weight) kept = hands
            if (weight > bestWeight) {
                best = hands
                bestWeight = weight
            }
            tries++
        }
        return kept ?: best
    }

    /** One judge per contract: its solvers are large, so they are kept for the whole deal. */
    private fun judgeFor(contract: Contract, view: PlayerView): CardJudge {
        if (judged != contract || judge == null) {
            judge = CardJudge(contract, view.rules)
            judged = contract
        }
        return checkNotNull(judge)
    }

    /** A number that changes with everything the player has seen, to seed the draws of this decision. */
    private fun positionKey(view: PlayerView): Long {
        var key = view.hand.bits.toLong() * MIX + view.seat.ordinal
        (view.tricks + listOfNotNull(view.currentTrick)).forEach { trick ->
            trick.cards.forEach { key = key * MIX + it.index }
        }
        return key
    }

    private companion object {
        const val MIN_SAMPLES = 4
        const val MAX_CANDIDATES = 200
        const val NANOS_PER_MILLI = 1_000_000L
        const val MIX = 1_000_003L
    }
}
