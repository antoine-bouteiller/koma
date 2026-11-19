# Koma

A tracker-first manga reader for Android. MangaBaka is the library and the discovery surface;
sources only supply chapters and pages. The design lives in
[`doc/architecture/specs/app.spec.md`](doc/architecture/specs/app.spec.md).

Koma is an [Expo Exact](https://github.com/expo/exact) app: `app.contract` is the view, `app.ts`
the data module, `app.test.contract` the tests and `app.json` the manifest. Exact has no packaged
CLI, so the app uses a checkout of `expo/exact` at `../exact`, pinned to the commit in
[`.exact-ref`](.exact-ref).

## Setup

Needs [Nix](https://nixos.org) with flakes, and optionally [direnv](https://direnv.net). The dev
shell (`flake.nix`) provides Bun, rustup, a JDK, treefmt and the pre-commit hooks.

1. Enter the dev shell: `direnv allow`, or `nix develop`.
2. Clone Exact at the pinned commit into `../exact`: `tools/exact-checkout.sh`.
3. Install Exact's toolchains (pinned Rust, wasm-bindgen, Hermes, Chromium):
   `bun ../exact/scripts/exact.mjs setup`. It takes several minutes the first time.
4. Install the Android SDK packages into `$ANDROID_HOME` (default `~/Library/Android/sdk`) with
   the Android command-line tools (`brew install --cask android-commandlinetools`):

   ```sh
   sdkmanager --sdk_root="$ANDROID_HOME" 'platforms;android-37.2' 'build-tools;37.0.0' 'ndk;27.1.12297006' platform-tools
   ```

5. Check what is still missing: `bun ../exact/scripts/exact.mjs setup --check --platform android`.

## Commands

Run from the repository root, inside the dev shell.

| Command | Does |
| --- | --- |
| `bun exact.mjs contract build app.contract --errors-only` | Compiles the view and type-checks `app.ts` |
| `bun exact.mjs test web` | Runs `app.test.contract` headless in Chromium |
| `bun exact.mjs web` | Web dev loop with reload on save |
| `bun exact.mjs android --run` | Builds the APK, installs it on the device `adb` lists and launches it |
| `bun exact.mjs test android` | Runs the tests on that device |
| `bun exact.mjs android --no-install` | Builds `target/android/app/outputs/apk/release/app-release.apk` only |
| `nix fmt` | Formats Nix, TypeScript, JSON, Markdown and YAML |

Android is the only shipped host. The web host is kept for fast tests and the dev loop.

To move to a newer Exact, check out the new commit in `../exact`, write it to `.exact-ref`, then
run `bun exact.mjs update` and `bun exact.mjs contract build app.contract --errors-only`.

## CI

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs three jobs on every push:

- `format`: `nix flake check`, which fails on unformatted files.
- `test`: builds the contract and runs the tests on the web host.
- `android`: builds the APK and uploads it as the `koma-debug-apk` artifact. It is signed with
  the debug key.
