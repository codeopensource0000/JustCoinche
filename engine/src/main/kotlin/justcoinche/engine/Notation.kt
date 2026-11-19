package justcoinche.engine

// Text notation for the game record, in French and in plain letters:
// seats S E N O, ranks 7 8 9 10 V D R A, suits P (pique), C (coeur),
// K (carreau, so that it never clashes with coeur) and T (trefle).

private val SEAT_LETTERS = mapOf(Seat.SOUTH to "S", Seat.EAST to "E", Seat.NORTH to "N", Seat.WEST to "O")
private val SUIT_LETTERS = mapOf(Suit.SPADES to "P", Suit.HEARTS to "C", Suit.DIAMONDS to "K", Suit.CLUBS to "T")
private val RANK_NAMES = mapOf(
    Rank.SEVEN to "7", Rank.EIGHT to "8", Rank.NINE to "9", Rank.TEN to "10",
    Rank.JACK to "V", Rank.QUEEN to "D", Rank.KING to "R", Rank.ACE to "A",
)
private const val NO_TRUMP = "SA"
private const val ALL_TRUMP = "TA"
private const val PASS = "-"
private const val COINCHE = "coinche"
private const val SURCOINCHE = "surcoinche"
private const val CAPOT = "capot-"
private const val CAPOT_BELOTE = "capotbelote-"

internal val Seat.letter: String get() = SEAT_LETTERS.getValue(this)

internal fun parseSeat(text: String): Seat = SEAT_LETTERS.inverse(text, "seat")

/** For example "10K" for the ten of diamonds, "VC" for the jack of hearts. */
internal val Card.notation: String get() = RANK_NAMES.getValue(rank) + SUIT_LETTERS.getValue(suit)

internal fun parseCard(text: String): Card {
    require(text.length >= 2) { "Not a card: '$text'" }
    return Card.of(RANK_NAMES.inverse(text.dropLast(1), "rank"), SUIT_LETTERS.inverse(text.takeLast(1), "suit"))
}

internal val ContractType.notation: String
    get() = when (this) {
        is ContractType.SuitTrump -> SUIT_LETTERS.getValue(trump)
        ContractType.NoTrump -> NO_TRUMP
        ContractType.AllTrump -> ALL_TRUMP
    }

internal fun parseContractType(text: String): ContractType = when (text) {
    NO_TRUMP -> ContractType.NoTrump
    ALL_TRUMP -> ContractType.AllTrump
    else -> ContractType.SuitTrump(SUIT_LETTERS.inverse(text, "contract type"))
}

/** "-" to pass, "80T" for 80 clubs, "capot-SA", "capotbelote-C", "coinche", "surcoinche". */
internal val Bid.notation: String
    get() = when (this) {
        Bid.Pass -> PASS
        is Bid.Announce -> "$value${type.notation}"
        is Bid.Capot -> CAPOT + type.notation
        is Bid.CapotBelote -> CAPOT_BELOTE + type.notation
        Bid.Coinche -> COINCHE
        Bid.Surcoinche -> SURCOINCHE
    }

internal fun parseBid(text: String): Bid = when {
    text == PASS -> Bid.Pass
    text == COINCHE -> Bid.Coinche
    text == SURCOINCHE -> Bid.Surcoinche
    text.startsWith(CAPOT_BELOTE) -> Bid.CapotBelote(parseContractType(text.removePrefix(CAPOT_BELOTE)))
    text.startsWith(CAPOT) -> Bid.Capot(parseContractType(text.removePrefix(CAPOT)))
    else -> {
        val value = text.takeWhile { it.isDigit() }
        require(value.isNotEmpty()) { "Not a bid: '$text'" }
        Bid.Announce(value.toInt(), parseContractType(text.drop(value.length)))
    }
}

/** The key whose notation is [text], or an error naming what was expected. */
private fun <K> Map<K, String>.inverse(text: String, what: String): K =
    entries.firstOrNull { it.value == text }?.key ?: throw IllegalArgumentException("Unknown $what: '$text'")
