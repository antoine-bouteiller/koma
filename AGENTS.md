# Koma

A native Android app: Kotlin, Jetpack Compose, one Gradle module (`app/`). `README.md` has the
package layout and the commands. The design is in `doc/architecture/specs/`.

Run everything inside the dev shell (`nix develop`, or direnv).

- Before finishing a change: `./gradlew testDebugUnitTest lintDebug` and `nix fmt`. Lint treats
  warnings as errors.
- Tests are JVM tests under Robolectric. Drive the UI through `testing/AppHarness.kt` and its test
  tags; stub every request with `FakeNetwork`. An unstubbed request fails the test.
- A feature package imports `data`, `ui` and `version`, never another feature nor
  `app.koma.data.mangabaka`. Screens decide nothing: what they show comes from a ViewModel's
  state, built by pure functions.
- Every Android-only capability is a seam that `AppContainer` injects. Tests swap seams by building
  the container, never by mocking.
- Commit messages follow Conventional Commits; they decide releases (`README.md`, "Commits").
