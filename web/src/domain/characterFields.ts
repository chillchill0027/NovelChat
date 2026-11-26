import type {
  CharacterCard,
  CharacterFieldDefinition,
  FieldDisplay,
  FieldGroup,
  FieldTarget,
  FieldUpdateMode,
  FieldValueType,
  PlayerState,
} from '../types/models';

export const MAX_FIELDS = 40;
export const MAX_VALUE_LENGTH = 6000;
const KEY_PATTERN = /^[a-z][a-z0-9_]{0,39}$/;

function mkField(
  key: string,
  label: string,
  description: string,
  group: FieldGroup,
  opts: Partial<CharacterFieldDefinition> = {},
): CharacterFieldDefinition {
  return {
    key,
    label,
    description,
    group,
    value_type: opts.value_type ?? 'text',
    min_value: opts.min_value ?? 0,
    max_value: opts.max_value ?? 100,
    options: opts.options ?? [],
    default_value: opts.default_value ?? '',
    unit: opts.unit ?? '',
    applies_to: opts.applies_to ?? 'all',
    display: opts.display ?? 'detail',
    primary_for: opts.primary_for ?? 'all',
    update_mode: opts.update_mode ?? 'event',
    order: opts.order ?? 100,
  };
}

function mkNum(
  key: string,
  label: string,
  description: string,
  group: FieldGroup,
  minValue: number,
  maxValue: number,
  defaultValue: string,
  opts: Partial<CharacterFieldDefinition> = {},
): CharacterFieldDefinition {
  return mkField(key, label, description, group, {
    value_type: 'number',
    min_value: minValue,
    max_value: maxValue,
    default_value: defaultValue,
    ...opts,
  });
}

export const DEFAULT_FIELD_DEFINITIONS: CharacterFieldDefinition[] = [
  mkField('name', '姓名', '角色在故事和对话中使用的真实姓名', 'identity', { update_mode: 'manual', display: 'hidden', order: 1 }),
  mkField('occupation', '身份', '职业、阶层、角色职能或故事身份', 'identity', { order: 2 }),
  mkField('race', '种族', '角色所属种族或物种', 'identity', { order: 3 }),
  mkNum('age', '年龄', '角色当前年龄', 'identity', 0, 120, '0', { order: 4 }),
  mkField('gender', '性别', '角色的性别描述', 'identity', { order: 5 }),
  mkField('appearance', '外貌', '当前稳定外貌、衣着或明显身体特征', 'profile', { order: 10 }),
  mkField('personality', '人物特征', '由长期选择和行为逐步形成的性格特征', 'profile', { order: 11 }),
  mkField('background', '经历', '已经发生并持续影响角色的背景经历', 'profile', { order: 12 }),
  mkField('skills', '技能', '角色已经掌握的技能列表', 'ability', { value_type: 'list', order: 20 }),
  mkNum('level', '等级', '故事定义的综合成长等级', 'ability', 0, 9999, '1', { applies_to: 'player', order: 21 }),
  mkNum('charm', '魅力', '影响说服、第一印象与社交反馈的能力', 'ability', 0, 100, '25', { applies_to: 'player', order: 22 }),
  mkNum('action_skill', '行动技巧', '执行复杂、体力或技术行动的综合能力', 'ability', 0, 100, '10', { applies_to: 'player', order: 23 }),
  mkField('social_rank', '社交等级', '角色在当前社会体系中的位置', 'ability', { default_value: 'E', applies_to: 'player', order: 24 }),
  mkNum('willpower', '意志', '抵抗诱惑、压力与精神冲击的能力', 'ability', 0, 100, '15', { applies_to: 'player', order: 25 }),
  mkNum('resilience', '体魄抗性', '承受伤病、疲劳与环境影响的能力', 'ability', 0, 100, '20', { applies_to: 'player', order: 26 }),
  mkNum('luck', '运气', '故事规则允许的偶然性倾向', 'ability', 0, 100, '50', { applies_to: 'player', order: 27 }),
  mkNum('stamina', '体力', '角色继续行动所需的可用精力', 'status', 0, 100, '100', { display: 'primary', primary_for: 'player', update_mode: 'dynamic', order: 30 }),
  mkField('stress', '压力', '当前心理负荷', 'status', { default_value: '低', display: 'primary', primary_for: 'player', update_mode: 'dynamic', order: 31 }),
  mkNum('risk', '风险', '主角当前处境的综合风险', 'status', 0, 100, '0', { applies_to: 'player', display: 'primary', primary_for: 'player', update_mode: 'dynamic', order: 32 }),
  mkField('health', '健康', '身体整体健康状况', 'status', { default_value: '良好', update_mode: 'dynamic', order: 33 }),
  mkField('fatigue', '疲劳', '持续行动造成的疲劳程度', 'status', { default_value: '无', update_mode: 'dynamic', order: 34 }),
  mkField('hunger', '饥饿', '进食需求', 'status', { default_value: '不饿', update_mode: 'dynamic', order: 35 }),
  mkField('mood', '情绪', '当前可观察到的情绪状态', 'status', { default_value: '平静', update_mode: 'dynamic', order: 36 }),
  mkField('pain', '疼痛', '身体疼痛程度', 'status', { default_value: '无', update_mode: 'dynamic', order: 37 }),
  mkField('focus', '当前关注', '角色此刻优先关注的事情', 'status', { update_mode: 'dynamic', order: 38 }),
  mkField('current_thoughts', '当前想法', '角色当前明确形成的想法', 'status', { update_mode: 'dynamic', display: 'hidden', order: 39 }),
  mkField('relationship_stage', '关系阶段', 'NPC 与主角当前的关系阶段', 'relationship', { applies_to: 'npc', update_mode: 'dynamic', order: 40 }),
  mkNum('favor', '好感', 'NPC 对主角的总体好感', 'relationship', -100, 100, '0', { applies_to: 'npc', update_mode: 'dynamic', order: 41 }),
  mkNum('trust', '信任', 'NPC 对主角的信任程度', 'relationship', 0, 100, '0', { applies_to: 'npc', update_mode: 'dynamic', order: 42 }),
];

