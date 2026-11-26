import type {
  AppSettings,
  CharacterCard,
  CharacterFieldDefinition,
  InventoryItem,
  PlayerState,
  StoryRecord,
  StoryState,
  WorldbookEntry,
  WorldSetting,
} from '../types/models';
import {
  defaultMentalState,
  defaultNarrationSettings,
  defaultPhysicalState,
  defaultSyncStatus,
  emptyCharacterLore,
  emptyWorldLore,
  nowIsoString,
  worldLoreHasContent,
} from '../types/models';
import { effectiveDefinitions, isValidFieldKey, normalizeFieldDefinition } from './characterFields';
import { parseLooseJson } from './engine/jsonExtract';
import { completeChatJson } from './engine/llmClient';

export const MAX_ADVANCED_JSON_LENGTH = 80_000;
const GENERIC_PLAYER_NAMES = new Set(['主角', '玩家', '我', '你', 'player', 'protagonist', 'mc']);
const GENERIC_NPC_RE = /^(主角|玩家|陌生人|路人|邻居|店员|服务员)[A-Z0-9甲乙丙丁一二三四]?$/;

export interface ParsedStoryBundle {
  world: WorldSetting;
  player: PlayerState;
  characters: CharacterCard[];
  worldbook: WorldbookEntry[];
  opening: StoryState;
  summary: string;
}

function asStringArray(val: unknown): string[] {
  if (!Array.isArray(val)) return [];
  return val.map((x) => String(x).trim()).filter(Boolean);
}

function asStringMap(val: unknown): Record<string, string> {
  if (!val || typeof val !== 'object' || Array.isArray(val)) return {};
  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(val as Record<string, unknown>)) {
    if (k.trim() && v !== null && v !== undefined) out[k.trim()] = String(v).trim();
  }
  return out;
}

function parseInventory(val: unknown): InventoryItem[] {
  if (!Array.isArray(val)) return [];
  return val
    .map((item) => {
      if (typeof item === 'string') {
        return {
          name: item.trim(),
          quantity: 1,
          type: '物品',
          description: '',
          origin: '开局持有',
          acquired_at: '开局',
        };
      }
      if (!item || typeof item !== 'object') return null;
      const o = item as Record<string, unknown>;
      const name = String(o.name ?? '').trim();
      if (!name) return null;
      return {
        name,
        quantity: typeof o.quantity === 'number' && o.quantity > 0 ? Math.round(o.quantity) : 1,
        type: String(o.type ?? '物品').trim(),
        description: String(o.description ?? '').trim(),
        origin: String(o.origin ?? '').trim(),
        acquired_at: String(o.acquired_at ?? '开局').trim(),
      };
    })
    .filter((x): x is InventoryItem => x !== null);
}

function parseLore(raw: unknown) {
  const base = emptyCharacterLore();
  if (!raw || typeof raw !== 'object') return base;
  const o = raw as Record<string, unknown>;
  return {
    ...base,
    aliases: asStringArray(o.aliases),
    role: String(o.role ?? '').trim(),
    pronouns: String(o.pronouns ?? '').trim(),
    birth_date: String(o.birth_date ?? '').trim(),
    nationality: String(o.nationality ?? '').trim(),
    residence: String(o.residence ?? '').trim(),
    education: String(o.education ?? '').trim(),
    values: asStringArray(o.values),
    goals: asStringArray(o.goals),
    motivations: asStringArray(o.motivations),
    fears: asStringArray(o.fears),
    weaknesses: asStringArray(o.weaknesses),
    secrets: asStringArray(o.secrets),
    habits: asStringArray(o.habits),
    speech_style: String(o.speech_style ?? o.speechStyle ?? '').trim(),
    sample_dialogues: asStringArray(o.sample_dialogues ?? o.sampleDialogues),
    abilities: asStringArray(o.abilities),
    knowledge: asStringArray(o.knowledge),
    boundaries: asStringArray(o.boundaries),
    character_arc: String(o.character_arc ?? '').trim(),
    relationship_notes: asStringMap(o.relationship_notes),
    custom: asStringMap(o.custom),
  };
}

/**
 * Strict parser for NovelChat Advanced Story Import JSON v1
 * (100% compatible with docs/advanced_story_import_v1.schema.json & docs/v2_live_debug_story_v1.json).
 */
