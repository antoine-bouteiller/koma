#!/bin/sh
# Installs only what an Android build needs, as Exact's native CI preparation does; `exact setup` would also fetch the web's.
set -eu
pin=$(sed -n 's/^channel = "\(.*\)"/\1/p' "$EXACT/rust-toolchain.toml")
rustup toolchain install "$pin" --profile minimal --target aarch64-linux-android
cargo +"$pin" run --locked --manifest-path "$EXACT/vendor/ibex/crates/hermes-lean-sys-installer/Cargo.toml" -- --target aarch64-linux-android
cargo +"$pin" fetch --locked --manifest-path "$EXACT/Cargo.toml"
bun "$EXACT/scripts/exact.mjs" setup --check --platform android
