package justcoinche.engine

enum class Team {
    NORTH_SOUTH,
    EAST_WEST;

    val opponent: Team get() = if (this == NORTH_SOUTH) EAST_WEST else NORTH_SOUTH
}

/**
 * The four seats, declared in playing order. Play goes anticlockwise: after
 * SOUTH comes EAST (South's right), then NORTH, then WEST. Declaring them in
 * that order turns "who plays next" into simple arithmetic on [ordinal].
 */
enum class Seat {
    SOUTH, EAST, NORTH, WEST;

    /** The seat that plays after this one. */
    val next: Seat get() = entries[(ordinal + 1) % entries.size]

    /** The seat opposite, on the same team. */
    val partner: Seat get() = entries[(ordinal + 2) % entries.size]

    val team: Team get() = Team.entries[ordinal % Team.entries.size]
}
