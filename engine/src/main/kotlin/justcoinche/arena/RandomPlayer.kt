package justcoinche.arena

import justcoinche.engine.Bid
import justcoinche.engine.Card
import justcoinche.engine.Player
import justcoinche.engine.PlayerView
import kotlin.random.Random

/**
 * The weakest possible player, a baseline: it plays a random legal card, and
 * in the auction mostly passes, otherwise bids the lowest possible value in a
 * random suit or type. Picking among all legal bids at random would mostly
 * produce capots, which says nothing useful.
 */
class RandomPlayer(private val random: Random, private val passProbability: Double = 0.8) : Player {

    override fun bid(view: PlayerView): Bid {
        val announces = view.legalBids.filterIsInstance<Bid.Announce>()
        if (announces.isEmpty() || random.nextDouble() < passProbability) return Bid.Pass
        val lowest = announces.minOf { it.value }
        return announces.filter { it.value == lowest }.random(random)
    }

    override fun play(view: PlayerView): Card = view.playableCards.toList().random(random)
}
