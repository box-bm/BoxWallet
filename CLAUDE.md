# BoxWallet — working guide

Android app (Kotlin, Jetpack Compose) for budgets, expenses and scheduled incomes.
Code lives in `app/src/main/java/.../` (`data`, `domain`, `ui`, `di`, `worker`, `analytics`, `util`);
unit tests in `app/src/test/java/`. Product and design context: `PRODUCT.md`, `DESIGN.md`.

## Golden rules

- **Never commit or push to `main` directly.** Every change goes through a branch + PR.
- **Never edit `version.properties` by hand, and never create `v*` tags or GitHub releases manually.**
  The **Deploy** workflow owns versioning.
- The **PR title is the release input**: it lands in the merge commit and decides the version bump,
  so it must be a Conventional Commit.
- Don't commit secrets: `app/google-services.json`, `*.jks`, `play-store-key.json`, `local.properties`.

## Day-to-day workflow

1. Branch from an up-to-date `main`:
   `git switch main && git pull --ff-only && git switch -c <type>/<short-name>`
   (e.g. `feat/budgets`, `fix/loan-rounding`, `chore/ci-cache`).
2. Make the change and add/update unit tests next to the code you touch
   (`app/src/test/java/<same package>/<Class>Test.kt`). PR Checks runs tests by matching file names,
   so keeping `FooTest.kt` beside `Foo.kt`'s package lets CI pick it up.
3. Verify locally before pushing: `./gradlew testDebugUnitTest`
   (or a single test: `./gradlew testDebugUnitTest --tests com.example.FooTest`).
4. Commit with Conventional Commits, push, and open the PR with `gh pr create --base main`.
5. Wait for **PR Checks** to pass, then merge with a merge commit (the merge body carries the PR title,
   which the version script reads).

## Conventional Commits → version bump

Format: `type(optional-scope)!: description`. Allowed types:
`feat|feature|fix|bugfix|perf|refactor|chore|docs|test|build|ci|style|revert`.

| PR title / commit | Bump |
|---|---|
| `feat!: …` / any `type!:` / `BREAKING CHANGE:` in body | **major** |
| `feat: …` / `feature: …` | **minor** |
| anything else (`fix`, `perf`, `refactor`, `chore`, …) | **patch** |

The highest bump across all commits/PRs since the last `v*` tag wins.
`feat` and `fix` descriptions become the Play Store "What's new" text, so write them for users.

## GitHub workflows (`.github/workflows/`)

| Workflow | Trigger | What it does |
|---|---|---|
| **PR Checks** (`pr-tests.yml`) | PR opened / updated / title edited | Validates the PR title (Conventional Commits) and reports the version impact; runs only related unit tests (all tests if build files changed, compile-only if none match). Logic: `.github/scripts/related-tests.sh`. |
| **Main CI** (`main-ci.yml`) | push to `main` touching `*.kt`, `*.kts`, `gradle/**` | Full unit test suite. Skips `[skip ci]` release commits. |
| **Deploy** (`deploy.yml`) | manual, from `main` only | Computes the next version (`.github/scripts/next-version.sh`), runs tests, writes `version.properties` + Play changelogs, builds the signed AAB, commits `chore(release): vX.Y.Z (build N) [skip ci]`, tags `vX.Y.Z`, uploads to Play **internal**, creates a **draft** GitHub release (AAB + mapping). |
| **Publish Release** (`publish-release.yml`) | manual | After promoting the build to production in Play Console, publishes the draft release and marks it **Latest** (empty input = most recent draft). |

### Releasing

1. Merge the PRs to ship into `main` and make sure **Main CI** is green.
2. Run Deploy: `gh workflow run deploy.yml --ref main` (inputs: `-f bump=auto|patch|minor|major`,
   `-f release_status=completed|draft`, `-f upload_store_listing=true|false`). Watch with `gh run watch`.
3. Test the internal build, promote it to production in Play Console.
4. Run Publish Release: `gh workflow run publish-release.yml` (optionally `-f tag=vX.Y.Z`).

Notes:
- Re-running Deploy with no new commits redeploys the current tag without bumping.
- **First release (no `v*` tag)** ships `versionName` from `version.properties` as-is (currently `1.0.0`)
  with `versionCode + 1`; Play notes fall back to `changelogs/default.txt`.
- `versionCode` must only ever increase — Google Play rejects lower or reused codes, even after a version reset.
- After Deploy, run `git pull` on `main` locally: it pushes the release commit.
- To override the generated Play changelog (e.g. translated text), commit
  `fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt` in a PR before deploying.

## Required repository secrets

`GOOGLE_SERVICES_JSON`, `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`,
`PLAY_STORE_JSON_KEY` (base64-encoded where the workflows `base64 -d` them).

## Changing CI

- Workflow changes ship in a PR like anything else; `ci:` titles bump patch.
- The version script uses bash 4+ features (`declare -A`, `grep -P`): test it on Linux, not macOS's bash 3.2.
- Keep `README.md` (CI/CD section) and this file in sync when workflows change.
