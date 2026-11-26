import type {
  CharacterCard,
  CharacterFieldDefinition,
  MemoryNote,
  PlayerState,
  WorldSetting,
} from '../../types/models';
import {
  appliesTo,
  effectiveDefinitions,
  formatFieldValue,
  valueForNpc,
  valueForPlayer,
} from '../characterFields';

function clipLines(lines: string[], maxChars: number): string {
  if (maxChars <= 0) return '';
  let out = '';
  for (const line of lines) {
    const nextLen = out ? out.length + 1 + line.length : line.length;
    if (nextLen <= maxChars) {
      out = out ? `${out}\n${line}` : line;
    } else {
      const remaining = maxChars - out.length - (out ? 1 : 0);
      if (remaining >= 24) {
        out = out ? `${out}\n${line.slice(0, remaining - 1)}…` : `${line.slice(0, remaining - 1)}…`;
      }
      break;
    }
  }
  return out;
}

export function renderWorldProse(world: WorldSetting, maxChars = 2500): string {
  const lines: string[] = [];
  if (world.name.trim()) {
    const meta = [world.genre, world.lore.era, world.tone].filter((s) => s?.trim()).join('｜');
    lines.push(meta ? `${world.name}（${meta}）` : world.name);
  }
  if (world.description.trim()) lines.push(world.description.trim());
  const lore = world.lore;
  const addLine = (label: string, val: string) => {
    if (val?.trim()) lines.push(`${label}：${val.trim()}`);
  };
  addLine('背景前提', lore.premise);
  addLine('历史脉络', lore.history);
  addLine('地理空间', lore.geography);
  const powerTech = Array.from(
    new Set([lore.power_system, world.magic_level, world.tech_level, lore.technology_rules].map((s) => s?.trim()).filter(Boolean)),
  ).join('；');
  addLine('力量/技术规则', powerTech);
  const society = [lore.society, lore.culture, lore.law, lore.economy].map((s) => s?.trim()).filter(Boolean).join('；');
  addLine('社会与制度', society);
  if (lore.factions.length) lines.push(`主要势力：${lore.factions.slice(0, 8).join('、')}`);
  if (lore.conflicts.length) lines.push(`核心矛盾：${lore.conflicts.slice(0, 6).join('；')}`);
  if (lore.rules.length) lines.push(`世界法则：${lore.rules.slice(0, 8).join('；')}`);
  if (world.forbidden.length) lines.push(`叙事禁忌：${world.forbidden.slice(0, 8).join('；')}`);
  for (const [k, v] of Object.entries(lore.custom ?? {}).slice(0, 6)) {
    if (k.trim() && v.trim()) lines.push(`${k}：${v}`);
  }
  return clipLines(lines, maxChars);
}

const STANDARD_PLAYER_KEYS = new Set([
  'name', 'age', 'gender', 'race', 'occupation', 'appearance', 'personality', 'background', 'skills',
]);

export function renderPlayerProfile(player: PlayerState, world: WorldSetting, maxChars = 1600): string {
  const lines: string[] = [];
  const basics = [
    player.name.trim(),
    player.gender.trim(),
    player.age > 0 ? `${player.age}岁` : '',
    player.class.trim(),
    player.race.trim() && player.race.trim() !== '人类' ? player.race.trim() : '',
  ]
    .filter(Boolean)
    .join('｜');
  if (basics) lines.push(basics);
  if (player.appearance.trim()) lines.push(`外貌：${player.appearance.trim()}`);
  if (player.personality.trim()) lines.push(`性格：${player.personality.trim()}`);
  if (player.background.trim()) lines.push(`背景：${player.background.trim()}`);

  const lore = player.lore;
  if (lore.speech_style.trim()) lines.push(`说话习惯：${lore.speech_style.trim()}`);
  if (lore.values.length) lines.push(`看重：${lore.values.slice(0, 5).join('、')}`);
  if (lore.goals.length) lines.push(`长期目标：${lore.goals.slice(0, 5).join('；')}`);
  if (lore.fears.length) lines.push(`忌惮：${lore.fears.slice(0, 4).join('、')}`);
  if (lore.weaknesses.length) lines.push(`弱点：${lore.weaknesses.slice(0, 4).join('、')}`);
  if (lore.habits.length) lines.push(`习惯：${lore.habits.slice(0, 4).join('、')}`);
  if (lore.boundaries.length) lines.push(`底线：${lore.boundaries.slice(0, 4).join('；')}`);
  if (lore.abilities.length) {
    lines.push(`擅长/能力：${lore.abilities.slice(0, 6).join('、')}`);
  } else if (player.skills.length) {
    lines.push(`技能：${player.skills.slice(0, 6).map((s) => s.name).join('、')}`);
  }

  const staticExtras = effectiveDefinitions(world.character_fields)
    .filter(
      (f) =>
        f.update_mode !== 'dynamic' &&
        f.display !== 'hidden' &&
        appliesTo(f, 'player') &&
        !STANDARD_PLAYER_KEYS.has(f.key),
    )
    .map((f) => {
      const raw = valueForPlayer(f, player);
      return raw ? `${f.label}：${formatFieldValue(f, raw, false)}` : null;
    })
    .filter((x): x is string => x !== null)
    .slice(0, 6);
  if (staticExtras.length) lines.push(staticExtras.join('；'));

  const text = lines.join('\n').trim();
  return text.length <= maxChars ? text : `${text.slice(0, maxChars - 1)}…`;
}

