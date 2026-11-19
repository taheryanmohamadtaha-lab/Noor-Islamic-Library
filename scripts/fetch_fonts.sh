#!/usr/bin/env bash
# Downloads open-licensed (OFL) fonts into app/src/main/res/font. Fails the build if a download fails.
set -euo pipefail
DIR="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/res/font"
mkdir -p "$DIR"
BASE="https://raw.githubusercontent.com/google/fonts/main/ofl"
get() { # url, output
  curl -fsSL --retry 3 "$1" -o "$DIR/$2"
  # TrueType magic: 00 01 00 00 or 'true'
  head -c 4 "$DIR/$2" | xxd -p | grep -qE '^(00010000|74727565)$' || { echo "not a TTF: $2" >&2; exit 1; }
  echo "ok $2 ($(wc -c < "$DIR/$2") bytes)"
}
get "$BASE/amiriquran/AmiriQuran-Regular.ttf" amiri_quran.ttf
get "$BASE/notonaskharabic/NotoNaskhArabic%5Bwght%5D.ttf" noto_naskh.ttf
get "$BASE/vazirmatn/Vazirmatn%5Bwght%5D.ttf" vazirmatn.ttf
