# Changelog

## 2.0.0 - 2026-11-26

### Breaking / Architecture

- Promoted Engine V2 to the sole runtime engine and removed the legacy V1 engine (`RpgGameEngine`, `RpgPromptBuilder`, `GameEvent`, 10 V1-only engine policies, and V1-only tests) along with the V1/V2 UI toggle.
- Simplified API stage routing UI to the 5 active V2 stages (`STORY_IMPORT`, `CHARACTER_SETUP`, `NARRATIVE`, `STATE_KEEPER`, `SUMMARIZER`) while preserving backward compatibility for legacy persisted saves, V1 round checkpoints, `state_sync_pending_from_round` recovery, and saved `task_assignment` routes.

### Changed

- Completely redesigned the UI from first principles using a Material 3 Expressive "Midnight Ink & Warm Lamp" editorial dark design system (`Color.kt`, `Type.kt`, `Theme.kt`, `Components.kt`) with 5-level tonal surface depth, warm ivory reading typography (`16sp` / `27sp` line height), deterministic character monogram avatars, segmented pill selectors, and smooth `NavHost` transitions.
- Rebuilt `ChatScreen` around reading immersion: direct top-bar shortcuts for Worldbook and Characters, a unified collapsible Scene & Protagonist Capsule, Gemini-style unified floating pill composer with airy suggestion chips, and Material 3 `ModalBottomSheet` drawers for Player Details, NPC Details, Narration Settings, and Performance Debug.
- Rebuilt `WelcomeScreen`, `MainLobbyScreen`, `ApiSetupScreen`, `StoryImportScreen`, `StoryConfirmScreen`, `CharacterConfirmScreen`, `PresetSelectScreen`, `CharacterCustomScreen`, `CharacterListScreen`, `CharacterDetailScreen` (with speech-profile bottom sheet), and `WorldbookScreen` (with category filter pills, keyword chips, extended FAB, and editor bottom sheet).
- Adapted edge-to-edge display, punch-hole cutout (`shortEdges`), HyperOS dark mode (`forceDarkAllowed=false`), navigation bar transparency, and IME insets for China Mainland Xiaomi 17 Pro (`1220×2656 @ 480dpi`).
- Added full Material 3 Adaptive large-screen support for China Mainland Xiaomi Pad 6 Pro (`2880×1800 @ 360dpi`, `1280dp × 800dp` landscape & `800dp × 1280dp` portrait, plus HyperOS split-screen and floating windows): persistent two-pane Story Sidebar (with grouped `SidebarNpcRow` character roster and pinned Worldbook/Character shortcuts) + centered `17sp/29sp` reading pane in landscape `ChatScreen`, hardware-keyboard `Enter` / `Ctrl+Enter` send support, two-column `ApiSetupScreen` / `WelcomeScreen` / `CharacterDetailScreen` layouts, and 2-to-3 column adaptive card grids in `MainLobbyScreen`, `PresetSelectScreen`, `CharacterListScreen`, and `WorldbookScreen`.
- Redesigned ChatScreen scroll-to-latest control into a compact translucent bottom pill with expanded list bottom padding so it never occludes prose lines.
- Improved numeric formatting and semantic colors in `CharacterFieldPolicy.formatValue` and chat detail dialogs: removed absurd `/120` age, `/9999` level, and `/100%` percentage denominators, and colored risk/alertness gauges inversely (`0` green -> high red) while keeping identity/ability stats neutral.
- Improved `CharacterDetailScreen` and detail dialogs to use full-width blocks for profile fields (`appearance`, `personality`, `background`, `skills`, and strings > 16–18 chars) and omit blank `"未知"` clutter rows.
- Updated `WorldbookScreen` bottom padding (`96.dp`) so the `+` FAB never obscures bottom cards and refined status pill presentation.
- Updated imported story lobby subtitles to concise `世界名 · 主角身份`, localized `PresetSelectScreen` rank labels (`阶层 E/D/C`), and improved contrast on disabled `CharacterConfirmScreen` buttons.

### Fixed

- Fixed Chinese Pinyin IME full-width URL punctuation (`：`, `／`, `．`, `。`) causing API endpoint validation failures (`ApiEndpointPolicy`, `SetupFlowPolicy`, `ApiSetupScreen`).
- Fixed `sample_dialogues` being dropped during JSON story import (`JsonResponseParser`, `StoryParseContentPolicy`, `StoryConfirmScreen`).
- Fixed `StateDeltaApplier` overwriting existing rich worldbook descriptions instead of appending new facts.
- Fixed `StoryImportScreen` guide bottom sheet and `PresetSelectScreen` / `CharacterCustomScreen` bottom content clipping behind the system gesture navigation bar.

### Verification

- Passed 385 JVM unit tests (0 failures, 0 skipped), `lintDebug`, Android instrumented-test source compilation, and `assembleDebug` / `assembleQa` APK builds.
- Passed 12 Android instrumentation tests (`OK (12 tests)`) and visually verified live foreground screens across both Xiaomi 17 Pro (`1220×2656 @ 480dpi`) and Xiaomi Pad 6 Pro (`2880×1800 @ 360dpi`, landscape and portrait) emulators with zero crashes.

## 1.1.0 - 2026-11-26

### Added

