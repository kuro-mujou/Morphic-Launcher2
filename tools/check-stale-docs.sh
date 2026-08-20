#!/usr/bin/env bash
#
# check-stale-docs.sh — find identifiers that survive only in comments.
#
# KDoc is the one artifact in this repo that nothing verifies: no compiler, no test, no linter. So it
# rots silently, and the first thing to rot is a name. When a type or function is renamed or deleted,
# every KDoc that mentioned it goes on mentioning it, and still reads as true.
#
# This catches that one class of rot: an identifier that appears in a comment but nowhere in the code.
#
# Usage:
#   tools/check-stale-docs.sh              fast scan
#   tools/check-stale-docs.sh --history    keep only names that were once in this repo's history, so
#                                          a deliberate reference to L1 or to the platform is dropped
#                                          and a rename the comment did not follow is kept (slower:
#                                          one `git log -S` per candidate)
#   tools/check-stale-docs.sh --list       bare names only, for piping
#
# Exits 1 when anything is found, so it can gate a commit hook.
# Deliberate mentions are suppressed in tools/stale-docs-ignore.txt (one name per line, # for notes).

set -u
cd "$(dirname "$0")/.." || exit 2

MODE=scan
case "${1:-}" in
  --history) MODE=history ;;
  --list)    MODE=list ;;
  --help|-h) sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
  "")        ;;
  *)         echo "unknown option: $1 (try --help)" >&2; exit 2 ;;
esac

IGNORE_FILE=tools/stale-docs-ignore.txt
WORK=$(mktemp -d) || exit 2
trap 'rm -rf "$WORK"' EXIT

find . -name '*.kt' -not -path '*/build/*' > "$WORK/files" || exit 2
[ -s "$WORK/files" ] || { echo "no .kt files found" >&2; exit 2; }

# The comment pattern is inlined in each awk rather than passed with -v: -v processes escape
# sequences, which turns ^(\*|//|/\*) into an invalid regex that then silently matches nothing — so
# the filter appears to work while doing the opposite of what it says.

# --- 1. every identifier that really appears in code ----------------------------------------------
# Imported simple names count as present: a type we import is a platform or library type, so naming
# it in a comment is not rot even though it is declared nowhere here.
xargs -a "$WORK/files" -d '\n' awk '
  { line = $0; gsub(/^[ \t]+/, "", line)
    if (line ~ /^(\*|\/\/|\/\*)/) next
    if (line ~ /^import /) { n = split(line, p, "."); print p[n]; next }
    while (match($0, /[A-Za-z_][A-Za-z0-9_]*/)) {
      print substr($0, RSTART, RLENGTH); $0 = substr($0, RSTART + RLENGTH) } }
' 2>/dev/null | sort -u > "$WORK/in_code"

# A file name is a legitimate thing to mention: `CellFit` names a file of top-level functions with no
# type of that name, so a KDoc pointing at it is not rot. Basenames therefore count as present.
sed 's#.*/##; s#\.kt$##' "$WORK/files" >> "$WORK/in_code"
sort -u -o "$WORK/in_code" "$WORK/in_code"

# --- 2. candidates from comments ------------------------------------------------------------------
# Only `backticked` spans and [KDoc links] are candidates — prose is not. Dotted paths are split, so
# `AppsViewModel.setPagerGrid` tests both halves.
xargs -a "$WORK/files" -d '\n' grep -nHE '^[[:space:]]*(\*|//|/\*)' 2>/dev/null \
| awk -F: '
  { file = $1; lineno = $2; $1 = ""; $2 = ""; body = $0
    while (match(body, /`[^`]+`|\[[A-Za-z_][A-Za-z0-9_.]*\]/)) {
      tok = substr(body, RSTART, RLENGTH); body = substr(body, RSTART + RLENGTH)
      gsub(/[`\[\]]/, "", tok)
      n = split(tok, seg, /[^A-Za-z0-9_]+/)
      for (i = 1; i <= n; i++) if (seg[i] != "") print seg[i] "\t" file ":" lineno } }' \
> "$WORK/candidates"

# --- 3. filter ------------------------------------------------------------------------------------
# Keep only names shaped like a Kotlin declaration: at least one uppercase *and* one lowercase letter,
# no underscore. That admits GridRect and setPagerGrid, and rejects prose ("null", "zone") along with
# SCREAMING_SNAKE, which is enum values and platform constants rather than renamed types.
awk -F'\t' '$1 ~ /[A-Z]/ && $1 ~ /[a-z]/ && $1 !~ /_/ && length($1) > 3' "$WORK/candidates" \
| sort -u > "$WORK/shaped"

if [ -f "$IGNORE_FILE" ]; then
  sed 's/#.*//; s/[[:space:]]*$//' "$IGNORE_FILE" | grep -v '^$' | sort -u > "$WORK/ignore"
else
  : > "$WORK/ignore"
fi

cut -f1 "$WORK/shaped" | sort -u > "$WORK/names"
comm -23 "$WORK/names" "$WORK/in_code" | comm -23 - "$WORK/ignore" > "$WORK/hits"

# --- 4. history confirmation ----------------------------------------------------------------------
# The pickaxe searches for a *declaration* of the name, not for the name anywhere. Plain `-S` matches
# file content, comments included — so the very comment under suspicion is what it finds, and the
# filter passes everything. Asking whether `class|fun|val …  Name` ever existed is what separates a
# rename of ours from a deliberate reference to L1 or the platform. One `git log` per candidate, so
# this is the slow path (~1 minute over a few hundred).
if [ "$MODE" = history ]; then
  echo "confirming $(wc -l < "$WORK/hits" | tr -d ' ') candidate(s) against git history…" >&2
  : > "$WORK/confirmed"
  while read -r name; do
    [ -n "$name" ] || continue
    if [ -n "$(git log --pickaxe-regex -S"(class|interface|object|fun|val|var) +$name([^A-Za-z0-9_]|$)"                  --oneline -1 -- '*.kt' 2>/dev/null)" ]; then
      printf '%s\n' "$name" >> "$WORK/confirmed"
    fi
  done < "$WORK/hits"
  mv "$WORK/confirmed" "$WORK/hits"
fi

# --- 5. report ------------------------------------------------------------------------------------
count=$(wc -l < "$WORK/hits" | tr -d ' ')
if [ "$count" -eq 0 ]; then
  [ "$MODE" = list ] || echo "No stale identifiers in comments."
  exit 0
fi
if [ "$MODE" = list ]; then cat "$WORK/hits"; exit 1; fi

echo "$count identifier(s) appear in comments but nowhere in code:"
echo
while read -r name; do
  [ -n "$name" ] || continue
  n=$(awk -F'\t' -v k="$name" '$1 == k' "$WORK/shaped" | wc -l | tr -d ' ')
  printf '  %-26s %s mention(s)\n' "$name" "$n"
  awk -F'\t' -v k="$name" '$1 == k { print "      " $2 }' "$WORK/shaped" | sed 's#^      \./#      #' | head -6
done < "$WORK/hits"
echo
if [ "$MODE" = history ]; then
  echo "Each of these was in the repo once, so each is a rename or deletion a comment did not follow."
else
  echo "Re-run with --history to drop names that were never ours (L1 and platform references)."
fi
echo "Deliberate mentions belong in $IGNORE_FILE."
exit 1