const BUILT_IN_KEYS = new Set([
  ...DEFAULT_FIELD_DEFINITIONS.map((d) => d.key),
  'rarity', 'action_tendency', 'defense_threshold', 'relationship_status', 'hidden_clue',
]);

export function isValidFieldKey(key: string): boolean {
  return KEY_PATTERN.test(key.trim());
}

export function normalizeFieldDefinition(raw: Partial<CharacterFieldDefinition>): CharacterFieldDefinition | null {
  const key = (raw.key ?? '').trim().toLowerCase();
  const label = (raw.label ?? '').trim();
  if (!key || !label || !isValidFieldKey(key)) return null;
  const base = DEFAULT_FIELD_DEFINITIONS.find((d) => d.key === key);
  const validTypes: FieldValueType[] = ['number', 'text', 'choice', 'list'];
  const validGroups: FieldGroup[] = ['identity', 'profile', 'ability', 'status', 'relationship'];
  const validTargets: FieldTarget[] = ['all', 'player', 'npc'];
  const validDisplays: FieldDisplay[] = ['primary', 'detail', 'hidden'];
  const validModes: FieldUpdateMode[] = ['dynamic', 'event', 'manual'];

  return {
    key,
    label,
    description: (raw.description ?? base?.description ?? '').trim(),
    group: validGroups.includes(raw.group as FieldGroup) ? (raw.group as FieldGroup) : (base?.group ?? 'status'),
    value_type: validTypes.includes(raw.value_type as FieldValueType) ? (raw.value_type as FieldValueType) : (base?.value_type ?? 'text'),
    min_value: typeof raw.min_value === 'number' && Number.isFinite(raw.min_value) ? Math.round(raw.min_value) : (base?.min_value ?? 0),
    max_value: typeof raw.max_value === 'number' && Number.isFinite(raw.max_value) ? Math.round(raw.max_value) : (base?.max_value ?? 100),
    options: Array.isArray(raw.options) ? raw.options.map((s) => String(s).trim()).filter(Boolean) : (base?.options ?? []),
    default_value: raw.default_value !== undefined ? String(raw.default_value).trim() : (base?.default_value ?? ''),
    unit: (raw.unit ?? base?.unit ?? '').trim(),
    applies_to: validTargets.includes(raw.applies_to as FieldTarget) ? (raw.applies_to as FieldTarget) : (base?.applies_to ?? 'all'),
    display: validDisplays.includes(raw.display as FieldDisplay) ? (raw.display as FieldDisplay) : (base?.display ?? 'detail'),
    primary_for: validTargets.includes(raw.primary_for as FieldTarget) ? (raw.primary_for as FieldTarget) : (base?.primary_for ?? 'all'),
    update_mode: validModes.includes(raw.update_mode as FieldUpdateMode) ? (raw.update_mode as FieldUpdateMode) : (base?.update_mode ?? 'event'),
    order: typeof raw.order === 'number' && Number.isFinite(raw.order) ? Math.round(raw.order) : (base?.order ?? 100),
  };
}

