"""Measure a wallpaper render — the "measure pixels rather than trusting the eye" rule, made runnable.

    measure.py scan  <png> [--at N] [--down|--across]   runs of colour along one line
    measure.py tiles <png> [--crop Y0 Y1] [--step N]     every non-ground region's bounding box
    measure.py grout <png> [--crop Y0 Y1]                gap positions, and how uniform their width is
    measure.py slope <png> [--crop Y0 Y1]                the angle of the first boundary, in degrees

Works on our own renders and on screenshots of the reference alike. See README.md for what each subcommand has
already settled; needs Pillow.
"""
import argparse
import math
import statistics
import sys
from collections import Counter, deque

try:
    from PIL import Image, ImageFile
except ImportError:  # pragma: no cover - a dependency note is more use than a traceback
    sys.exit("needs Pillow: pip install pillow")

# An occasional harness PNG is written short on the device; measuring what arrived beats refusing to open it.
ImageFile.LOAD_TRUNCATED_IMAGES = True

TOLERANCE = 14


def load(path, crop):
    im = Image.open(path)
    im.load()
    im = im.convert("RGB")
    if crop:
        im = im.crop((0, crop[0], im.width, min(crop[1], im.height)))
    return im


def ground(im, step=2):
    """The ground colour — the one the motif sits on.

    **Not the most common colour**, which is the obvious guess and is wrong: a big tile or a wide band beats the
    ground easily. It is the colour that touches nearly every *row* and nearly every *column* — a tile's pixels are a
    compact block, the ground's run the whole frame. Palette-independent, and it has not been fooled yet.
    """
    px = im.load()
    w, h = im.size
    counts = Counter(px[x, y] for x in range(0, w, step) for y in range(0, h, step))
    best, found = -1.0, None
    for color, n in counts.most_common(10):
        if n < w * h / 400:
            continue
        rows = len({y for y in range(0, h, 4) for x in range(0, w, 8) if px[x, y] == color})
        cols = len({x for x in range(0, w, 4) for y in range(0, h, 8) if px[x, y] == color})
        spread = min(rows / (h / 4), cols / (w / 4))
        if spread > best:
            best, found = spread, color
    return found


def near(a, b, tol=TOLERANCE):
    return all(abs(p - q) <= tol for p, q in zip(a, b))


def runs_of(values):
    """Consecutive stretches of one colour, as `(colour, from, to)`."""
    out, prev, start = [], None, 0
    for i, c in enumerate(values):
        if prev is None:
            prev, start = c, i
        elif not near(c, prev):
            out.append((prev, start, i - 1))
            prev, start = c, i
    out.append((prev, start, len(values) - 1))
    return out


def cmd_scan(im, args):
    px = im.load()
    w, h = im.size
    if args.across:
        at = args.at if args.at is not None else h // 2
        line = [px[x, at] for x in range(w)]
        print(f"across y={at}, {w}px")
    else:
        at = args.at if args.at is not None else w // 2
        line = [px[at, y] for y in range(h)]
        print(f"down x={at}, {h}px")
    found = runs_of(line)
    print(f"{len(found)} runs (colour, from, to, extent, luma):")
    for c, a, b in found:
        if b - a + 1 < args.min:
            continue
        luma = 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]
        print(f"  {str(c):20s} {a:5d}..{b:5d}  extent={b - a + 1:5d}  luma={luma:6.1f}")
    extents = [b - a + 1 for c, a, b in found if b - a + 1 >= args.min]
    if len(extents) > 2:
        # The interior runs: the first and last usually touch the frame edge and are cut off by it.
        inner = extents[1:-1]
        print(f"interior extents: {inner}")
        if len(inner) > 1:
            print(f"  min/max {min(inner)}/{max(inner)}   spread {max(inner) / max(1, min(inner)):.2f}x")


