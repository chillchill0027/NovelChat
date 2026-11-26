import type {
  CharacterCard,
  CharacterFieldDefinition,
  InventoryItem,
  MemoryNote,
  PlayerState,
  StoryRecord,
  SummaryEntry,
  WorldbookEntry,
} from '../../types/models';
import {
  defaultMentalState,
  defaultPhysicalState,
  emptyCharacterLore,
} from '../../types/models';
import type { NpcDelta, StateDelta } from '../../types/stateDelta';
import { appliesTo, effectiveDefinitions, sanitizeFieldValue } from '../characterFields';

const GOAL_NOISE = /[\s，,。\.！!？\?；;：:“”"'（）()\[\]【】—\-…]/g;
const IMPORTANT_MEMORY_RE = /承诺|约定|答应|秘密|偷|背叛|冲突|吵|交易|借|欠|钥匙|合同|誓约|警告|威胁|交付|面试/;
const GENERIC_NPC_RE = /^(主角|玩家|陌生人|路人|邻居|店员|服务员|老板|男子|女子|男人|女人)[A-Z0-9甲乙丙丁一二三四]?$/;
const MAX_MEMORIES = 30;
const MAX_PENDING_EVENTS = 8;
const MAX_DELTA_PER_ROUND = 15;

function normalizeGoal(text: string): string {
  return text.trim().replace(GOAL_NOISE, '').toLowerCase();
}

function addItems(inv: InventoryItem[], name: string, qty: number, acquiredAt: string): InventoryItem[] {
  const next = inv.map((i) => ({ ...i }));
  const idx = next.findIndex((i) => i.name === name);
  if (idx >= 0) {
    next[idx].quantity += Math.max(1, qty);
  } else {
    next.push({
      name,
      quantity: Math.max(1, qty),
      type: '物品',
      description: '',
      origin: '剧情获得',
      acquired_at: acquiredAt,
    });
  }
  return next;
}

function removeItems(inv: InventoryItem[], name: string, qty: number): InventoryItem[] {
  const next: InventoryItem[] = [];
  for (const item of inv) {
    if (item.name === name) {
      const remain = item.quantity - Math.max(1, qty);
      if (remain > 0) next.push({ ...item, quantity: remain });
    } else {
      next.push({ ...item });
    }
  }
  return next;
}

function applyFieldsToPlayer(
  player: PlayerState,
  patch: Record<string, string>,
  defs: CharacterFieldDefinition[],
): PlayerState {
  const next: PlayerState = structuredClone(player);
  const byKey = new Map(defs.filter((d) => appliesTo(d, 'player')).map((d) => [d.key, d]));

  for (const [rawKey, rawVal] of Object.entries(patch)) {
    const key = rawKey.trim();
    const field = byKey.get(key);
    if (!field || field.update_mode === 'manual') continue;
    const val = sanitizeFieldValue(field, rawVal);
    if (val === null) continue;
    switch (key) {
      case 'occupation': next.class = val; break;
      case 'appearance': next.appearance = val; break;
      case 'personality': next.personality = val; break;
      case 'background': next.background = val; break;
      case 'skills':
        next.skills = val.split('、').filter(Boolean).map((name) => ({ name, level: 1 }));
        break;
      case 'level': next.level = Number(val); break;
      case 'charm': next.charm = Number(val); break;
      case 'action_skill': next.hunting_skill = Number(val); break;
      case 'social_rank': next.social_rank = val; break;
      case 'luck': next.luck = Number(val); break;
      case 'willpower': next.alpha = Number(val); break;
      case 'resilience': next.resistance = Number(val); break;
      case 'risk': next.cv = Number(val); break;
      case 'stamina': next.physical_state.stamina = Number(val); break;
      case 'health': next.physical_state.health = val; break;
      case 'fatigue': next.physical_state.fatigue = val; break;
      case 'hunger': next.physical_state.hunger = val; break;
      case 'pain': next.physical_state.pain = val; break;
      case 'mood': next.mental_state.mood = val; break;
      case 'stress': next.mental_state.stress = val; break;
      case 'focus': next.mental_state.focus = val; break;
      case 'current_thoughts': next.mental_state.current_thoughts = val; break;
      default:
        next.custom_attributes[key] = val;
    }
  }
  return next;
}

function applyFieldsToNpc(
  card: CharacterCard,
  patch: Record<string, string>,
  defs: CharacterFieldDefinition[],
  includeManual: boolean,
): CharacterCard {
  const next: CharacterCard = structuredClone(card);
  const byKey = new Map(defs.filter((d) => appliesTo(d, 'npc')).map((d) => [d.key, d]));

  for (const [rawKey, rawVal] of Object.entries(patch)) {
    const key = rawKey.trim();
    const field = byKey.get(key);
    if (!field || (!includeManual && field.update_mode === 'manual')) continue;
    const val = sanitizeFieldValue(field, rawVal);
    if (val === null) continue;
    switch (key) {
      case 'occupation': next.occupation = val; break;
      case 'appearance': next.appearance = val; break;
      case 'personality': next.personality = val; break;
      case 'background': next.background = val; break;
      case 'stamina': next.physical_state.stamina = Number(val); break;
      case 'health': next.physical_state.health = val; break;
      case 'fatigue': next.physical_state.fatigue = val; break;
      case 'hunger': next.physical_state.hunger = val; break;
      case 'pain': next.physical_state.pain = val; break;
      case 'mood':
        next.emotion = val;
        next.mental_state.mood = val;
        break;
      case 'stress': next.mental_state.stress = val; break;
      case 'focus': next.mental_state.focus = val; break;
      case 'current_thoughts': next.mental_state.current_thoughts = val; break;
      default:
        next.custom_attributes[key] = val;
    }
  }
  return next;
}

function appendMemory(
  current: MemoryNote[],
  rawMemory: string | undefined,
  turn: number,
  favorDelta: number,
): MemoryNote[] {
  const text = rawMemory?.trim() ?? '';
  if (!text) return current;
  const norm = normalizeGoal(text);
  if (current.some((m) => normalizeGoal(m.text) === norm)) return current;
  const weight = Math.abs(favorDelta) >= 5 || IMPORTANT_MEMORY_RE.test(text) ? 2 : 1;
  const next = [...current, { turn, text: text.slice(0, 120), weight }];
  while (next.length > MAX_MEMORIES) {
    const idx = next.findIndex((m) => m.weight <= 1);
    if (idx >= 0) next.splice(idx, 1);
    else next.shift();
  }
  return next;
}

function mergeWorldbookDescription(existing: string, incoming: string): string {
  const cur = existing.trim();
  const next = incoming.trim();
  if (!next) return cur;
  if (!cur) return next;
  if (cur.includes(next)) return cur;
  if (next.includes(cur)) return next;
  return `${cur}\n${next}`;
}

export interface ApplyDeltaOutput {
  record: StoryRecord;
  summary?: SummaryEntry;
  suggestions: string[];
}

export function applyStateDelta(
  base: StoryRecord,
  delta: StateDelta,
  rounds: number[],
  now: string,
): ApplyDeltaOutput {
  const rec: StoryRecord = structuredClone(base);
  const targetRound = Math.max(...rounds, rec.story.current_round, 1);
  const defs = effectiveDefinitions(rec.world.character_fields);
  const playerName = rec.player.name.trim() || '主角';
  const targetNarrativeText = rec.narratives
    .filter((n) => rounds.includes(n.turn))
    .map((n) => n.narrative)
    .join('\n');

  const isPlayerAlias = (raw: string) => {
    const t = raw.trim();
    return t === playerName || t === '主角' || t === '我' || t === '玩家';
  };

  const findCardIdx = (raw: string): number => {
    const t = raw.trim();
    if (!t) return -1;
    return rec.characters.findIndex(
      (c) =>
        c.name === t ||
        c.id === t ||
        c.lore.aliases.some((a) => a.trim() === t),
    );
  };

  // 1. New NPCs
  const mergedFromNew: NpcDelta[] = [];
  const createdNames: string[] = [];
  for (const newNpc of delta.new_npcs ?? []) {
    const rawName = (newNpc.name ?? '').trim();
    if (!rawName || GENERIC_NPC_RE.test(rawName) || isPlayerAlias(rawName)) continue;
    const existingIdx = findCardIdx(rawName);
    if (existingIdx >= 0) {
      mergedFromNew.push({
        name: rec.characters[existingIdx].name,
        relationship_stage: newNpc.relationship_to_player?.trim() || undefined,
        fields: newNpc.fields,
      });
      continue;
    }
    const relStage = newNpc.relationship_to_player?.trim() || '初识';
    let card: CharacterCard = {
      id: rawName,
      name: rawName,
      status: 'active',
      rarity: '普通',
      libido: 50,
      threshold: 50,
      emotion: '平静',
      relationship_status: relStage,
      kink: '',
      favor: 0,
      race: '人类',
      age: typeof newNpc.age === 'number' && newNpc.age > 0 ? newNpc.age : 0,
      gender: (newNpc.gender ?? '').trim(),
      occupation: (newNpc.identity ?? '').trim(),
      appearance: (newNpc.appearance ?? '').trim(),
      personality: (newNpc.personality ?? '').trim(),
      background: '',
      lore: {
        ...emptyCharacterLore(),
        speech_style: (newNpc.speech_style ?? '').trim(),
      },
      physical_state: defaultPhysicalState(),
      mental_state: defaultMentalState(),
      custom_attributes: {},
      relationships: { [playerName]: { favor: 0, trust: 0, stage: relStage } },
      inventory: [],
      memories: [],
      created_by: 'ai',
      created_at: now,
      last_modified: now,
    };
    if (newNpc.fields && Object.keys(newNpc.fields).length) {
      card = applyFieldsToNpc(card, newNpc.fields, defs, true);
    }
    rec.characters.push(card);
    createdNames.push(card.name);
  }

  // 2. Update NPCs
  for (const npcDelta of [...(delta.npcs ?? []), ...mergedFromNew]) {
    const rawName = (npcDelta.name ?? '').trim();
    if (!rawName || isPlayerAlias(rawName)) continue;
    let idx = findCardIdx(rawName);
    if (idx < 0) {
      if (!GENERIC_NPC_RE.test(rawName) && targetNarrativeText.includes(rawName)) {
        const minimal: CharacterCard = {
          id: rawName,
          name: rawName,
          status: 'active',
          rarity: '普通',
          libido: 50,
          threshold: 50,
          emotion: '平静',
          relationship_status: '初识',
          kink: '',
          favor: 0,
          race: '人类',
          age: 0,
          gender: '',
          occupation: '',
          appearance: '',
          personality: '',
          background: '',
          lore: emptyCharacterLore(),
          physical_state: defaultPhysicalState(),
          mental_state: defaultMentalState(),
          custom_attributes: {},
          relationships: { [playerName]: { favor: 0, trust: 0, stage: '初识' } },
          inventory: [],
          memories: [],
          created_by: 'ai',
          created_at: now,
          last_modified: now,
        };
        rec.characters.push(minimal);
        idx = rec.characters.length - 1;
        createdNames.push(minimal.name);
      } else {
        continue;
      }
    }

    let card = rec.characters[idx];
    const curRel = card.relationships[playerName] ?? {
      favor: card.favor,
      trust: 0,
      stage: card.relationship_status || '陌生人',
    };
    const favorDelta = Math.max(
      -MAX_DELTA_PER_ROUND,
      Math.min(MAX_DELTA_PER_ROUND, Math.round(npcDelta.favor_delta ?? 0)),
    );
    const trustDelta = Math.max(
      -MAX_DELTA_PER_ROUND,
      Math.min(MAX_DELTA_PER_ROUND, Math.round(npcDelta.trust_delta ?? 0)),
    );
    const nextStage = npcDelta.relationship_stage?.trim() || curRel.stage;
    const nextRel = {
      favor: Math.max(-100, Math.min(100, curRel.favor + favorDelta)),
      trust: Math.max(0, Math.min(100, curRel.trust + trustDelta)),
      stage: nextStage,
    };

    let inv = card.inventory;
    for (const op of npcDelta.inventory ?? []) {
      const itemName = (op.item ?? '').trim();
      if (!itemName) continue;
      if (op.op?.toLowerCase() === 'add') {
        inv = addItems(inv, itemName, op.qty ?? 1, `第${targetRound}轮`);
      } else if (op.op?.toLowerCase() === 'remove') {
        inv = removeItems(inv, itemName, op.qty ?? 1);
      }
    }

    const nextMood = npcDelta.emotion?.trim() || undefined;
    card = {
      ...card,
      emotion: nextMood ?? card.emotion,
      favor: nextRel.favor,
      relationship_status: npcDelta.relationship_stage?.trim() ? nextStage : card.relationship_status,
      mental_state: nextMood ? { ...card.mental_state, mood: nextMood } : card.mental_state,
      relationships: { ...card.relationships, [playerName]: nextRel },
      inventory: inv,
      memories: appendMemory(card.memories, npcDelta.memory, targetRound, favorDelta),
      last_modified: now,
    };
    if (npcDelta.fields && Object.keys(npcDelta.fields).length) {
      card = applyFieldsToNpc(card, npcDelta.fields, defs, false);
    }
    rec.characters[idx] = card;
  }

  // 3. Player updates
  if (delta.player) {
    if (delta.player.fields && Object.keys(delta.player.fields).length) {
      rec.player = applyFieldsToPlayer(rec.player, delta.player.fields, defs);
    }
    for (const op of delta.player.inventory ?? []) {
      const itemName = (op.item ?? '').trim();
      if (!itemName) continue;
      if (op.op?.toLowerCase() === 'add') {
        rec.player.inventory = addItems(rec.player.inventory, itemName, op.qty ?? 1, `第${targetRound}轮`);
      } else if (op.op?.toLowerCase() === 'remove') {
        rec.player.inventory = removeItems(rec.player.inventory, itemName, op.qty ?? 1);
      }
    }
    for (const relOp of delta.player.relationships ?? []) {
      const target = relOp.name?.trim();
      const stage = relOp.stage?.trim();
      if (!target || !stage) continue;
      const idx = findCardIdx(target);
      const resolved = idx >= 0 ? rec.characters[idx].name : target;
      const existing = rec.player.relationships[resolved] ?? { favor: 0, trust: 0, stage };
      rec.player.relationships[resolved] = { ...existing, stage };
    }
  }

  // 4. Item transfers
  for (const transfer of delta.transfers ?? []) {
    const itemName = (transfer.item ?? '').trim();
    const fromName = (transfer.from ?? '').trim();
    const toName = (transfer.to ?? '').trim();
    const qty = Math.max(1, transfer.qty ?? 1);
    if (!itemName || !fromName || !toName) continue;
    const fromIsPlayer = isPlayerAlias(fromName);
    const toIsPlayer = isPlayerAlias(toName);
    const fromIdx = fromIsPlayer ? -1 : findCardIdx(fromName);
    const toIdx = toIsPlayer ? -1 : findCardIdx(toName);
    if ((!fromIsPlayer && fromIdx < 0) || (!toIsPlayer && toIdx < 0)) continue;

    if (fromIsPlayer) {
      rec.player.inventory = removeItems(rec.player.inventory, itemName, qty);
    } else {
      rec.characters[fromIdx].inventory = removeItems(rec.characters[fromIdx].inventory, itemName, qty);
    }
    if (toIsPlayer) {
      rec.player.inventory = addItems(rec.player.inventory, itemName, qty, `第${targetRound}轮`);
    } else {
      rec.characters[toIdx].inventory = addItems(rec.characters[toIdx].inventory, itemName, qty, `第${targetRound}轮`);
    }
  }

  // 5. Goals & Pending events
  let pending = [...rec.story.pending_events];
  if (delta.goals) {
    for (const done of delta.goals.complete ?? []) {
      const normDone = normalizeGoal(done);
      if (!normDone) continue;
      pending = pending.filter((existing) => {
        const normEx = normalizeGoal(existing);
        return !(
          normEx === normDone ||
          (normDone.length >= 3 && normEx.includes(normDone)) ||
          (normEx.length >= 3 && normDone.includes(normEx))
        );
      });
    }
    for (const upd of delta.goals.update ?? []) {
      const normFrom = normalizeGoal(upd.from ?? '');
      const toText = (upd.to ?? '').trim();
      if (!toText) continue;
      const idx = pending.findIndex((ex) => {
        const normEx = normalizeGoal(ex);
        return normEx === normFrom || (normFrom.length >= 3 && normEx.includes(normFrom));
      });
      if (idx >= 0) pending[idx] = toText;
      else if (!pending.some((ex) => normalizeGoal(ex) === normalizeGoal(toText))) pending.push(toText);
    }
    for (const add of delta.goals.add ?? []) {
      const clean = add.trim();
      if (clean && !pending.some((ex) => normalizeGoal(ex) === normalizeGoal(clean))) {
        pending.push(clean);
      }
    }
    pending = pending.map((s) => s.trim()).filter(Boolean).slice(-MAX_PENDING_EVENTS);
    const explicitCurrent = delta.goals.current?.trim();
    if (explicitCurrent) {
      rec.player.story_progress.current_goal = explicitCurrent;
    } else if (pending.length === 0 && (delta.goals.complete?.length ?? 0) > 0) {
      rec.player.story_progress.current_goal = '';
    } else if (pending.length > 0 && !rec.player.story_progress.current_goal.trim()) {
      rec.player.story_progress.current_goal = pending[0];
    }
  }

  // 6. Scene & Departed
  let activeChars = [...rec.story.active_characters];
  if (delta.scene) {
    if (delta.scene.location?.trim()) rec.story.current_location = delta.scene.location.trim();
    if (delta.scene.time_of_day?.trim()) rec.story.time_of_day = delta.scene.time_of_day.trim();
    if (delta.scene.weather?.trim()) rec.story.weather = delta.scene.weather.trim();
    if (delta.scene.description?.trim()) rec.story.scene_description = delta.scene.description.trim();
    if (Array.isArray(delta.scene.present)) {
      const resolved = delta.scene.present
        .filter((n) => !isPlayerAlias(n))
        .map((n) => {
          const i = findCardIdx(n);
          return i >= 0 ? rec.characters[i].name : null;
        })
        .filter((x): x is string => x !== null);
      activeChars = Array.from(new Set(resolved));
    } else if (createdNames.length) {
      activeChars = Array.from(new Set([...activeChars, ...createdNames]));
    }
  } else if (createdNames.length) {
    activeChars = Array.from(new Set([...activeChars, ...createdNames]));
  }

  for (const dep of delta.departed ?? []) {
    const idx = findCardIdx(dep.name ?? '');
    if (idx < 0) continue;
    const name = rec.characters[idx].name;
    activeChars = activeChars.filter((n) => n !== name);
    if (dep.permanent) {
      rec.characters[idx].status = 'pool';
      rec.characters[idx].last_modified = now;
    }
  }

  rec.story.active_characters = Array.from(new Set(activeChars));
  rec.story.pending_events = pending;

  // 7. Worldbook incremental updates (append, never clobber rich descriptions!)
  for (const w of delta.world ?? []) {
    const name = (w.name ?? '').trim();
    if (!name) continue;
    const validTypes = ['location', 'item', 'organization', 'event', 'character'];
    const type = validTypes.includes((w.type ?? '').toLowerCase()) ? (w.type ?? 'location').toLowerCase() : 'location';
    const desc = (w.description ?? '').trim();
    const keywords = Array.from(new Set([...(w.keywords ?? []).map((k) => k.trim()).filter(Boolean), name]));
    const idx = rec.worldbook.findIndex((e) => e.name.toLowerCase() === name.toLowerCase());
    if (idx >= 0) {
      const cur = rec.worldbook[idx];
      rec.worldbook[idx] = {
        ...cur,
        description: mergeWorldbookDescription(cur.description, desc),
        keywords: Array.from(new Set([...cur.keywords, ...keywords])),
        last_modified: now,
      };
    } else {
      const entry: WorldbookEntry = {
        id: `wb_${targetRound}_${Math.random().toString(36).slice(2, 8)}`,
        type,
        name,
        description: desc,
        keywords,
        related_characters: [],
        related_events: [],
        related_locations: [],
        is_secret: false,
        known_by_player: true,
        is_active: true,
        activated_by: '',
        deactivated_by: '',
        status: '正常',
        status_history: [],
        created_by: 'ai',
        created_at: now,
        last_modified: now,
      };
      rec.worldbook.push(entry);
    }
  }

  // 8. Events
  for (const ev of delta.events ?? []) {
    const title = (ev.title ?? '').trim();
    const desc = (ev.description ?? '').trim();
    if (!title && !desc) continue;
    const related = (ev.related ?? []).map((s) => s.trim()).filter(Boolean);
    const historyItem = {
      time: `第${targetRound}轮`,
      status: title || '剧情事件',
      event: desc || title,
      caused_by: related[0] ?? '',
    };
    const matchedIdx = related
      .map((ref) => rec.worldbook.findIndex((w) => w.name.toLowerCase() === ref.toLowerCase()))
      .find((i) => i >= 0);
    if (matchedIdx !== undefined && matchedIdx >= 0) {
      const cur = rec.worldbook[matchedIdx];
      rec.worldbook[matchedIdx] = {
        ...cur,
        status: title || cur.status,
        status_history: [...cur.status_history, historyItem],
        last_modified: now,
      };
    } else {
      const evName = title || `第${targetRound}轮事件`;
      const existingIdx = rec.worldbook.findIndex(
        (w) => w.type === 'event' && w.name.toLowerCase() === evName.toLowerCase(),
      );
      if (existingIdx >= 0) {
        const cur = rec.worldbook[existingIdx];
        rec.worldbook[existingIdx] = {
          ...cur,
          description: mergeWorldbookDescription(cur.description, desc),
          status_history: [...cur.status_history, historyItem],
          last_modified: now,
        };
      } else {
        rec.worldbook.push({
          id: `event_${targetRound}_${Math.random().toString(36).slice(2, 8)}`,
          type: 'event',
          name: evName,
          description: desc,
          keywords: Array.from(new Set([...related, evName])),
          related_characters: [],
          related_events: [],
          related_locations: [],
          is_secret: false,
          known_by_player: true,
          is_active: true,
          activated_by: '',
          deactivated_by: '',
          status: title || '已记录',
          status_history: [historyItem],
          created_by: 'ai',
          created_at: now,
          last_modified: now,
        });
      }
    }
  }

  // 9. Summary & Suggestions
  let summaryEntry: SummaryEntry | undefined;
  const summaryText = delta.summary?.trim();
  if (summaryText) {
    summaryEntry = { turn: targetRound, summary: summaryText.slice(0, 180), created_at: now };
    rec.summaries = [...rec.summaries.filter((s) => s.turn !== targetRound), summaryEntry].sort(
      (a, b) => a.turn - b.turn,
    );
  }

  const suggestions = Array.from(
    new Set((delta.suggestions ?? []).map((s) => s.trim()).filter(Boolean)),
  ).slice(0, 4);

  return { record: rec, summary: summaryEntry, suggestions };
}
