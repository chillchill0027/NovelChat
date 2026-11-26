import type { ChapterSummary, NarrationPov, StoryRecord, WorldSetting } from '../../types/models';
import { appliesTo, effectiveDefinitions, valueForPlayer } from '../characterFields';
import type { LengthHint } from './lengthPolicy';

export const DEFAULT_STYLE_GUIDE_ZH = `- 写具体的东西：动作、对白、声音、气味、手里的物件。情绪通过人的反应表现出来，而不是直接点名。
- 对白像真人说话：会停顿、打断、答非所问、话说一半。不同的人说话不一样。
- 节奏有快有慢：紧张时用短句；平静时可以舒展一些。比喻偶尔用，用就用准。
- 赶路、等待这类过场一两句带过，把笔墨留给人和事。
- 篇幅跟着内容走：事情多就多写，事情少就少写，不要为了凑字数重复。`;

export function povPromptLabel(pov: NarrationPov): string {
  return pov === 'second' ? '第二人称（“你”）的视角' : '第三人称、贴近主角的视角';
}

export function buildNarratorSystemPrompt(params: {
  playerName: string;
  pov: NarrationPov;
  styleGuide: string;
  styleSample: string;
  worldProse: string;
  playerProfile: string;
  chaptersText: string;
}): string {
  const lines: string[] = [
    `你是这个互动故事的叙事者：描写世界，并扮演除主角以外的所有角色。玩家扮演主角「${params.playerName || '主角'}」。`,
    '',
    '## 怎么写',
    '- 玩家告诉你主角做了什么、说了什么；你写出接下来发生的事：环境的回应、其他角色的言行，以及行动带来的结果。',
    `- 用${povPromptLabel(params.pov)}写作，只写主角能看见、听见、感觉到的东西。`,
    '- 主角的台词和重大选择属于玩家。玩家给出的行动，你可以写得具体生动；但不要替主角说出玩家没给的关键台词，也不要替他做决定。需要主角表态的时候，让场面停在那里，等玩家。',
    '- 其他角色有自己的目的、情绪和说话方式。他们会主动开口、提要求、隐瞒、拒绝、误会，不只是围着主角转。',
    '- 每次回应都让局面往前走一步：一个新信息、一个人的反应、一处变化，或者一个小麻烦。',
    '- 已经发生的事和世界规则不能推翻。标着“作者备注”的内容是只有你知道的秘密：可以让它影响角色的行为，但角色不会无故说破，主角也不会凭空知道。',
    '- 新出场、和主角有交流的人，给他一个自然的名字，并在正文里用这个名字。',
    '',
    '## 文风',
    params.styleGuide.trim() || DEFAULT_STYLE_GUIDE_ZH,
  ];
  if (params.styleSample.trim()) {
    lines.push('', '## 文风示范（只学语气和节奏，不要复用内容）', params.styleSample.trim());
  }
  lines.push('', '## 输出', '直接写故事正文。不要标题、列表、解释或总结，不要提到“玩家”“回合”“AI”“设定”。');
  if (params.worldProse.trim()) lines.push('', '## 世界', params.worldProse.trim());
  if (params.playerProfile.trim()) lines.push('', '## 主角', params.playerProfile.trim());
  if (params.chaptersText.trim()) lines.push('', '## 前情总览', params.chaptersText.trim());
  return lines.join('\n').trimEnd();
}

export function buildTurnContext(params: {
  timeOfDay: string;
  location: string;
  weather: string;
  sceneDescription: string;
  playerName: string;
  presentNames: string[];
  playerStatus: string;
  currentGoal: string;
  otherPending: string[];
  presentSheets: string[];
  relatedBriefs: string[];
  worldbookLines: string[];
  recentSummaries: string[];
  lengthHint: LengthHint;
}): string {
  const lines: string[] = ['【当前情境】'];
  const header = [params.timeOfDay, params.location, params.weather]
    .map((s) => s.trim())
    .filter(Boolean)
    .join('｜');
  if (header) lines.push(header);
  if (params.sceneDescription.trim()) lines.push(`画面：${params.sceneDescription.trim()}`);
  lines.push(
    `在场：${params.presentNames.length ? params.presentNames.join('、') : `只有${params.playerName || '主角'}`}`,
  );
  if (params.playerStatus.trim()) {
    lines.push(`${params.playerName || '主角'}现状：${params.playerStatus.trim()}`);
  }
  if (params.currentGoal.trim() || params.otherPending.length) {
    const parts: string[] = [];
    if (params.currentGoal.trim()) parts.push(`当前目标：${params.currentGoal.trim()}`);
    if (params.otherPending.length) parts.push(`其他待办：${params.otherPending.join('；')}`);
    lines.push(parts.join('；'));
  }

  if (params.presentSheets.length) {
    lines.push('', '【在场人物】');
    params.presentSheets.forEach((sheet, idx) => {
      if (idx > 0) lines.push('');
      lines.push(sheet);
    });
  }
  if (params.relatedBriefs.length) {
    lines.push('', '【相关人物】', ...params.relatedBriefs.map((b) => `- ${b}`));
  }
  if (params.worldbookLines.length) {
    lines.push('', '【相关设定】', ...params.worldbookLines.map((w) => `- ${w}`));
  }
  if (params.recentSummaries.length) {
    lines.push('', '【近期提要】', ...params.recentSummaries);
  }
  lines.push('', `【本次篇幅】大约 ${params.lengthHint.minChars}-${params.lengthHint.maxChars} 字。`);
  return lines.join('\n');
}

