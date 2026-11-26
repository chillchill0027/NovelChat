// =============================================================================
// Core Data Models — 100% JSON schema compatible with Android NovelChat 2.0.0
// Uses snake_case serialized field names matching Android @SerializedName tags.
// =============================================================================

export type LengthPreference = 'short' | 'standard' | 'detailed';
export type CreativityLevel = 'steady' | 'balanced' | 'vivid';
export type NarrationPov = 'third_limited' | 'second';

export type AiCallStage =
  | 'story_import'
  | 'character_setup'
  | 'narrative'
  | 'state_keeper'
  | 'summarizer';

export const AI_STAGE_LABELS: Record<AiCallStage, { label: string; desc: string }> = {
  narrative: { label: '剧情正文', desc: '前台流式小说叙事（决定文笔与节奏）' },
  state_keeper: { label: '后台状态同步', desc: '异步提取人物/场景/世界书增量 JSON' },
  summarizer: { label: '长期章节记忆', desc: '每 10 轮/60 轮压缩章节与前情总览' },
  story_import: { label: '故事 AI 解析', desc: '将自然语言设定解析为结构化世界与角色' },
  character_setup: { label: '开局初始化', desc: '角色开局辅助生成' },
};

export interface ApiProfile {
  name: string;
  base_url: string;
  api_key: string;
  model: string;
}

export interface ApiStageRoutes {
  story_import?: string;
  character_setup?: string;
  narrative?: string;
  state_keeper?: string;
  summarizer?: string;
}

export interface AppSettings {
  api_base_url: string;
  api_key: string;
  model: string;
  current_profile_name: string;
  profiles: ApiProfile[];
  stage_routes: ApiStageRoutes;
  default_length: LengthPreference;
  creativity: CreativityLevel;
  setup_completed: boolean;
}

export interface NarrationSettings {
  pov: NarrationPov;
  length?: LengthPreference | null;
  style_sample: string;
}

export interface WorldLore {
  era: string;
  premise: string;
  history: string;
  geography: string;
  cosmology: string;
  society: string;
  culture: string;
  politics: string;
  economy: string;
  law: string;
  religion: string;
  languages: string[];
  calendar: string;
  power_system: string;
  technology_rules: string;
  factions: string[];
  conflicts: string[];
  rules: string[];
  themes: string[];
  narrative_style: string;
  custom: Record<string, string>;
}

export interface CharacterLore {
  aliases: string[];
  role: string;
  pronouns: string;
  birth_date: string;
  nationality: string;
  residence: string;
  education: string;
  values: string[];
  goals: string[];
  motivations: string[];
  fears: string[];
  weaknesses: string[];
  secrets: string[];
  habits: string[];
  speech_style: string;
  sample_dialogues: string[];
  abilities: string[];
  knowledge: string[];
  boundaries: string[];
  character_arc: string;
  relationship_notes: Record<string, string>;
  custom: Record<string, string>;
}

export type FieldValueType = 'number' | 'text' | 'choice' | 'list';
export type FieldGroup = 'identity' | 'profile' | 'ability' | 'status' | 'relationship';
export type FieldTarget = 'all' | 'player' | 'npc';
export type FieldDisplay = 'primary' | 'detail' | 'hidden';
export type FieldUpdateMode = 'dynamic' | 'event' | 'manual';

export interface CharacterFieldDefinition {
  key: string;
  label: string;
  description: string;
  group: FieldGroup;
  value_type: FieldValueType;
  min_value: number;
  max_value: number;
  options: string[];
  default_value: string;
  unit: string;
  applies_to: FieldTarget;
  display: FieldDisplay;
  primary_for: FieldTarget;
  update_mode: FieldUpdateMode;
  order: number;
}

export interface WorldSetting {
  name: string;
  genre: string;
  tech_level: string;
  magic_level: string;
  tone: string;
  forbidden: string[];
  description: string;
  lore: WorldLore;
  character_fields: CharacterFieldDefinition[];
}

