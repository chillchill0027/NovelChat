import shendiaoTemplate from '../assets/templates/shendiao_v1.json';
import type { StoryRecord } from '../types/models';
import { buildStoryRecordFromParsed, parseAdvancedStoryJson } from './storyImport';

export const DEFAULT_STORY_TEXT = `标题：神雕侠侣 · 终南烟雨

背景：南宋端平元年（公元1234年）深秋，金国初灭，蒙古铁骑南指。终南山全真教执道门牛耳，后山活死人墓隐世独居，赤练仙子李莫愁横行江南，北侠郭靖与黄蓉心系襄阳防务。

主角设定：玩家扮演十四岁的孤儿杨过，刚随郭靖上终南山重阳宫拜赵志敬为师三月，受尽冷眼与师兄鹿清笃刁难。

核心命线机制：若杨过偏离神雕原著主线，系统在自然剧情中安排最多 3 次主动回归引导；若继续偏离则彻底解绑进入「自创江湖」，不再受原著束缚，而江湖八大时代大事仍按时间线在背景中独立发生。`;

export const BUILTIN_ADVANCED_TEMPLATES = [
  {
    id: 'shendiao',
    name: '神雕侠侣 · 终南烟雨（扮演杨过）',
    tag: '唯一默认史诗剧本 · 三度引流 + 自创江湖',
    description:
      '7 万字级超大体量武侠设定：24 位实名经典人物与台词口吻、42 条地点/宗门/秘籍/八大年表世界书；偏离主线触发 3 次自然回归引导，继续偏离彻底解绑「自创江湖」，江湖大势仍按年表在背景中独立演进。',
    json: JSON.stringify(shendiaoTemplate, null, 2),
  },
];

export function createDefaultStory(): StoryRecord {
  const parsed = parseAdvancedStoryJson(BUILTIN_ADVANCED_TEMPLATES[0].json, true);
  return buildStoryRecordFromParsed(parsed);
}
