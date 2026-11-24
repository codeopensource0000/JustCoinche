#!/usr/bin/env python3
"""Cut the 32 cards of a coinche deck out of SVG-cards 2.0.1 (David Bellot, LGPL).

The source file draws the whole deck on one sheet. For each card this writes
one SVG holding only that card, moved to the origin, ready to be converted to
an Android vector drawable (see SvgToVector.java and README.md beside it).

One change is made to the artwork: the rank letters and numbers in the corners
are text in the source, which vector drawables cannot hold. They are removed
here and drawn by the app instead, with the French letters (V, D, R) that the
source itself uses for French readers.

Usage: python3 -I split_cards.py <Svg-cards-2.0.svg> <output folder>
"""
import copy
import os
import sys
import xml.etree.ElementTree as ET

SVG = "http://www.w3.org/2000/svg"
XLINK = "http://www.w3.org/1999/xlink"
HREF = "{%s}href" % XLINK

# The card outline in the source ("#base"), including half of its 2.5 wide stroke.
STROKE = 1.25
CARD_TOP = -235.27 - STROKE
CARD_WIDTH = 166.575 + 2 * STROKE
CARD_HEIGHT = 242.14 + 2 * STROKE

# Source ids of the corner indices, all text.
INDICES = {"#n_%d" % n for n in range(1, 11)} | {"#ace", "#jack", "#queen", "#king"}

SUITS = {"club": "clubs", "diamond": "diamonds", "heart": "hearts", "spade": "spades"}
RANKS = {"7": "7", "8": "8", "9": "9", "10": "10", "jack": "jack", "queen": "queen", "king": "king", "1": "ace"}


def source_id(suit, rank):
    return "%s_%s" % (rank, suit) if rank in ("jack", "queen", "king") else "%s_%s" % (suit, rank)


def without_text(element):
    """Removes every text (and the language switches around them) below [element]."""
    for parent in element.iter():
        for child in list(parent):
            if child.tag in ("{%s}text" % SVG, "{%s}switch" % SVG):
                parent.remove(child)
    return element


def main(source, out):
    ET.register_namespace("", SVG)
    ET.register_namespace("xlink", XLINK)
    root = ET.parse(source).getroot()
    defs = without_text(copy.deepcopy(root.find("{%s}defs" % SVG)))
    cards = {g.get("id"): g for g in root.iter("{%s}g" % SVG) if g.get("id")}
    os.makedirs(out, exist_ok=True)
    for suit, suit_name in SUITS.items():
        for rank, rank_name in RANKS.items():
            card = copy.deepcopy(cards[source_id(suit, rank)])
            for use in list(card):
                if use.get(HREF) in INDICES:
                    card.remove(use)
            base = next(use for use in card if use.get(HREF) == "#base")
            left = float(base.get("x")) - STROKE
            top = float(base.get("y")) + CARD_TOP
            sheet = ET.Element("{%s}svg" % SVG, {
                "width": "%.3f" % CARD_WIDTH,
                "height": "%.3f" % CARD_HEIGHT,
                "viewBox": "0 0 %.3f %.3f" % (CARD_WIDTH, CARD_HEIGHT),
            })
            sheet.append(defs)
            moved = ET.SubElement(sheet, "{%s}g" % SVG, {"transform": "translate(%.3f,%.3f)" % (-left, -top)})
            moved.append(card)
            name = "card_%s_%s.svg" % (suit_name, rank_name)
            ET.ElementTree(sheet).write(os.path.join(out, name), encoding="utf-8", xml_declaration=True)
    print("32 cards written to", out)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
