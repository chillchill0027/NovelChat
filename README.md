# NovelChat

AI 驱动的互动叙事引擎。提供 **Android 原生应用** 与 **纯静态 Web / PWA 网页版**（零后端，手机/平板可添加到主屏幕全屏游玩）。

当前版本：`2.1.0`

- **网页版在线访问（GitHub Pages）**：https://chillchill0027.github.io/NovelChat/
- **Android 最新安装包（Releases）**：https://github.com/chillchill0027/NovelChat/releases/latest

## 网页版（纯静态 Web / PWA）

位于 [`web/`](web/) 目录，采用 React + TypeScript + Vite + Tailwind CSS + PWA，浏览器端完整运行二代异步双轨引擎，数据与 API 密钥仅保存在本地 `IndexedDB`，支持一键导出/导入 JSON 存档实现手机/平板/电脑互通，并 100% 兼容高级 JSON 剧本模板。

```bash
cd web/
npm install
npm run dev      # 本地预览
npm test         # 运行单元测试
npm run build    # 构建纯静态 PWA 产物至 web/dist/
```

推送至 `main` 后，[`.github/workflows/deploy-web.yml`](.github/workflows/deploy-web.yml) 会自动测试并部署到 GitHub Pages。

## Android 快速开始

### 前置要求

- Android Studio (推荐最新稳定版)
- Android SDK（安装项目所需的 compileSdk 37；应用最低支持 Android 8.0）
- JDK 17+ (Android Studio 自带的 JBR 即可)

### 编译安装

```bash
cd NovelChat/
./gradlew assembleDebug
```

APK 生成位置: `app/build/outputs/apk/debug/app-debug.apk`

或者直接在 Android Studio 中打开项目，Run 到模拟器/真机。

## 首次使用

1. 首次打开 App，配置 OpenAI 兼容 API 地址、模型和密钥
2. 云端 API 必须使用 HTTPS 并填写密钥；无鉴权的本机或局域网模型可以使用 HTTP 并留空密钥
3. 可点击「测试连接」确认配置；本地配置格式有效时也可以先继续导入故事
4. 配置完成后再次启动会直接进入故事库
5. 点击「神雕侠侣」一键进入唯一默认剧本《神雕侠侣 · 终南烟雨》（扮演杨过），或点击「导入故事」选择 AI 识别/高级 JSON 接入
6. 确认解析结果与主角设定，然后进入正式剧情模式

高级接入支持从文件、剪贴板或内置模板导入版本化 JSON。世界规则、角色长期动机、初始身心状态、物品、世界书关系和开局现场都会结构化保存。`world_setting.character_fields` 可定义整张角色卡：内置字段可重命名、改范围和显示位置，也可新增任意字段；初始值写入 `custom_attributes`，后续按 `dynamic`、`event`、`manual` 规则随剧情刷新。接入材料：

- [`docs/ADVANCED_STORY_IMPORT_GUIDE.md`](docs/ADVANCED_STORY_IMPORT_GUIDE.md)：完整操作与字段指南
- [`docs/advanced_story_import_shendiao_v1.json`](docs/advanced_story_import_shendiao_v1.json)：唯一内置默认史诗剧本《神雕侠侣 · 终南烟雨》（扮演杨过，含 24 位经典实名角色、42 条世界书与「三度引流 + 自创江湖 + 八大年表自演」机制）
- [`docs/advanced_story_import_v1.schema.json`](docs/advanced_story_import_v1.schema.json)：机器可读 JSON Schema

唯一默认故事：

- 剧本：《神雕侠侣 · 终南烟雨》（南宋端平元年，终南山重阳宫演武场开局）
- 主角：十四岁的杨过（含 12 项武侠专属动态字段、24 位经典实名 NPC 与台词口吻、42 条地点/宗门/秘籍/八大年表世界书）
- 命线机制：偏离原著主线时自然安排最多 3 次主动回归引导（`destiny_nudges: 0→3`）；继续偏离则彻底解绑进入「自创江湖」，不再受原著束缚，而江湖八大时代大事仍按年表在背景中独立演进

默认配置：

- API 地址：`https://api.deepseek.com`
- 默认模型：`deepseek-v4-flash`
- API Key：由用户填写；本地无鉴权兼容服务可以留空
- 安全限制：远程 HTTP 地址、URL 内嵌凭据和带查询参数的 API 基址会被拒绝

对话行为：

- 主角和 NPC 在剧情中使用姓名交流，身份作为辅助信息展示；旧存档中的“邻居A”等占位称呼会稳定映射为不同姓名
- 用户输入的连续动作会按原顺序扩写为场景、反馈和人物反应，并主动续写一个合理后果或新情节
- 正文默认以 1000-1500 字为首次生成目标；模型输出偏短或偏长时不会自动进行第二次补写，也不会仅因字数偏差阻断任务和角色状态刷新
- 每轮行动支持复制、修改和重新生成；新旧存档都会回到目标轮开始前原位重建，并替换该轮之后的旧分支
- 主角与 NPC 角色卡由当前故事的字段契约决定：首页最多显示用户指定的关键判断项，完整状态、性格、经历和能力放在详情页；旧故事自动使用兼容默认字段
- 生成过程中返回会先确认是否停止，状态落盘阶段会阻止误退出；临时网络错误会在正文开始前有限重试
- 损坏的关键存档会显示恢复警告且不会被空默认值覆盖；服务端错误会脱敏并限制长度后再展示

