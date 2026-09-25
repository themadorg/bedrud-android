#!/bin/bash
#
# Rebuild app/src/main/res/font/roboto_cyrillic_greek.ttf, the face the app falls back to for the
# Cyrillic and Greek letters Vazirmatn does not carry. See Typography in DESIGN.md for why it
# exists and why it is Roboto.
#
#   tools/fonts/build-companion-font.sh
#
# Needs fonttools on the PATH (`python3 -m pip install fonttools`). The source is Google Fonts'
# variable Roboto, pinned to one commit and checked against its hash, so a rerun produces the same
# file rather than whatever upstream has moved on to.
#
# What it keeps, and why:
#   - Width fixed at normal (wdth=100). Nothing in the app asks for a condensed face.
#   - Weight limited to 400-700, the range the type scale's four weights sit in.
#   - Cyrillic and its supplement, basic Greek, and the combining marks Roboto has. Android keeps a
#     mark in its letter's font when that font carries it, so a stress mark on a Cyrillic vowel is
#     drawn by the same face as the vowel.
#   - Nothing Vazirmatn already carries besides those marks. Vazirmatn comes first in the chain,
#     so a digit, a Latin letter or a sign like the numero is always drawn by it; a copy here would
#     never be reached.
#   - Every name record, which carries Roboto's copyright and its Open Font License notice.

set -euo pipefail

SOURCE_COMMIT="6183fc0d26361f6ddfd6f6b7a736e1467c6d8a43"
SOURCE_URL="https://raw.githubusercontent.com/google/fonts/$SOURCE_COMMIT/ofl/roboto/Roboto%5Bwdth,wght%5D.ttf"
SOURCE_SHA256="d7598e12c5dbef095ff8272cfc55da0250bd07fbdecbac8a530b9b277872a134"

# fonttools stamps the output's `head.modified` with the current time unless told otherwise. The
# source commit's own time (2026-01-27T18:54:45Z) makes two runs byte-identical, so an unchanged
# rerun shows no diff.
export SOURCE_DATE_EPOCH=1769540085

# Cyrillic, Cyrillic Supplement, combining diacritical marks, Greek and Coptic.
UNICODES="U+0400-052F,U+0300-036F,U+0370-03FF"

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUTPUT="$ROOT/app/src/main/res/font/roboto_cyrillic_greek.ttf"

command -v fonttools >/dev/null || { echo "fonttools not found: python3 -m pip install fonttools" >&2; exit 1; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

curl -fsSL -o "$WORK/roboto.ttf" "$SOURCE_URL"
echo "$SOURCE_SHA256  $WORK/roboto.ttf" | shasum -a 256 -c - >/dev/null

fonttools varLib.instancer "$WORK/roboto.ttf" wdth=100 wght=400:700 \
    --output="$WORK/instanced.ttf" --quiet

fonttools subset "$WORK/instanced.ttf" \
    --unicodes="$UNICODES" \
    --layout-features='*' \
    --name-IDs='*' \
    --name-languages='*' \
    --output-file="$OUTPUT"

echo "$(wc -c <"$OUTPUT" | tr -d ' ') bytes -> ${OUTPUT#"$ROOT"/}"
