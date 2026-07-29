# NovelChat

AI 驱动的互动叙事引擎。Android 原生应用。

当前版本：`1.0.1`

## 快速开始

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
5. 点击「现代开局」进入默认故事，或点击「导入故事」选择 AI 识别/高级 JSON 接入
6. 确认解析结果与主角设定，然后进入正式剧情模式

高级接入支持从文件、剪贴板或内置模板导入版本化 JSON。世界规则、角色长期动机、初始身心状态、物品、世界书关系和开局现场都会结构化保存。接入材料：

- [`docs/ADVANCED_STORY_IMPORT_GUIDE.md`](docs/ADVANCED_STORY_IMPORT_GUIDE.md)：完整操作与字段指南
- [`docs/advanced_story_import_v1.example.json`](docs/advanced_story_import_v1.example.json)：可直接导入的完整示例
- [`docs/advanced_story_import_v1.schema.json`](docs/advanced_story_import_v1.schema.json)：机器可读 JSON Schema

默认故事方向：

- 背景：当代虚构一线城市「海临市」
- 类型：现代真实世界模拟器 RPG
- 关注：租房、通勤、工作、健康、人际关系、收入压力和职业选择
- 快速开局：林克（城市租客）、周然（职场新人）、许知南（自由职业者）、沈清禾（社区医生）、陆远（返乡创业者）

默认配置：

- API 地址：`https://api.deepseek.com`
- 默认模型：`deepseek-v4-flash`
- API Key：由用户填写；本地无鉴权兼容服务可以留空
- 安全限制：远程 HTTP 地址、URL 内嵌凭据和带查询参数的 API 基址会被拒绝

对话行为：

- 主角和 NPC 在剧情中使用姓名交流，身份作为辅助信息展示；旧存档中的“邻居A”等占位称呼会稳定映射为不同姓名
- 用户输入的连续动作会按原顺序扩写为场景、反馈和人物反应，并主动续写一个合理后果或新情节
- 每轮行动支持复制、修改和重新生成；新旧存档都会回到目标轮开始前原位重建，并替换该轮之后的旧分支
- 主角卡首页聚焦体力、压力和风险，NPC 卡首页聚焦关系阶段、情绪、健康与信任；高频波动明细放在详情页
- 生成过程中返回会先确认是否停止，状态落盘阶段会阻止误退出；临时网络错误会在正文开始前有限重试
- 损坏的关键存档会显示恢复警告且不会被空默认值覆盖；服务端错误会脱敏并限制长度后再展示

## 技术架构

| 层 | 技术 |
|-----|------|
| UI | Jetpack Compose + Material3 |
| AI通信 | Retrofit + OkHttp SSE |
| 持久化 | JSON 文件系统 + DataStore |

### 核心模块

- **RpgGameEngine**: 编排剧情生成、质量复核、任务分配、角色状态更新与中断恢复
- **RpgPromptBuilder**: 构建符合设计文档的提示词模板
- **FileStorageManager**: 文件系统 CRUD，目录结构对齐设计文档
- **WorldbookMatcher**: 三种关键词触发方式匹配世界书条目
- **JsonResponseParser**: 健壮的 AI 响应 JSON 提取、松散类型兼容与降级
- **AdvancedStoryImportPolicy**: 严格校验高级故事 JSON、实名角色和开局名册
- **LorePromptSnapshotPolicy**: 将详细世界/角色设定压缩为适配本地小模型的有界上下文
- **NarrativeQualityPolicy**: 校验动作覆盖、分段、主动续写、结尾场景和首次登场人物
- **TaskAssignmentRosterPolicy**: 保证在场人物有真实姓名和对应角色卡
- **TaskAssignmentQualityPolicy**: 拒绝结构合法但没有任何状态变化的空壳任务分配
- **TaskAssignmentExecutionPolicy**: 清洗并验证事件、世界书更新和物品转移，避免无法定位的更新被误判为成功
- **CharacterReferencePolicy**: 用姓名、稳定 ID 和唯一身份线索解析旧剧情称呼，避免同一角色重复建卡或误认
- **StateSyncPolicy**: 记录未完成状态同步，并在后续回合恢复
- **ApiEndpointPolicy**: 统一验证 HTTPS、局域网 HTTP、密钥传输和 `/v1` 基址兼容性
- **ApiErrorPolicy**: 提取、截断并脱敏兼容服务的结构化错误信息
- **ApiRetryPolicy**: 仅在响应正文开始前重试短暂网络错误和 408/429/5xx，避免流式正文重复
- **StoredJsonCodec**: 严格解析关键 JSON 存档，区分“文件不存在”和“文件已损坏”
- **PlayerPromptSnapshotPolicy**: 将主角存档转换为中性、紧凑、可判断的模型上下文
- **DefaultStory**: 内置现代真实世界模拟器默认故事、NPC 和世界书种子数据
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
  └── narrative/             # 正文剧情
```

## 设计文档

详见 [`docs/TECH_DESIGN_V1.md`](docs/TECH_DESIGN_V1.md)、[`DEVELOPMENT_STATUS.md`](DEVELOPMENT_STATUS.md) 和 [`AGENTS.md`](AGENTS.md)。

## 开发

项目使用 Gradle 版本目录 (libs.versions.toml) 管理依赖。
核心依赖：Compose BOM 2025.06, Kotlin 2.2, AGP 9.2, Retrofit 3.0, OkHttp 5.4

```bash
# 仅编译 Kotlin（跳过 lint 和测试）
./gradlew app:compileDebugKotlin -x lint -x test
```
