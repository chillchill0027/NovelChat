# Changelog

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
