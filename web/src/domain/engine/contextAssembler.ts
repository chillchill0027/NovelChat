import type {
  CharacterCard,
  ChapterSummary,
  LengthPreference,
  StoryRecord,
  WorldbookEntry,
} from '../../types/models';
import { matchWorldbook } from '../worldbookMatcher';
import { computeLengthHint, type LengthHint } from './lengthPolicy';
import {
  buildNarratorSystemPrompt,
  buildTurnContext,
  DEFAULT_STYLE_GUIDE_ZH,
  formatHistoryUser,
  formatUserTurn,
} from './prompts';
import {
  renderNpcBrief,
  renderNpcFullSheet,
  renderPlayerProfile,
  renderPlayerStatus,
  renderWorldProse,
} from './renderers';

export interface LlmMessage {
  role: 'system' | 'user' | 'assistant';
  content: string;
}

export interface ContextBudget {
  systemChars: number;
  chapterChars: number;
  historyChars: number;
  latestTurnFloorChars: number;
  contextChars: number;
  maxWorldbookEntries: number;
  maxPresentNpcs: number;
  maxRelatedNpcs: number;
  maxMemoriesPerNpc: number;
  maxPendingEvents: number;
}

export const DEFAULT_CONTEXT_BUDGET: ContextBudget = {
  systemChars: 4200,
  chapterChars: 2400,
  historyChars: 9000,
  latestTurnFloorChars: 2600,
  contextChars: 3800,
  maxWorldbookEntries: 8,
  maxPresentNpcs: 6,
  maxRelatedNpcs: 4,
  maxMemoriesPerNpc: 3,
  maxPendingEvents: 4,
};

export interface ContextDebugInfo {
  systemChars: number;
  chapterChars: number;
  historyChars: number;
  historyTurnsIncluded: number;
  contextChars: number;
  presentNpcs: string[];
  relatedNpcs: string[];
  worldbookNames: string[];
  trimmedItems: string[];
}

export interface AssembledPrompt {
  systemPrompt: string;
  history: LlmMessage[];
  turnContext: string;
  userTurn: string;
  lengthHint: LengthHint;
  debug: ContextDebugInfo;
}

function renderWorldbookLine(entry: WorldbookEntry): string {
  const desc = entry.description.trim().slice(0, 200);
  if (entry.is_secret || !entry.known_by_player) {
    return `${entry.name}（作者备注，角色不会主动透露）：${desc}`;
  }
  return `${entry.name}：${desc}`;
}

function selectChapters(
  chapters: ChapterSummary[],
  budgetChars: number,
  trimmedItems: string[],
): { text: string; lastToTurn: number } {
  if (!chapters.length || budgetChars <= 0) return { text: '', lastToTurn: 0 };
  const overview = chapters.find((c) => c.is_overview);
  const coveredByOverview = overview?.to_turn ?? 0;
  const regular = chapters
    .filter((c) => !c.is_overview && c.to_turn > coveredByOverview)
    .sort((a, b) => a.index - b.index);
  const lastToTurn = Math.max(...chapters.map((c) => c.to_turn));

  const chosen: string[] = [];
  let remaining = budgetChars;

  if (overview?.text?.trim()) {
    const line = `早先梗概（至第${overview.to_turn}轮）：${overview.text.trim()}`;
    if (line.length <= remaining) {
      chosen.push(line);
      remaining -= line.length + 1;
    } else if (remaining >= 24) {
      chosen.push(line.slice(0, remaining));
      remaining = 0;
    }
  }

  const selectedRegular: ChapterSummary[] = [];
  for (let i = regular.length - 1; i >= 0; i--) {
    const chapter = regular[i];
    const line = `第${chapter.from_turn}-${chapter.to_turn}轮：${chapter.text.trim()}`;
    if (line.length <= remaining) {
      selectedRegular.unshift(chapter);
      remaining -= line.length + 1;
    } else {
      trimmedItems.push(`chapter:${chapter.index}`);
    }
  }
  for (const ch of selectedRegular) {
    chosen.push(`第${ch.from_turn}-${ch.to_turn}轮：${ch.text.trim()}`);
  }
  return { text: chosen.join('\n'), lastToTurn };
}