export function effectiveDefinitions(configured: CharacterFieldDefinition[] = []): CharacterFieldDefinition[] {
  if (!configured.length) return DEFAULT_FIELD_DEFINITIONS;
  const normalized: CharacterFieldDefinition[] = [];
  const seen = new Set<string>();
  for (const item of configured) {
    const n = normalizeFieldDefinition(item);
    if (n && !seen.has(n.key)) {
      seen.add(n.key);
      normalized.push(n);
    }
    if (normalized.length >= MAX_FIELDS) break;
  }
  const overrides = new Map(normalized.map((d) => [d.key, d]));
  const mergedDefaults = DEFAULT_FIELD_DEFINITIONS.map((d) => overrides.get(d.key) ?? d);
  const additions = normalized.filter((d) => !BUILT_IN_KEYS.has(d.key));
  return [...mergedDefaults, ...additions].sort((a, b) => a.order - b.order || a.key.localeCompare(b.key));
}

export function appliesTo(field: CharacterFieldDefinition, target: 'player' | 'npc'): boolean {
  return field.applies_to === 'all' || field.applies_to === target;
}

export function isPrimaryFor(field: CharacterFieldDefinition, target: 'player' | 'npc'): boolean {
  return field.display === 'primary' && (field.primary_for === 'all' || field.primary_for === target);
}

export function sanitizeFieldValue(field: CharacterFieldDefinition, rawValue: unknown): string | null {
  if (rawValue === null || rawValue === undefined) return null;
  const value = String(rawValue).trim().slice(0, MAX_VALUE_LENGTH);
  if (!value) return null;
  if (field.value_type === 'number') {
    const parsed = Number.parseFloat(value.replace(/[^\d.+-]/g, ''));
    if (!Number.isFinite(parsed)) return null;
    const clamped = Math.max(field.min_value, Math.min(field.max_value, Math.round(parsed)));
    return String(clamped);
  }
  if (field.value_type === 'choice') {
    if (field.options.length > 0 && !field.options.includes(value)) return null;
    return value;
  }
  if (field.value_type === 'list') {
    const items = Array.from(
      new Set(
        value
          .split(/[,，、;；|]/)
          .map((s) => s.trim())
          .filter(Boolean),
      ),
    ).slice(0, 30);
    return items.length ? items.join('、') : null;
  }
  return value;
}