export interface PhysicalState {
  stamina: number;
  health: string;
  injuries: string[];
  fatigue: string;
  hunger: string;
  pain: string;
}

export interface MentalState {
  mood: string;
  stress: string;
  focus: string;
  current_thoughts: string;
}

export interface Relationship {
  favor: number;
  trust: number;
  stage: string;
}

export interface InventoryItem {
  name: string;
  quantity: number;
  type: string;
  description: string;
  origin: string;
  acquired_at: string;
}

export interface PlayerSkill {
  name: string;
  level: number;
}

export interface Quest {
  name: string;
  status: string;
  description: string;
  current_step: string;
}

export interface StoryProgress {
  current_arc: string;
  current_chapter: string;
  current_scene: string;
  current_goal: string;
}

export interface PlayerState {
  name: string;
  race: string;
  age: number;
  gender: string;
  class: string;
  appearance: string;
  personality: string;
  background: string;
  lore: CharacterLore;
  charm: number;
  hunting_skill: number;
  social_rank: string;
  luck: number;
  alpha: number;
  resistance: number;
  cv: number;
  level: number;
  skills: PlayerSkill[];
  physical_state: PhysicalState;
  mental_state: MentalState;
  custom_attributes: Record<string, string>;
  relationships: Record<string, Relationship>;
  inventory: InventoryItem[];
  quest_log: Quest[];
  story_progress: StoryProgress;
}

export interface MemoryNote {
  turn: number;
  text: string;
  weight: number;
}

export interface CharacterCard {
  id: string;
  name: string;
  status: string;
  rarity: string;
  libido: number;
  threshold: number;
  emotion: string;
  relationship_status: string;
  kink: string;
  favor: number;
  race: string;
  age: number;
  gender: string;
  occupation: string;
  appearance: string;
  personality: string;
  background: string;
  lore: CharacterLore;
  physical_state: PhysicalState;
  mental_state: MentalState;
  custom_attributes: Record<string, string>;
  relationships: Record<string, Relationship>;
  inventory: InventoryItem[];
  memories: MemoryNote[];
  created_by: string;
  created_at: string;
  last_modified: string;
}

export interface StatusHistory {
  time: string;
  status: string;
  event: string;
  caused_by: string;
}

export interface WorldbookEntry {
  id: string;
  type: 'location' | 'item' | 'organization' | 'event' | 'character' | string;
  name: string;
  description: string;
  keywords: string[];
  related_characters: string[];
  related_events: string[];
  related_locations: string[];
  is_secret: boolean;
  known_by_player: boolean;
  is_active: boolean;
  activated_by: string;
  deactivated_by: string;
  status: string;
  status_history: StatusHistory[];
  created_by: string;
  created_at: string;
  last_modified: string;
}

export interface StoryState {
  current_round: number;
  active_characters: string[];
  current_location: string;
  time_of_day: string;
  weather: string;
  mood: string;
  scene_description: string;
  pending_events: string[];
  global_flags: Record<string, string>;
}

export interface NarrativeEntry {
  turn: number;
  user_input: string;
  narrative: string;
  created_at: string;
}

export interface SummaryEntry {
  turn: number;
  summary: string;
  created_at: string;
}

export interface ChapterSummary {
  index: number;
  from_turn: number;
  to_turn: number;
  text: string;
  is_overview: boolean;
  created_at: string;
}

export interface RoundCheckpoint {
  version: number;
  round: number;
  story_state: StoryState;
  player_state: PlayerState;
  character_cards: CharacterCard[];
  worldbook_entries: WorldbookEntry[];
  provisional: boolean;
  created_at: string;
}

export interface SyncStatus {
  synced_round: number;
  pending_rounds: number[];
  failed_rounds: number[];
  last_suggestions: string[];
  last_error?: string;
}

