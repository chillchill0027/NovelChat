import { beforeEach, describe, expect, it } from 'vitest';
import {
  chatCompletionsUrl,
  normalizeApiUrlDraft,
  validateApiConfig,
} from '../apiEndpoint';
import { formatFieldValue, playerPrimaryFields } from '../characterFields';
import { BUILTIN_ADVANCED_TEMPLATES, createDefaultStory } from '../defaultStories';
import { assembleNarratorContext } from '../engine/contextAssembler';
import { parseLooseJson } from '../engine/jsonExtract';
import { cleanNarrativeText } from '../engine/narrativeCleaner';
import { evaluateNarrativeGuard } from '../engine/narrativeGuard';
import { applyStateDelta } from '../engine/stateDeltaApplier';
import { parseAdvancedStoryJson } from '../storyImport';
import { matchWorldbook } from '../worldbookMatcher';
import { clearMemoryDbForTests } from '../../storage/db';
import {
  createCheckpointSnapshot,
  exportSingleStory,
  getStory,
  importBackupBundle,
  rollbackToBeforeRound,
  saveStory,
} from '../../storage/storyRepository';

describe('ApiEndpointPolicy (Web)', () => {
  it('normalizes Chinese IME full-width punctuation in URLs', () => {
    expect(normalizeApiUrlDraft('https：//api。deepseek．com／v1')).toBe('https://api.deepseek.com/v1');
    expect(chatCompletionsUrl('https：//api。deepseek。com')).toBe(
      'https://api.deepseek.com/v1/chat/completions',
    );
  });

  it('allows local HTTP without key but requires HTTPS + key for cloud hosts', () => {
    expect(validateApiConfig('http://127.0.0.1:11434', '', 'qwen2.5:14b')).toBeNull();
    expect(validateApiConfig('http://api.deepseek.com', 'sk-test', 'deepseek-chat')).toContain('HTTPS');
    expect(validateApiConfig('https://api.deepseek.com', '', 'deepseek-chat')).toContain('API Key');
    expect(validateApiConfig('https://api.deepseek.com', 'sk-test123', 'deepseek-chat')).toBeNull();
  });
});

describe('Sole Default Story: 神雕侠侣 · 终南烟雨', () => {
  it('ships Shen Diao Xia Lv as the sole built-in template and default story', () => {
    expect(BUILTIN_ADVANCED_TEMPLATES).toHaveLength(1);
    expect(BUILTIN_ADVANCED_TEMPLATES[0].id).toBe('shendiao');

    const parsed = parseAdvancedStoryJson(BUILTIN_ADVANCED_TEMPLATES[0].json, true);
    expect(parsed.world.name).toBe('神雕侠侣 · 终南烟雨');
    expect(parsed.player.name).toBe('杨过');
    expect(parsed.characters.length).toBe(24);
    expect(parsed.worldbook.length).toBe(42);
    expect(parsed.player.custom_attributes.story_mode).toBe('正史主线');
    expect(parsed.player.custom_attributes.destiny_nudges).toBe('0');
    expect(parsed.opening.active_characters).toEqual(['赵志敬', '鹿清笃', '甄志丙']);

    const xiaoLongNv = parsed.characters.find((c) => c.name === '小龙女');
    expect(xiaoLongNv?.lore.sample_dialogues.length).toBeGreaterThan(0);

    const primary = playerPrimaryFields(parsed.player, parsed.world.character_fields);
    expect(primary.map((p) => p.definition.label)).toEqual(['真气精力', '内功修为', '江湖凶险']);
  });

  it('rejects generic placeholder names in strict mode', () => {
    const bad = JSON.stringify({
      schema_version: 1,
      world_setting: { name: '测试世界', description: '一段描述' },
      player: { name: '主角' },
      npcs: [],
      worldbook: [],
      opening: { current_location: '起点' },
    });
    expect(() => parseAdvancedStoryJson(bad, true)).toThrow(/具体姓名/);
  });
});