function selectHistory(
  record: StoryRecord,
  budgetChars: number,
  latestFloorChars: number,
): { messages: LlmMessage[]; totalChars: number; turnsIncluded: number; oldestTurn: number } {
  if (!record.narratives.length || budgetChars <= 0) {
    return { messages: [], totalChars: 0, turnsIncluded: 0, oldestTurn: Number.MAX_SAFE_INTEGER };
  }
  const sorted = [...record.narratives].sort((a, b) => a.turn - b.turn);
  const selected: { turn: number; user: LlmMessage; assistant: LlmMessage }[] = [];
  let totalChars = 0;

  for (let i = sorted.length - 1; i >= 0; i--) {
    const entry = sorted[i];
    const userMsg: LlmMessage = { role: 'user', content: formatHistoryUser(entry.user_input) };
    const assistantText = entry.narrative.trim();
    const pairChars = userMsg.content.length + assistantText.length;

    if (totalChars + pairChars <= budgetChars) {
      selected.unshift({
        turn: entry.turn,
        user: userMsg,
        assistant: { role: 'assistant', content: assistantText },
      });
      totalChars += pairChars;
    } else if (selected.length === 0) {
      const allowed = Math.min(latestFloorChars, Math.max(300, budgetChars - userMsg.content.length));
      const trimmed =
        assistantText.length > allowed ? `…${assistantText.slice(-allowed)}` : assistantText;
      selected.unshift({
        turn: entry.turn,
        user: userMsg,
        assistant: { role: 'assistant', content: trimmed },
      });
      totalChars += userMsg.content.length + trimmed.length;
      break;
    } else {
      break;
    }
  }

  return {
    messages: selected.flatMap((p) => [p.user, p.assistant]),
    totalChars,
    turnsIncluded: selected.length,
    oldestTurn: selected[0]?.turn ?? Number.MAX_SAFE_INTEGER,
  };
}

function resolvePresentNpcs(
  allCards: CharacterCard[],
  activeNames: string[],
  maxPresent: number,
): CharacterCard[] {
  const out: CharacterCard[] = [];
  const seen = new Set<string>();
  for (const raw of activeNames) {
    const name = raw.trim();
    if (!name) continue;
    const match = allCards.find(
      (c) =>
        c.name === name ||
        c.id === name ||
        c.lore.aliases.some((a) => a.trim() === name),
    );
    if (match) {
      const key = match.id || match.name;
      if (!seen.has(key)) {
        seen.add(key);
        out.push(match);
      }
    }
    if (out.length >= maxPresent) break;
  }
  return out;
}

