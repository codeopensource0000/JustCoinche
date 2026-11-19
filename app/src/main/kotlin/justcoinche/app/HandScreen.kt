package justcoinche.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import justcoinche.engine.Card
import justcoinche.engine.CardSet
import justcoinche.engine.ContractType
import justcoinche.engine.Deal
import justcoinche.engine.Seat
import java.security.SecureRandom

/**
 * First screen: deals a hand to the player, who sits South, and shows it
 * sorted by suit. A first brick of the interface, before the playable table.
 */
@Composable
fun HandScreen() {
    // The deal survives recompositions of this screen; a new one replaces it on demand.
    var deal by remember { mutableStateOf(newDeal()) }

    Scaffold { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text("Ta main", style = MaterialTheme.typography.headlineSmall)
            HandRow(deal.hand(Seat.SOUTH))
            Button(onClick = { deal = newDeal() }) {
                Text("Nouvelle donne")
            }
        }
    }
}

/** The eight cards side by side, sharing the screen's width equally. */
@Composable
private fun HandRow(hand: CardSet) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        sortedForDisplay(hand).forEach { card ->
            CardChip(card, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CardChip(card: Card, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(
            text = card.label,
            color = if (card.isRed) RED_SUIT else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 20.dp),
        )
    }
}

/** By suit, then from the highest card to the lowest in plain order (A, 10, R, D, V, 9, 8, 7). */
internal fun sortedForDisplay(hand: CardSet): List<Card> =
    hand.toList().sortedWith(
        compareBy<Card> { it.suit.ordinal }.thenByDescending { ContractType.NoTrump.strength(it) },
    )

/** A fresh deal from an unpredictable 64-bit seed. */
private fun newDeal(): Deal = Deal.random(SecureRandom().nextLong())

private val RED_SUIT = Color(0xFFC62828)
