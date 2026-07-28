# NovelChat 高级故事设定接入指南

本文档对应高级导入协议 `schema_version: 1`。该入口用于把完整世界观、主角、NPC、世界书和开局状态直接接入 NovelChat，不经过 AI 二次识别。

配套文件：

- [advanced_story_import_v1.example.json](./advanced_story_import_v1.example.json)：可直接导入的完整示例。
- [advanced_story_import_v1.schema.json](./advanced_story_import_v1.schema.json)：机器可读 JSON Schema。
- 应用内“载入完整模板”：与示例同等完整，可直接校验和创建故事。

最快体验方式：把示例 JSON 保存到手机，进入“导入故事 -> 高级接入”，点击文件按钮选择它，校验预览后点击“确认并开始”。

## 1. 入口与流程

1. 打开“新故事”或“导入故事”。
2. 切换到“高级接入”。
3. 选择一种输入方式：
   - 文件按钮：读取 UTF-8 编码的 `.json` 或文本文件。
   - 粘贴按钮：读取系统剪贴板。
   - 模板按钮：载入内置完整示例。
4. 点击“校验并生成预览”。
5. 查看世界、主角、NPC、世界书和开局的完整预览。
6. 点击“确认并开始”创建独立故事存档。

校验和预览均为本地确定性操作，不请求模型，也不消耗 API 额度。

创建阶段会先写入独立临时故事目录，再建立故事库索引；任何一步失败都会清理本次未完成目录，不会覆盖其他故事。创建成功后，世界设定、主角、NPC、世界书和开局状态均保存在应用私有目录，重启应用仍可恢复。删除该故事只删除它自己的目录。

## 2. 顶层结构

