#!/usr/bin/env bash
# Baixa o codigo-fonte OFICIAL dos motores nas versoes fixadas e confere o commit.
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
source "$HERE/versions.env"
TP="$HERE/third_party"
mkdir -p "$TP"

fetch() { # nome repo ref commit
  local dir="$TP/$1"
  if [ -d "$dir/.git" ] && [ "$(git -C "$dir" rev-parse HEAD)" = "$4" ]; then
    echo "[ok] $1 ja esta em $4"; return
  fi
  rm -rf "$dir"
  git clone --depth 1 --branch "$3" --recurse-submodules --shallow-submodules "$2" "$dir"
  local got; got="$(git -C "$dir" rev-parse HEAD)"
  if [ "$got" != "$4" ]; then
    echo "ERRO: $1 em $got, esperado $4 (a tag $3 foi movida?)" >&2; exit 1
  fi
  echo "[ok] $1 @ $got"
}

fetch stable-diffusion.cpp "$SD_CPP_REPO" "$SD_CPP_REF" "$SD_CPP_COMMIT"
fetch llama.cpp "$LLAMA_CPP_REPO" "$LLAMA_CPP_REF" "$LLAMA_CPP_COMMIT"