export function valueForPlayer(field: CharacterFieldDefinition, player: PlayerState): string | null {
  if (!appliesTo(field, 'player')) return null;
  let val: string | undefined;
  switch (field.key) {
    case 'name': val = player.name; break;
    case 'age': val = player.age > 0 ? String(player.age) : ''; break;
    case 'gender': val = player.gender; break;
    case 'race': val = player.race; break;
    case 'occupation': val = player.class; break;
    case 'appearance': val = player.appearance; break;
    case 'personality': val = player.personality; break;
    case 'background': val = player.background; break;
    case 'skills': val = player.skills.map((s) => s.name).filter(Boolean).join('、'); break;
    case 'level': val = String(player.level); break;
    case 'charm': val = String(player.charm); break;
    case 'action_skill': val = String(player.hunting_skill); break;
    case 'social_rank': val = player.social_rank; break;
    case 'luck': val = String(player.luck); break;
    case 'willpower': val = String(player.alpha); break;
    case 'resilience': val = String(player.resistance); break;
    case 'stamina': val = String(player.physical_state.stamina); break;
    case 'health': val = player.physical_state.health; break;
    case 'fatigue': val = player.physical_state.fatigue; break;
    case 'hunger': val = player.physical_state.hunger; break;
    case 'pain': val = player.physical_state.pain; break;
    case 'mood': val = player.mental_state.mood || field.default_value; break;
    case 'stress': val = player.mental_state.stress; break;
    case 'focus': val = player.mental_state.focus; break;
    case 'current_thoughts': val = player.mental_state.current_thoughts; break;
    case 'risk': val = String(player.cv); break;
    default: val = player.custom_attributes[field.key] ?? field.default_value;
  }
  return val && val.trim().length > 0 ? val.trim() : null;
}

export function valueForNpc(
  field: CharacterFieldDefinition,
  card: CharacterCard,
  playerName = '',
): string | null {
  if (!appliesTo(field, 'npc')) return null;
  const rel = card.relationships[playerName];
  let val: string | undefined;
  switch (field.key) {
    case 'name': val = card.name; break;
    case 'age': val = card.age > 0 ? String(card.age) : ''; break;
    case 'gender': val = card.gender; break;
    case 'race': val = card.race; break;
    case 'occupation': val = card.occupation; break;
    case 'appearance': val = card.appearance; break;
    case 'personality': val = card.personality; break;
    case 'background': val = card.background; break;
    case 'stamina': val = String(card.physical_state.stamina); break;
    case 'health': val = card.physical_state.health; break;
    case 'fatigue': val = card.physical_state.fatigue; break;
    case 'hunger': val = card.physical_state.hunger; break;
    case 'pain': val = card.physical_state.pain; break;
    case 'mood': val = card.emotion || card.mental_state.mood || field.default_value; break;
    case 'stress': val = card.mental_state.stress; break;
    case 'focus': val = card.mental_state.focus; break;
    case 'current_thoughts': val = card.mental_state.current_thoughts; break;
    case 'relationship_stage': val = rel?.stage || card.relationship_status; break;
    case 'favor': val = String(rel?.favor ?? card.favor); break;
    case 'trust': val = rel !== undefined ? String(rel.trust) : undefined; break;
    default: val = card.custom_attributes[field.key] ?? field.default_value;
  }
  return val && val.trim().length > 0 ? val.trim() : null;
}

export function formatFieldValue(
  field: CharacterFieldDefinition,
  value: string,
  compact: boolean,
): string {
  if (field.value_type !== 'number') return `${value}${field.unit}`;
  if (field.key === 'age') return field.unit ? `${value}${field.unit}` : `${value}岁`;
  if (field.key === 'level' || field.max_value > 1000) return `${value}${field.unit}`;
  if (field.unit === '%') return `${value}%`;
  return compact ? `${value}${field.unit}` : `${value}/${field.max_value}${field.unit}`;
}

export interface RenderedField {
  definition: CharacterFieldDefinition;
  rawValue: string;
  formattedCompact: string;
  formattedDetail: string;
  color: string;
}

const INVERSE_RISK_KEYS = new Set(['risk', 'alertness', 'suspicion', 'threat', 'danger', 'stress', 'corruption']);
const NEUTRAL_KEYS = new Set(['age', 'level', 'charm', 'action_skill', 'willpower', 'resilience', 'luck', 'reputation', 'social_rank']);

