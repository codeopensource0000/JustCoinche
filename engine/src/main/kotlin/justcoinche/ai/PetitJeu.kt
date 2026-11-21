package justcoinche.ai

import justcoinche.engine.Bid
import justcoinche.engine.Call
import justcoinche.engine.Card
import justcoinche.engine.CardSet
import justcoinche.engine.ContractType
import justcoinche.engine.PlayerView
import justcoinche.engine.Rank
import justcoinche.engine.Suit

/**
 * The "petit jeu" bidding convention as the author plays it (docs/cadrage.md
 * §4.2), simplified to its rules of thumb. Deterministic, so that the expert
 * players can also use it as a model of how the others bid.
 *
 * - **Opening** (shows the trumps only): 100 with Jack and Nine, or a Jack
 *   fifth; 90 with four trumps holding the Jack or the Nine; 80 with three
 *   such trumps and something beside (an Ace, or the Ace or Ten of trumps).
 * - **Free answer** to partner's opening: under 100, only with the complement
 *   (the Jack +20 or the Nine +10, plus 10 per outside Ace), never on Aces
 *   alone; over 100, 10 per outside Ace.
 * - **Forced answer** (an opponent outbid partner): anything that helps counts,
 *   Aces, Jack, Nine and the belote.
 * - **No rebid**: the opener does not add his own Aces after partner's
 *   answer. Measured in the arena: adding them overbid (130 to 150 made only
 *   about half the time) and cost about 18 points per deal.
 * - **Coinche**: with sure defensive tricks against a high contract.
 * - A player announces at most twice in a deal, which ends every auction.
 */
object PetitJeu {

    fun bid(view: PlayerView): Bid {
        val legal = view.legalBids
        val wanted = coinche(view) ?: if (legal.any { it is Bid.Announce }) announce(view) else null
        return wanted?.takeIf { it in legal } ?: Bid.Pass
    }

    private fun announce(view: PlayerView): Bid? {
        val me = view.seat
        val announces = view.calls.filter { it.bid is Bid.Announce }
        val mine = announces.filter { it.seat == me }
        if (mine.size >= MAX_ANNOUNCES) return null
        val highest = announces.lastOrNull()
        val partnerCalls = announces.filter { it.seat == me.partner }
        return when {
            // Partner holds the highest bid: answer it once.
            highest != null && highest.seat == me.partner ->
                if (mine.isEmpty()) freeAnswer(view.hand, highest.announce) else null // no rebid
            // Partner bid, but an opponent outbid him: forced support.
            partnerCalls.isNotEmpty() && highest != null && highest.seat.team != me.team ->
                forcedAnswer(view.hand, partnerCalls.last().announce, highest.announce)
            else -> opening(view.hand, highest?.announce)
        }
    }

    /** Opening, or an overcall above [over]: the best trump suit at its convention value. */
    private fun opening(hand: CardSet, over: Bid.Announce?): Bid.Announce? {
        val best = Suit.entries.mapNotNull { suit -> openingValue(hand, suit)?.let { suit to it } }
            .maxWithOrNull(compareBy({ it.second }, { hand.ofSuit(it.first).size }))
        return best?.let { (suit, value) ->
            val type = ContractType.SuitTrump(suit)
            when {
                over == null -> Bid.Announce(value, type)
                // Over an opponent: compete only when the hand is worth it, counting own Aces.
                value + Bid.STEP * outsideAces(hand, suit) >= over.value + Bid.STEP &&
                    over.value + Bid.STEP <= Bid.MAX_VALUE -> Bid.Announce(over.value + Bid.STEP, type)
                else -> null
            }
        }
    }

    /** The convention value of opening [trump], or null when the suit does not qualify. */
    fun openingValue(hand: CardSet, trump: Suit): Int? {
        val trumps = hand.ofSuit(trump)
        val honours = honours(hand, trump)
        val n = trumps.size
        // A weak trump suit needs something beside it: an Ace, or the Ace or Ten of trumps.
        val support = outsideAces(hand, trump) > 0 || hasTopPlain(trumps, trump)
        return when {
            honours == 0 -> null
            honours == JACK_AND_NINE || (honours == JACK && n >= JACK_FIFTH) -> HUNDRED
            n >= FOUR -> NINETY
            n >= THREE && support -> Bid.MIN_VALUE
            else -> null
        }
    }

