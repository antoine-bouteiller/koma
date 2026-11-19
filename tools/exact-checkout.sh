#!/bin/sh
# Clones expo/exact at the commit .exact-ref pins into ../exact, where exact.mjs looks for it.
set -eu
root=$(cd "$(dirname "$0")/.." && pwd)
ref=$(cat "$root/.exact-ref")
exact="${EXACT:-$root/../exact}"
if [ ! -d "$exact/.git" ]; then
  git init -q "$exact"
  git -C "$exact" remote add origin https://github.com/expo/exact.git
fi
git -C "$exact" fetch -q --depth 1 origin "$ref"
git -C "$exact" checkout -q --detach "$ref"
bun install --frozen-lockfile --cwd "$exact"
