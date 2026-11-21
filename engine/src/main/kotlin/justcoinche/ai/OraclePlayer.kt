package justcoinche.ai

import justcoinche.arena.Omniscient
import justcoinche.engine.Bid
import justcoinche.engine.Card
import justcoinche.engine.CardPlay
import justcoinche.engine.CardSet
import justcoinche.engine.Deal
import justcoinche.engine.Player
import justcoinche.engine.PlayerView
import justcoinche.engine.Seat

/**
 * "Oracle" (ia3): sees every hand and plays the card that scores best with
 * all the cards open. It cheats, so it is only a yardstick: how much better
 * could the card play be? It bids like ia2, so that a match against ia2
 * measures the card play alone.
 */
class OraclePlayer : Player, Omniscient {
    private var deal: Deal? = null

    override fun see(deal: Deal) {
        this.deal = deal
    }

    override fun bid(view: PlayerView): Bid = PetitJeu.bid(view)

    override fun play(view: PlayerView): Card {
        val cards = view.playableCards.toList()
        if (cards.size == 1) return cards.single()
        val deal = checkNotNull(deal) { "The oracle has not seen the deal" }
        val contract = checkNotNull(view.contract)
        val trick = checkNotNull(view.currentTrick)
        val played = CardSet.of((view.tricks + trick).flatMap { it.cards })
        val hands = Seat.entries.map { CardSet(deal.hand(it).bits and played.bits.inv()) }
        val scores = CardJudge(contract, view.rules)
            .judge(hands, view.tricks, trick, CardPlay.beloteTeam(deal, contract.type))
        return bestOf(scores.mapValues { it.value.toDouble() }, view)
    }
}

/**
 * The card with the best score; among equals, the one ia2 would play if it
 * is one of them, so that the play stays natural when the cards do not matter.
 */
internal fun bestOf(scores: Map<Card, Double>, view: PlayerView): Card {
    val top = scores.values.max()
    val best = scores.filterValues { it >= top - TIE }.keys
    val natural = RulesPlusPlay(view).choose()
    return if (natural in best) natural else best.first()
}

private const val TIE = 1e-9