describe('Engine V2 Core with Shen Diao Xia Lv', () => {
  it('cleans fenced/JSON-wrapped narrative output cleanly', () => {
    const raw = '```json\n{"narrative": "终南山松风猎猎，杨过冷笑一声横剑当胸。"}\n```';
    expect(cleanNarrativeText(raw)).toBe('终南山松风猎猎，杨过冷笑一声横剑当胸。');
  });

  it('strips meta-leak sentences and rejects overly short text on attempt 0', () => {
    const shortVerdict = evaluateNarrativeGuard('太短了。', '', 0);
    expect(shortVerdict.kind).toBe('retry');

    const prose =
      '终南山重阳宫演武场上松风猎猎，几十双眼睛齐刷刷落在杨过身上。作为AI我来为你续写。鹿清笃冷哼一声，提着铁木剑大步跨出队列，剑尖斜指青石板：“杨师弟，既然入门三月，今日便请指教！”赵志敬在阶上捻须微笑，眼底却闪过一丝阴冷。';
    const passVerdict = evaluateNarrativeGuard(prose, '', 0);
    expect(passVerdict.kind).toBe('pass');
    if (passVerdict.kind === 'pass') {
      expect(passVerdict.hadMetaLeakStripped).toBe(true);
      expect(passVerdict.cleanedText).not.toContain('作为AI');
    }
  });

  it('matches worldbook with location > recent > input priority on Shen Diao lore', () => {
    const rec = createDefaultStory();
    const matched = matchWorldbook('我想逃进后山活死人墓看看玉蜂', rec.story, rec.worldbook, [], 6);
    expect(matched.length).toBeGreaterThan(0);
    expect(matched.some((m) => m.name === '活死人墓' || m.name.includes('玉蜂'))).toBe(true);
  });

  it('assembles narrator prompt containing Yang Guo, world rules, and 3-nudge branching law', () => {
    const rec = createDefaultStory();
    const prompt = assembleNarratorContext(rec, '我不理会鹿清笃挑衅，转身准备溜下终南山', 'standard');
    expect(prompt.systemPrompt).toContain('神雕侠侣 · 终南烟雨');
    expect(prompt.systemPrompt).toContain('杨过');
    expect(prompt.systemPrompt).toContain('三度引流');
    expect(prompt.systemPrompt).toContain('自创江湖');
    expect(prompt.userTurn).toContain('赵志敬');
    expect(prompt.userTurn).toContain('【本次篇幅】');
  });

  it('tracks story_mode & destiny_nudges through 3 nudges into 自创江湖 without clobbering worldbook', () => {
    const rec = createDefaultStory();
    const existingGumu = rec.worldbook.find((w) => w.name === '活死人墓')!;

    const delta = parseLooseJson<Record<string, unknown>>(`
      \`\`\`json
      {
        "summary": "杨过三次婉拒重返终南山与大胜关之邀，决意远走大理自创江湖。",
        "scene": { "location": "风陵渡口", "present": [] },
        "player": {
          "fields": {
            "story_mode": "自创江湖",
            "destiny_nudges": "3",
            "internal_force": "28",
            "martial_realm": "三流好手"
          }
        },
        "world": [{ "name": "活死人墓", "type": "location", "description": "江湖传闻白衣仙子与赤练仙子曾在墓中大战。" }],
        "suggestions": ["在渡口酒肆听听江湖消息", "雇船南下前往江南", "寻找奇人异士切磋武艺"]
      }
      \`\`\`
    `);
    expect(delta).not.toBeNull();
    const out = applyStateDelta(rec, delta as never, [3], '2026-11-26T22:15:00Z');
    expect(out.record.player.custom_attributes.story_mode).toBe('自创江湖');
    expect(out.record.player.custom_attributes.destiny_nudges).toBe('3');
    expect(out.record.player.custom_attributes.martial_realm).toBe('三流好手');
    const updatedGumu = out.record.worldbook.find((w) => w.name === '活死人墓')!;
    expect(updatedGumu.description).toContain(existingGumu.description.slice(0, 12));
    expect(updatedGumu.description).toContain('白衣仙子与赤练仙子曾在墓中大战');
    expect(out.suggestions).toHaveLength(3);
  });

  it('formats character fields cleanly without absurd /120 age or /100% denominators', () => {
    expect(
      formatFieldValue(
        {
          key: 'age', label: '年龄', description: '', group: 'identity',
          value_type: 'number', min_value: 0, max_value: 120, options: [],
          default_value: '0', unit: '', applies_to: 'all', display: 'detail',
          primary_for: 'all', update_mode: 'event', order: 4,
        },
        '14',
        false,
      ),
    ).toBe('14岁');
    expect(
      formatFieldValue(
        {
          key: 'risk', label: '江湖凶险', description: '', group: 'status',
          value_type: 'number', min_value: 0, max_value: 100, options: [],
          default_value: '25', unit: '%', applies_to: 'player', display: 'primary',
          primary_for: 'player', update_mode: 'dynamic', order: 20,
        },
        '35',
        false,
      ),
    ).toBe('35%');
  });
});

describe('Repository Checkpoints, Rollback & Backup Export/Import', () => {
  beforeEach(() => {
    clearMemoryDbForTests();
  });

  it('rolls back round state cleanly and round-trips Shen Diao story export/import', async () => {
    const story = createDefaultStory();
    story.checkpoints[1] = createCheckpointSnapshot(story, 1, false);
    story.story.current_round = 1;
    story.story.current_location = '终南山后山古林与寒潭';
    story.narratives.push({
      turn: 1,
      user_input: '施展蛤蟆功撞开鹿清笃，转身奔向后山松林',
      narrative: '杨过伏低身子双掌骤推，震得鹿清笃连退数步，趁乱跃入后山松林。',
      created_at: '2026-11-26T22:15:00Z',
    });
    await saveStory(story);

    const exported = await exportSingleStory(story.id);
    const imported = await importBackupBundle(JSON.stringify(exported));
    expect(imported).toHaveLength(1);
    expect(imported[0].narratives).toHaveLength(1);
    expect(imported[0].player.name).toBe('杨过');

    const rolled = await rollbackToBeforeRound(story.id, 1);
    expect(rolled.story.current_round).toBe(0);
    expect(rolled.story.current_location).toBe('终南山重阳宫演武场');
    expect(rolled.narratives).toHaveLength(0);

    const fetched = await getStory(story.id);
    expect(fetched?.narratives).toHaveLength(0);
  });
});
