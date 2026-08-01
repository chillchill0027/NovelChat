# NovelChat Codex Development Guide

This file is the starting context for Codex and other coding agents working on
NovelChat. Read it together with `README.md`, `DEVELOPMENT_STATUS.md`, and
`docs/TECH_DESIGN_V1.md` before making broad changes.

## Product Goal

NovelChat is a highly usable Android interactive-story application. It combines
long-form narrative generation with persistent player, character, worldbook,
task, and scene state. Preserve story continuity and existing user data above
all else.

## Project Map

- `app/src/main/java/com/novelchat/ui`: Compose screens and UI policies.
- `app/src/main/java/com/novelchat/ui/viewmodel`: screen state and user flows.
- `app/src/main/java/com/novelchat/domain/engine`: story generation pipeline,
  model prompts, state policies, and round orchestration.
- `app/src/main/java/com/novelchat/data/remote`: OpenAI-compatible API client.
- `app/src/main/java/com/novelchat/data/storage`: JSON persistence and migration.
- `app/src/main/java/com/novelchat/data/model`: persisted and transport models.
- `app/src/test`: local policy and engine tests.
- `app/src/androidTest`: device-backed storage and integration tests.
- `docs`: story import schema, user guide, and architecture documentation.

## Required Workflow

1. Inspect the affected code and existing tests before editing.
2. Keep persisted JSON backward compatible. Add migrations or tolerant defaults
   when a model changes.
3. Never clear app data, uninstall the app, delete stories, or confirm a real AI
   generation on a user's story unless explicitly authorized.
4. Never commit API keys, `local.properties`, user stories, screenshots, build
   output, or device dumps.
5. For generation changes, verify narrative output, task assignment, state
   synchronization, interruption recovery, and local small-model behavior.
6. For UI changes, validate on a phone-sized viewport and inspect the Compose
   semantics tree for clipped, overlapping, or inaccessible controls.

## Build Gates

Set `ANDROID_HOME` when it is not already available:

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew testDebugUnitTest lintDebug app:compileDebugAndroidTestKotlin assembleDebug
```

Run storage integration tests on an API 26+ emulator:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.novelchat.data.storage.FileStorageManagerInstrumentedTest
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Device Debugging

Use ADB or the installed `android-device-control` Codex skill. Record the active
input method before testing and restore it if changed. Prefer semantic UI nodes
over fixed coordinates. Use `adb install -r` so application data is preserved.

Useful commands:

```bash
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.novelchat -c android.intent.category.LAUNCHER 1
adb logcat --pid="$(adb shell pidof -s com.novelchat)"
```

## Persistence And Regeneration

Every newly generated round captures a `RoundCheckpoint` before generation.
Editing or regenerating a round restores that checkpoint and removes the target
and later narrative, summary, journal, and checkpoint data before rebuilding the
branch. Legacy rounds without checkpoints receive a one-time compatibility
checkpoint so old stories can use the same in-place workflow.

Do not weaken this behavior without adding storage integration coverage.

## API Compatibility

The application supports multiple saved OpenAI-compatible connections and
per-stage routing. Remote HTTP endpoints, credentials embedded in URLs, and
query-bearing API base URLs are rejected. Local or private-network HTTP services
may omit an API key. Gemma and Qwen-class local models require conservative
timeouts, bounded context snapshots, and compatible reasoning/structured-output
fallbacks.

## Release Checklist

- Update `versionCode`, `versionName`, and `CHANGELOG.md`.
- Run all build gates.
- Confirm the release tree excludes local settings and generated artifacts.
- Build and archive the APK with the version in its filename.
- Commit only reviewed source and documentation changes.
