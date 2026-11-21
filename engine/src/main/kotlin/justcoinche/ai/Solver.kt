package justcoinche.ai

import justcoinche.engine.Card
import justcoinche.engine.CardSet
import justcoinche.engine.ContractType
import justcoinche.engine.Rules
import justcoinche.engine.Team
import justcoinche.engine.Trick

/** What the attack tries to maximise. */
enum class Objective {
    /** Card points, with the 10 for the last trick: every contract but the capot. */
    POINTS,

    /** Tricks won: a capot is made only by winning all eight. */
    TRICKS,
}

/**
 * Exact card play with all four hands visible, as bridge programs do it
 * ("double dummy"): the value of a position is what the attack makes from
 * now on when everybody plays perfectly, the attack maximising and the
 * defence minimising.
 *
 * The rules are those of the engine (`legalCards`, `Trick`), rewritten on
 * bit masks for speed; a test checks both agree on thousands of positions.
 * Alpha-beta search with a transposition table at trick starts that also
 * remembers the best lead, a bound on what is left to win, a simple move
 * ordering, and pointless cards next to each other tried only once. The
 * belote does not depend on the play and is left out.
 *
 * Not thread-safe: one solver per thread.
 */
class Solver(
    private val type: ContractType,
    private val rules: Rules,
    private val attack: Team,
    private val objective: Objective = Objective.POINTS,
) {
    private val trumpSuit = (type as? ContractType.SuitTrump)?.trump?.ordinal ?: NO_TRUMP
    private val strength = IntArray(DECK) { type.strength(Card(it)) }
    private val points = IntArray(DECK) { type.points(Card(it)) }
    private val trumpOrder = BooleanArray(SUITS) { suit -> type.usesTrumpOrder(Card(suit * RANKS).suit) }

    /** For each card, the cards of its suit that beat it. */
    private val stronger = IntArray(DECK) { card ->
        (0 until DECK).filter { it / RANKS == card / RANKS && strength[it] > strength[card] }
            .fold(0) { mask, other -> mask or (1 shl other) }
    }

    // The position being searched.
    private val hands = IntArray(SEATS)
    private val trickCards = IntArray(SEATS)
    private var trickSize = 0
    private var leader = 0
    private var winPos = 0
    private var tricksLeft = 0

    /** Points of the cards not yet in a finished trick: what is still to be won. */
    private var pointsLeft = 0

    private val table = TranspositionTable()

    /** The transposition table can be switched off, to check that it changes no result. */
    internal var useTable = true

    /** The best card of the node searched last, read by its caller right after the search. */
    private var bestMove = -1

    /** Nodes visited since the last [load], for measures. */
    var nodes = 0L
        private set

    /** Sets up a position: the hands by seat, the trick being played, the tricks left (this one included). */
    fun load(handsBySeat: List<CardSet>, trick: Trick, tricksLeft: Int) {
        require(handsBySeat.size == SEATS)
        handsBySeat.forEachIndexed { seat, hand -> hands[seat] = hand.bits }
        trickSize = trick.cards.size
        trick.cards.forEachIndexed { i, card -> trickCards[i] = card.index }
        leader = trick.leader.ordinal
        winPos = if (trickSize == 0) 0 else trick.cards.indexOf(checkNotNull(trick.winningCard(type)))
        this.tricksLeft = tricksLeft
        pointsLeft = handsBySeat.sumOf { hand -> hand.toList().sumOf { points[it.index] } } +
            trick.cards.sumOf { points[it.index] }
        table.clear()
        nodes = 0
    }

    /** The value of the loaded position: what the attack makes from now on. */
    fun value(): Int = search(-INFINITY, INFINITY)

    /** Whether the attack makes at least [target] from now on: a fast null-window search. */
    fun reaches(target: Int): Boolean = search(target - 1, target) >= target

    /** The value of each legal card of the player to act: what the attack makes if that card is played. */
    fun moveValues(): Map<Card, Int> {
        val seat = (leader + trickSize) % SEATS
        val result = LinkedHashMap<Card, Int>()
        var moves = legal(seat)
        while (moves != 0) {
            val card = Integer.numberOfTrailingZeros(moves)
            moves = moves and (moves - 1)
            result[Card(card)] = playThenSearch(seat, card, -INFINITY, INFINITY)
        }
        return result
    }

    /** For each legal card of the player to act, whether the attack still makes at least [target] after it. */
    fun moveReaches(target: Int): Map<Card, Boolean> {
        val seat = (leader + trickSize) % SEATS
        val result = LinkedHashMap<Card, Boolean>()
        var moves = legal(seat)
        while (moves != 0) {
            val card = Integer.numberOfTrailingZeros(moves)
            moves = moves and (moves - 1)
            result[Card(card)] = playThenSearch(seat, card, target - 1, target) >= target
        }
        return result
    }

    /**
     * The value of the position if it lies in the window (alpha, beta);
     * otherwise a bound on the side of the window where it lies.
     */
    private fun search(alpha: Int, beta: Int): Int {
        nodes++
        // The attack makes between nothing and all that is left: no need to search outside.
        val most = if (objective == Objective.TRICKS) tricksLeft else pointsLeft + LAST_TRICK
        return when {
            tricksLeft == 0 -> 0
            most <= alpha -> most
            beta <= 0 -> 0
            trickSize == 0 && useTable -> searchTrickStart(alpha, beta)
            else -> searchMoves(alpha, beta, NONE)
        }
    }

    /** At a trick start: what the table already knows, else a search whose result the table keeps. */
    private fun searchTrickStart(alpha: Int, beta: Int): Int {
        val slot = table.find(hands, leader)
        val known = slot >= 0
        val lower = if (known) table.lower[slot] else Int.MIN_VALUE
        val upper = if (known) table.upper[slot] else Int.MAX_VALUE
        return when {
            lower >= beta || lower == upper -> lower
            upper <= alpha -> upper
            else -> {
                // The lead that was best last time is tried first.
                val value = searchMoves(maxOf(alpha, lower), minOf(beta, upper), if (known) table.best[slot] else NONE)
                table.record(table.claim(hands, leader), value, alpha, beta, bestMove)
                value
            }
        }
    }

    /** Tries the moves, [first] first if legal, and leaves the best one in [bestMove]. */
    private fun searchMoves(alphaIn: Int, betaIn: Int, first: Int): Int {
        var alpha = alphaIn
        var beta = betaIn
        val seat = (leader + trickSize) % SEATS
        val maximizing = seat % 2 == attack.ordinal
        var next = orderedMoves(seat, first)
        var best = if (maximizing) -INFINITY else INFINITY
        var bestCard = NONE
        while (moveBuffer[next] >= 0) {
            val card = moveBuffer[next++]
            val v = playThenSearch(seat, card, alpha, beta)
            if (if (maximizing) v > best else v < best) {
                best = v
                bestCard = card
            }
            if (maximizing) alpha = maxOf(alpha, best) else beta = minOf(beta, best)
            if (alpha >= beta) break
        }
        bestMove = bestCard
        return best
    }

    /** Plays [card] for [seat], searches the rest, and undoes the move. */
    private fun playThenSearch(seat: Int, card: Int, alpha: Int, beta: Int): Int {
        val savedWinPos = winPos
        hands[seat] = hands[seat] and (1 shl card).inv()
        trickCards[trickSize] = card
        // The lead wins until beaten.
        if (trickSize == 0) winPos = 0 else if (beats(card, trickCards[winPos])) winPos = trickSize
        trickSize++
        val value = if (trickSize < SEATS) {
            search(alpha, beta)
        } else {
            val savedLeader = leader
            // The next tricks reuse trickCards: keep this trick's cards to put them back.
            val (c0, c1, c2) = Triple(trickCards[0], trickCards[1], trickCards[2])
            val trickPoints = points[c0] + points[c1] + points[c2] + points[card]
            val winner = (leader + winPos) % SEATS
            val gained = when {
                winner % 2 != attack.ordinal -> 0
                objective == Objective.TRICKS -> 1
                else -> trickPoints + if (tricksLeft == 1) LAST_TRICK else 0
            }
            leader = winner
            trickSize = 0
            tricksLeft--
            pointsLeft -= trickPoints
            val rest = gained + search(alpha - gained, beta - gained)
            pointsLeft += trickPoints
            tricksLeft++
            trickSize = SEATS
            leader = savedLeader
            trickCards[0] = c0
            trickCards[1] = c1
            trickCards[2] = c2
            trickCards[LAST_SEAT] = card
            rest
        }
        trickSize--
        winPos = savedWinPos
        hands[seat] = hands[seat] or (1 shl card)
        return value
    }

    /** Whether [challenger] takes the trick from [current], as in `Trick`. */
    private fun beats(challenger: Int, current: Int): Boolean = when {
        challenger / RANKS == current / RANKS -> strength[challenger] > strength[current]
        else -> challenger / RANKS == trumpSuit
    }

    /** The legal cards of [seat], as `legalCards` computes them. */
    private fun legal(seat: Int): Int {
        val hand = hands[seat]
        if (trickSize == 0) return hand
        val led = trickCards[0] / RANKS
        val following = hand and suitMask(led)
        val winning = trickCards[winPos]
        val trumps = if (trumpSuit == NO_TRUMP) 0 else hand and suitMask(trumpSuit)
        val partnerIsMaster = (leader + winPos) % SEATS == (seat + 2) % SEATS
        val overtrumps = trumps and stronger[winning]
        return when {
            // Following: in a suit ranked like trumps, higher than the winning card if possible.
            following != 0 -> if (trumpOrder[led] && following and stronger[winning] != 0) {
                following and stronger[winning]
            } else {
                following
            }
            trumps == 0 || partnerIsMaster -> hand
            // Cannot follow: trump, over the winning trump if possible.
            winning / RANKS != trumpSuit -> trumps
            overtrumps != 0 -> overtrumps
            rules.mustUnderTrump -> trumps
            else -> hand
        }
    }

    // Move ordering, one buffer per depth.
    private val moveBuffer = IntArray((DECK + 1) * (RANKS + 1))
    private val scoreBuffer = IntArray(RANKS + 1)

    /**
     * The legal cards of [seat], the most promising first: [first] if it is
     * one of them, then, when the move leaves its team winning the trick, the
     * richest card; otherwise the cheapest. They are written in [moveBuffer]
     * from the returned index, ending with -1.
     */
    private fun orderedMoves(seat: Int, first: Int): Int {
        val depth = DECK - hands.sumOf { Integer.bitCount(it) }
        val base = depth * (RANKS + 1)
        var count = 0
        var moves = withoutEquivalents(legal(seat))
        while (moves != 0) {
            val card = Integer.numberOfTrailingZeros(moves)
            moves = moves and (moves - 1)
            moveBuffer[base + count] = card
            scoreBuffer[count] = if (card == first) FIRST else moveScore(seat, card)
            count++
        }
        // Insertion sort, best score first: at most eight cards.
        for (i in 1 until count) {
            val card = moveBuffer[base + i]
            val score = scoreBuffer[i]
            var j = i - 1
            while (j >= 0 && scoreBuffer[j] < score) {
                moveBuffer[base + j + 1] = moveBuffer[base + j]
                scoreBuffer[j + 1] = scoreBuffer[j]
                j--
            }
            moveBuffer[base + j + 1] = card
            scoreBuffer[j + 1] = score
        }
        moveBuffer[base + count] = -1
        return base
    }

    /**
     * Drops the cards that would change nothing: of two pointless cards of a
     * suit with no card still in play between them, playing one or the other
     * is the same, so only the lower one is tried.
     */
    private fun withoutEquivalents(moves: Int): Int {
        var inPlay = hands[0] or hands[1] or hands[2] or hands[LAST_SEAT]
        for (i in 0 until trickSize) inPlay = inPlay or (1 shl trickCards[i])
        var kept = moves
        var rest = moves
        while (rest != 0) {
            val card = Integer.numberOfTrailingZeros(rest)
            rest = rest and (rest - 1)
            val above = stronger[card] and inPlay
            if (points[card] == 0 && above != 0) {
                // The next stronger card still in play, if pointless and in the same hand, is the same move.
                val next = weakest(above, strength)
                if ((moves shr next) and 1 == 1 && points[next] == 0) kept = kept and (1 shl next).inv()
            }
        }
        return kept
    }

    private fun moveScore(seat: Int, card: Int): Int {
        if (trickSize == 0) return strength[card] * RANKS + points[card]
        val winnerAfter = if (beats(card, trickCards[winPos])) seat else (leader + winPos) % SEATS
        return if (winnerAfter % 2 == seat % 2) {
            WINNING + points[card] - strength[card]
        } else {
            -points[card] - strength[card]
        }
    }

    private companion object {
        const val DECK = 32
        const val SUITS = 4
        const val RANKS = 8
        const val SEATS = 4
        const val LAST_SEAT = 3
        const val NO_TRUMP = -1
        const val NONE = -1
        const val LAST_TRICK = 10
        const val WINNING = 1000
        const val FIRST = 1_000_000
        const val INFINITY = 100_000
    }
}