    /** Answer to partner's opening, nobody in between. */
    private fun freeAnswer(hand: CardSet, partner: Bid.Announce): Bid.Announce? {
        val trump = (partner.type as? ContractType.SuitTrump)?.trump ?: return null
        val aces = outsideAces(hand, trump)
        val raise = if (partner.value < HUNDRED) {
            // Under 100 partner holds the Jack or the Nine: answer only with the other one.
            val complement = honours(hand, trump)
            if (complement == 0) 0 else complement + Bid.STEP * aces
        } else {
            Bid.STEP * aces
        }
        return raised(partner, raise)
    }

    /** Partner's suit was outbid by an opponent: support it with everything that helps. */
    private fun forcedAnswer(hand: CardSet, partner: Bid.Announce, over: Bid.Announce): Bid.Announce? {
        val trump = (partner.type as? ContractType.SuitTrump)?.trump ?: return null
        val belote = if (hasBelote(hand, trump)) BELOTE else 0
        val help = honours(hand, trump) + Bid.STEP * outsideAces(hand, trump) + belote
        val target = minOf(partner.value + help, Bid.MAX_VALUE)
        return if (help > 0 && target > over.value) Bid.Announce(target, partner.type) else null
    }

    /**
     * A coinche when the opponents' contract looks beatable: high enough,
     * against sure defensive tricks (outside Aces) and a hold on their trumps.
     */
    private fun coinche(view: PlayerView): Bid? {
        val contract = view.calls.lastOrNull { it.bid is Bid.Announce }?.announce
        val trump = (contract?.type as? ContractType.SuitTrump)?.trump
        if (Bid.Coinche !in view.legalBids || contract == null || trump == null) return null
        val aces = outsideAces(view.hand, trump)
        val holds = honours(view.hand, trump)
        val strong = (aces >= 2 && holds > 0) || (holds >= JACK_AND_NINE && aces >= 1)
        return if (contract.value >= COINCHE_FROM && strong) Bid.Coinche else null
    }

    private fun raised(bid: Bid.Announce, raise: Int): Bid.Announce? =
        if (raise <= 0) null else Bid.Announce(minOf(bid.value + raise, Bid.MAX_VALUE), bid.type)

    private val Call.announce: Bid.Announce get() = bid as Bid.Announce

    private const val MAX_ANNOUNCES = 2
    private const val HUNDRED = 100
    private const val NINETY = 90
    private const val JACK_FIFTH = 5
    private const val FOUR = 4
    private const val THREE = 3
    private const val JACK = 20
    private const val JACK_AND_NINE = 30
    private const val BELOTE = 20
    private const val COINCHE_FROM = 110
}

// Hand features the convention counts, shared with the expert players.

/** 20 for the Jack of [trump], 10 for its Nine. */
internal fun honours(hand: CardSet, trump: Suit): Int {
    val jack = if (Card.of(Rank.JACK, trump) in hand) JACK_POINTS else 0
    val nine = if (Card.of(Rank.NINE, trump) in hand) Bid.STEP else 0
    return jack + nine
}

internal fun outsideAces(hand: CardSet, trump: Suit): Int =
    Suit.entries.count { it != trump && Card.of(Rank.ACE, it) in hand }

internal fun hasTopPlain(trumps: CardSet, trump: Suit): Boolean =
    Card.of(Rank.ACE, trump) in trumps || Card.of(Rank.TEN, trump) in trumps

internal fun hasBelote(hand: CardSet, trump: Suit): Boolean =
    Card.of(Rank.KING, trump) in hand && Card.of(Rank.QUEEN, trump) in hand

private const val JACK_POINTS = 20