export function renderPlayerStatus(player: PlayerState, world: WorldSetting, maxChars = 120): string {
  const parts: string[] = [];
  for (const field of effectiveDefinitions(world.character_fields)) {
    if (field.update_mode !== 'dynamic' || field.display === 'hidden' || !appliesTo(field, 'player')) continue;
    const raw = valueForPlayer(field, player);
    if (!raw) continue;
    if (field.key === 'health' && raw === '良好') continue;
    if ((field.key === 'fatigue' || field.key === 'pain') && raw === '无') continue;
    if (field.key === 'hunger' && raw === '不饿') continue;
    parts.push(`${field.label} ${formatFieldValue(field, raw, true)}`);
  }
  if (player.physical_state.injuries.length) {
    parts.push(`伤势：${player.physical_state.injuries.slice(0, 2).join('、')}`);
  }
  if (player.inventory.length) {
    const items = player.inventory
      .slice(0, 4)
      .map((i) => (i.quantity > 1 ? `${i.name}×${i.quantity}` : i.name))
      .join('、');
    parts.push(`随身：${items}`);
  }
  const summary = parts.join('；') || '状态平稳';
  return summary.length <= maxChars ? summary : `${summary.slice(0, maxChars - 1)}…`;
}

const STANDARD_NPC_STATUS_KEYS = new Set([
  'mood', 'favor', 'trust', 'relationship_stage', 'current_thoughts',
  'health', 'fatigue', 'hunger', 'pain', 'stress', 'focus',
]);

export function selectTopMemories(memories: MemoryNote[], currentRound: number, limit: number): MemoryNote[] {
  if (limit <= 0 || !memories.length) return [];
  return memories
    .filter((m) => m.text.trim())
    .sort((a, b) => {
      const scoreA = a.weight * 10 - Math.max(0, currentRound - a.turn);
      const scoreB = b.weight * 10 - Math.max(0, currentRound - b.turn);
      return scoreB - scoreA;
    })
    .slice(0, limit);
}

export function renderNpcFullSheet(
  card: CharacterCard,
  playerName: string,
  characterFields: CharacterFieldDefinition[],
  currentRound: number,
  maxMemories = 3,
): string {
  const rel = card.relationships[playerName];
  const relStage = rel?.stage?.trim() || (card.relationship_status && card.relationship_status !== '未知' ? card.relationship_status : '');
  const headerMeta = [card.occupation.trim(), card.age > 0 ? `${card.age}岁` : ''].filter(Boolean).join('，');
  const relationPart = relStage ? `与${playerName || '主角'}：${relStage}` : '';
  const fullMeta = [headerMeta, relationPart].filter(Boolean).join('；');

  const lines: string[] = [fullMeta ? `### ${card.name}（${fullMeta}）` : `### ${card.name}`];
  if (card.personality.trim()) lines.push(`性格：${card.personality.trim()}`);
  if (card.appearance.trim()) lines.push(`外貌：${card.appearance.trim()}`);
  if (card.lore.speech_style.trim()) lines.push(`说话方式：${card.lore.speech_style.trim()}`);
  if (card.lore.sample_dialogues.length) {
    const quotes = card.lore.sample_dialogues
      .map((q) => q.trim().replace(/^[「“"]|[」”"]$/g, ''))
      .filter(Boolean)
      .slice(0, 4)
      .map((q) => `「${q}」`)
      .join('');
    if (quotes) lines.push(`示例台词：${quotes}`);
  }

  const nowParts: string[] = [];
  const mood = card.emotion.trim() || card.mental_state.mood.trim();
  if (mood) {
    const thoughts = card.mental_state.current_thoughts.trim();
    nowParts.push(thoughts ? `${mood}（${thoughts}）` : mood);
  }
  const favor = rel?.favor ?? card.favor;
  const trust = rel?.trust ?? 0;
  nowParts.push(`好感 ${favor}，信任 ${trust}/100`);

  const dynamicExtra = effectiveDefinitions(characterFields)
    .filter(
      (f) =>
        f.display !== 'hidden' &&
        f.update_mode === 'dynamic' &&
        appliesTo(f, 'npc') &&
        !STANDARD_NPC_STATUS_KEYS.has(f.key),
    )
    .map((f) => {
      const raw = valueForNpc(f, card, playerName);
      return raw ? `${f.label} ${formatFieldValue(f, raw, false)}` : null;
    })
    .filter((x): x is string => x !== null)
    .slice(0, 4);
  nowParts.push(...dynamicExtra);
  if (nowParts.length) lines.push(`此刻：${nowParts.join('；')}。`);

  if (maxMemories > 0 && card.memories.length) {
    const top = selectTopMemories(card.memories, currentRound, maxMemories);
    if (top.length) {
      lines.push(`记得：${top.map((m) => `${m.text}（第${m.turn}轮）`).join('；')}。`);
    }
  }

  const secrets = Array.from(
    new Set([
      ...card.lore.secrets.map((s) => s.trim()).filter(Boolean),
      ...(card.kink.trim() ? [card.kink.trim()] : []),
    ]),
  );
  if (secrets.length) {
    lines.push(`作者备注（角色不会主动透露）：${secrets.join('；')}`);
  }
  return lines.join('\n');
}

export function renderNpcBrief(card: CharacterCard, playerName: string): string {
  const rel = card.relationships[playerName];
  const relStage = rel?.stage?.trim() || (card.relationship_status && card.relationship_status !== '未知' ? card.relationship_status : '');
  const parts = [card.occupation.trim(), relStage ? `与${playerName || '主角'}：${relStage}` : ''].filter(Boolean);
  return parts.length ? `${card.name}（${parts.join('，')}）` : card.name;
}