def cmd_tiles(im, args):
    """Every non-ground region's bounding box.

    Reading the *regions* rather than the grout lines is what tells a boundary running the whole frame from three
    independent ones that happen to land on the same pixel — the question that decides rows-first, columns-first, or a
    recursive split, and one line-finding cannot answer.
    """
    step = args.step
    small = im.resize((im.width // step, im.height // step), Image.NEAREST)
    bg = ground(small, step=1)
    print(f"ground = {bg}  (sampling every {step}px)")
    px = small.load()
    w, h = small.size

    mask = [[not near(px[x, y], bg) for y in range(h)] for x in range(w)]
    seen = [[False] * h for _ in range(w)]
    boxes = []
    for sx in range(w):
        for sy in range(h):
            if not mask[sx][sy] or seen[sx][sy]:
                continue
            queue = deque([(sx, sy)])
            seen[sx][sy] = True
            xs, ys, n = [], [], 0
            while queue:
                x, y = queue.popleft()
                xs.append(x)
                ys.append(y)
                n += 1
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and mask[nx][ny] and not seen[nx][ny]:
                        seen[nx][ny] = True
                        queue.append((nx, ny))
            if n >= args.min_area:
                boxes.append((min(xs) * step, min(ys) * step, max(xs) * step, max(ys) * step))

    boxes.sort(key=lambda b: (b[1], b[0]))
    print(f"{len(boxes)} regions:")
    for x0, y0, x1, y1 in boxes:
        print(f"  x {x0:5d}..{x1:5d}   y {y0:5d}..{y1:5d}   {x1 - x0 + 1:5d} x {y1 - y0 + 1:5d}")
    # Shared edges are the tell: few distinct lefts and rights means columns that span the frame.
    for name, values in (
        ("left", {b[0] for b in boxes}), ("right", {b[2] for b in boxes}),
        ("top", {b[1] for b in boxes}), ("bottom", {b[3] for b in boxes}),
    ):
        print(f"distinct {name:6s} edges ({len(values):3d}): {sorted(values)}")


def spans_where(flags):
    """The `(from, to)` stretches where `flags` is true.

    Deliberately **not** [runs_of]: that compares colours with a tolerance, and `abs(True - False)` is `1`, well
    inside it — so a series of flags collapsed into a single run and the grout appeared to be one line down the
    middle of the frame. A wrong answer of exactly the shape a plausible one has.
    """
    out, start = [], None
    for i, flag in enumerate(flags):
        if flag and start is None:
            start = i
        elif not flag and start is not None:
            out.append((start, i - 1))
            start = None
    if start is not None:
        out.append((start, len(flags) - 1))
    return out


def cmd_grout(im, args):
    """Where the gaps are, and how uniform their width is.

    The uniformity is the interesting number: a gap that holds its width while a jitter knob is driven says the jitter
    moves one shared lattice, where a spread says each piece distorts on its own.
    """
    px = im.load()
    w, h = im.size
    bg = ground(im)
    print(f"ground = {bg}")

    for label, size, sample in (("VERTICAL (x)", w, lambda i, j: px[i, j]), ("HORIZONTAL (y)", h, lambda i, j: px[j, i])):
        other = h if label.startswith("VERT") else w
        share = [sum(near(sample(i, j), bg) for j in range(0, other, 2)) / (other / 2) for i in range(size)]
        for thresh in (0.90, 0.50, 0.25):
            centres = [(a + b) // 2 for a, b in spans_where([v > thresh for v in share])]
            # A line surviving >0.90 runs the whole frame; one that only survives >0.25 is a segment between two
            # pieces. Which threshold a boundary drops out at is the useful part.
            print(f"  {label} >{thresh:.2f} ({len(centres):3d}): {centres}")

    widths = []
    for y in range(0, h, 12):
        start = None
        for x in range(w):
            on = near(px[x, y], bg)
            if on and start is None:
                start = x
            elif not on and start is not None:
                if start > 0:  # a run touching the frame edge is a margin, not a gap between two pieces
                    widths.append(x - start)
                start = None
    if widths:
        widths.sort()
        mean = statistics.mean(widths)
        print(f"{len(widths)} interior gaps: min {widths[0]}  median {statistics.median(widths)}  max {widths[-1]}")
        print(f"  mean {mean:.1f}  stdev {statistics.pstdev(widths):.1f}  (stdev/mean {statistics.pstdev(widths) / mean:.2f})")
        print(f"  deciles: {[widths[int(len(widths) * f)] for f in (0.1, 0.25, 0.5, 0.75, 0.9)]}")
        print("  the 10th-50th percentile is the perpendicular width; the long tail is scanlines crossing a slope.")


def cmd_slope(im, args):
    """The angle of the first boundary — whether a design's angles are drawn on the screen or on the unit square."""
    px = im.load()
    w, h = im.size

    def first_change(x):
        prev = px[x, 0]
        for y in range(1, h):
            c = px[x, y]
            if not near(c, prev):
                return y
            prev = c
        return None

    samples = [(x, first_change(x)) for x in (0, w // 4, w // 2, 3 * w // 4, w - 1)]
    print("first boundary y by x:", samples)
    (x0, y0), (x1, y1) = samples[0], samples[-1]
    if y0 is None or y1 is None:
        print("no boundary found in one of the columns — is the crop inside the motif?")
        return
    slope = (y1 - y0) / (x1 - x0)
    print(f"slope dy/dx = {slope:.4f}  ->  {math.degrees(math.atan(slope)):.2f}° below horizontal")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=("scan", "tiles", "grout", "slope"))
    parser.add_argument("png")
    parser.add_argument("--crop", nargs=2, type=int, metavar=("Y0", "Y1"),
                        help="measure only this band of rows — crop the app's own chrome out")
    parser.add_argument("--at", type=int, help="scan: which row or column")
    parser.add_argument("--across", action="store_true", help="scan: along a row instead of down a column")
    parser.add_argument("--down", action="store_true", help="scan: down a column (the default)")
    parser.add_argument("--min", type=int, default=5, help="scan: ignore runs shorter than this")
    parser.add_argument("--step", type=int, default=4, help="tiles: sampling stride, bigger is faster and coarser")
    parser.add_argument("--min-area", type=int, default=20, help="tiles: ignore regions smaller than this many samples")
    args = parser.parse_args()

    im = load(args.png, args.crop)
    print(f"{args.png}  {im.width}x{im.height}" + (f"  (rows {args.crop[0]}..{args.crop[1]})" if args.crop else ""))
    {"scan": cmd_scan, "tiles": cmd_tiles, "grout": cmd_grout, "slope": cmd_slope}[args.command](im, args)


if __name__ == "__main__":
    main()
