package justcoinche.ai

import justcoinche.engine.Bid
import justcoinche.engine.Card
import justcoinche.engine.CardSet
import justcoinche.engine.Contract
import justcoinche.engine.ContractType
import justcoinche.engine.Player
import justcoinche.engine.PlayerView
import justcoinche.engine.Rank
import justcoinche.engine.Seat
import justcoinche.engine.Suit
import justcoinche.engine.Trick

/**
 * "Règles+" (ia2): the petit jeu convention in the auction, and in the card
 * play the beginner's reflexes plus what a club player adds without
 * calculating: the direct call (discarding a small card of the suit whose Ace
 * one holds when partner is master), following partner's call when leading,
 * and drawing trumps through partner when he is the declarer.
 *
 * Deterministic: the expert players use it as their model of the others.
 */
class RulesPlusPlayer : Player {

    override fun bid(view: PlayerView): Bid = PetitJeu.bid(view)

    override fun play(view: PlayerView): Card = RulesPlusPlay(view).choose()
}

/** One card play decision of [RulesPlusPlayer], with what it works out from the table. */
internal class RulesPlusPlay(private val view: PlayerView) {
    private val contract: Contract = checkNotNull(view.contract) { "No card play before a contract" }
    private val type = contract.type
    private val trump: Suit? = (type as? ContractType.SuitTrump)?.trump
    private val trick: Trick = checkNotNull(view.currentTrick) { "No trick being played" }
    private val me = view.seat
    private val attacking = contract.declarer.team == me.team
    private val cards = view.playableCards.toList()

    /** Cards neither in this hand nor played yet: the ones the three others may hold. */
    private val unseen: CardSet = run {
        val played = CardSet.of((view.tricks + trick).flatMap { it.cards })
        CardSet((view.hand union played).bits.inv())
    }

    fun choose(): Card = when {
        cards.size == 1 -> cards.single()
        trick.cards.isEmpty() -> lead()
        trick.winner(type) == me.partner -> partnerMaster()
        else -> opponentMaster()
    }

    private fun lead(): Card {
        val masters = cards.filter { isMaster(it) }
        val trumps = cards.filter { it.suit == trump }
        val opponentsMayHoldTrumps = trump != null && !unseen.ofSuit(trump).isEmpty()
        val called = partnerCall()?.let { suit -> cards.filter { it.suit == suit } }.orEmpty()
        return when {
            attacking && opponentsMayHoldTrumps && masters.any { it.suit == trump } ->
                masters.first { it.suit == trump }
            // Partner declared: lead a small trump so that he draws them with his honours.
            attacking && opponentsMayHoldTrumps && contract.declarer == me.partner && trumps.isNotEmpty() ->
                trumps.minBy { type.strength(it) }
            masters.any { it.suit != trump } -> masters.filter { it.suit != trump }.maxBy { type.points(it) }
            // Partner called this suit: he holds its Ace.
            called.isNotEmpty() -> called.minBy { type.strength(it) }
            else -> cheapest(cards)
        }
    }

    private fun partnerMaster(): Card {
        val partnerCard = checkNotNull(trick.winningCard(type))
        val last = trick.cards.size == LAST_POSITION
        val safe = last || isMaster(partnerCard)
        val ledSuit = trick.ledSuit
        // Partner master and the led suit missing: free to discard, so this is the time to call.
        val discarding = view.hand.ofSuit(checkNotNull(ledSuit)).isEmpty()
        return when {
            discarding && callCard() != null -> checkNotNull(callCard())
            safe -> gift() ?: cheapest(cards)
            else -> cheapest(cards)
        }
    }

    private fun opponentMaster(): Card {
        val winners = cards.filter { trick.play(it).winner(type) == me }
        val masterWinners = winners.filter { isMaster(it) }
        val trumpWinners = winners.filter { it.suit == trump && trick.ledSuit != trump }
        return when {
            winners.isEmpty() -> cheapest(cards)
            trick.cards.size == LAST_POSITION -> winners.weakest()
            trumpWinners.isNotEmpty() -> trumpWinners.weakest()
            masterWinners.isNotEmpty() -> masterWinners.weakest()
            else -> cheapest(cards)
        }
    }

    /** The direct call: a small card of a plain suit whose Ace this hand holds, if any. */
    private fun callCard(): Card? {
        if (callAlreadyMade()) return null
        return Suit.entries.filter { it != trump && Card.of(Rank.ACE, it) in view.hand }
            .flatMap { suit -> cards.filter { it.suit == suit && it.rank != Rank.ACE } }
            .minWithOrNull(compareBy({ type.points(it) }, { type.strength(it) }))
    }

    /** Points for partner: the richest plain card that would not win a trick of its own. */
    private fun gift(): Card? =
        cards.filter { it.suit != trump && !isMaster(it) }
            .maxWithOrNull(compareBy({ type.points(it) }, { -type.strength(it) }))

    /** The suit partner called: his first discard while this player was master, as the convention says. */
    private fun partnerCall(): Suit? = firstDiscardWhileMaster(by = me.partner, master = me)

    private fun callAlreadyMade(): Boolean = firstDiscardWhileMaster(by = me, master = me.partner) != null

    private fun firstDiscardWhileMaster(by: Seat, master: Seat): Suit? {
        (view.tricks + trick).forEach { past ->
            past.cards.forEachIndexed { index, card ->
                val seat = Seat.entries[(past.leader.ordinal + index) % Seat.entries.size]
                if (seat == by && index > 0) {
                    val before = Trick(past.leader, past.cards.take(index))
                    val discard = card.suit != past.ledSuit && card.suit != trump
                    if (discard && before.winner(type) == master) return card.suit
                }
            }
        }
        return null
    }

    /** Nobody can hold a higher card of its suit: it wins whenever its suit is played. */
    private fun isMaster(card: Card): Boolean =
        unseen.ofSuit(card.suit).toList().none { type.strength(it) > type.strength(card) }

    private fun cheapest(choice: List<Card>): Card =
        choice.minWith(compareBy({ it.suit == trump }, { type.points(it) }, { type.strength(it) }))

    private fun List<Card>.weakest(): Card = minBy { type.strength(it) }

    private companion object {
        const val LAST_POSITION = 3
    }
}
