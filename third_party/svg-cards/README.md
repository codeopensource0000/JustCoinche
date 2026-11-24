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

- `app/src/main/res/drawable/card_<suit>_<rank>.xml`: the 7 to 10 and the Aces, as vectors.
- `app/src/main/res/drawable-nodpi/card_<suit>_<jack|queen|king>.png`: the 12 court cards, as
  images 720 pixels high. Their figures are drawn with paths of up to 75 000 characters, and the
  compiled resources of an app cannot hold a string over 32 767 characters (the build replaces
  it with "STRING_TOO_LARGE" without failing, and the app crashes when it draws the card). Such a
  path can only be cut where no hole is lost, and the outline of each figure holds hundreds of
  holes. The images are rendered from the vector drawables with Android Studio's preview renderer.
- `app/src/main/res/drawable/ic_launcher_foreground.xml` and `ic_launcher_monochrome.xml`: the
  app icon uses the deck's spade and heart.

Changes made to the drawings (2026-11-22, 2026-11-24):

1. Each card was moved to the origin of its own file, its outline included.
2. The rank letters and numbers in the corners were removed: they are text in the source, and
   a vector drawable cannot hold text. The app draws them itself at the same places and in the
   same proportions (`app/src/main/kotlin/justcoinche/app/DeckArt.kt`), with the French
   letters V, D and R, which the source itself uses for French readers.
3. Paths too long for the app were split into several paths with the same look (the Ace of
   clubs; rendered pixel for pixel the same as before), or the card was rendered to an image
   (the court cards).

Nothing else was changed: figures, pips, colours and outlines are the source's.

## Doing it again

```sh
# The converters use Android Studio's own code (com.android.tools:sdk-common), found in the
# Gradle cache after any app build:
CP="<sdk-common>:<common>:<guava>:<kotlin-stdlib jars>"

# 1. Cut the 32 cards out of the sheet (one SVG per card, corner text removed).
python3 -I scripts/cards/split_cards.py third_party/svg-cards/Svg-cards-2.0.svg /tmp/cards-svg

# 2. Convert them to vector drawables (Svg2Vector), with a PNG preview of each card.
java -Djava.awt.headless=true -cp "$CP" scripts/cards/SvgToVector.java /tmp/cards-svg /tmp/cards-xml /tmp/cards-png

# 3. Split the paths that are too long; the files that cannot be split are named.
python3 -I scripts/cards/split_long_paths.py /tmp/cards-xml/card_*.xml

# 4. Render the court cards to images, and keep the other cards as vectors.
java -Djava.awt.headless=true -cp "$CP" scripts/cards/VectorToPng.java 720 \
    app/src/main/res/drawable-nodpi /tmp/cards-xml/card_*_jack.xml /tmp/cards-xml/card_*_queen.xml \
    /tmp/cards-xml/card_*_king.xml
rm /tmp/cards-xml/card_*_jack.xml /tmp/cards-xml/card_*_queen.xml /tmp/cards-xml/card_*_king.xml
cp /tmp/cards-xml/card_*.xml app/src/main/res/drawable/
```