- Added switchable Engine V2 (`V1` / `V2` selector in API setup; V1 remains default until live API eval completion), decoupling foreground streaming prose (`NarratorAgent`, `RoundOrchestrator`) from background state sync (`StateKeeperAgent`, `SyncQueue`) and 10-round/60-round hierarchical chapter memory (`MemoryManager`).
- Added prefix-cache-friendly prompt assembly (`ContextAssembler`, `WorldRenderer`, `PlayerRenderer`, `CharacterSheetRenderer`), sampling penalty fallback (`SamplingPenaltyFallbackPolicy`), SSE total/idle watchdogs, and `cached_tokens` / TTFT instrumentation (`LlmScheduler`, `LlmMetrics`).
- Added atomic single-writer repository locking (`StoryRepository`), two-phase commit journals (`RoundCommitJournal`), `provisional` round-checkpoint patching across overlapped turns, and recovery of legacy V1 `state_sync_pending_from_round` saves.
- Added per-story narration settings dialog (`NarrationSettingsDialog` for POV, length override, creativity, and style samples), NPC speech-profile editor (`CharacterDetailScreen`), debug/QA performance inspector (`PerfDebugSheet`), and V2 API routing stages (`STATE_KEEPER`, `SUMMARIZER`).

### Changed

- V2 rounds now stream natural prose directly without JSON wrappers and run background state sync asynchronously without blocking subsequent player turns (`ChatInputPolicy`, `StoryContextPanel`, `SyncRetryBanner`).
- Concurrent character, worldbook, and confirmation writes now route through `StoryRepository` locks, and deleting a conversation closes its active V2 session cleanly (`MainLobbyViewModel`).
- Updated `CharacterAttributeMapper` (shared by V1 and V2) so `manual` attribute filtering occurs before merging custom attributes.

### Fixed

- Fixed ChatStreamDecoder dropping token usage telemetry on trailing `[DONE]` chunks with empty `choices`.

### Verification

- Passed 438 JVM unit tests (0 failures, 0 skipped), `lintDebug`, Android instrumented-test source compilation, and both `assembleDebug` and `assembleQa` APK builds.
- Ran instrumentation tests on Android emulator (`emulator-5554`, API 37): 11 storage/recovery/context tests passed and 2 opt-in API regression/eval tests were skipped cleanly by assumption; visually verified V2 engine settings and 8-stage routing UI on emulator without crashes.

## 1.0.3 - 2026-08-01

### Changed

- Narrative generation now aims for 1200-1400 characters so normal model
  variation has room inside the accepted 1000-1500 range.
- A pure length miss no longer triggers a second AI generation call. If the
  narrative is otherwise complete, it is saved directly and state sync
  continues without asking the user to repeat the same action.
- Mixed length and structural issues now repair missing actions, evidence order,
  character grounding, and scene metadata without running a length-only pass.

### Fixed

- Removed repeated automatic expansion calls and the target-length rejection
  that followed them.
- Fixed the player name being mistaken for an unresolved NPC in
  `ending_scene.present_characters`.
- Increased narrative output budgets to reduce avoidable truncation on longer
  structured responses.

### Verification

- Passed 393 JVM tests, Android Lint, Android instrumented-test source
  compilation, and debug APK assembly.
- Verified on a connected Android phone with DeepSeek: one narrative request
  proceeded directly to task assignment and character-state refresh, with no
  length-only narrative repair call.

## 1.0.2 - 2026-08-01

### Added

- Added story-owned `character_fields` contracts for renaming, grouping,
  constraining, displaying, and updating player and NPC card fields.
- Added arbitrary story-specific attributes with typed initial values and
  `dynamic`, `event`, or `manual` update modes.
- Added a complete magic-academy import example, integration guide, and
  ten-round DeepSeek device QA report.
- Added partial-result merging and explicit dynamic-field completeness checks
  to state-assignment retries.

### Changed

- Player and NPC cards now render labels, units, ranges, groups, and primary
  indicators from the current story instead of a fixed card layout.
- Natural-language import now infers missing field contracts and initial values;
  advanced JSON import validates them deterministically.
- Narrative state now refreshes the current scene description together with the
  ending location and physically present characters.
- Narratives below the 1000-character target use a dedicated expansion pass and
  are not saved unless the final text is within 1000-1500 characters.

### Fixed

- Fixed alarm, spell, rest, oath, and time changes being omitted by otherwise
  valid task-assignment responses.
- Fixed later task retries overwriting valid fields returned by earlier retries.
- Fixed generic English `partner` references creating an unintended romantic
  character when the context only meant a teammate.
- Preserved full compatibility for stories created before `character_fields`.

### Verification

- Passed 393 JVM tests, Android Lint, Android instrumented-test source
  compilation, and debug APK assembly.

## 1.0.1 - 2026-07-29

### Added

- Added per-stage routing across saved OpenAI-compatible API configurations.
- Added copy, edit, and in-place regeneration controls for story rounds.
- Added round checkpoints covering story, player, character, and worldbook state.
- Added compatibility checkpoints so pre-update stories can regenerate old
  rounds with the same workflow.

### Changed

- Improved prompts for longer, more literary action expansion and proactive
  continuation.
- Increased slow local-model timeouts for narrative review and state correction.
- Refined player and NPC cards to show decision-relevant, slower-moving status.
- Migrated legacy test-story labels to normal story metadata.

### Fixed

- Fixed second-stage task assignment failures and delayed character-card refresh.
- Fixed local 4B models incorrectly appearing to hit the output-length limit.
- Fixed old-round actions missing edit and regenerate controls.