private const val SUIT_BITS = 0xFF
private const val RANKS_PER_SUIT = 8

private fun suitMask(suit: Int): Int = SUIT_BITS shl (suit * RANKS_PER_SUIT)

/** The card of [cards] with the lowest strength. */
private fun weakest(cards: Int, strength: IntArray): Int {
    var best = -1
    var rest = cards
    while (rest != 0) {
        val card = Integer.numberOfTrailingZeros(rest)
        rest = rest and (rest - 1)
        if (best < 0 || strength[card] < strength[best]) best = card
    }
    return best
}

/**
 * Known bounds on the value of positions at the start of a trick, keyed by
 * the four hands and the leader, with the best lead found. One entry per
 * slot, the newest wins.
 */
private class TranspositionTable {
    private val keyHigh = LongArray(SIZE)
    private val keyLow = LongArray(SIZE)
    private val keyLeader = IntArray(SIZE)
    private val stamp = IntArray(SIZE)
    val lower = IntArray(SIZE)
    val upper = IntArray(SIZE)
    val best = IntArray(SIZE)
    private var generation = 1

    fun clear() {
        generation++
    }

    /** The slot of this position, or -1 if the table does not know it. */
    fun find(hands: IntArray, leader: Int): Int {
        val slot = slotOf(hands, leader)
        return if (matches(slot, hands, leader)) slot else -1
    }

