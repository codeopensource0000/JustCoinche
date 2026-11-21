package justcoinche.ai

import justcoinche.engine.Auction
import justcoinche.engine.AuctionResult
import justcoinche.engine.Bid
import justcoinche.engine.CardPlay
import justcoinche.engine.CardSet
import justcoinche.engine.Contract
import justcoinche.engine.ContractType
import justcoinche.engine.Deal
import justcoinche.engine.PlayerView
import justcoinche.engine.Rules
import justcoinche.engine.ScoringMode
import justcoinche.engine.Seat
import justcoinche.engine.Suit
import justcoinche.engine.Team
import justcoinche.engine.Trick
import justcoinche.engine.scoreDeal
import kotlin.random.Random

/**
 * Bidding by simulation, as GIB does it ("Borel simulation"): the petit jeu
 * convention proposes a few calls; deals that agree with the auction so far
 * are drawn ([AuctionReading]); on each one, every candidate call is tried,
 * the rest of the auction is bid by the convention for all four players, and
 * the contract reached is played with all the cards open. The call with the
 * best mean score wins; the convention's own call wins ties. A coinche or a
 * surcoinche is weighed the same way against a pass.
 *
 * Its limits: the others are assumed to bid by the convention, and every
 * contract is judged as if played open-handed, which flatters the declarer.
 * After [MIN_DEALS] deals, no new deal is judged once [maxMillis] have passed.
 */