export function parseAdvancedStoryJson(raw: string, strict = true): ParsedStoryBundle {
  const trimmed = raw.trim();
  if (!trimmed) throw new Error('请粘贴 JSON，或先载入内置模板');
  if (trimmed.length > MAX_ADVANCED_JSON_LENGTH) {
    throw new Error(`高级设定不能超过 ${MAX_ADVANCED_JSON_LENGTH} 个字符`);
  }

  let root: Record<string, unknown>;
  try {
    const parsed = strict ? JSON.parse(trimmed) : parseLooseJson<Record<string, unknown>>(trimmed);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      throw new Error('JSON 根节点必须是对象');
    }
    root = parsed as Record<string, unknown>;
  } catch (err) {
    throw new Error(`JSON 语法错误：${err instanceof Error ? err.message : String(err)}`);
  }

  if (strict && root.schema_version !== undefined && root.schema_version !== 1) {
    throw new Error(`不支持的 schema_version：${String(root.schema_version)}（当前仅支持 1）`);
  }

  const rawWorld = (root.world_setting ?? root.worldSetting) as Record<string, unknown> | undefined;
  if (!rawWorld || typeof rawWorld !== 'object') {
    throw new Error('缺少必需对象 world_setting');
  }
  const rawPlayer = root.player as Record<string, unknown> | undefined;
  if (!rawPlayer || typeof rawPlayer !== 'object') {
    throw new Error('缺少必需对象 player');
  }

  // Parse character_fields
  const rawFields = (rawWorld.character_fields ?? rawWorld.characterFields) as unknown;
  const parsedFields: CharacterFieldDefinition[] = [];
  if (Array.isArray(rawFields)) {
    if (rawFields.length > 40) throw new Error('character_fields 最多支持 40 个字段');
    rawFields.forEach((f, idx) => {
      if (!f || typeof f !== 'object') {
        if (strict) throw new Error(`character_fields[${idx}] 必须是对象`);
        return;
      }
      const fo = f as Partial<CharacterFieldDefinition>;
      if (strict && (!fo.key || !isValidFieldKey(fo.key))) {
        throw new Error(`character_fields[${idx}].key 必须以小写字母开头，仅含小写字母/数字/下划线`);
      }
      const norm = normalizeFieldDefinition(fo);
      if (norm) parsedFields.push(norm);
    });
  }

  const rawWorldLore = (rawWorld.lore ?? {}) as Record<string, unknown>;
  const worldLore = {
    ...emptyWorldLore(),
    era: String(rawWorldLore.era ?? '').trim(),
    premise: String(rawWorldLore.premise ?? '').trim(),
    history: String(rawWorldLore.history ?? '').trim(),
    geography: String(rawWorldLore.geography ?? '').trim(),
    cosmology: String(rawWorldLore.cosmology ?? '').trim(),
    society: String(rawWorldLore.society ?? '').trim(),
    culture: String(rawWorldLore.culture ?? '').trim(),
    politics: String(rawWorldLore.politics ?? '').trim(),
    economy: String(rawWorldLore.economy ?? '').trim(),
    law: String(rawWorldLore.law ?? '').trim(),
    religion: String(rawWorldLore.religion ?? '').trim(),
    languages: asStringArray(rawWorldLore.languages),
    calendar: String(rawWorldLore.calendar ?? '').trim(),
    power_system: String(rawWorldLore.power_system ?? '').trim(),
    technology_rules: String(rawWorldLore.technology_rules ?? '').trim(),
    factions: asStringArray(rawWorldLore.factions),
    conflicts: asStringArray(rawWorldLore.conflicts),
    rules: asStringArray(rawWorldLore.rules),
    themes: asStringArray(rawWorldLore.themes),
    narrative_style: String(rawWorldLore.narrative_style ?? '').trim(),
    custom: asStringMap(rawWorldLore.custom),
  };

  const world: WorldSetting = {
    name: String(rawWorld.name ?? '').trim(),
    genre: String(rawWorld.genre ?? '').trim(),
    tech_level: String(rawWorld.tech_level ?? rawWorld.techLevel ?? '').trim(),
    magic_level: String(rawWorld.magic_level ?? rawWorld.magicLevel ?? '').trim(),
    tone: String(rawWorld.tone ?? '').trim(),
    forbidden: asStringArray(rawWorld.forbidden),
    description: String(rawWorld.description ?? '').trim(),
    lore: worldLore,
    character_fields: parsedFields,
  };

  if (!world.name) throw new Error('world_setting.name 不能为空');
  if (!world.description && !worldLoreHasContent(world.lore)) {
    throw new Error('请填写 world_setting.description 或 world_setting.lore');
  }

  const playerName = String(rawPlayer.name ?? '').trim();
  if (!playerName) throw new Error('player.name 不能为空，且必须使用角色真实姓名');
  if (strict && GENERIC_PLAYER_NAMES.has(playerName.toLowerCase())) {
    throw new Error(`player.name 必须是具体姓名，不能使用“${playerName}”`);
  }

  const pPhys = (rawPlayer.physical_state ?? {}) as Record<string, unknown>;
  const pMent = (rawPlayer.mental_state ?? {}) as Record<string, unknown>;
  const rawSkills = Array.isArray(rawPlayer.skills)
    ? rawPlayer.skills
        .map((s) =>
          typeof s === 'string'
            ? { name: s.trim(), level: 1 }
            : s && typeof s === 'object'
              ? { name: String((s as Record<string, unknown>).name ?? '').trim(), level: Number((s as Record<string, unknown>).level ?? 1) }
              : null,
        )
        .filter((x): x is { name: string; level: number } => Boolean(x?.name))
    : [];

  const rawOpening = (root.opening ?? {}) as Record<string, unknown>;
  const pendingEvents = asStringArray(rawOpening.pending_events ?? rawOpening.pendingEvents);

  const player: PlayerState = {
    name: playerName,
    race: String(rawPlayer.race ?? '人类').trim() || '人类',
    age: typeof rawPlayer.age === 'number' ? Math.round(rawPlayer.age) : 25,
    gender: String(rawPlayer.gender ?? '未明确').trim() || '未明确',
    class: String(rawPlayer.class ?? rawPlayer.occupation ?? '').trim(),
    appearance: String(rawPlayer.appearance ?? '').trim(),
    personality: String(rawPlayer.personality ?? '').trim(),
    background: String(rawPlayer.background ?? '').trim(),
    lore: parseLore(rawPlayer.lore),
    charm: Number(rawPlayer.charm ?? 25),
    hunting_skill: Number(rawPlayer.action_skill ?? rawPlayer.hunting_skill ?? 10),
    social_rank: String(rawPlayer.social_rank ?? 'E').trim() || 'E',
    luck: Number(rawPlayer.luck ?? 50),
    alpha: Number(rawPlayer.willpower ?? rawPlayer.alpha ?? 15),
    resistance: Number(rawPlayer.resilience ?? rawPlayer.resistance ?? 20),
    cv: Number(rawPlayer.risk ?? rawPlayer.cv ?? 0),
    level: Number(rawPlayer.level ?? 1),
    skills: rawSkills,
    physical_state: {
      ...defaultPhysicalState(),
      stamina: typeof pPhys.stamina === 'number' ? Math.round(pPhys.stamina) : 100,
      health: String(pPhys.health ?? '良好').trim() || '良好',
      injuries: asStringArray(pPhys.injuries),
      fatigue: String(pPhys.fatigue ?? '无').trim() || '无',
      hunger: String(pPhys.hunger ?? '不饿').trim() || '不饿',
      pain: String(pPhys.pain ?? '无').trim() || '无',
    },
    mental_state: {
      ...defaultMentalState(),
      mood: String(pMent.mood ?? '平静').trim() || '平静',
      stress: String(pMent.stress ?? '低').trim() || '低',
      focus: String(pMent.focus ?? '').trim(),
      current_thoughts: String(pMent.current_thoughts ?? '').trim(),
    },
    custom_attributes: asStringMap(rawPlayer.custom_attributes),
    relationships: {},
    inventory: parseInventory(rawPlayer.inventory),
    quest_log: [],
    story_progress: {
      current_arc: world.name,
      current_chapter: String(rawOpening.time_of_day ?? '序章').trim(),
      current_scene: String(rawOpening.current_location ?? '').trim(),
      current_goal: pendingEvents[0] ?? '',
    },
  };

  // Parse NPCs
  const now = nowIsoString();
  const rawNpcs = Array.isArray(root.npcs) ? root.npcs : [];
  const npcNames = new Set<string>();
  const characters: CharacterCard[] = [];

  for (const rawNpc of rawNpcs) {
    if (!rawNpc || typeof rawNpc !== 'object') continue;
    const o = rawNpc as Record<string, unknown>;
    const name = String(o.name ?? '').trim();
    if (!name || (strict && GENERIC_NPC_RE.test(name))) {
      if (strict) throw new Error(`npcs 中的“${name || '未命名角色'}”不是具体姓名`);
      continue;
    }
    if (name === playerName) {
      if (strict) throw new Error(`主角与 NPC 不能使用同一个姓名“${playerName}”`);
      continue;
    }
    if (npcNames.has(name)) {
      if (strict) throw new Error(`多个 NPC 使用了同一个姓名“${name}”`);
      continue;
    }
    npcNames.add(name);

    const nPhys = (o.physical_state ?? {}) as Record<string, unknown>;
    const nMent = (o.mental_state ?? {}) as Record<string, unknown>;
    const relStage = String(o.relationship_to_player ?? o.relationship_status ?? '初识').trim() || '初识';
    const initialFavor = Number(o.favor ?? 0);
    const initialTrust = Number(o.trust ?? 0);

    characters.push({
      id: name,
      name,
      status: 'active',
      rarity: String(o.rarity ?? '普通').trim() || '普通',
      libido: 50,
      threshold: 50,
      emotion: String(o.emotion ?? nMent.mood ?? '平静').trim() || '平静',
      relationship_status: relStage,
      kink: String(o.kink ?? '').trim(),
      favor: Number.isFinite(initialFavor) ? Math.round(initialFavor) : 0,
      race: String(o.race ?? '人类').trim() || '人类',
      age: typeof o.age === 'number' ? Math.round(o.age) : 0,
      gender: String(o.gender ?? '').trim(),
      occupation: String(o.occupation ?? '').trim(),
      appearance: String(o.appearance ?? '').trim(),
      personality: String(o.personality ?? '').trim(),
      background: String(o.background ?? '').trim(),
      lore: parseLore(o.lore),
      physical_state: {
        ...defaultPhysicalState(),
        stamina: typeof nPhys.stamina === 'number' ? Math.round(nPhys.stamina) : 100,
        health: String(nPhys.health ?? '良好').trim() || '良好',
        injuries: asStringArray(nPhys.injuries),
        fatigue: String(nPhys.fatigue ?? '无').trim() || '无',
        hunger: String(nPhys.hunger ?? '不饿').trim() || '不饿',
        pain: String(nPhys.pain ?? '无').trim() || '无',
      },
      mental_state: {
        ...defaultMentalState(),
        mood: String(nMent.mood ?? o.emotion ?? '平静').trim() || '平静',
        stress: String(nMent.stress ?? '低').trim() || '低',
        focus: String(nMent.focus ?? '').trim(),
        current_thoughts: String(nMent.current_thoughts ?? '').trim(),
      },
      custom_attributes: asStringMap(o.custom_attributes),
      relationships: {
        [playerName]: {
          favor: Number.isFinite(initialFavor) ? Math.round(initialFavor) : 0,
          trust: Number.isFinite(initialTrust) ? Math.round(initialTrust) : 0,
          stage: relStage,
        },
      },
      inventory: parseInventory(o.inventory),
      memories: [],
      created_by: 'import',
      created_at: now,
      last_modified: now,
    });
  }

  // Parse Opening
  const activeCharacters = asStringArray(rawOpening.active_characters ?? rawOpening.activeCharacters);
  if (strict) {
    for (const ac of activeCharacters) {
      if (!npcNames.has(ac)) {
        throw new Error(`opening.active_characters 中的“${ac}”未在 npcs 中定义`);
      }
    }
  }
  const opening: StoryState = {
    current_round: 0,
    active_characters: activeCharacters.filter((ac) => npcNames.has(ac)),
    current_location: String(rawOpening.current_location ?? rawOpening.currentLocation ?? '').trim() || world.name,
    time_of_day: String(rawOpening.time_of_day ?? rawOpening.timeOfDay ?? '开局').trim() || '开局',
    weather: String(rawOpening.weather ?? '').trim(),
    mood: String(rawOpening.mood ?? '').trim(),
    scene_description: String(rawOpening.scene_description ?? rawOpening.sceneDescription ?? '').trim(),
    pending_events: pendingEvents,
    global_flags: asStringMap(rawOpening.global_flags),
  };

  // Parse Worldbook
  const rawWb = Array.isArray(root.worldbook) ? root.worldbook : [];
  const worldbook: WorldbookEntry[] = [];
  rawWb.forEach((item, idx) => {
    if (!item || typeof item !== 'object') return;
    const o = item as Record<string, unknown>;
    const name = String(o.name ?? '').trim();
    if (!name) return;
    const keywords = Array.from(new Set([...asStringArray(o.keywords), name]));
    worldbook.push({
      id: String(o.id ?? `wb_import_${idx}_${Math.random().toString(36).slice(2, 6)}`),
      type: String(o.type ?? 'location').trim().toLowerCase() || 'location',
      name,
      description: String(o.description ?? '').trim(),
      keywords,
      related_characters: asStringArray(o.related_characters),
      related_events: asStringArray(o.related_events),
      related_locations: asStringArray(o.related_locations),
      is_secret: Boolean(o.is_secret),
      known_by_player: o.known_by_player === undefined ? !o.is_secret : Boolean(o.known_by_player),
      is_active: o.is_active === undefined ? true : Boolean(o.is_active),
      activated_by: '',
      deactivated_by: '',
      status: String(o.status ?? '正常').trim() || '正常',
      status_history: [],
      created_by: 'import',
      created_at: now,
      last_modified: now,
    });
  });

  const fieldCount = effectiveDefinitions(world.character_fields).length;
  const summary = `${world.name} · 1 位主角（${player.name}）· ${characters.length} 位 NPC · ${worldbook.length} 条世界书 · ${fieldCount} 个角色字段`;

  return { world, player, characters, worldbook, opening, summary };
}

