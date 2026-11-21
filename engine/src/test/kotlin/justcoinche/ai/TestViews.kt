package justcoinche.ai

import justcoinche.engine.Auction
import justcoinche.engine.CardSet
import justcoinche.engine.CoincheLevel
import justcoinche.engine.Contract
import justcoinche.engine.ContractType
import justcoinche.engine.PlayerView
import justcoinche.engine.Rules
import justcoinche.engine.Seat
import justcoinche.engine.Suit
import justcoinche.engine.Trick
import justcoinche.engine.legalCards
import justcoinche.engine.parseBid
import justcoinche.engine.parseCard
import justcoinche.engine.parseSeat
import kotlin.test.assertEquals

// Views of the table built from the game record's notation: "VC" is the Jack
// of hearts (P spades, C hearts, K diamonds, T clubs), "N:80C" North's 80 in hearts.

internal fun hand(text: String): CardSet = CardSet.of(text.split(" ").map { parseCard(it) })

internal fun cards(text: String) = if (text.isEmpty()) emptyList() else text.split(" ").map { parseCard(it) }

/** South's view of an auction where [calls] were made after [dealer] dealt. */
internal fun bidView(cards: String, dealer: Seat, vararg calls: String): PlayerView {
    val rules = Rules()
    var auction = Auction.start(dealer, rules)
    calls.forEach { call ->
        val (seat, bid) = call.split(":")
        auction = auction.call(parseSeat(seat), parseBid(bid))
    }
    assertEquals(Seat.SOUTH, auction.toSpeak)
    return PlayerView(
        seat = Seat.SOUTH, rules = rules, dealer = dealer, hand = hand(cards), calls = auction.calls,
        contract = null, tricks = emptyList(), currentTrick = null,
        legalBids = auction.legalBids(Seat.SOUTH), playableCards = CardSet.EMPTY,
    )
}

/** South's view in a hearts contract declared by [declarer], the current trick led by [leader]. */
internal fun playView(
    cards: String,
    declarer: Seat,
    leader: Seat,
    played: String = "",
    tricks: List<Trick> = emptyList(),
): PlayerView {
    val rules = Rules()
    val type = ContractType.SuitTrump(Suit.HEARTS)
    val trick = Trick(leader, cards(played))
    assertEquals(Seat.SOUTH, trick.nextSeat)
    val hand = hand(cards)
    return PlayerView(
        seat = Seat.SOUTH, rules = rules, dealer = Seat.WEST, hand = hand, calls = emptyList(),
        contract = Contract(declarer, type, value = 80, isCapot = false, coinche = CoincheLevel.NONE),
        tricks = tricks, currentTrick = trick, legalBids = emptyList(),
        playableCards = legalCards(hand, trick, type, rules),
    )
}

internal fun trick(leader: Seat, played: String) = Trick(leader, cards(played))