export function assembleNarratorContext(
  record: StoryRecord,
  rawInput: string,
  defaultLength: LengthPreference,
  budget: ContextBudget = DEFAULT_CONTEXT_BUDGET,
): AssembledPrompt {
  const trimmedItems: string[] = [];
  const playerName = record.player.name.trim() || '主角';
  const isOpening = record.narratives.length === 0;
  const effectiveLength = record.narration.length ?? defaultLength;
  const lengthHint = computeLengthHint(rawInput, effectiveLength, isOpening);

  const worldProse = renderWorldProse(record.world, Math.floor(budget.systemChars * 0.55));
  const playerProfile = renderPlayerProfile(
    record.player,
    record.world,
    Math.floor(budget.systemChars * 0.3),
  );
  const { text: chaptersText, lastToTurn } = selectChapters(
    record.chapters,
    budget.chapterChars,
    trimmedItems,
  );

  const systemPrompt = buildNarratorSystemPrompt({
    playerName,
    pov: record.narration.pov,
    styleGuide: record.world.lore.narrative_style || DEFAULT_STYLE_GUIDE_ZH,
    styleSample: record.narration.style_sample,
    worldProse,
    playerProfile,
    chaptersText,
  });

  const historySelection = selectHistory(record, budget.historyChars, budget.latestTurnFloorChars);

  const recentSummaries = record.summaries
    .filter(
      (s) =>
        s.turn > lastToTurn &&
        s.turn < historySelection.oldestTurn &&
        s.summary.trim().length > 0,
    )
    .sort((a, b) => a.turn - b.turn)
    .map((s) => `第${s.turn}轮：${s.summary.trim()}`);

  const recentNarratives = record.narratives.slice(-8);
  const matchedWorldbook = matchWorldbook(
    rawInput,
    record.story,
    record.worldbook,
    recentNarratives,
    budget.maxWorldbookEntries,
  );

  const presentCards = resolvePresentNpcs(
    record.characters,
    record.story.active_characters,
    budget.maxPresentNpcs,
  );
  const presentKeys = new Set(presentCards.map((c) => c.id || c.name));

  const mentionPool = `${rawInput} ${record.story.pending_events.join(' ')} ${matchedWorldbook
    .flatMap((w) => w.related_characters)
    .join(' ')} ${recentNarratives.slice(-2).map((n) => n.narrative.slice(-400)).join(' ')}`;

  const candidateRelated = record.characters
    .filter((c) => {
      if (presentKeys.has(c.id || c.name)) return false;
      return mentionPool.includes(c.name) || c.lore.aliases.some((a) => a && mentionPool.includes(a));
    })
    .slice(0, budget.maxRelatedNpcs);

  const currentGoal =
    record.player.story_progress.current_goal.trim() ||
    record.story.pending_events[0]?.trim() ||
    '';
  const otherPendingAll = record.story.pending_events
    .map((s) => s.trim())
    .filter((s) => s && s !== currentGoal);
  const playerStatus = renderPlayerStatus(record.player, record.world);
  const currentRound = Math.max(
    record.story.current_round,
    record.narratives.reduce((m, n) => Math.max(m, n.turn), 0),
  );

  let wbLimit = matchedWorldbook.length;
  let relatedLimit = candidateRelated.length;
  let memLimit = budget.maxMemoriesPerNpc;
  let pendingLimit = Math.min(otherPendingAll.length, budget.maxPendingEvents);

  const renderCtx = () =>
    buildTurnContext({
      timeOfDay: record.story.time_of_day,
      location: record.story.current_location,
      weather: record.story.weather,
      sceneDescription: isOpening ? record.story.scene_description : '',
      playerName,
      presentNames: presentCards.map((c) => c.name),
      playerStatus,
      currentGoal,
      otherPending: otherPendingAll.slice(0, pendingLimit),
      presentSheets: presentCards.map((c) =>
        renderNpcFullSheet(c, playerName, record.world.character_fields, currentRound, memLimit),
      ),
      relatedBriefs: candidateRelated.slice(0, relatedLimit).map((c) => renderNpcBrief(c, playerName)),
      worldbookLines: matchedWorldbook.slice(0, wbLimit).map(renderWorldbookLine),
      recentSummaries,
      lengthHint,
    });

  let turnContext = renderCtx();
  if (turnContext.length > budget.contextChars && wbLimit > 4) {
    wbLimit = 4;
    trimmedItems.push('worldbook:->4');
    turnContext = renderCtx();
  }
  if (turnContext.length > budget.contextChars && relatedLimit > 0) {
    relatedLimit = 0;
    trimmedItems.push('relatedNpcs:->0');
    turnContext = renderCtx();
  }
  if (turnContext.length > budget.contextChars && memLimit > 1) {
    memLimit = 1;
    trimmedItems.push('npcMemories:->1');
    turnContext = renderCtx();
  }
  if (turnContext.length > budget.contextChars && memLimit > 0) {
    memLimit = 0;
    trimmedItems.push('npcMemories:->0');
    turnContext = renderCtx();
  }
  if (turnContext.length > budget.contextChars && pendingLimit > 0) {
    pendingLimit = 0;
    trimmedItems.push('pendingEvents:->0');
    turnContext = renderCtx();
  }
  if (turnContext.length > budget.contextChars && wbLimit > 2) {
    wbLimit = 2;
    trimmedItems.push('worldbook:->2');
    turnContext = renderCtx();
  }

  return {
    systemPrompt,
    history: historySelection.messages,
    turnContext,
    userTurn: formatUserTurn(turnContext, rawInput),
    lengthHint,
    debug: {
      systemChars: systemPrompt.length,
      chapterChars: chaptersText.length,
      historyChars: historySelection.totalChars,
      historyTurnsIncluded: historySelection.turnsIncluded,
      contextChars: turnContext.length,
      presentNpcs: presentCards.map((c) => c.name),
      relatedNpcs: candidateRelated.slice(0, relatedLimit).map((c) => c.name),
      worldbookNames: matchedWorldbook.slice(0, wbLimit).map((w) => w.name),
      trimmedItems,
    },
  };
}
