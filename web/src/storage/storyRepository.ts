import type {
  CharacterCard,
  NarrationSettings,
  RoundCheckpoint,
  StoryExportBundle,
  StoryMeta,
  StoryRecord,
  WorldbookEntry,
} from '../types/models';
import {
  defaultMentalState,
  defaultNarrationSettings,
  defaultPhysicalState,
  defaultSyncStatus,
  emptyCharacterLore,
  emptyWorldLore,
  nowIsoString,
} from '../types/models';
import { idbDelete, idbGet, idbGetAll, idbPut, STORE_STORIES, withStoryLock } from './db';

export function buildStoryMeta(record: StoryRecord): StoryMeta {
  const lastNarrative = record.narratives[record.narratives.length - 1];
  const preview =
    lastNarrative?.narrative?.trim().slice(0, 140) ||
    record.world.description?.trim().slice(0, 140) ||
    record.story.scene_description?.trim().slice(0, 140) ||
    '点击进入故事，写下你的第一步行动。';
  return {
    id: record.id,
    title: record.player.name || record.title || '未命名主角',
    subtitle: `${record.world.name || '未命名世界'}${record.player.class ? ` · ${record.player.class}` : ''}`,
    preview,
    round: record.story.current_round,
    player_name: record.player.name,
    world_name: record.world.name,
    updated_at: record.updated_at,
    created_at: record.created_at,
  };
}

export async function listStories(): Promise<StoryMeta[]> {
  const all = await idbGetAll<StoryRecord>(STORE_STORIES);
  return all
    .map(buildStoryMeta)
    .sort((a, b) => b.updated_at.localeCompare(a.updated_at));
}

export async function getStory(storyId: string): Promise<StoryRecord | undefined> {
  return idbGet<StoryRecord>(STORE_STORIES, storyId);
}

export async function saveStory(record: StoryRecord): Promise<StoryRecord> {
  const updated: StoryRecord = {
    ...record,
    updated_at: nowIsoString(),
    title: record.player.name || record.title,
    subtitle: `${record.world.name || '未命名世界'}${record.player.class ? ` · ${record.player.class}` : ''}`,
  };
  await idbPut(STORE_STORIES, updated);
  return updated;
}

export async function mutateStory(
  storyId: string,
  mutator: (current: StoryRecord) => StoryRecord,
): Promise<StoryRecord> {
  return withStoryLock(storyId, async () => {
    const current = await getStory(storyId);
    if (!current) throw new Error('故事存档不存在或已被删除');
    const next = mutator(structuredClone(current));
    return saveStory(next);
  });
}

export async function deleteStory(storyId: string): Promise<void> {
  await withStoryLock(storyId, async () => {
    await idbDelete(STORE_STORIES, storyId);
  });
}

export function createCheckpointSnapshot(
  record: StoryRecord,
  round: number,
  provisional: boolean,
): RoundCheckpoint {
  return {
    version: 2,
    round,
    story_state: structuredClone(record.story),
    player_state: structuredClone(record.player),
    character_cards: structuredClone(record.characters),
    worldbook_entries: structuredClone(record.worldbook),
    provisional,
    created_at: nowIsoString(),
  };
}

export async function rollbackToBeforeRound(
  storyId: string,
  targetRound: number,
): Promise<StoryRecord> {
  return mutateStory(storyId, (rec) => {
    const checkpoint = rec.checkpoints[targetRound];
    if (!checkpoint) {
      throw new Error(`缺少第 ${targetRound} 轮的检查点，无法原位回溯`);
    }
    const nextCheckpoints: Record<number, RoundCheckpoint> = {};
    for (const [k, cp] of Object.entries(rec.checkpoints)) {
      const r = Number(k);
      if (r < targetRound) nextCheckpoints[r] = cp;
    }
    return {
      ...rec,
      story: structuredClone(checkpoint.story_state),
      player: structuredClone(checkpoint.player_state),
      characters: structuredClone(checkpoint.character_cards),
      worldbook: structuredClone(checkpoint.worldbook_entries),
      narratives: rec.narratives.filter((n) => n.turn < targetRound),
      summaries: rec.summaries.filter((s) => s.turn < targetRound),
      chapters: rec.chapters.filter((c) => c.to_turn < targetRound),
      checkpoints: nextCheckpoints,
      sync_status: {
        ...rec.sync_status,
        synced_round: Math.min(rec.sync_status.synced_round, targetRound - 1),
        pending_rounds: rec.sync_status.pending_rounds.filter((r) => r < targetRound),
        failed_rounds: rec.sync_status.failed_rounds.filter((r) => r < targetRound),
        last_error: undefined,
      },
    };
  });
}

