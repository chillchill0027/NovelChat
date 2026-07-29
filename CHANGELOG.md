# Changelog

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

