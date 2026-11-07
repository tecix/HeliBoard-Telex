# Keeping up with upstream HeliBoard

HeliBoard Telex is HeliBoard ([HeliBorg/HeliBoard](https://github.com/HeliBorg/HeliBoard)) plus a
Vietnamese Telex layer. The layer is kept apart from upstream code so new HeliBoard versions can be
merged with few conflicts:

| Part | Location | Touches upstream files? |
|---|---|---|
| Telex engine, lexicon, slip correction (pure Kotlin/JVM) | `viettelex-core/` | no |
| Glue to HeliBoard: combiner, hooks, settings, settings UI | `app/src/main/java/helium314/keyboard/viettelex/` | no |
| UI strings | `res/values*/strings_viettelex.xml` | no |
| One-line hook calls | the files listed below | yes, small |

## Automatic sync

`.github/workflows/sync-upstream.yml` runs every Monday (and on demand from the Actions tab, where a
release tag such as `v4.4` can be chosen instead of `main`). It runs `scripts/sync-upstream.sh`,
which merges upstream into a copy of `main` and runs the Vietnamese tests. Then:

- **Merged, tests pass**: pushes `upstream-sync/<ref>-<sha>`, builds the debug APK (run artifact) and
  opens a pull request. Install the APK, type a few Vietnamese words, then merge the PR.
- **Merged, Vietnamese tests fail**: the PR is still opened, marked as failing; fix it on that branch.
- **Conflict**: an issue lists the conflicting files; resolve locally as below.

The workflow needs the repository setting *Settings → Actions → General → Allow GitHub Actions to
create and approve pull requests*. Without it, the branch is still pushed and an issue with a
"compare" link is opened instead of the pull request.

## Manual sync / resolving conflicts

```sh
git checkout -b upstream-sync/manual main
scripts/sync-upstream.sh            # or: scripts/sync-upstream.sh v4.4
# on conflict: fix the files, keeping the hook calls listed below
git add -A && git commit --no-edit
./gradlew :viettelex-core:test :app:testDebugUnitTest \
    --tests 'helium314.keyboard.viettelex.*' --tests 'helium314.keyboard.latin.VietTelexInputLogicTest'
```

When resolving, take upstream's version of the code and re-add our hook lines around it. Never move
Vietnamese logic back into upstream files; put it in `VietTelexHooks` instead.

`VietTelexInputLogicTest` types through the real `LatinIME`/`InputLogic`. It fails when an upstream
change makes a hook stop running even if the engine tests still pass, so check it before merging.

## Hook points in upstream files

**`latin/inputlogic/InputLogic.java`**
- `startInput`: `restartCombining(VietTelexHooks.combiningSpecFor(...))` and `VietTelexHooks.resetContext`
- `onCodeInput`: `mConnection.beginBatchEdit()` moved before `VietTelexHooks.beforeCodeInput(...)`,
  which returns `ACTION_UNLEARN_AND_RESET` / `ACTION_COMMIT_TYPED` for InputLogic to perform
- `handleNonFunctionalEvent`, combining branch: `viet_telex` shares Khipro's auto-space after a picked
  suggestion; DELETE events from a combiner update space state, delete count and shift state
- `handleSeparatorEvent`: `VietTelexHooks.finishBoundary` before committing,
  `VietTelexHooks.noteBoundaryCharacter` after the separator
- `restartSuggestionsAfterDelete`: final `else` calls `VietTelexHooks.reopenAfterDelete`
- `resetEntireInputState`: `VietTelexHooks.resetContext`
- `commitTyped`, `commitCurrentAutoCorrection`: `VietTelexHooks.finishBoundary`

**`latin/WordComposer.java`**: raw keystroke cache (`mVietTelexRawKeystrokes`, copied in the copy
constructor together with `mCombiningSpec`), cursor-at-end notification in
`setCursorPositionWithinWord`, Vietnamese branches in `setBatchInputWord` / `setComposingWord`
(resume the word without replaying it through the combiner), and the accessors
`getVietTelexRawKeystrokes`, `hasVietTelexCombiner`, `getVietTelexCombiner`, `syncVietTelexState`.

**`latin/suggestions/Suggest.kt`**: `VietTelexHooks.addSuggestions(...)` after
`makeFirstTwoSuggestionsNonEmoji`.

**`event/CombinerChain.kt`**: `viet_telex` → `VietTelexCombiner`, `vietTelexCombiner`,
`refreshStateFeedback`.

**`latin/LatinIME.java`**: `VietTelexHooks.onCreate(this)` at the start of `onCreate`.

**Vietnamese subtype**: `res/xml/method.xml` (`CombiningRules=viet_telex` on the `vi` subtype),
`latin/utils/SubtypeLocaleUtils.kt` (`vi` defaults to `viet_telex`),
`settings/screens/LanguageScreen.kt` (no missing-dictionary dialog for `vi`).

**Settings UI**: `settings/SettingsContainer.kt` (`createVietnameseSettings`),
`settings/screens/SubtypeScreen.kt` (`VietnameseSubtypeSettings(...)`).

**Fork build/branding** (not Vietnamese logic): `settings.gradle` and `app/build.gradle.kts`
(`:viettelex-core`, arm64 only), `jni/Application.mk`, `res/values/donottranslate.xml` (app name),
`README.md`, `.github/workflows/build-debug-apk.yml`.
