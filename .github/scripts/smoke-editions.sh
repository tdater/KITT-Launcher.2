#!/usr/bin/env bash
# Picks which editions the emulator smoke test needs, from the list of changed files on stdin
# (one path per line). Prints a JSON array of matrix entries ({"flavor": ..., "size": ...}).
#
# Editions share almost all their code, so only changes that provably reach a subset of them
# narrow the list, and anything unrecognized runs all four:
#   app/src/kitt2/  KITT Launcher.2 and KITT Holo Landscape (Holo Landscape reuses its classes)
#   app/src/kitt/   KITT Launcher and KITT Holo Portrait (Holo Portrait reuses its classes)
#   app/src/holo/   both KITT Holo editions
#   unit tests, docs and workflows the smoke test doesn't use: none
set -euo pipefail

kitt2=0 kitt=0 holo_landscape=0 holo_portrait=0
all() { kitt2=1 kitt=1 holo_landscape=1 holo_portrait=1; }

while IFS= read -r path; do
  [ -n "$path" ] || continue
  case "$path" in
    app/src/kitt2/*) kitt2=1 holo_landscape=1 ;;
    app/src/kitt/*) kitt=1 holo_portrait=1 ;;
    app/src/holo/*) holo_landscape=1 holo_portrait=1 ;;
    app/src/holoLandscape/*) holo_landscape=1 ;;
    app/src/holoPortrait/*) holo_portrait=1 ;;
    app/src/test/*) ;;
    .github/workflows/page-media.yml | .github/scripts/capture-media.sh) ;;
    .claude/* | .idea/*) ;;
    app/src/*) all ;;
    *.md) ;;
    *) all ;;
  esac
done

entries=()
[ "$kitt2" = 1 ] && entries+=('{"flavor":"Kitt2","size":"1280x720"}')
[ "$kitt" = 1 ] && entries+=('{"flavor":"Kitt","size":"800x1151"}')
[ "$holo_landscape" = 1 ] && entries+=('{"flavor":"HoloLandscape","size":"1280x720"}')
[ "$holo_portrait" = 1 ] && entries+=('{"flavor":"HoloPortrait","size":"800x1151"}')
(IFS=,; echo "[${entries[*]}]")