internal class SimulatedBidding(
    private val samples: Int,
    private val seed: Long,
    private val maxMillis: Long = Long.MAX_VALUE / NANOS_PER_MILLI,
) {
    // Solvers are large: kept between decisions, one per kind of contract, with a smaller table.
    private val solvers = HashMap<Triple<ContractType, Team, Objective>, Solver>()

    fun bid(view: PlayerView): Bid {
        val convention = PetitJeu.bid(view)
        val candidates = candidates(view, convention)
        if (candidates.size < 2) return convention
        val random = Random(seed xor auctionKey(view))
        val start = System.nanoTime()
        val deals = drawDeals(view, random)
        val totals = candidates.associateWith { 0.0 }.toMutableMap()
        // A few deals at least, then as many as drawn while time allows (the sequence stops lazily).
        deals.asSequence().withIndex().takeWhile { (index, _) ->
            index < MIN_DEALS || System.nanoTime() - start < maxMillis * NANOS_PER_MILLI
        }.forEach { (_, hands) ->
            // Different calls often lead to the same contract: each one is judged once per deal.
            val judged = HashMap<Contract?, Int>()
            candidates.forEach { bid ->
                val contract = finalContract(view, hands, bid)
                totals[bid] = totals.getValue(bid) + judged.getOrPut(contract) { netScore(view, hands, contract) }
            }
        }
        val best = totals.maxBy { it.value }
        return if (totals.getValue(convention) >= best.value - TIE) convention else best.key
    }

    /** The convention's call, and the few others worth comparing with it. */
    private fun candidates(view: PlayerView, convention: Bid): List<Bid> {
        val legal = view.legalBids
        val others = buildList {
            add(Bid.Pass)
            add(Bid.Coinche)
            add(Bid.Surcoinche)
            if (convention is Bid.Announce) {
                add(Bid.Announce(minOf(convention.value + Bid.STEP, Bid.MAX_VALUE), convention.type))
                add(Bid.Announce(maxOf(convention.value - Bid.STEP, Bid.MIN_VALUE), convention.type))
            } else {
                // The convention passes: also try the cheapest bid in the longest suit held with its Jack or Nine.
                cheapestInLongSuit(view)?.let { add(it) }
            }
        }
        return (listOf(convention) + others.filter { it in legal }).distinct()
    }

    private fun cheapestInLongSuit(view: PlayerView): Bid? {
        val suit = Suit.entries.filter { honours(view.hand, it) > 0 }
            .maxByOrNull { view.hand.ofSuit(it).size } ?: return null
        return view.legalBids.filterIsInstance<Bid.Announce>()
            .filter { it.type == ContractType.SuitTrump(suit) }.minByOrNull { it.value }
    }

    /**
     * Initial hands of the four players that agree with the auction: each
     * drawn deal is kept with the probability [AuctionReading] gives it.
     */
    private fun drawDeals(view: PlayerView, random: Random): List<List<CardSet>> {
        val reading = AuctionReading(view)
        val unseen = CardSet(view.hand.bits.inv()).toList()
        val others = Seat.entries.filter { it != view.seat }
        val kept = mutableListOf<List<CardSet>>()
        var tries = 0
        while (kept.size < samples && tries < samples * MAX_TRIES_PER_DEAL) {
            val shuffled = unseen.shuffled(random).chunked(Deal.HAND_SIZE)
            val hands = Seat.entries.map { seat ->
                if (seat == view.seat) view.hand else CardSet.of(shuffled[others.indexOf(seat)])
            }
            if (random.nextDouble() < reading.weight(hands)) kept += hands
            tries++
        }
        return kept
    }

    /** The contract reached if [bid] is called now and everybody then bids by the convention; null on a redeal. */
    private fun finalContract(view: PlayerView, hands: List<CardSet>, bid: Bid): Contract? {
        var auction = Auction.start(view.dealer, view.rules)
        view.calls.forEach { auction = auction.call(it.seat, it.bid) }
        auction = auction.call(view.seat, bid)
        while (!auction.isOver) {
            val seat = auction.toSpeak
            val modelView = PlayerView(
                seat = seat, rules = view.rules, dealer = view.dealer, hand = hands[seat.ordinal],
                calls = auction.calls, contract = null, tricks = emptyList(), currentTrick = null,
                legalBids = auction.legalBids(seat), playableCards = CardSet.EMPTY,
            )
            auction = auction.call(seat, PetitJeu.bid(modelView))
        }
        return (auction.result as? AuctionResult.Contracted)?.contract
    }

    /** The net score of this player's team if [contract] is played open-handed on [hands]; 0 on a redeal. */
    private fun netScore(view: PlayerView, hands: List<CardSet>, contract: Contract?): Int {
        if (contract == null) return 0
        val attack = contract.declarer.team
        val beloteTeam = CardPlay.beloteTeam(Deal(hands), contract.type)
        val lead = Trick(view.dealer.next)
        val outcome = when {
            contract.isCapot -> {
                val solver = solver(contract.type, attack, Objective.TRICKS, view.rules)
                solver.load(hands, lead, Deal.HAND_SIZE)
                outcome(attack, ALL_POINTS, solver.reaches(Deal.HAND_SIZE), beloteTeam)
            }
            view.rules.scoringMode == ScoringMode.CONTRACT_POINTS -> {
                // Only made or failed is scored: a null-window search is enough.
                val solver = solver(contract.type, attack, Objective.POINTS, view.rules)
                solver.load(hands, lead, Deal.HAND_SIZE)
                val belote = if (beloteTeam == attack) CardPlay.BELOTE_BONUS else 0
                val made = solver.reaches(contract.value - belote)
                outcome(attack, if (made) ALL_POINTS else 0, capot = false, beloteTeam = beloteTeam)
            }
            else -> {
                val solver = solver(contract.type, attack, Objective.POINTS, view.rules)
                solver.load(hands, lead, Deal.HAND_SIZE)
                outcome(attack, solver.value(), capot = false, beloteTeam = beloteTeam)
            }
        }
        val score = scoreDeal(contract, outcome, view.rules)
        return score[view.seat.team] - score[view.seat.team.opponent]
    }

    /** The rules do not change during a game: the solvers are kept per kind of contract only. */
    private fun solver(type: ContractType, attack: Team, objective: Objective, rules: Rules): Solver =
        solvers.getOrPut(Triple(type, attack, objective)) {
            Solver(type, rules, attack, objective, tableBits = SMALL_TABLE)
        }

    /** A number that changes with the hand and the auction, to seed this decision's draws. */
    private fun auctionKey(view: PlayerView): Long {
        var key = view.hand.bits.toLong() * MIX + view.seat.ordinal
        view.calls.forEach { key = key * MIX + it.bid.hashCode() }
        return key
    }

    private companion object {
        const val MAX_TRIES_PER_DEAL = 200
        const val SMALL_TABLE = 16
        const val MIX = 1_000_003L
        const val TIE = 1e-9
        const val MIN_DEALS = 4
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