export function formatUserTurn(turnContext: string, rawInput: string): string {
  return `${turnContext}\n\n【主角的行动】\n${rawInput.trim()}`;
}

export function formatHistoryUser(rawInput: string): string {
  return `【主角的行动】\n${rawInput.trim()}`;
}

const STATE_KEEPER_FULL_EXAMPLE = `{"summary":"一句话说明发生了什么","scene":{"location":"地点","time_of_day":"时间","weather":"天气","present":["在场人名"],"description":"一句话结尾画面"},"goals":{"complete":["已完成待办"],"add":["新待办"],"update":[{"from":"旧待办","to":"更新后待办"}],"current":"当前主要目标"},"player":{"fields":{"stamina":"80","stress":"中"},"inventory":[{"op":"add","item":"物品","qty":1}],"relationships":[{"name":"人名","stage":"关系阶段"}]},"npcs":[{"name":"已知人名","emotion":"情绪","favor_delta":3,"trust_delta":2,"relationship_stage":"关系阶段","fields":{"stress":"低"},"memory":"一句重要记忆","inventory":[{"op":"remove","item":"物品","qty":1}]}],"new_npcs":[{"name":"新人真名","identity":"身份","relationship_to_player":"关系","gender":"女","age":24,"personality":"性格","appearance":"外貌","speech_style":"说话方式","fields":{}}],"departed":[{"name":"离开人名","permanent":false}],"transfers":[{"item":"物品","qty":1,"from":"给出方","to":"接收方"}],"world":[{"name":"地点或势力名","type":"location","description":"新事实","keywords":["关键词"]}],"events":[{"title":"事件标题","description":"发生了什么","related":["相关人物或地点"]}],"suggestions":["下一步建议1","建议2","建议3"]}`;

export function buildStateKeeperSystemPrompt(world: WorldSetting): string {
  const fieldLines = effectiveDefinitions(world.character_fields)
    .filter((f) => f.key !== 'name' && f.display !== 'hidden')
    .map((f) => {
      let constraint = '文本';
      if (f.value_type === 'number') constraint = `${f.min_value}~${f.max_value}${f.unit}`;
      else if (f.value_type === 'choice' && f.options.length) constraint = `选项:${f.options.join('/')}`;
      else if (f.value_type === 'list') constraint = '列表(顿号分隔)';
      const desc = f.description.trim().slice(0, 60);
      return `${f.key}｜${f.label}｜${constraint}｜${f.applies_to}｜${f.update_mode}${desc ? `｜${desc}` : ''}`;
    });

  return [
    '你是互动小说的记录员。阅读本轮剧情，把它对人物和世界造成的变化记成一份 JSON。',
    '',
    '规则：',
    '1. 只记录正文里实际发生的变化。不确定的就不写。所有字段都可以省略；什么都没变就输出 {"summary": "…"}。',
    '2. 人物一律使用【已知人物】里的原名。正文里首次出场、和主角有互动的人放进 new_npcs，并给出名字和身份。',
    '3. 角色字段写本轮结束时的最终值，必须符合【字段说明】中的类型和范围。update_mode 为 manual 的字段不要写。',
    '4. 好感、信任写变化量 favor_delta 和 trust_delta（通常在 -10 到 +10 之间）。',
    '5. 待办：彻底完成的放进 complete；有进展但没完成的用 update 改写；新出现的放进 add。',
    '6. 角色之间交接物品写 transfers；从环境中获得或消耗的物品写 player.inventory 或 npcs[].inventory。同一件事只记一次。',
    '7. scene.present 只写正文结尾时和主角在同一现场的人（打电话、发消息的另一方不算）。',
    '8. npcs[].memory：这个角色以后会记得的一件事，一句话；没有就不写。',
    '9. suggestions：给出 3 个玩家接下来可能想做的事，每条 15 字以内，贴合当前局面，彼此不同。',
    '10. 只输出 JSON。',
    '',
    '【字段说明】',
    ...fieldLines,
    '',
    '【输出格式示例】',
    STATE_KEEPER_FULL_EXAMPLE,
  ].join('\n');
}