    /** The slot of this position, emptied for it if it held another one. */
    fun claim(hands: IntArray, leader: Int): Int {
        val slot = slotOf(hands, leader)
        if (!matches(slot, hands, leader)) {
            keyHigh[slot] = high(hands)
            keyLow[slot] = low(hands)
            keyLeader[slot] = leader
            stamp[slot] = generation
            lower[slot] = Int.MIN_VALUE
            upper[slot] = Int.MAX_VALUE
        }
        return slot
    }

    /** Records [value], found with the window (alpha, beta), as an exact value or a bound, and the best lead. */
    fun record(slot: Int, value: Int, alpha: Int, beta: Int, bestLead: Int) {
        best[slot] = bestLead
        if (value > alpha) lower[slot] = maxOf(lower[slot], value)
        if (value < beta) upper[slot] = minOf(upper[slot], value)
    }

    private fun matches(slot: Int, hands: IntArray, leader: Int): Boolean =
        stamp[slot] == generation && keyLeader[slot] == leader &&
            keyHigh[slot] == high(hands) && keyLow[slot] == low(hands)

    private fun high(hands: IntArray): Long = (hands[0].toLong() shl INT_BITS) or (hands[1].toLong() and UNSIGNED)

    private fun low(hands: IntArray): Long =
        (hands[2].toLong() shl INT_BITS) or (hands[LAST].toLong() and UNSIGNED)

    private fun slotOf(hands: IntArray, leader: Int): Int {
        var h = high(hands) * MIX xor low(hands) xor leader.toLong()
        h = h xor (h ushr MIX_SHIFT)
        return (h * MIX).ushr(MIX_SHIFT).toInt() and (SIZE - 1)
    }

    private companion object {
        const val SIZE = 1 shl 18
        const val LAST = 3
        const val INT_BITS = 32
        const val UNSIGNED = 0xFFFFFFFFL
        const val MIX = -0x61c8864680b583ebL
        const val MIX_SHIFT = 31
    }
}
