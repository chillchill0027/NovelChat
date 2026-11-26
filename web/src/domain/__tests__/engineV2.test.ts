import { beforeEach, describe, expect, it } from 'vitest';
import {
  chatCompletionsUrl,
  normalizeApiUrlDraft,
  validateApiConfig,
} from '../apiEndpoint';
import { formatFieldValue, playerPrimaryFields } from '../characterFields';
import { BUILTIN_ADVANCED_TEMPLATES, CHARACTER_PRESETS, createStoryFromPreset } from '../defaultStories';
import { assembleNarratorContext } from '../engine/contextAssembler';
import { parseLooseJson } from '../engine/jsonExtract';
import { cleanNarrativeText } from '../engine/narrativeCleaner';
import { evaluateNarrativeGuard } from '../engine/narrativeGuard';
import { applyStateDelta } from '../engine/stateDeltaApplier';
import { buildStoryRecordFromParsed, parseAdvancedStoryJson } from '../storyImport';
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

describe('Advanced Story Import & Built-in Templates', () => {
  it('validates both built-in templates strictly and preserves sample_dialogues & character_fields', () => {
    for (const tpl of BUILTIN_ADVANCED_TEMPLATES) {
      const parsed = parseAdvancedStoryJson(tpl.json, true);
      expect(parsed.world.name.length).toBeGreaterThan(0);
      expect(parsed.player.name.length).toBeGreaterThan(0);
      expect(parsed.characters.length).toBeGreaterThan(0);
      expect(parsed.worldbook.length).toBeGreaterThan(0);
      if (tpl.id === 'shendiao') {
        expect(parsed.world.name).toBe('神雕侠侣 · 终南烟雨');
        expect(parsed.player.name).toBe('杨过');
        expect(parsed.characters.length).toBe(24);
        expect(parsed.worldbook.length).toBe(42);
        expect(parsed.player.custom_attributes.story_mode).toBe('正史主线');
        expect(parsed.player.custom_attributes.destiny_nudges).toBe('0');
        expect(parsed.opening.active_characters).toEqual(['赵志敬', '鹿清笃', '甄志丙']);
      }
      if (tpl.id === 'misty_harbor') {
        expect(parsed.world.name).toBe('雾港老城');
        expect(parsed.player.name).toBe('沈舟');
        const xuTang = parsed.characters.find((c) => c.name === '许棠');
        expect(xuTang?.lore.sample_dialogues.length).toBeGreaterThan(0);
        const primary = playerPrimaryFields(parsed.player, parsed.world.character_fields);
        expect(primary.map((p) => p.definition.label)).toContain('精力');
        expect(primary.map((p) => p.definition.label)).toContain('警觉度');
      }
    }
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

describe('Engine V2 Core (Cleaner, Guard, Worldbook, ContextAssembler, StateDeltaApplier)', () => {
  it('cleans fenced/JSON-wrapped narrative output cleanly', () => {
    const raw = '```json\n{"narrative": "细雨落在青石巷的屋檐上，沈舟推开木门。"}\n```';
    expect(cleanNarrativeText(raw)).toBe('细雨落在青石巷的屋檐上，沈舟推开木门。');
  });

  it('strips meta-leak sentences and rejects overly short text on attempt 0', () => {
    const shortVerdict = evaluateNarrativeGuard('太短了。', '', 0);
    expect(shortVerdict.kind).toBe('retry');

    const prose =
      '青石巷的石板缝里积着雨水，走一步映一段屋檐影子。作为AI我来为你续写。沈舟把帆布包往肩上提了提，拐进听雨茶馆的木门，迎面一股炒青乌龙的香气扑来。许棠正在擦柜台，听见门响抬头笑道：“来得正好，二楼现在没人，水刚烧开。”';
    const passVerdict = evaluateNarrativeGuard(prose, '', 0);
    expect(passVerdict.kind).toBe('pass');
    if (passVerdict.kind === 'pass') {
      expect(passVerdict.hadMetaLeakStripped).toBe(true);
      expect(passVerdict.cleanedText).not.toContain('作为AI');
    }
  });

  it('matches worldbook with location > recent > input priority', () => {
    const misty = BUILTIN_ADVANCED_TEMPLATES.find((t) => t.id === 'misty_harbor')!;
    const parsed = parseAdvancedStoryJson(misty.json, true);
    const rec = buildStoryRecordFromParsed(parsed);
    const matched = matchWorldbook('去七号码头看看清单', rec.story, rec.worldbook, [], 5);
    expect(matched.length).toBeGreaterThan(0);
    expect(matched.some((m) => m.name.includes('码头') || m.keywords.includes('清单'))).toBe(true);
  });

  it('assembles narrator prompt with world, player, present NPCs, and length hint', () => {
    const misty = BUILTIN_ADVANCED_TEMPLATES.find((t) => t.id === 'misty_harbor')!;
    const parsed = parseAdvancedStoryJson(misty.json, true);
    const rec = buildStoryRecordFromParsed(parsed);
    const prompt = assembleNarratorContext(rec, '推门走进茶馆看看许棠在不在', 'standard');
    expect(prompt.systemPrompt).toContain('雾港老城');
    expect(prompt.systemPrompt).toContain('沈舟');
    expect(prompt.userTurn).toContain('【本次篇幅】');
    expect(prompt.userTurn).toContain('推门走进茶馆看看许棠在不在');
  });

  it('applies StateDelta incrementally without overwriting rich worldbook descriptions', () => {
    const misty = BUILTIN_ADVANCED_TEMPLATES.find((t) => t.id === 'misty_harbor')!;
    const parsed = parseAdvancedStoryJson(misty.json, true);
    const rec = buildStoryRecordFromParsed(parsed);
    const existingEntry = rec.worldbook.find((w) => w.name === '听雨茶馆');
    expect(existingEntry).toBeDefined();

    const delta = parseLooseJson<Record<string, unknown>>(`
      \`\`\`json
      {
        "summary": "沈舟在听雨茶馆与许棠确认了铜印线索。",
        "scene": { "location": "听雨茶馆二楼", "present": ["许棠"] },
        "npcs": [{ "name": "许棠", "favor_delta": 6, "trust_delta": 5, "memory": "与沈舟约定一同查清1998年码头清单" }],
        "world": [{ "name": "听雨茶馆", "type": "location", "description": "二楼靠窗的榆木桌适合密谈。" }],
        "suggestions": ["询问陆沉来意", "翻看旧清单背面", "下楼去老修表店"]
      }
      \`\`\`
    `);
    expect(delta).not.toBeNull();
    const out = applyStateDelta(rec, delta as never, [1], '2026-11-26T21:00:00Z');
    const updatedTeaHouse = out.record.worldbook.find((w) => w.name === '听雨茶馆');
    expect(updatedTeaHouse?.description).toContain(existingEntry!.description.slice(0, 10));
    expect(updatedTeaHouse?.description).toContain('二楼靠窗的榆木桌适合密谈');
    const updatedXuTang = out.record.characters.find((c) => c.name === '许棠');
    expect(updatedXuTang?.relationships['沈舟']?.favor).toBe((existingEntry ? 6 : 6));
    expect(updatedXuTang?.memories.length).toBe(1);
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
        '26',
        false,
      ),
    ).toBe('26岁');
    expect(
      formatFieldValue(
        {
          key: 'alertness', label: '警觉度', description: '', group: 'status',
          value_type: 'number', min_value: 0, max_value: 100, options: [],
          default_value: '35', unit: '%', applies_to: 'all', display: 'primary',
          primary_for: 'player', update_mode: 'dynamic', order: 20,
        },
        '55',
        false,
      ),
    ).toBe('55%');
  });
});

describe('Repository Checkpoints, Rollback & Backup Export/Import', () => {
  beforeEach(() => {
    clearMemoryDbForTests();
  });

  it('rolls back round state cleanly and round-trips story export/import', async () => {
    const story = createStoryFromPreset(CHARACTER_PRESETS[0]);
    story.checkpoints[1] = createCheckpointSnapshot(story, 1, false);
    story.story.current_round = 1;
    story.story.current_location = '近岸商务区';
    story.narratives.push({
      turn: 1,
      user_input: '坐地铁去公司',
      narrative: '地铁车厢里挤满了早高峰通勤的人。',
      created_at: '2026-11-26T21:00:00Z',
    });
    await saveStory(story);

    const exported = await exportSingleStory(story.id);
    const imported = await importBackupBundle(JSON.stringify(exported));
    expect(imported).toHaveLength(1);
    expect(imported[0].narratives).toHaveLength(1);

    const rolled = await rollbackToBeforeRound(story.id, 1);
    expect(rolled.story.current_round).toBe(0);
    expect(rolled.story.current_location).toBe('云桥里老小区');
    expect(rolled.narratives).toHaveLength(0);

    const fetched = await getStory(story.id);
    expect(fetched?.narratives).toHaveLength(0);
  });
});
