# Koma

A tracker-first manga reader for Android. MangaBaka is the library and the discovery surface;
sources only supply chapters and pages. The design lives in
[`doc/architecture/specs/`](doc/architecture/specs/), starting from
[`app.spec.md`](doc/architecture/specs/app.spec.md).

Koma is a Kotlin and Jetpack Compose app in one Gradle module, `app/`, with packages by feature:

| Package | Holds |
| --- | --- |
| `app.koma` | `KomaApplication`, `MainActivity`, `LibraryUpdateWorker` (the background chapter update), and `AppContainer`, which builds every dependency |
| `app.koma.data` | The `Backend` interface, the media, library, history and account repositories, the HTTP client, and the SQLite store (SQLDelight, `app/src/main/sqldelight/`) |
| `app.koma.data.mangabaka` | `MangaBakaBackend`: MangaBaka's API and its sign-in (OAuth with PKCE) |
| `app.koma.auth` | The sign-in prompt and session state, the Custom Tab browser, the Keystore secret store |
| `app.koma.library` | The library's shelves and their screen |
| `app.koma.history` | The reading history's screen |
| `app.koma.updates` | The Updates tab: the new chapters of library titles |
| `app.koma.title` | A title's screen: the backend's facts, summary and tags, and its library status and rating |
| `app.koma.update` | The header pill offering a newer release, its download and its hand-off to the installer |
| `app.koma.settings` | The Settings tab: the Theme and account pages |
| `app.koma.ui` | The app shell: tabs, header, empty states, settings rows, option groups, day labels, theme |
| `app.koma.version` | `versionCode`, also compiled by `buildSrc` for the build |

A feature imports `data`, `ui` and `version`, never another feature nor `app.koma.data.mangabaka`,
which only `KomaApplication` builds. Only `ui/KomaApp.kt` and `MainActivity.kt` compose features.
Screens render a ViewModel's state and decide nothing.

## Setup

Needs [Nix](https://nixos.org) with flakes, and optionally [direnv](https://direnv.net). The dev
shell (`flake.nix`) provides JDK 21, Nushell, the GitHub CLI, treefmt and the git hooks.

1. Enter the dev shell: `direnv allow`, or `nix develop`.
2. Install the Android SDK into `$ANDROID_HOME` (default `~/Library/Android/sdk`), with Android
   Studio or the command-line tools (`brew install --cask android-commandlinetools`), and accept
   its licenses: `sdkmanager --licenses`. Gradle installs the SDK platform it needs.

## Commands

Run from the repository root, inside the dev shell.

| Command | Does |
| --- | --- |
| `./gradlew testDebugUnitTest` | Runs every test on the JVM with Robolectric, the UI included |
| `./gradlew lintDebug` | Runs Android Lint; a warning fails it |
| `./gradlew installDebug` | Installs Koma Dev (`app.koma.dev`) on the device `adb` lists |
| `./gradlew assembleRelease` | Builds `app/build/outputs/apk/release/app-release.apk`, signed with the debug key unless `KOMA_KEYSTORE` is set |
| `nix fmt` | Formats Kotlin (ktfmt), Nix, JSON and YAML |

Android Studio opens the repository as a Gradle project, with Compose previews and Live Edit.

## Tests

Tests run the whole app on the JVM through `app/src/test/kotlin/app/koma/testing/AppHarness.kt`:
an in-memory secret store, a fake browser, a fixed clock, and `FakeNetwork`, an OkHttp interceptor
that answers from stubs and fails the test on any request no stub answers. Work runs on the main
looper there, so Compose's idling waits for it. The harness records image URLs and returns failures
unless a test supplies a bitmap.

## MangaBaka sign-in

Koma signs in as the public OAuth client `lYFPQeyaYBnVaZJmcQHPiNjlprtCsVDL`, registered on
mangabaka.org with the redirect URL `koma://auth`. Koma and Koma Dev both register that scheme, so
with both installed Android asks which one completes a sign-in.

## CI

Two workflows run `format` (`nix flake check`, which fails on unformatted files) and `test`
(`./gradlew testDebugUnitTest lintDebug`):

- [`.github/workflows/check.yml`](.github/workflows/check.yml), on pull requests, also runs
  `android`: it builds the release APK, signed with the debug key, and uploads it as the
  `koma-debug-apk` artifact.
- [`.github/workflows/publish.yml`](.github/workflows/publish.yml), on pushes to `main` and
  `develop`, runs `publish` after `format` and `test` pass. It runs
  [`tools/release.nu`](tools/release.nu): when the commits since the last tag call for a release,
  it builds the release APK, and the GitHub release `v<version>` carries it with generated notes.
  `develop` publishes betas (`1.2.0-beta.1`). `tools/release.nu --dry-run` prints the next version
  and its notes without building or publishing. The design is in
  [`doc/architecture/specs/distribution.spec.md`](doc/architecture/specs/distribution.spec.md).

## Commits

Commit messages follow [Conventional Commits](https://www.conventionalcommits.org); the
`commit-msg` hook checks them with [`tools/commit-msg.nu`](tools/commit-msg.nu). `feat` releases a
minor version, `fix`, `perf`, `revert` and `build(deps)` a patch, and a breaking change (`feat!:`
or a `BREAKING CHANGE:` footer) a minor one too: Koma stays at 0.x, from the `v0.0.0` tag on the
root commit.
`docs`, `ci`, `chore`, `refactor`, `style` and `test` release nothing.

## Install and updates

Install Koma from
<https://github.com/antoine-bouteiller/koma/releases/latest/download/koma.apk>. At each launch the
app asks GitHub for the latest stable release; when it is newer, an Update pill in the header
offers it. Its dialog downloads the APK inside the app and opens Android's installer on it, which
asks to install it over the current one. The first time, Android asks to let Koma install apps.

The publish job reads these repository secrets: `EXACT_KEYSTORE_BASE64`,
`EXACT_KEYSTORE_PASSWORD`, `EXACT_KEY_ALIAS` and `EXACT_KEY_PASSWORD` (the release upload key). It
hands them to the build as `KOMA_KEYSTORE` and the `KOMA_KEY*` variables.
