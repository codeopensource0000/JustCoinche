package justcoinche.engine

/**
 * The card play of one deal, from the opening lead to the eighth trick.
 *
 * Immutable like [Trick]: [play] returns the next state. It accepts legal cards
 * only, sets completed tricks aside, gives the lead to each trick's winner, and
 * counts each team's points, last-trick bonus and belote included.
 */
class CardPlay private constructor(
    val contract: ContractType,
    val rules: Rules,
    private val hands: List<CardSet>,
    /** The trick being played. Once the deal is over, an empty trick led by the last winner. */
    val currentTrick: Trick,
    /** Completed tricks, in playing order: together, the full record of the card play. */
    val tricks: List<Trick>,
    /** The team holding king and queen of trumps, if any. */
    val beloteTeam: Team?,
) {

    val isOver: Boolean get() = tricks.size == Deal.HAND_SIZE

    /** The seat whose turn it is. */
    val toPlay: Seat get() = currentTrick.nextSeat

    fun hand(seat: Seat): CardSet = hands[seat.ordinal]

    /** The cards [toPlay] may play now. */
    fun playableCards(): CardSet = legalCards(hand(toPlay), currentTrick, contract, rules)

    fun play(card: Card): CardPlay {
        check(!isOver) { "The deal is over" }
        require(card in playableCards()) { "$card cannot be played by $toPlay here" }

        val seat = toPlay
        val newHands = hands.mapIndexed { index, hand -> if (index == seat.ordinal) hand - card else hand }
        val trick = currentTrick.play(card)
        if (!trick.isComplete) return CardPlay(contract, rules, newHands, trick, tricks, beloteTeam)

        val nextLeader = winnerOf(trick)
        return CardPlay(contract, rules, newHands, Trick(nextLeader), tricks + trick, beloteTeam)
    }

    /**
     * Points made by [team]: the card points of the tricks it won, the 10 for
     * the last trick once the deal is over, and the belote if it holds it.
     * Over a whole deal the two teams' points total 162, or 182 with a belote.
     */
    fun points(team: Team): Int {
        val cardPoints = tricks.filter { winnerOf(it).team == team }.sumOf { it.points(contract) }
        val lastTrick = if (isOver && winnerOf(tricks.last()).team == team) LAST_TRICK_BONUS else 0
        val belote = if (beloteTeam == team) BELOTE_BONUS else 0
        return cardPoints + lastTrick + belote
    }

    private fun winnerOf(trick: Trick): Seat = checkNotNull(trick.winner(contract))

    companion object {
        const val LAST_TRICK_BONUS = 10
        const val BELOTE_BONUS = 20

        fun start(deal: Deal, contract: ContractType, rules: Rules, leader: Seat): CardPlay = CardPlay(
            contract = contract,
            rules = rules,
            hands = Seat.entries.map { deal.hand(it) },
            currentTrick = Trick(leader),
            tricks = emptyList(),
            beloteTeam = beloteTeam(deal, contract),
        )

        /**
         * The belote exists only in a suit contract: there is no trump in
         * no-trump, and it is disabled in all-trump (docs/regles.md §6).
         */
        private fun beloteTeam(deal: Deal, contract: ContractType): Team? {
            if (contract !is ContractType.SuitTrump) return null
            val kingAndQueen = CardSet.of(
                listOf(Card.of(Rank.KING, contract.trump), Card.of(Rank.QUEEN, contract.trump)),
            )
            return Seat.entries.firstOrNull { (deal.hand(it) intersect kingAndQueen) == kingAndQueen }?.team
        }
    }
}