export async function updateNarrationSettings(
  storyId: string,
  narration: NarrationSettings,
): Promise<StoryRecord> {
  return mutateStory(storyId, (rec) => ({
    ...rec,
    narration: {
      pov: narration.pov,
      length: narration.length ?? null,
      style_sample: narration.style_sample.trim().slice(0, 800),
    },
  }));
}

export async function upsertWorldbookEntry(
  storyId: string,
  entry: WorldbookEntry,
): Promise<StoryRecord> {
  return mutateStory(storyId, (rec) => {
    const now = nowIsoString();
    const clean: WorldbookEntry = {
      ...entry,
      id: entry.id || `wb_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
      name: entry.name.trim(),
      description: entry.description.trim(),
      keywords: Array.from(new Set([...entry.keywords.map((k) => k.trim()).filter(Boolean), entry.name.trim()])),
      created_at: entry.created_at || now,
      last_modified: now,
    };
    const idx = rec.worldbook.findIndex((w) => w.id === clean.id);
    if (idx >= 0) {
      rec.worldbook[idx] = clean;
    } else {
      rec.worldbook.unshift(clean);
    }
    return rec;
  });
}

export async function deleteWorldbookEntry(
  storyId: string,
  entryId: string,
): Promise<StoryRecord> {
  return mutateStory(storyId, (rec) => ({
    ...rec,
    worldbook: rec.worldbook.filter((w) => w.id !== entryId),
  }));
}

export async function updateCharacterSpeechProfile(
  storyId: string,
  characterKey: string,
  speechStyle: string,
  sampleDialogues: string[],
): Promise<StoryRecord> {
  return mutateStory(storyId, (rec) => {
    const idx = rec.characters.findIndex(
      (c) => c.id === characterKey || c.name === characterKey,
    );
    if (idx < 0) return rec;
    const card: CharacterCard = rec.characters[idx];
    rec.characters[idx] = {
      ...card,
      lore: {
        ...card.lore,
        speech_style: speechStyle.trim(),
        sample_dialogues: sampleDialogues.map((s) => s.trim()).filter(Boolean).slice(0, 6),
      },
      last_modified: nowIsoString(),
    };
    return rec;
  });
}

export async function exportSingleStory(storyId: string): Promise<StoryExportBundle> {
  const story = await getStory(storyId);
  if (!story) throw new Error('未找到要导出的故事');
  return {
    format: 'novelchat_backup_v2',
    exported_at: nowIsoString(),
    stories: [story],
  };
}

export async function exportAllStories(): Promise<StoryExportBundle> {
  const stories = await idbGetAll<StoryRecord>(STORE_STORIES);
  return {
    format: 'novelchat_backup_v2',
    exported_at: nowIsoString(),
    stories,
  };
}

function normalizeImportedStoryRecord(raw: Partial<StoryRecord>): StoryRecord {
  const now = nowIsoString();
  const id = `story_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;
  const player = raw.player;
  const world = raw.world;
  if (!player || !world) {
    throw new Error('备份文件缺少必需的 world 或 player 数据');
  }
  return {
    id,
    title: player.name || raw.title || '导入的故事',
    subtitle: `${world.name || '未命名世界'}${player.class ? ` · ${player.class}` : ''}`,
    preview: raw.preview || world.description || '',
    created_at: raw.created_at || now,
    updated_at: now,
    world: {
      name: world.name || '未命名世界',
      genre: world.genre || '',
      tech_level: world.tech_level || '',
      magic_level: world.magic_level || '',
      tone: world.tone || '',
      forbidden: Array.isArray(world.forbidden) ? world.forbidden : [],
      description: world.description || '',
      lore: { ...emptyWorldLore(), ...(world.lore ?? {}) },
      character_fields: Array.isArray(world.character_fields) ? world.character_fields : [],
    },
    story: {
      current_round: raw.story?.current_round ?? (raw.narratives?.length ?? 0),
      active_characters: raw.story?.active_characters ?? [],
      current_location: raw.story?.current_location ?? '',
      time_of_day: raw.story?.time_of_day ?? '白天',
      weather: raw.story?.weather ?? '晴朗',
      mood: raw.story?.mood ?? '',
      scene_description: raw.story?.scene_description ?? '',
      pending_events: raw.story?.pending_events ?? [],
      global_flags: raw.story?.global_flags ?? {},
    },
    player: {
      name: player.name || '林克',
      race: player.race || '人类',
      age: player.age ?? 25,
      gender: player.gender || '未明确',
      class: player.class || '',
      appearance: player.appearance || '',
      personality: player.personality || '',
      background: player.background || '',
      lore: { ...emptyCharacterLore(), ...(player.lore ?? {}) },
      charm: player.charm ?? 25,
      hunting_skill: player.hunting_skill ?? 10,
      social_rank: player.social_rank || 'E',
      luck: player.luck ?? 50,
      alpha: player.alpha ?? 15,
      resistance: player.resistance ?? 20,
      cv: player.cv ?? 0,
      level: player.level ?? 1,
      skills: player.skills ?? [],
      physical_state: { ...defaultPhysicalState(), ...(player.physical_state ?? {}) },
      mental_state: { ...defaultMentalState(), ...(player.mental_state ?? {}) },
      custom_attributes: player.custom_attributes ?? {},
      relationships: player.relationships ?? {},
      inventory: player.inventory ?? [],
      quest_log: player.quest_log ?? [],
      story_progress: {
        current_arc: player.story_progress?.current_arc ?? '',
        current_chapter: player.story_progress?.current_chapter ?? '',
        current_scene: player.story_progress?.current_scene ?? '',
        current_goal: player.story_progress?.current_goal ?? '',
      },
    },
    characters: Array.isArray(raw.characters) ? raw.characters : [],
    worldbook: Array.isArray(raw.worldbook) ? raw.worldbook : [],
    narratives: Array.isArray(raw.narratives) ? raw.narratives : [],
    summaries: Array.isArray(raw.summaries) ? raw.summaries : [],
    chapters: Array.isArray(raw.chapters) ? raw.chapters : [],
    checkpoints: raw.checkpoints ?? {},
    narration: { ...defaultNarrationSettings(), ...(raw.narration ?? {}) },
    sync_status: { ...defaultSyncStatus(), ...(raw.sync_status ?? {}) },
  };
}

export async function importBackupBundle(rawJson: string): Promise<StoryRecord[]> {
  let parsed: unknown;
  try {
    parsed = JSON.parse(rawJson);
  } catch {
    throw new Error('无法解析 JSON 文件，请确认格式有效');
  }
  const obj = parsed as Partial<StoryExportBundle & StoryRecord>;
  let list: Partial<StoryRecord>[] = [];
  if (obj.format === 'novelchat_backup_v2' && Array.isArray(obj.stories)) {
    list = obj.stories;
  } else if (obj.world && obj.player) {
    list = [obj];
  } else {
    throw new Error('不是有效的 NovelChat 备份文件（如果是世界书剧本 JSON，请通过「导入故事 -> 高级 JSON」导入）');
  }
  const imported: StoryRecord[] = [];
  for (const item of list) {
    const rec = normalizeImportedStoryRecord(item);
    await saveStory(rec);
    imported.push(rec);
  }
  return imported;
}

export function triggerJsonDownload(filename: string, data: unknown): void {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
