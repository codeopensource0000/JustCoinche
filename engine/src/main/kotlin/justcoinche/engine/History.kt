package justcoinche.engine

/**
 * The game record: one human-readable line of text per deal, enough to replay
 * the deal exactly (docs/cadrage.md §6 bis). Version 1 reads:
 *
 * ```
 * v1 | donneur O | regles pts-contrat,sous-couper,coinche-tour,sa,ta,belote-perdue
 *    | donne ffffaaaa55550000 | encheres S:80T E:- N:- O:-
 *    | jeu S:7T 7K 7C 7P / S:8T 8K 8C 8P / ... | score NS 80 EO 0
 * ```
 *
 * (on a single line). The hands are not written out: the 16 hex digits are the
 * deal's 64-bit code, and once the deal is played each trick shows every card
 * with its player anyway. Each trick starts with its leader. A redeal writes
 * "-" for the play and the score. Notation: see Notation.kt.
 *
 * Only facts are stored. Reading a line replays it through the engine, so an
 * illegal bid or card, or a score that does not match, is rejected.
 */
object History {

    const val VERSION = "v1"
    private const val SEPARATOR = " | "
    private const val TRICK_SEPARATOR = " / "
    private const val NOTHING = "-"
    private const val RAW = " brut"
    private const val FIELD_COUNT = 7

    /** [rounded] is false for the final deal of a game: its score is then written raw, marked "brut". */
    fun write(round: Round, rounded: Boolean = true): String {
        require(round.phase == Round.Phase.OVER || round.phase == Round.Phase.REDEAL) { "The deal is not finished" }
        val tricks = round.cardPlay?.tricks?.joinToString(TRICK_SEPARATOR) { trick ->
            "${trick.leader.letter}:" + trick.cards.joinToString(" ") { it.notation }
        }
        val score = if (round.phase == Round.Phase.OVER) {
            round.score(rounded).let { "NS ${it.northSouth} EO ${it.eastWest}" + if (rounded) "" else RAW }
        } else {
            NOTHING
        }
        return listOf(
            VERSION,
            "donneur ${round.dealer.letter}",
            "regles ${rulesNotation(round.rules)}",
            "donne ${round.deal.encode().toULong().toString(HEX).padStart(HEX_DIGITS, '0')}",
            "encheres " + round.auction.calls.joinToString(" ") { "${it.seat.letter}:${it.bid.notation}" },
            "jeu ${tricks ?: NOTHING}",
            "score $score",
        ).joinToString(SEPARATOR)
    }

    /** Replays a recorded deal. Throws [IllegalArgumentException] if the line is not a valid record. */
    fun read(line: String): Round {
        val fields = line.split(SEPARATOR)
        require(fields.size == FIELD_COUNT && fields.first() == VERSION) { "Not a $VERSION history line: $line" }
        // Every field after the version is "name value": read them by name, not by position.
        val values = fields.drop(1).associate { it.substringBefore(' ') to it.substringAfter(' ') }
        fun field(name: String): String = values[name] ?: throw IllegalArgumentException("Missing field '$name'")

        val dealer = parseSeat(field("donneur"))
        val deal = Deal.decode(field("donne").toULong(HEX).toLong())
        var round = Round.start(dealer, deal, parseRules(field("regles")))
        field("encheres").split(" ").forEach { call ->
            val (seat, bid) = call.split(":")
            round = round.call(parseSeat(seat), parseBid(bid))
        }
        round = replayTricks(round, field("jeu"))

        val recorded = field("score")
        val rounded = !recorded.endsWith(RAW)
        require(write(round, rounded).endsWith("score $recorded")) {
            "The recorded score '$recorded' does not match the replay"
        }
        return round
    }

    private fun replayTricks(start: Round, text: String): Round {
        if (text == NOTHING) return start
        var round = start
        text.split(TRICK_SEPARATOR).forEach { trick ->
            val (leader, cards) = trick.split(":")
            require(parseSeat(leader) == round.cardPlay?.toPlay) { "Trick '$trick' is not led by the right player" }
            cards.split(" ").forEach { round = round.play(parseCard(it)) }
        }
        return round
    }

    private fun rulesNotation(rules: Rules): String = listOf(
        if (rules.scoringMode == ScoringMode.CONTRACT_POINTS) "pts-contrat" else "contrat+pts",
        if (rules.mustUnderTrump) "sous-couper" else "pisser-permis",
        if (rules.coincheTiming == CoincheTiming.IN_TURN) "coinche-tour" else "coinche-volee",
        if (rules.allowNoTrump) "sa" else "sans-sa",
        if (rules.allowAllTrump) "ta" else "sans-ta",
        if (rules.beloteKeptOnFailure) "belote-gardee" else "belote-perdue",
        "$TARGET${rules.targetScore}",
    ).joinToString(",")

    private fun parseRules(text: String): Rules {
        val tokens = text.split(",").toSet()
        fun flag(yes: String, no: String): Boolean = when {
            yes in tokens -> true
            no in tokens -> false
            else -> throw IllegalArgumentException("Rules '$text' say neither '$yes' nor '$no'")
        }
        return Rules(
            scoringMode = if (flag("pts-contrat", "contrat+pts")) {
                ScoringMode.CONTRACT_POINTS
            } else {
                ScoringMode.CONTRACT_AND_POINTS_MADE
            },
            mustUnderTrump = flag("sous-couper", "pisser-permis"),
            coincheTiming = if (flag("coinche-tour", "coinche-volee")) {
                CoincheTiming.IN_TURN
            } else {
                CoincheTiming.ANY_TIME
            },
            allowNoTrump = flag("sa", "sans-sa"),
            allowAllTrump = flag("ta", "sans-ta"),
            beloteKeptOnFailure = flag("belote-gardee", "belote-perdue"),
            targetScore = tokens.firstOrNull { it.startsWith(TARGET) }?.removePrefix(TARGET)?.toIntOrNull()
                ?: throw IllegalArgumentException("Rules '$text' give no target score"),
        )
    }

    private const val TARGET = "cible-"
    private const val HEX = 16
    private const val HEX_DIGITS = 16
}
