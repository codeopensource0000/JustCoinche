#!/usr/bin/env python3
"""Split the path data that are too long for Android into several paths.

The compiled resources of an APK cannot hold a string longer than 32767
characters: the build tools silently replace it with "STRING_TOO_LARGE", which
crashes the app when the drawing is loaded. Some figures of SVG-cards are drawn
with paths of up to 75 000 characters.

A long path is cut into its sub-paths, each starting with a move, with its
numbers written as short as the syntax allows (".5" for "0.5", no space before
a minus sign). A sub-path that makes a hole in another stays with it (see
groups()); the groups are then packed into new paths, each short enough, with
the same attributes. That the pixels stay the same is checked by rendering both
versions (see README.md).

Usage: python3 -I split_long_paths.py <vector drawable>...  (files are rewritten in place)
"""
import re
import sys

LIMIT = 32000  # under the 32767 of the compiled resources
TOKEN = re.compile(r"[MmLlHhVvCcSsQqTtAaZz]|[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")
CURVE_STEPS = 6
ARGS = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "T": 2, "A": 7, "Z": 0}


def fmt(value):
    """A number as short as the path syntax allows: "0.5" is ".5", "-0.5" is "-.5"."""
    text = ("%.3f" % value).rstrip("0").rstrip(".")
    if text in ("-0", ""):
        return "0"
    if text.startswith("0."):
        return text[1:]
    if text.startswith("-0."):
        return "-" + text[2:]
    return text


def numbers(values):
    """Numbers written one after the other: a minus sign already separates them, else a space does."""
    out = ""
    for text in map(fmt, values):
        out += text if not out or text.startswith("-") else " " + text
    return out


def cubic(p0, p1, p2, p3):
    """Points along a cubic curve, its end included, enough to tell inside from outside."""
    points = []
    for step in range(1, CURVE_STEPS + 1):
        t = step / CURVE_STEPS
        u = 1 - t
        points.append((u * u * u * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t * t * t * p3[0],
                       u * u * u * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t * t * t * p3[1]))
    return points


def subpaths(data):
    """The sub-paths of [data], each as (text starting with an absolute move, outline as a list of points)."""
    tokens = TOKEN.findall(data)
    result = []
    x = y = start_x = start_y = 0.0
    pieces = outline = None
    control = None  # the second control point of the last cubic, for a smooth one
    i = 0
    command = None
    while i < len(tokens):
        if tokens[i].isalpha():
            command = tokens[i]
            i += 1
        upper = command.upper()
        relative = command != upper
        count = ARGS[upper]
        values = [float(v) for v in tokens[i:i + count]]
        i += count
        base_x, base_y = (x, y) if relative else (0.0, 0.0)
        if upper == "M":
            if pieces is not None:
                result.append(("".join(pieces), outline))
            x, y = start_x, start_y = base_x + values[0], base_y + values[1]
            pieces, outline, control = ["M" + numbers([x, y])], [(x, y)], None
            # Further pairs after a move are line-tos of the same kind.
            command = "l" if relative else "L"
            continue
        if upper == "Z":
            pieces.append(command)
            x, y, control = start_x, start_y, None
            continue
        # Every other command is kept as written; the pen is followed to draw the outline.
        pieces.append(command + numbers(values))
        if upper == "C" or upper == "S":
            first = (2 * x - control[0], 2 * y - control[1]) if upper == "S" and control else (x, y)
            if upper == "C":
                first = (base_x + values[0], base_y + values[1])
            rest = [(base_x + values[k], base_y + values[k + 1]) for k in range(len(values) - 4, len(values), 2)]
            outline += cubic((x, y), first, rest[0], rest[1])
            control = rest[0]
            x, y = rest[1]
            continue
        control = None
        if upper == "H":
            x = base_x + values[0]
        elif upper == "V":
            y = base_y + values[0]
        elif upper == "A":
            x, y = base_x + values[5], base_y + values[6]
        else:
            x, y = base_x + values[-2], base_y + values[-1]
        outline.append((x, y))
    if pieces is not None:
        result.append(("".join(pieces), outline))
    return result


