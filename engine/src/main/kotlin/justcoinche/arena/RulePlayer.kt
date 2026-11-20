package justcoinche.arena

import justcoinche.engine.Bid
import justcoinche.engine.Card
import justcoinche.engine.CardSet
import justcoinche.engine.Contract
import justcoinche.engine.ContractType
import justcoinche.engine.Player
import justcoinche.engine.PlayerView
import justcoinche.engine.Rank
import justcoinche.engine.Suit
import justcoinche.engine.Trick

/**
 * A beginner's reflexes, first version: simple rules, no calculation, no
 * convention. It is the reference opponent of the arena, the one the expert
 * player will have to beat by a wide margin. Each rule is meant to be
 * corrected by a human player's own reflexes.
 *
 * Auction (suit contracts only; never no-trump, all-trump, capot or coinche):
 * - open in a suit holding at least 3 trumps including the Jack or the Nine,
 *   at 80 plus 10 per Ace outside trumps, 10 for Jack and Nine together and
 *   10 for 5 trumps or more;
 * - when the partner holds the highest bid, raise their suit by 10 per Ace
 *   outside it and per Jack or Nine of it;
 * - announce at most once per auction, which keeps partners from raising each
 *   other on the same cards; pass otherwise.
 *
 * Card play:
 * - lead: the attacking side draws trumps with the master trump while the
 *   opponents may still hold some; otherwise lead a master card (the Ace of a
 *   suit, or what is left on top of it); otherwise the cheapest card;
 * - partner master, and sure to stay so (last to play, or a card nobody can
 *   overtake in its suit): give them points;
 * - opponent master: win when last to play with the weakest card that does it;
 *   otherwise by trumping as low as possible, or with a master card;
 *   else play the cheapest card.
 *
 * "Cheapest" means a non-trump if possible, then the fewest points, then the
 * weakest. The player has no memory: everything comes from the [PlayerView].
 */
class RulePlayer : Player {

    override fun bid(view: PlayerView): Bid {
        val alreadyAnnounced = view.calls.any { it.seat == view.seat && it.bid is Bid.Announce }
        val highest = view.calls.lastOrNull { it.bid is Bid.Announce }
        val wanted = when {
            alreadyAnnounced -> null
            highest != null && highest.seat == view.seat.partner -> support(view.hand, highest.bid as Bid.Announce)
            else -> opening(view.hand)
        }
        // The engine decides what is legal: a bid too low, or after a coinche, becomes a pass.
        return wanted?.takeIf { it in view.legalBids } ?: Bid.Pass
    }

    override fun play(view: PlayerView): Card {
        val contract = checkNotNull(view.contract) { "No card play before a contract" }
        val trick = checkNotNull(view.currentTrick) { "No trick being played" }
        val table = Table(view, contract)
        return when {
            trick.cards.isEmpty() -> table.lead()
            trick.winner(contract.type) == view.seat.partner -> table.partnerMaster(trick)
            else -> table.opponentMaster(trick)
        }
    }

    /** The best suit to open in, or null when no suit qualifies. */
    private fun opening(hand: CardSet): Bid.Announce? =
        Suit.entries
            .mapNotNull { trump -> openingValue(hand, trump)?.let { Bid.Announce(it, ContractType.SuitTrump(trump)) } }
            // The highest value wins; on a tie, the longer trump suit.
            .maxWithOrNull(compareBy({ it.value }, { hand.ofSuit((it.type as ContractType.SuitTrump).trump).size }))

    private fun openingValue(hand: CardSet, trump: Suit): Int? {
        val trumps = hand.ofSuit(trump)
        val jack = Card.of(Rank.JACK, trump) in trumps
        val nine = Card.of(Rank.NINE, trump) in trumps
        if (trumps.size < MIN_TRUMPS || !(jack || nine)) return null
        var value = Bid.MIN_VALUE + Bid.STEP * outsideAces(hand, trump)
        if (jack && nine) value += Bid.STEP
        if (trumps.size >= LONG_TRUMPS) value += Bid.STEP
        return value
    }

    /** A raise of the partner's suit, or null with nothing to add (or a no-trump or all-trump bid). */
    private fun support(hand: CardSet, partnerBid: Bid.Announce): Bid.Announce? {
        val trump = (partnerBid.type as? ContractType.SuitTrump)?.trump ?: return null
        val honours = listOf(Rank.JACK, Rank.NINE).count { Card.of(it, trump) in hand }
        val raise = Bid.STEP * (outsideAces(hand, trump) + honours)
        return if (raise == 0) null else Bid.Announce(minOf(partnerBid.value + raise, Bid.MAX_VALUE), partnerBid.type)
    }

    private fun outsideAces(hand: CardSet, trump: Suit): Int =
        Suit.entries.count { it != trump && Card.of(Rank.ACE, it) in hand }

    private companion object {
        const val MIN_TRUMPS = 3
        const val LONG_TRUMPS = 5
        const val LAST_POSITION = 3
    }

    /** What the player can work out at the table: playable cards, and which cards are still unseen. */
    private class Table(view: PlayerView, contract: Contract) {
        private val type = contract.type
        private val trump = (type as? ContractType.SuitTrump)?.trump
        private val attacking = contract.declarer.team == view.seat.team
        private val cards = view.playableCards.toList()

        /** Cards neither in this hand nor played yet: the ones the three others may hold. */
        private val unseen: CardSet = run {
            val played = CardSet.of((view.tricks + listOfNotNull(view.currentTrick)).flatMap { it.cards })
            CardSet((view.hand union played).bits.inv()) // every card that is neither held nor played
        }

        fun lead(): Card {
            val masters = cards.filter { isMaster(it) }
            val masterTrump = masters.firstOrNull { it.suit == trump }
            val opponentsMayHoldTrumps = trump != null && !unseen.ofSuit(trump).isEmpty()
            val otherMasters = masters.filter { it.suit != trump }
            return when {
                attacking && masterTrump != null && opponentsMayHoldTrumps -> masterTrump
                otherMasters.isNotEmpty() -> otherMasters.maxBy { type.points(it) }
                else -> cheapest(cards)
            }
        }

        fun partnerMaster(trick: Trick): Card {
            val partnerCard = checkNotNull(trick.winningCard(type))
            val safe = trick.cards.size == LAST_POSITION || isMaster(partnerCard)
            // Points go to the partner, but the cards that win tricks of their own are kept.
            val gifts = cards.filter { it.suit != trump && !isMaster(it) }
            return if (safe && gifts.isNotEmpty()) gifts.maxWith(compareBy({ type.points(it) }, { -type.strength(it) }))
            else cheapest(cards)
        }

        fun opponentMaster(trick: Trick): Card {
            val me = trick.nextSeat
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

        /** Nobody can hold a higher card of its suit: it wins whenever its suit is played. */
        private fun isMaster(card: Card): Boolean =
            unseen.ofSuit(card.suit).toList().none { type.strength(it) > type.strength(card) }

        private fun cheapest(choice: List<Card>): Card =
            choice.minWith(compareBy({ it.suit == trump }, { type.points(it) }, { type.strength(it) }))

        private fun List<Card>.weakest(): Card = minBy { type.strength(it) }
    }
}
