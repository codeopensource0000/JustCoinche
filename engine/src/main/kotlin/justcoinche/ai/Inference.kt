package justcoinche.ai

import justcoinche.engine.Auction
import justcoinche.engine.Call
import justcoinche.engine.Card
import justcoinche.engine.CardSet
import justcoinche.engine.ContractType
import justcoinche.engine.PlayerView
import justcoinche.engine.Seat
import justcoinche.engine.Trick
import justcoinche.engine.legalCards

/**
 * How well a drawn deal explains what the others did, as a weight in (0, 1]:
 * the expert players keep a drawn deal with that probability, so the deals
 * they solve follow what the table has shown ("inference" in the bridge
 * programs). Each reading is soft: a deal that contradicts it loses weight
 * but stays possible, since a player may bid or play off the convention.
 */
internal fun interface Reading {
    /** The weight of a deal, given each seat's initial hand. */
    fun weight(initialHands: List<CardSet>): Double
}

/**
 * The auction read with the petit jeu convention: for every call of the
 * other three players, would [PetitJeu] have made the same call with the
 * drawn hand? Each call it would not have made multiplies the weight by
 * [MISMATCH].
 */
internal class AuctionReading(view: PlayerView) : Reading {
    private val rules = view.rules
    private val dealer = view.dealer

    /** Each call to check, with the auction just before it. */
    private val checks: List<Pair<Auction, Call>> = buildList {
        var auction = Auction.start(view.dealer, view.rules)
        view.calls.forEach { call ->
            if (call.seat != view.seat) add(auction to call)
            auction = auction.call(call.seat, call.bid)
        }
    }

    override fun weight(initialHands: List<CardSet>): Double = checks.fold(1.0) { weight, (before, call) ->
        val seat = call.seat
        val modelView = PlayerView(
            seat = seat, rules = rules, dealer = dealer, hand = initialHands[seat.ordinal], calls = before.calls,
            contract = null, tricks = emptyList(), currentTrick = null,
            legalBids = before.legalBids(seat), playableCards = CardSet.EMPTY,
        )
        if (PetitJeu.bid(modelView) == call.bid) weight else weight * MISMATCH
    }

    private companion object {
        const val MISMATCH = 0.1
    }
}

/**
 * The direct calls of the card play, read with ia2's own card play: a player
 * whose partner is master and who cannot follow discards first in a suit
 * whose Ace he holds, or gives points when he has no Ace to show. For each
 * other player's first discard under his partner's lead, would [RulesPlusPlay],
 * holding the drawn hand at that moment, have discarded in the same suit?
 * If not, the drawn deal counts [MISMATCH].
 */
internal class CallReading(private val view: PlayerView) : Reading {
    private val contract = checkNotNull(view.contract)
    private val trump = (contract.type as? ContractType.SuitTrump)?.trump

    /** A first discard under partner's lead: who, what was played before, and the card. */
    private class Discard(val seat: Seat, val tricksBefore: List<Trick>, val trick: Trick, val card: Card) {
        /** The cards this seat had played before this trick. */
        val playedBefore: CardSet = CardSet.of(
            tricksBefore.flatMap { past -> past.cards.filterIndexed { i, _ -> seatAt(past, i) == seat } },
        )
    }

    private val discards: List<Discard> = Seat.entries.filter { it != view.seat }.mapNotNull { firstDiscard(it) }

    private fun firstDiscard(seat: Seat): Discard? {
        val tricks = view.tricks + listOfNotNull(view.currentTrick)
        tricks.forEachIndexed { number, trick ->
            trick.cards.forEachIndexed { index, card ->
                val before = Trick(trick.leader, trick.cards.take(index))
                val discard = index > 0 && card.suit != trick.ledSuit && card.suit != trump
                if (seatAt(trick, index) == seat && discard && before.winner(contract.type) == seat.partner) {
                    return Discard(seat, tricks.take(number), before, card)
                }
            }
        }
        return null
    }

    override fun weight(initialHands: List<CardSet>): Double = discards.fold(1.0) { weight, discard ->
        val hand = CardSet(initialHands[discard.seat.ordinal].bits and discard.playedBefore.bits.inv())
        val modelView = PlayerView(
            seat = discard.seat, rules = view.rules, dealer = view.dealer, hand = hand, calls = view.calls,
            contract = contract, tricks = discard.tricksBefore, currentTrick = discard.trick, legalBids = emptyList(),
            playableCards = legalCards(hand, discard.trick, contract.type, view.rules),
        )
        if (RulesPlusPlay(modelView).choose().suit == discard.card.suit) weight else weight * MISMATCH
    }

    private companion object {
        const val MISMATCH = 0.2
    }
}

private fun seatAt(trick: Trick, position: Int): Seat =
    Seat.entries[(trick.leader.ordinal + position) % Seat.entries.size]
