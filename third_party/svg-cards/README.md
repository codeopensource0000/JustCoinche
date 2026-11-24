# SVG-cards 2.0.1 — the card faces

The card faces of JustCoinche come from **SVG-cards 2.0.1**, a complete deck drawn in SVG by
**David Bellot** (copyright 2005), with French court figures of the Paris pattern.

- Source: <https://commons.wikimedia.org/wiki/File:Svg-cards-2.0.svg>
  (file `Svg-cards-2.0.svg` here, unchanged, SHA-256
  `d2e325eb1233094af910468eca32ba2c44bd76a26f300db41fee6f6028bff760`).
- License: GNU Lesser General Public License. The Wikimedia Commons page gives
  "version 2.1 of the License, or (at your option) any later version"; the file's own header
  says "version 2 of the License, or (at your option) any later version". The LGPL 2.1 text
  is in `COPYING`.

The rest of JustCoinche is under the Apache License 2.0 (see `LICENSE` at the root). The card
drawings, and the files derived from them listed below, stay under the LGPL.

## What JustCoinche made from it

The 32 cards of a coinche deck (7 to Ace in each suit) were cut out of the sheet and converted
to Android vector drawables:

- `app/src/main/res/drawable/card_<suit>_<rank>.xml`: one card each.
- `app/src/main/res/drawable/ic_launcher_foreground.xml`: the app icon uses the deck's spade
  and heart.

Changes made to the drawings (2026-11-22):

1. Each card was moved to the origin of its own file, its outline included.
2. The rank letters and numbers in the corners were removed: they are text in the source, and
   a vector drawable cannot hold text. The app draws them itself at the same places and in the
   same proportions (`app/src/main/kotlin/justcoinche/app/DeckArt.kt`), with the French
   letters V, D and R, which the source itself uses for French readers.

Nothing else was changed: figures, pips, colours and outlines are the source's.

## Doing it again

```sh
# 1. Cut the 32 cards out of the sheet (one SVG per card, corner text removed).
python3 -I scripts/cards/split_cards.py third_party/svg-cards/Svg-cards-2.0.svg /tmp/cards-svg

# 2. Convert them with Android Studio's own converter (Svg2Vector, in com.android.tools:sdk-common,
#    found in the Gradle cache after any app build), with a PNG preview of each card.
java -Djava.awt.headless=true -cp "<sdk-common>:<common>:<guava>:<kotlin-stdlib jars>" \
    scripts/cards/SvgToVector.java /tmp/cards-svg app/src/main/res/drawable /tmp/cards-png
```
