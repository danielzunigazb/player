#!/usr/bin/env bash
# Assembles the site into a folder (default _site/): the page with the current version and
# test count filled in, the design-system styles and the app screenshots used by the video.
# The showcase video itself is added by the Site workflow from the showcase-v1 release.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
out=${1:-$root/_site}

version=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' "$root/app/build.gradle.kts")
tests=$(grep -rhoE '@Test\b' "$root/app/src/test" | wc -l | tr -d ' ')
[ -n "$version" ] || { echo "versionName not found" >&2; exit 1; }

rm -rf "$out"
mkdir -p "$out/img" "$out/media"
cp -r "$root/site/styles" "$root/site/icons.svg" "$out/"
cp "$root"/player-showcase/public/img/*.png "$out/img/"
# Local previews get the committed thumbnail; CI replaces it with the release's.
cp "$root/docs/showcase-thumb.jpg" "$out/media/"
sed -e "s/{{VERSION}}/$version/g" -e "s/{{TESTS}}/$tests/g" "$root/site/index.html" > "$out/index.html"
if grep -q '{{' "$out/index.html"; then echo "unfilled placeholder in index.html" >&2; exit 1; fi
echo "site: player $version · $tests tests → $out"