```json
{
  "schema_version": 1,
  "world_setting": {},
  "player": {},
  "npcs": [],
  "worldbook": [],
  "opening": {}
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `schema_version` | integer | 建议 | 当前值固定为 `1`；省略时按 v1 兼容解析 |
| `world_setting` | object | 是 | 全局、稳定、每轮都适用的世界规则 |
| `player` | object | 是 | 主角身份、长期设定和初始状态 |
| `npcs` | array | 否 | 初始已知 NPC，最多 50 位 |
| `worldbook` | array | 否 | 按关键词加载的具体资料，最多 100 条 |
| `opening` | object | 否 | 故事第一刻的现场状态 |

推荐使用本文档中的 `snake_case` 字段名。解析器也兼容常见 `camelCase` 别名，但外部系统不应依赖别名。

## 3. 信息应该放在哪里

### `world_setting`

只放全局稳定且几乎每轮都需要遵守的内容，例如：

- 时代和现实层级
- 科技、魔法或力量边界
- 法律、经济和因果规则
- 叙事基调与明确禁区
- 世界级冲突和主题

### `player` / `npcs`

分为两类：

- 基础身份：姓名、年龄、职业、性格、外貌、背景。
- `lore`：不会因为一两轮剧情就改变的动机、价值观、秘密、语言风格和关系事实。

身体疲劳、当前情绪、随身物品不属于长期人物设定，应写入 `physical_state`、`mental_state` 和 `inventory`。

### `worldbook`

具体地点、组织、物品、人物资料和历史事件应拆成独立条目。剧情每轮最多注入相关条目，因此世界可以很详细，而不必把全部资料永久塞进小模型上下文。

一个条目必须能独立理解。不要只写“见上文”“同前”或依赖数组顺序。

### `opening`

只描述故事开始的当前时刻：

- 主角脚下的实际地点
- 具体时间和天气
- 物理同场的已定义 NPC
- 当前可见的现场
- 尚未解决、需要后续回应的事项
- 已经成立的全局事实

久远历史应放在 `world_setting.lore.history` 或世界书，不要塞进 `pending_events`。

## 4. 世界设定字段

```json
{
  "world_setting": {
    "name": "世界名称",
    "genre": "类型",
    "tech_level": "科技水平",
    "magic_level": "魔法或力量水平",
    "tone": "总体基调",
    "forbidden": ["禁止内容"],
    "description": "可独立理解的世界摘要",
    "lore": {}
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `name` | string | 必填；用于预览、存档与提示词 |
| `genre` | string | 类型组合，例如“都市现实主义 / 职场” |
| `tech_level` | string | 技术能力及限制，不只写时代标签 |
| `magic_level` | string | 魔法、超能力或明确的“无” |
| `tone` | string | 氛围和创作基调 |
| `forbidden` | string[] | 模型不得引入或不得越过的元素 |
| `description` | string | 世界摘要；未填写时必须提供非空 `lore` |
| `lore` | object | 详细全局设定 |

### `world_setting.lore`

| 字段 | 类型 | 用途 |
| --- | --- | --- |
| `era` | string | 年代、季节、技术周期 |
| `premise` | string | 故事成立的核心前提 |
| `history` | string | 影响当下的历史概要 |
| `geography` | string | 大尺度空间关系 |
| `cosmology` | string | 宇宙观、现实层级、死亡或时间规则 |
| `society` | string | 阶层、人口、家庭与公共生活结构 |
| `culture` | string | 习俗、礼仪、审美、禁忌 |
| `politics` | string | 权力、治理、政治结构 |
| `economy` | string | 货币、资源、价格和交易规则 |
| `law` | string | 法律、执法和程序边界 |
| `religion` | string | 信仰与社会作用 |
| `languages` | string[] | 语言、方言、书写系统 |
| `calendar` | string | 历法、工作日、季节和时间单位 |
| `power_system` | string | 能力来源、代价、上限和可验证规则 |
| `technology_rules` | string | 技术能做什么、不能做什么 |
| `factions` | string[] | 世界级阵营摘要；具体阵营仍应建世界书 |
| `conflicts` | string[] | 持续影响剧情的结构性矛盾 |
| `rules` | string[] | 任何剧情都不能违背的因果规则 |
| `themes` | string[] | 主题，不应写成强制剧情结论 |
| `narrative_style` | string | 视角、语言、节奏和叙事限制 |
| `custom` | object | 最多 24 个自定义字符串键值 |

`lore` 中每个大段应写概要。某个城市、门派、公司或地点的完整资料请拆到世界书。

## 5. 主角字段

```json
{
  "player": {
    "name": "林克",
    "age": 27,
    "gender": "男",
    "race": "人类",
    "class": "产品经理",
    "personality": "稳定性格描述",
    "background": "故事开始前的背景",
    "appearance": "可观察外貌",
    "skills": ["信息整理"],
    "lore": {},
    "physical_state": {},
    "mental_state": {},
    "inventory": []
  }
}
```

`name` 必须是具体姓名。`主角`、`玩家`、`我`、`player` 等身份词会被高级校验拒绝。

`class` 是兼容字段，代表职业、阶级或角色职业。现实题材可直接填写具体职业。

## 6. NPC 字段

```json
{
  "name": "王常",
  "age": 31,
  "gender": "男",
  "race": "人类",
  "occupation": "云桥里邻居",
  "personality": "谨慎但热心",
  "background": "搬来小区五年",
  "appearance": "常穿灰色工作外套",
  "relationship_to_player": "新邻居",
  "lore": {},
  "physical_state": {},
  "mental_state": {},
  "inventory": []
}
```

每个 NPC 必须有唯一的具体姓名：

- 正确：`name: "王常"`，`occupation: "邻居"`
- 错误：`name: "邻居A"`
- 错误：`name: "咖啡店店员"`
- 错误：主角和 NPC 都叫“林克”

职业、亲属、伴侣等身份信息不能代替姓名。正文会优先采用“身份 + 姓名”的自然表达，但存档主键始终是具体姓名。

## 7. 角色长期设定 `lore`

主角和 NPC 共用同一结构：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `aliases` | string[] | 别名、称呼，不包含泛化身份 |
| `role` | string | 该角色在故事中的长期作用 |
| `pronouns` | string | 代词或称谓偏好 |
| `birth_date` | string | 日期格式由世界设定决定 |
| `nationality` | string | 国籍、籍贯或阵营归属 |
| `residence` | string | 稳定住处 |
| `education` | string | 教育、训练或师承 |
| `values` | string[] | 不易改变的价值排序 |
| `goals` | string[] | 长期目标 |
| `motivations` | string[] | 为什么追求这些目标 |
| `fears` | string[] | 真实恐惧和风险敏感点 |
| `weaknesses` | string[] | 能产生判断和冲突的局限 |
| `secrets` | string[] | 作者侧事实，不等于主角已知 |
| `habits` | string[] | 可被正文观察到的稳定习惯 |
| `speech_style` | string | 用词、句长、回避点和表达方式 |
| `abilities` | string[] | 稳定能力，不等于临时状态 |
| `knowledge` | string[] | 角色确实知道的领域 |
| `boundaries` | string[] | 不愿或不能越过的行为边界 |
| `character_arc` | string | 可能的成长方向，不是强制结局 |
| `relationship_notes` | object | 键为具体人物姓名，值为关系事实 |
| `custom` | object | 额外稳定字段 |

不要把同一个事实重复写进 `personality`、`background`、`lore` 和世界书。推荐规则：

- 性格一句话摘要写 `personality`。
- 形成性经历写 `background`。
- 决策依据写 `lore.values`、`goals`、`motivations`。
- 具体关系写 `relationship_notes`。
- 能被关键词触发的长资料写世界书。

## 8. 初始身体状态

```json
{
  "physical_state": {
    "stamina": 88,
    "health": "良好",
    "injuries": [],
    "fatigue": "轻微",
    "hunger": "不饿",
    "pain": "无"
  }
}
```

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `stamina` | integer | 0 到 100 |
| `health` | string | 可判断的健康概况 |
| `injuries` | string[] | 具体伤势 |
| `fatigue` | string | 疲劳程度 |
| `hunger` | string | 饥饿状态 |
| `pain` | string | 疼痛状态 |

这些是故事第一刻的状态，后续会根据已发生剧情更新。

## 9. 初始心理状态

```json
{
  "mental_state": {
    "mood": "谨慎期待",
    "stress": "中等",
    "focus": "确认今天的安排",
    "current_thoughts": "需要先把住处稳定下来。"
  }
}
```

`current_thoughts` 是开始时的有限视角，不应包含角色不知道的秘密。

## 10. 初始物品

```json
{
  "inventory": [
    {
      "name": "手机",
      "quantity": 1,
      "type": "通讯工具",
      "description": "常用智能手机",
      "origin": "个人购买",
      "acquired_at": "故事开始前"
    }
  ]
}
```

物品名不能为空，数量会限制在 1 到 9999。重要物品若还有独立历史、规则或相关人物，应同时建立 `type: "item"` 的世界书条目。

## 11. 世界书条目

```json
{
  "type": "location",
  "name": "云桥里",
  "description": "可独立理解的完整事实。",
  "keywords": ["云桥里", "6 栋", "旧港地铁站"],
  "related_characters": ["林克", "王常"],
  "related_events": ["林克搬家"],
  "related_locations": ["旧港地铁站"],
  "is_secret": false,
  "known_by_player": true,
  "status": "正常"
}
```

### 类型

支持以下规范值：

- `location`
- `item`
- `organization`
- `event`
- `character`

中文或常见别名会被归一化，但外部系统应输出规范值。

### 关键词设计

关键词直接影响剧情能否取到资料。每条建议包含：

- 正式名称
- 常用别名
- 用户可能输入的简称
- 相关地点
- 相关人物
- 典型物品或事件名

避免过于宽泛的词，例如“城市”“男人”“事情”。宽泛词会让无关资料频繁进入上下文。

### 秘密

- `is_secret: true`：该条目属于幕后或受限资料。
- `known_by_player: false`：主角当前不知道。

秘密仍会提供给叙事模型以保持因果一致，但提示词明确禁止在可信发现之前通过正文、对白或主角内心泄露。不要把 API 密钥、真实隐私或其他不应发送给模型的内容写入设定。

## 12. 开局

```json
{
  "opening": {
    "current_location": "云桥里 6 栋 1203",
    "time_of_day": "2026-03-16 09:20",
    "weather": "小雨",
    "mood": "搬家后的安静与压力并存",
    "scene_description": "纸箱沿客厅墙边排开。",
    "active_characters": ["顾宁"],
    "pending_events": ["林克需要和顾宁确认今天的安排。"],
    "global_flags": {
      "搬家完成": "true"
    }
  }
}
```

### `active_characters`

只列与主角物理同场的 NPC 真实姓名：

- 姓名必须已存在于 `npcs`。
- 不包含主角。
- 不包含电话、消息或视频另一端的人。
- 不写“女朋友”“邻居A”等身份。

### `pending_events`

每项必须描述尚未解决的具体事实、请求或阻力。它应能帮助下一轮做判断。

- 正确：“林克需要回复顾宁是否接受三个月外地驻场。”
- 错误：“继续故事。”
- 错误：“开始新生活。”
- 错误：把已经完成的搬家继续保留为待办。

### `global_flags`

保存已经成立、需要跨轮保持的简短事实。键和值都必须是字符串。复杂资料仍应使用世界书。

## 13. 校验、归一化和容量

| 项目 | 限制 |
| --- | --- |
| JSON 总长度 | 80,000 字符 |
| NPC 数量 | 50 |
| 世界书数量 | 100 |
| 列表字段 | 一般最多保留 20 到 30 项 |
| 自定义键值 | 每个 `custom` 最多保留 24 项 |
| 姓名 | 最多 80 字符 |
| 短字段 | 最多 120 字符 |
| 摘要字段 | 最多 2,000 字符 |
| 长描述 | 最多 6,000 字符 |

导入时会：

- 去除字段首尾空白。
- 限制异常年龄、体力和数量。
- 去除空列表项与重复项。
- 归一化世界书类型。
- 检查主角和 NPC 的真实姓名、唯一性。
- 检查开局同场角色是否已定义。
- 为缺失的非关键开局字段提供克制默认值。

未知字段会被忽略。需要保留自定义数据时，请放入相应 `custom` 对象。

## 14. 对本地小模型的上下文策略

高级导入允许保存大量资料，但不会把全部内容原样塞进每一轮：

1. `world_setting` 作为全局设定进入提示词，字段会进行有界快照。
2. 主角和当前活跃角色的长期设定进入叙事上下文。
3. 世界书通过地点、用户输入、待办和近期正文关键词匹配。
4. 每轮最多选择相关世界书条目，单条描述也有长度边界。
5. 临时状态与长期 `lore` 分开，避免一次情绪更新覆盖人物设定。

这套分层同时适用于云端模型和 LM Studio 中的 Gemma、Qwen 等小模型。

为了提高小模型稳定性：

- 世界概要保持简练。
- 把具体实体拆成世界书。
- 每条世界书只表达一个主题。
- 关键词写用户真的会说出的词。
- 不在多个字段重复相同长文本。
- `custom` 只保存无法归入规范字段的信息。

## 15. AI 识别与高级接入的区别

| 项目 | AI 识别 | 高级接入 |
| --- | --- | --- |
| 输入 | 自然语言 | JSON v1 |
| 是否调用模型 | 是 | 否 |
| 是否产生推断 | 可能 | 不会 |
| 字段控制 | 模型决定 | 接入方决定 |
| 错误反馈 | 解析/质量错误 | 精确结构和字段错误 |
| 适用场景 | 快速开始、设定草稿 | 长期项目、外部生成器、精确角色卡 |

AI 识别现在也会尝试生成相同的 `lore`、初始状态、世界书关系和开局字段。对关键姓名、秘密、规则和关系仍建议使用高级接入复核。

## 16. 模型配置与兼容服务

故事 JSON 不保存 API 地址、模型名称或密钥。模型连接由应用的“API 配置”独立管理，因此同一份故事可以在云端模型和本地模型之间切换。

### DeepSeek 配置示例

| 字段 | 填写内容 |
| --- | --- |
| 配置名称 | `DeepSeek` |
| API 地址 | `https://api.deepseek.com` |
| 模型名称 | 应用当前支持的模型标识，例如默认值 `deepseek-v4-flash` |
| API Key | 用户自己的密钥 |

远程地址必须使用 HTTPS。密钥由 Android Keystore 加密保存，不应写入故事 JSON、世界书、截图或接入文档。

### LM Studio 配置示例

| 字段 | 填写内容 |
| --- | --- |
| 配置名称 | `Gemma4 Local` |
| API 地址 | `http://192.168.1.20:1234`（替换为运行 LM Studio 的电脑局域网地址） |
| 模型名称 | `gemma4` |
| API Key | 留空 |

后续模型可以各自保存为独立配置档，例如：

- `Gemma 4 E4B`：模型名称填写 LM Studio `/v1/models` 返回的准确标识。
- `Qwen3.5 4B`：同样填写服务端实际加载的模型标识。

手机不能用 `localhost` 访问电脑上的 LM Studio。应填写电脑的局域网地址，并确保 LM Studio 监听局域网、手机与电脑处于可互通网络、防火墙允许该端口。局域网 HTTP 仅允许在 API Key 为空时使用，避免明文传输凭证。

兼容服务至少需要实现 OpenAI 风格的 `/v1/chat/completions`。应用同时支持 SSE 流式正文和非流式 JSON；本地模型会使用严格 `json_schema`，不支持额外推理参数的服务会自动移除该参数后重试。

每个配置保存后都应点击“测试连接”。连接测试通过只证明端点可用；长故事是否稳定还取决于模型上下文长度、结构化输出能力和生成速度。4B 级模型建议使用简洁世界摘要、拆分世界书、少量初始活跃角色，并避免一次导入大量重复设定。

## 17. 外部系统接入建议

外部脚本或设定生成器应：

1. 按 JSON Schema 生成对象。
2. 使用 UTF-8 编码。
3. 输出纯 JSON，不加 Markdown 代码块。
4. 所有姓名先做唯一性检查。
5. 校验 `opening.active_characters` 是 `npcs[].name` 的子集。
6. 让 `relationship_notes` 的键引用具体姓名。
7. 把深层实体资料拆成世界书并生成关键词。
8. 在交付前使用标准 JSON Schema 校验器校验。

本地模型生成时，建议把 Schema 和一个短示例放入系统提示词，并要求模型只输出一个 JSON 对象。不要要求 4B 级模型一次生成接近 80,000 字符的完整世界；应按世界、角色和世界书分段生产，最后由程序合并并校验。

## 18. 常见错误

### “JSON 语法错误”

- 删除末尾多余逗号。
- 确认字符串中的换行、引号和反斜杠已转义。
- 不要粘贴 ```json 代码块标记。

### “缺少必填对象”

顶层必须有 `world_setting` 和 `player`，并且两者必须是对象。

### “不是具体姓名”

把身份拆开：

```json
{
  "name": "王常",
  "occupation": "邻居"
}
```

### “active_characters 中的姓名未在 npcs 中定义”

先在 `npcs` 创建该人物，并确保两个位置的姓名完全相同。

### 预览中某字段消失

- 检查字段名是否属于 v1 协议。
- 自定义字段应放入 `lore.custom`。
- 检查是否为空、重复或超过容量限制。

### 设定保存了但剧情没有使用

- 全局规则应放 `world_setting.lore.rules`。
- 具体资料应检查世界书关键词。
- 当前人物必须进入 `opening.active_characters` 或在后续场景中实际登场。
- 避免只用正文不会出现的内部编号作为关键词。

## 19. 发布前检查表

- [ ] `schema_version` 为 1。
- [ ] 世界有名称和摘要或详细 lore。
- [ ] 主角使用具体姓名。
- [ ] 所有 NPC 使用唯一具体姓名。
- [ ] 身份放在 `class`、`occupation` 或 `role`，没有替代姓名。
- [ ] 长期设定与即时状态分开。
- [ ] 具体实体已拆成世界书。
- [ ] 世界书关键词可被用户自然提及。
- [ ] 秘密已标记，且未包含真实凭证或不应发送给模型的隐私。
- [ ] 开局同场角色全部存在于 NPC 列表。
- [ ] 待办是尚未解决的具体事项。
- [ ] 在应用中通过校验并检查完整预览。