export function buildStateKeeperUserPrompt(record: StoryRecord, rounds: number[]): string {
  const sortedRounds = [...rounds].sort((a, b) => a - b);
  const lastRound = sortedRounds[sortedRounds.length - 1] ?? record.story.current_round;
  const targetEntries = record.narratives
    .filter((n) => sortedRounds.includes(n.turn))
    .sort((a, b) => a.turn - b.turn);
  const latest = targetEntries[targetEntries.length - 1];
  const earlier = targetEntries.length > 1 ? targetEntries.slice(0, -1).slice(-3) : [];

  const lines: string[] = ['【当前状态】'];
  const presentText = record.story.active_characters.length
    ? record.story.active_characters.join('、')
    : '无';
  lines.push(
    `场景：${record.story.current_location || '未知'}｜${record.story.time_of_day}｜${record.story.weather}；在场：${presentText}`,
  );
  if (!record.story.pending_events.length) {
    lines.push('待办：无');
  } else {
    lines.push('待办：');
    record.story.pending_events.forEach((ev, idx) => lines.push(`${idx + 1}. ${ev}`));
  }

  const dynamicPairs = effectiveDefinitions(record.world.character_fields)
    .filter((f) => f.update_mode === 'dynamic' && f.display !== 'hidden' && appliesTo(f, 'player'))
    .map((f) => {
      const v = valueForPlayer(f, record.player);
      return v ? `${f.key}=${v}` : null;
    })
    .filter((x): x is string => x !== null)
    .slice(0, 10)
    .join('，');
  const invText = record.player.inventory.length
    ? record.player.inventory
        .slice(0, 12)
        .map((i) => (i.quantity > 1 ? `${i.name}×${i.quantity}` : i.name))
        .join('、')
    : '无';
  lines.push(`${record.player.name || '主角'}：${dynamicPairs || '状态正常'}；物品：${invText}`);

  if (record.characters.length) {
    lines.push('', '【已知人物】');
    for (const card of record.characters.slice(0, 20)) {
      const rel = card.relationships[record.player.name];
      const stage = rel?.stage || card.relationship_status || '初识';
      const favor = rel?.favor ?? card.favor;
      const trust = rel?.trust ?? 0;
      const mood = card.emotion || card.mental_state.mood || '平静';
      lines.push(`${card.name}｜${card.occupation || '未知'}｜${stage}｜好感${favor}/信任${trust}｜${mood}`);
    }
  }

  if (earlier.length) {
    lines.push('', '【之前未记录的剧情】');
    for (const entry of earlier) {
      lines.push(`第${entry.turn}轮 行动：${entry.user_input.slice(0, 240)}`);
      lines.push(entry.narrative.slice(0, 900), '');
    }
  }

  lines.push('', `【本轮】（第${lastRound}轮）`);
  if (latest) {
    lines.push(`行动：${latest.user_input}`, '正文：', latest.narrative);
  }
  return lines.join('\n');
}

export function buildChapterSummarySystemPrompt(): string {
  return '你在为一部长篇互动小说写章节梗概。要写清楚：发生了哪些关键事件，人物关系有什么变化，留下了哪些悬念和承诺。只写事实，不评价，200-300 字。';
}

export function buildChapterSummaryUserPrompt(
  previousChapterText: string,
  roundSummaries: { turn: number; summary: string }[],
  lastNarrativeTail: string,
): string {
  const lines = [
    '【上一章梗概】',
    previousChapterText.trim() || '无',
    '',
    '【本章各回合摘要】',
    ...roundSummaries.map((s) => `第${s.turn}轮：${s.summary}`),
  ];
  if (lastNarrativeTail.trim()) {
    lines.push('', '【本章最后一轮正文节选】', lastNarrativeTail.trim());
  }
  return lines.join('\n');
}

export function buildOverviewSummarySystemPrompt(): string {
  return '你在为一部长篇互动小说合并早期章节梗概。必须保留：主线推进节点、长期人物关系变化、关键物品去向、未解决的承诺或悬念。只写事实，不超过 600 字。';
}

export function buildOverviewSummaryUserPrompt(
  existingOverview: string,
  chaptersToMerge: ChapterSummary[],
): string {
  const lines: string[] = [];
  if (existingOverview.trim()) {
    lines.push('【已有前情总览】', existingOverview.trim(), '');
  }
  lines.push(
    '【待合并的早期章节】',
    ...chaptersToMerge.map((c) => `第${c.from_turn}-${c.to_turn}轮：${c.text}`),
  );
  return lines.join('\n');
}
