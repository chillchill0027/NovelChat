import type { NarrativeEntry, StoryState, WorldbookEntry } from '../types/models';

/**
 * 3-tier priority Worldbook Matcher mirroring Android WorldbookMatcher.kt:
 * Tier 0: Current location exact/keyword match (highest)
 * Tier 1: Mentioned in recent narrative turns
 * Tier 2: Mentioned in user input or pending events
 */
export function matchWorldbook(
  userMessage: string,
  storyState: StoryState,
  allEntries: WorldbookEntry[],
  recentNarratives: NarrativeEntry[],
  maxEntries = 10,
): WorldbookEntry[] {
  const activeEntries = allEntries.filter((e) => e.is_active !== false);
  const matched = new Map<string, { entry: WorldbookEntry; priority: number }>();

  const loc = storyState.current_location.trim();
  if (loc) {
    for (const entry of activeEntries) {
      if (
        entry.type === 'location' &&
        (entry.name === loc || entry.keywords.some((kw) => kw === loc))
      ) {
        if (!matched.has(entry.id)) {
          matched.set(entry.id, { entry, priority: 0 });
        }
      }
    }
  }

  const recentText = recentNarratives.map((n) => n.narrative).join(' ').toLowerCase();
  if (recentText) {
    for (const entry of activeEntries) {
      if (matched.has(entry.id)) continue;
      if (entry.keywords.some((kw) => kw.trim().length > 0 && recentText.includes(kw.toLowerCase()))) {
        matched.set(entry.id, { entry, priority: 1 });
      }
    }
  }

  const decisionText = `${userMessage} ${storyState.pending_events.join(' ')}`.toLowerCase();
  for (const entry of activeEntries) {
    if (matched.has(entry.id)) continue;
    if (entry.keywords.some((kw) => kw.trim().length > 0 && decisionText.includes(kw.toLowerCase()))) {
      matched.set(entry.id, { entry, priority: 2 });
    }
  }

  return Array.from(matched.values())
    .sort((a, b) => a.priority - b.priority)
    .slice(0, maxEntries)
    .map((x) => x.entry);
}