export function fieldValueColor(field: CharacterFieldDefinition, rawValue: string): string {
  if (field.key === 'stress') {
    if (rawValue.includes('极高') || rawValue.includes('崩溃')) return '#F07B84';
    if (rawValue.includes('高')) return '#EE9F62';
    if (rawValue.includes('中')) return '#EAC86B';
    return '#6ED49B';
  }
  if (field.value_type !== 'number' || NEUTRAL_KEYS.has(field.key)) {
    return '#ECEAE4';
  }
  const num = Number.parseFloat(rawValue);
  if (!Number.isFinite(num)) return '#ECEAE4';
  const span = Math.max(1, field.max_value - field.min_value);
  const ratio = (num - field.min_value) / span;
  const isRisk =
    INVERSE_RISK_KEYS.has(field.key) ||
    /风险|警觉|危险|嫌疑|压力|污染|暴露/.test(field.label);
  if (isRisk) {
    if (ratio >= 0.7) return '#F07B84';
    if (ratio >= 0.45) return '#EE9F62';
    if (ratio >= 0.25) return '#EAC86B';
    return '#6ED49B';
  }
  if (ratio >= 0.65) return '#6ED49B';
  if (ratio >= 0.35) return '#EAC86B';
  return '#F07B84';
}

export function playerPrimaryFields(
  player: PlayerState,
  configured: CharacterFieldDefinition[],
): RenderedField[] {
  return effectiveDefinitions(configured)
    .filter((f) => appliesTo(f, 'player') && isPrimaryFor(f, 'player'))
    .map((definition) => {
      const rawValue = valueForPlayer(definition, player);
      if (!rawValue) return null;
      return {
        definition,
        rawValue,
        formattedCompact: formatFieldValue(definition, rawValue, true),
        formattedDetail: formatFieldValue(definition, rawValue, false),
        color: fieldValueColor(definition, rawValue),
      };
    })
    .filter((x): x is RenderedField => x !== null)
    .slice(0, 3);
}

export function npcPrimaryFields(
  card: CharacterCard,
  playerName: string,
  configured: CharacterFieldDefinition[],
): RenderedField[] {
  return effectiveDefinitions(configured)
    .filter((f) => appliesTo(f, 'npc') && isPrimaryFor(f, 'npc'))
    .map((definition) => {
      const rawValue = valueForNpc(definition, card, playerName);
      if (!rawValue) return null;
      return {
        definition,
        rawValue,
        formattedCompact: formatFieldValue(definition, rawValue, true),
        formattedDetail: formatFieldValue(definition, rawValue, false),
        color: fieldValueColor(definition, rawValue),
      };
    })
    .filter((x): x is RenderedField => x !== null)
    .slice(0, 3);
}

export function groupedFieldsForPlayer(
  player: PlayerState,
  configured: CharacterFieldDefinition[],
): Record<FieldGroup, RenderedField[]> {
  const result: Record<FieldGroup, RenderedField[]> = {
    status: [],
    identity: [],
    profile: [],
    ability: [],
    relationship: [],
  };
  for (const definition of effectiveDefinitions(configured)) {
    if (definition.display === 'hidden' || definition.key === 'name') continue;
    if (!appliesTo(definition, 'player')) continue;
    const rawValue = valueForPlayer(definition, player);
    if (!rawValue || rawValue === '未知') continue;
    result[definition.group].push({
      definition,
      rawValue,
      formattedCompact: formatFieldValue(definition, rawValue, true),
      formattedDetail: formatFieldValue(definition, rawValue, false),
      color: fieldValueColor(definition, rawValue),
    });
  }
  return result;
}

export function groupedFieldsForNpc(
  card: CharacterCard,
  playerName: string,
  configured: CharacterFieldDefinition[],
): Record<FieldGroup, RenderedField[]> {
  const result: Record<FieldGroup, RenderedField[]> = {
    status: [],
    identity: [],
    profile: [],
    ability: [],
    relationship: [],
  };
  for (const definition of effectiveDefinitions(configured)) {
    if (definition.display === 'hidden' || definition.key === 'name') continue;
    if (!appliesTo(definition, 'npc')) continue;
    const rawValue = valueForNpc(definition, card, playerName);
    if (!rawValue || rawValue === '未知') continue;
    result[definition.group].push({
      definition,
      rawValue,
      formattedCompact: formatFieldValue(definition, rawValue, true),
      formattedDetail: formatFieldValue(definition, rawValue, false),
      color: fieldValueColor(definition, rawValue),
    });
  }
  return result;
}