def area(points):
    """Twice the signed area of a closed outline: its sign tells which way it turns."""
    return sum(x0 * y1 - x1 * y0 for (x0, y0), (x1, y1) in zip(points, points[1:] + points[:1]))


def inside(point, points, box):
    """Whether [point] lies inside the closed outline [points], whose bounding box is [box]."""
    px, py = point
    if not (box[0] <= px <= box[2] and box[1] <= py <= box[3]):
        return False
    crossings = False
    for (x0, y0), (x1, y1) in zip(points, points[1:] + points[:1]):
        if (y0 > py) != (y1 > py) and px < x0 + (py - y0) * (x1 - x0) / (y1 - y0):
            crossings = not crossings
    return crossings


def groups(parts):
    """
    Sub-paths gathered so that none of them makes a hole in another group.

    With the default fill (non-zero winding), the inside of an outline is
    filled when the turns of the outlines around it do not add up to zero. A
    sub-path whose inside adds up to zero while the area around it does not
    is a hole: it stays with the outline right around it. Every other
    sub-path, an island in a hole included, can be drawn on its own.
    """
    outlines = [outline for _, outline in parts]
    boxes = [(min(p[0] for p in o), min(p[1] for p in o), max(p[0] for p in o), max(p[1] for p in o))
             for o in outlines]
    areas = [area(o) for o in outlines]
    # The parent of a sub-path: the smallest other outline around its first point.
    parents = []
    for k, outline in enumerate(outlines):
        around = [j for j in range(len(outlines))
                  if j != k and abs(areas[j]) > abs(areas[k]) and inside(outline[0], outlines[j], boxes[j])]
        parents.append(min(around, key=lambda j: abs(areas[j])) if around else None)
    winding = {}

    def turns(k):
        if k not in winding:
            outside = 0 if parents[k] is None else turns(parents[k])
            winding[k] = outside + (1 if areas[k] > 0 else -1)
        return winding[k]

    leader = list(range(len(parts)))

    def find(k):
        while leader[k] != k:
            leader[k] = leader[leader[k]]
            k = leader[k]
        return k

    for k, parent in enumerate(parents):
        if parent is not None and turns(parent) != 0 and turns(k) == 0:
            leader[find(k)] = find(parent)
    gathered = {}
    for k, (text, _) in enumerate(parts):
        gathered.setdefault(find(k), []).append(text)
    return ["".join(texts) for texts in gathered.values()]


class TooLong(Exception):
    pass


def pack(texts):
    """Groups packed into path data of at most LIMIT characters each."""
    chunks, current = [], ""
    for text in texts:
        if len(text) > LIMIT:
            raise TooLong("a single group of sub-paths is %d characters long" % len(text))
        if current and len(current) + len(text) > LIMIT:
            chunks.append(current)
            current = ""
        current += text
    return chunks + [current]


def split_file(name):
    xml = open(name, encoding="utf-8").read()
    changed = 0

    def replace(match):
        nonlocal changed
        element = match.group(0)
        data = re.search(r'android:pathData="([^"]*)"', element).group(1)
        if len(data) <= LIMIT:
            return element
        changed += 1
        indent = xml[xml.rfind("\n", 0, match.start()) + 1:match.start()]
        copies = [element.replace(data, chunk) for chunk in pack(groups(subpaths(data)))]
        return ("\n" + indent).join(copies)

    xml = re.sub(r"<path\b[^>]*/>", replace, xml)
    open(name, "w", encoding="utf-8").write(xml)
    return changed


if __name__ == "__main__":
    # A drawing that cannot be split is left as it is and named: it must be rendered to an image instead.
    for path in sys.argv[1:]:
        try:
            count = split_file(path)
        except TooLong as reason:
            print("%s: cannot be split (%s)" % (path, reason))
            continue
        if count:
            print("%s: %d long path(s) split" % (path, count))