export function buildStoryRecordFromParsed(bundle: ParsedStoryBundle): StoryRecord {
  const now = nowIsoString();
  const id = `story_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;
  const shortPending = bundle.opening.pending_events
    .map((s) => s.trim())
    .filter((s) => s.length > 0 && s.length <= 16)
    .slice(0, 3);
  const firstActive = bundle.opening.active_characters[0];
  const fallbackChips = [
    ...(firstActive ? [`和${firstActive}聊聊`] : []),
    ...(bundle.opening.pending_events.length > 0 ? ['继续推进当前目标'] : []),
    '查看待办和时间安排',
    '观察一下周围',
  ].slice(0, 4);
  const suggestions = shortPending.length >= 2 ? shortPending : fallbackChips;
  return {
    id,
    title: bundle.player.name,
    subtitle: `${bundle.world.name}${bundle.player.class ? ` · ${bundle.player.class}` : ''}`,
    preview: bundle.opening.scene_description || bundle.world.description,
    created_at: now,
    updated_at: now,
    world: bundle.world,
    story: bundle.opening,
    player: bundle.player,
    characters: bundle.characters,
    worldbook: bundle.worldbook,
    narratives: [],
    summaries: [],
    chapters: [],
    checkpoints: {},
    narration: defaultNarrationSettings(),
    sync_status: {
      ...defaultSyncStatus(),
      last_suggestions: suggestions.length ? suggestions : ['观察一下周围环境', '和眼前的人搭话', '检查随身物品与待办'],
    },
  };
}

export async function parseNaturalLanguageStoryWithAi(
  storyText: string,
  settings: AppSettings,
): Promise<ParsedStoryBundle> {
  const clean = storyText.trim();
  if (clean.length < 20) {
    throw new Error('请至少输入 20 字以上的世界观或故事简介');
  }
  const prompt = `你是互动故事设定解析 AI。将下面的中文设定文本解析为一个严格 JSON 对象（不要 Markdown 代码块，不要解释）。
必须包含 world_setting, player, npcs, worldbook, opening。
要求：
- 主角和所有 NPC 必须使用自然的具体姓名，禁止使用“主角、玩家、邻居A、陌生人”等泛称。
- world_setting 包含 name, genre, tech_level, magic_level, tone, description, forbidden[], lore{era, premise, history, geography, rules[], narrative_style}, character_fields[]。
- player 包含 name, age, gender, race, class, personality, background, appearance, skills[], physical_state, mental_state, inventory[]。
- npcs[] 每位包含 name, age, gender, occupation, personality, background, appearance, relationship_to_player, lore{speech_style, sample_dialogues[], secrets[]}。
- worldbook[] 每条包含 type(location/item/organization/event), name, description, keywords[], status。
- opening 包含 current_location, time_of_day, weather, mood, scene_description, active_characters[](必须在 npcs 中存在), pending_events[]。

【用户提供的文字】
${clean}

【请输出 JSON】`;

  const response = await completeChatJson({
    settings,
    stage: 'story_import',
    messages: [{ role: 'user', content: prompt }],
    temperature: 0.2,
    maxTokens: 4200,
    jsonMode: true,
  });
  return parseAdvancedStoryJson(response, false);
}