## 技术架构

| 层 | 技术 |
|-----|------|
| UI | Jetpack Compose + Material3 |
| AI通信 | Retrofit + OkHttp SSE |
| 持久化 | JSON 文件系统 + DataStore |

### 核心模块

- **StorySessionManager / StorySession / SyncQueue**: 二代双轨异步引擎（现已作为唯一引擎），前台纯正文流式直出，后台串行同步状态、行动建议与章节记忆，不阻塞玩家继续输入
- **NarratorAgent / NarrativeGuard / NarrativeTextCleaner**: 纯小说流式叙事、温和后置质量门禁（防复述/元泄/截断）与极简重写
- **StateKeeperAgent / StateDeltaParser / StateDeltaApplier**: 后台增量 JSON 状态同步、世界书增量合并与事件/好感/物品转移归属校验
- **MemoryManager / ContextAssembler**: 分层长期记忆（10 轮章节摘要 + 60 轮总览），前缀/历史可缓存上下文装配
- **StoryRepository / RoundCommitJournal**: 单故事锁、两阶段预写日志提交与 `provisional` 检查点回溯修补
- **LlmScheduler / LlmMetrics / OpenAiCompatibleLlmClient**: 前台优先并发调度、SSE 空闲看门狗与 `cached_tokens` 性能追踪
- **FileStorageManager**: 文件系统 CRUD，目录结构对齐设计文档
- **WorldbookMatcher**: 三种关键词触发方式匹配世界书条目
- **JsonResponseParser**: 健壮的 AI 响应 JSON 提取、松散类型兼容与降级
- **AdvancedStoryImportPolicy**: 严格校验高级故事 JSON、实名角色、`sample_dialogues` 台词示例和开局名册
- **CharacterFieldPolicy**: 合并故事字段定义、智能格式化数值（年龄/等级/百分比）、约束类型和范围并保持旧存档兼容
- **LorePromptSnapshotPolicy**: 将详细世界/角色设定压缩为适配本地小模型的有界上下文
- **NarrativeQualityPolicy**: 校验动作覆盖、分段、主动续写、结尾场景和首次登场人物
- **TaskAssignmentRosterPolicy**: 保证在场人物有真实姓名和对应角色卡
- **CharacterReferencePolicy**: 用姓名、稳定 ID 和唯一身份线索解析旧剧情称呼，避免同一角色重复建卡或误认
- **StateSyncPolicy**: 兼容旧版 V1 `state_sync_pending_from_round` 存档标记的打开恢复
- **ApiEndpointPolicy**: 统一验证 HTTPS、局域网 HTTP、密钥传输和 `/v1` 基址兼容性
- **ApiErrorPolicy**: 提取、截断并脱敏兼容服务的结构化错误信息
- **ApiRetryPolicy**: 仅在响应正文开始前重试短暂网络错误和 408/429/5xx，避免流式正文重复
- **StoredJsonCodec**: 严格解析关键 JSON 存档，区分“文件不存在”和“文件已损坏”
- **PlayerPromptSnapshotPolicy**: 将主角存档转换为中性、紧凑、可判断的模型上下文
- **DefaultStory**: 内置唯一默认史诗剧本《神雕侠侣 · 终南烟雨》的加载与初始化种子数据
- **LegacyDataMigrator**: 启动与列表加载时迁移旧测试索引，清理不适合当前产品方向的历史摘要

### 存储结构

```
{appFiles}/rpg/{characterId}/
  ├── world_setting.json     # 世界整体设定
  ├── states.json            # 剧情状态
  ├── characters/            # 角色卡
  │   ├── player.json        # 主角
  │   └── {name}.json        # NPC
  ├── worldbook/             # 世界书
  │   ├── locations/{id}.json
  │   ├── items/{id}.json
  │   ├── organizations/{id}.json
  │   ├── events/{id}.json
  │   └── characters/{id}.json
  ├── summary/               # 缩略剧情
  ├── chapters/              # V2 章节与总览摘要
  ├── checkpoints/           # 回合前检查点（含 provisional 标记）
  ├── narration.json         # 故事叙事偏好（视角/篇幅/文风示例）
  ├── sync_status.json       # V2 后台同步状态
  └── narrative/             # 正文剧情
```

## 设计文档

详见 [`docs/ENGINE_V2_DESIGN.md`](docs/ENGINE_V2_DESIGN.md)、[`docs/ENGINE_V2_IMPLEMENTATION_PLAN.md`](docs/ENGINE_V2_IMPLEMENTATION_PLAN.md)、[`docs/TECH_DESIGN_V1.md`](docs/TECH_DESIGN_V1.md)、[`DEVELOPMENT_STATUS.md`](DEVELOPMENT_STATUS.md) 和 [`AGENTS.md`](AGENTS.md)。

## 开发

项目使用 Gradle 版本目录 (libs.versions.toml) 管理依赖。
核心依赖：Compose BOM 2025.06, Kotlin 2.2, AGP 9.2, Retrofit 3.0, OkHttp 5.4

```bash
# 仅编译 Kotlin（跳过 lint 和测试）
./gradlew app:compileDebugKotlin -x lint -x test
```