export interface StoryRecord {
  id: string;
  title: string;
  subtitle: string;
  preview: string;
  created_at: string;
  updated_at: string;
  world: WorldSetting;
  story: StoryState;
  player: PlayerState;
  characters: CharacterCard[];
  worldbook: WorldbookEntry[];
  narratives: NarrativeEntry[];
  summaries: SummaryEntry[];
  chapters: ChapterSummary[];
  checkpoints: Record<number, RoundCheckpoint>;
  narration: NarrationSettings;
  sync_status: SyncStatus;
}

export interface StoryMeta {
  id: string;
  title: string;
  subtitle: string;
  preview: string;
  round: number;
  player_name: string;
  world_name: string;
  updated_at: string;
  created_at: string;
}

export interface CharacterPreset {
  key: string;
  name: string;
  player_name: string;
  description: string;
  charm: number;
  hunting_skill: number;
  social_rank: string;
  alpha: number;
  resistance: number;
  cv: number;
  luck: number;
  system_eval: string;
}

export interface LlmCallMetric {
  stage: AiCallStage;
  model: string;
  ttft_ms: number;
  total_ms: number;
  prompt_tokens: number;
  completion_tokens: number;
  cached_tokens: number;
  timestamp: string;
  ok: boolean;
  error?: string;
}

export interface StoryExportBundle {
  format: 'novelchat_backup_v2';
  exported_at: string;
  stories: StoryRecord[];
}

export function emptyWorldLore(): WorldLore {
  return {
    era: '',
    premise: '',
    history: '',
    geography: '',
    cosmology: '',
    society: '',
    culture: '',
    politics: '',
    economy: '',
    law: '',
    religion: '',
    languages: [],
    calendar: '',
    power_system: '',
    technology_rules: '',
    factions: [],
    conflicts: [],
    rules: [],
    themes: [],
    narrative_style: '',
    custom: {},
  };
}

export function emptyCharacterLore(): CharacterLore {
  return {
    aliases: [],
    role: '',
    pronouns: '',
    birth_date: '',
    nationality: '',
    residence: '',
    education: '',
    values: [],
    goals: [],
    motivations: [],
    fears: [],
    weaknesses: [],
    secrets: [],
    habits: [],
    speech_style: '',
    sample_dialogues: [],
    abilities: [],
    knowledge: [],
    boundaries: [],
    character_arc: '',
    relationship_notes: {},
    custom: {},
  };
}

export function defaultPhysicalState(): PhysicalState {
  return {
    stamina: 100,
    health: '良好',
    injuries: [],
    fatigue: '无',
    hunger: '不饿',
    pain: '无',
  };
}

export function defaultMentalState(): MentalState {
  return {
    mood: '平静',
    stress: '低',
    focus: '',
    current_thoughts: '',
  };
}

export function defaultNarrationSettings(): NarrationSettings {
  return {
    pov: 'third_limited',
    length: null,
    style_sample: '',
  };
}

export function defaultSyncStatus(): SyncStatus {
  return {
    synced_round: 0,
    pending_rounds: [],
    failed_rounds: [],
    last_suggestions: [],
  };
}

export function worldLoreHasContent(lore?: Partial<WorldLore>): boolean {
  if (!lore) return false;
  const textKeys: (keyof WorldLore)[] = [
    'era', 'premise', 'history', 'geography', 'cosmology',
    'society', 'culture', 'politics', 'economy', 'law',
    'religion', 'calendar', 'power_system', 'technology_rules', 'narrative_style',
  ];
  for (const k of textKeys) {
    const v = lore[k];
    if (typeof v === 'string' && v.trim().length > 0) return true;
  }
  const listKeys: (keyof WorldLore)[] = ['languages', 'factions', 'conflicts', 'rules', 'themes'];
  for (const k of listKeys) {
    const v = lore[k];
    if (Array.isArray(v) && v.length > 0) return true;
  }
  return Boolean(lore.custom && Object.keys(lore.custom).length > 0);
}

export function nowIsoString(): string {
  return new Date().toISOString();
}
