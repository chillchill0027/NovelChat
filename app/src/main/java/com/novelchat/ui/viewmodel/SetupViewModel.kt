package com.novelchat.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novelchat.NovelChatApp
import com.novelchat.data.model.*
import com.novelchat.data.remote.ApiEndpointPolicy
import com.novelchat.data.remote.model.ChatMessage
import com.novelchat.data.remote.model.ParsedWorldbookItem
import com.novelchat.data.remote.model.StoryParseResult
import com.novelchat.data.remote.model.ApiProfile
import com.novelchat.data.storage.SettingsRepository
import com.novelchat.domain.parser.JsonResponseParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SetupState(
    val apiUrl: String = SettingsRepository.DEFAULT_API_BASE_URL,
    val apiKey: String = "",
    val model: String = SettingsRepository.DEFAULT_MODEL,
    val profileName: String = "",
    val profiles: List<ApiProfile> = emptyList(),
    val isTesting: Boolean = false,
    val isContinuing: Boolean = false,
    val isProfileMutating: Boolean = false,
    val testResult: String? = null,
    val storyText: String = DEFAULT_MODERN_STORY_TEXT,
    val storyImportMode: StoryImportMode = StoryImportMode.AI_RECOGNITION,
    val advancedStoryJson: String = "",
    val advancedImportSummary: String? = null,
    val isParsing: Boolean = false,
    val parseResult: StoryParseResult? = null,
    val parseError: String? = null,
    val createdCharacterId: Long? = null,
    val isInitializing: Boolean = false
)

class SetupViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NovelChatApp
    private val settingsRepository = app.settingsRepository
    private val fileStorage = app.fileStorage
    private val indexStore = app.conversationIndexStore
    private val aiService = app.aiService
    private val jsonParser = JsonResponseParser()
    private var profilesLoaded = false
    private var profilesLoading = false
    private var storyParseJob: Job? = null
    private var storyParseGeneration = 0L

    fun loadProfiles() {
        if (profilesLoaded || profilesLoading) return
        profilesLoading = true
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(
                    profiles = settingsRepository.getApiProfiles(),
                    profileName = settingsRepository.getCurrentProfileName(),
                    apiUrl = SetupFlowPolicy.normalizeApiUrlDraft(settingsRepository.apiBaseUrl.first()),
                    apiKey = SetupFlowPolicy.normalizeApiKeyDraft(settingsRepository.apiKey.first()),
                    model = SetupFlowPolicy.normalizeModelDraft(settingsRepository.model.first())
                )
                profilesLoaded = true
            } catch (e: Exception) {
                e.rethrowCancellation()
                _state.value = _state.value.copy(
                    testResult = UserVisibleTextPolicy.failure("配置加载失败", e.message)
                )
            } finally {
                profilesLoading = false
            }
        }
    }

    fun selectProfile(profile: ApiProfile) {
        if (isConnectionBusy()) return
        _state.value = _state.value.copy(
            profileName = profile.name,
            apiUrl = SetupFlowPolicy.normalizeApiUrlDraft(profile.baseUrl),
            apiKey = SetupFlowPolicy.normalizeApiKeyDraft(profile.apiKey),
            model = SetupFlowPolicy.normalizeModelDraft(profile.model),
            testResult = null
        )
    }

    fun saveCurrentProfile(name: String) {
        if (isConnectionBusy()) return
        val s = _state.value
        val error = SetupFlowPolicy.validationError(s.apiUrl, s.apiKey, s.model)
        if (error != null) {
            showConfigurationError(error)
            return
        }
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) {
            showConfigurationError("配置名称不能为空")
            return
        }
        if (normalizedName.length > 40) {
            showConfigurationError("配置名称不能超过 40 个字符")
            return
        }
        val normalizedUrl = ApiEndpointPolicy.normalizeBaseUrl(s.apiUrl)
        val normalizedKey = s.apiKey.trim()
        val normalizedModel = s.model.trim()
        _state.value = _state.value.copy(isProfileMutating = true, testResult = null)
        viewModelScope.launch {
            try {
                settingsRepository.saveApiProfile(
                    normalizedName,
                    normalizedUrl,
                    normalizedKey,
                    normalizedModel
                )
                _state.value = _state.value.copy(
                    profileName = normalizedName,
                    profiles = settingsRepository.getApiProfiles(),
                    apiUrl = normalizedUrl,
                    apiKey = normalizedKey,
                    model = normalizedModel
                )
            } catch (e: Exception) {
                e.rethrowCancellation()
                showSetupError(UserVisibleTextPolicy.failure("配置保存失败", e.message))
            } finally {
                _state.value = _state.value.copy(isProfileMutating = false)
            }
        }
    }

    fun deleteProfile(name: String) {
        if (isConnectionBusy()) return
        _state.value = _state.value.copy(isProfileMutating = true, testResult = null)
        viewModelScope.launch {
            try {
                settingsRepository.deleteApiProfile(name)
                _state.value = _state.value.copy(
                    profiles = settingsRepository.getApiProfiles(),
                    profileName = settingsRepository.getCurrentProfileName()
                )
            } catch (e: Exception) {
                e.rethrowCancellation()
                showSetupError(UserVisibleTextPolicy.failure("配置删除失败", e.message))
            } finally {
                _state.value = _state.value.copy(isProfileMutating = false)
            }
        }
    }

    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state.asStateFlow()

    fun updateApiUrl(url: String) {
        if (isConnectionBusy()) return
        _state.value = _state.value.copy(
            apiUrl = SetupFlowPolicy.normalizeApiUrlDraft(url),
            profileName = "",
            testResult = null
        )
    }

    fun updateApiKey(key: String) {
        if (isConnectionBusy()) return
        _state.value = _state.value.copy(
            apiKey = SetupFlowPolicy.normalizeApiKeyDraft(key),
            profileName = "",
            testResult = null
        )
    }

    fun updateModel(model: String) {
        if (isConnectionBusy()) return
        _state.value = _state.value.copy(
            model = SetupFlowPolicy.normalizeModelDraft(model),
            profileName = "",
            testResult = null
        )
    }

    fun updateStoryText(text: String) {
        if (_state.value.isParsing) return
        _state.value = _state.value.copy(
            storyText = StoryImportPolicy.normalizeDraft(text),
            parseResult = null,
            parseError = null,
            advancedImportSummary = null,
            createdCharacterId = null
        )
    }

    fun selectStoryImportMode(mode: StoryImportMode) {
        if (_state.value.isParsing || _state.value.isInitializing) return
        if (_state.value.storyImportMode == mode) return
        _state.value = _state.value.copy(
            storyImportMode = mode,
            parseResult = null,
            parseError = null,
            advancedImportSummary = null,
            createdCharacterId = null
        )
    }

    fun updateAdvancedStoryJson(value: String) {
        if (_state.value.isParsing || _state.value.isInitializing) return
        val tooLong = value.length > AdvancedStoryImportPolicy.MAX_JSON_LENGTH
        _state.value = _state.value.copy(
            advancedStoryJson = AdvancedStoryImportPolicy.normalizeDraft(value),
            parseResult = null,
            parseError = if (tooLong) {
                "高级设定不能超过 ${AdvancedStoryImportPolicy.MAX_JSON_LENGTH} 个字符"
            } else {
                null
            },
            advancedImportSummary = null,
            createdCharacterId = null
        )
    }

    fun loadAdvancedStoryTemplate() {
        updateAdvancedStoryJson(AdvancedStoryImportPolicy.template)
    }

    fun showAdvancedImportError(message: String) {
        if (_state.value.isParsing || _state.value.isInitializing) return
        _state.value = _state.value.copy(
            parseResult = null,
            parseError = message,
            advancedImportSummary = null
        )
    }

    fun resetStoryDraft() {
        if (_state.value.isParsing || _state.value.isInitializing) return
        _state.value = _state.value.copy(
            storyText = DEFAULT_MODERN_STORY_TEXT,
            storyImportMode = StoryImportMode.AI_RECOGNITION,
            advancedStoryJson = "",
            parseResult = null,
            parseError = null,
            advancedImportSummary = null,
            createdCharacterId = null
        )
    }

    fun testConnection() {
        if (isConnectionBusy()) return
        val candidate = _state.value
        val error = SetupFlowPolicy.validationError(
            candidate.apiUrl,
            candidate.apiKey,
            candidate.model
        )
        if (error != null) {
            showConfigurationError(error)
            return
        }
        _state.value = _state.value.copy(isTesting = true, testResult = null)
        viewModelScope.launch {
            try {
                val response = aiService.testConnection(
                    baseUrl = candidate.apiUrl,
                    apiKey = candidate.apiKey,
                    model = candidate.model
                )
                saveConnection(candidate)
                _state.value = _state.value.copy(
                    isTesting = false,
                    testResult = SetupFlowPolicy.connectionSuccessMessage(response)
                )
            } catch (e: Exception) {
                e.rethrowCancellation()
                _state.value = _state.value.copy(
                    isTesting = false,
                    testResult = SetupFlowPolicy.connectionFailureMessage(e.message)
                )
            }
        }
    }

    fun continueWithCurrentConfig(onNext: () -> Unit) {
        if (isConnectionBusy()) return
        val candidate = _state.value
        val error = SetupFlowPolicy.validationError(
            candidate.apiUrl,
            candidate.apiKey,
            candidate.model
        )
        if (error != null) {
            showConfigurationError(error)
            return
        }
        _state.value = _state.value.copy(isContinuing = true, testResult = null)
        viewModelScope.launch {
            try {
                saveConnection(candidate)
            } catch (e: Exception) {
                e.rethrowCancellation()
                _state.value = _state.value.copy(isContinuing = false)
                showSetupError(UserVisibleTextPolicy.failure("配置保存失败", e.message))
                return@launch
            }
            try {
                withContext(Dispatchers.Main) { onNext() }
            } catch (e: Exception) {
                e.rethrowCancellation()
                showSetupError(UserVisibleTextPolicy.failure("配置已保存，但页面打开失败", e.message))
            } finally {
                _state.value = _state.value.copy(isContinuing = false)
            }
        }
    }

    fun parseStory() {
        if (storyParseJob?.isActive == true) return
        StoryImportPolicy.validationError(_state.value.storyText)?.let { error ->
            _state.value = _state.value.copy(parseError = error, parseResult = null)
            return
        }
        val generation = ++storyParseGeneration
        storyParseJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                isParsing = true,
                parseResult = null,
                parseError = null,
                advancedImportSummary = null,
                createdCharacterId = null
            )
            try {
                val storyText = _state.value.storyText.trim()
                val parsed = if (isDefaultModernStoryText(storyText)) {
                    defaultModernStoryParseResult()
                } else {
                    val prompt = buildParsePrompt(storyText)
                    var response = aiService.chatComplete(
                        messages = listOf(ChatMessage(role = "user", content = prompt)),
                        temperature = 0.2f,
                        maxTokens = 4200,
                        jsonMode = true
                    )
                    var candidate = jsonParser.parseStoryParseResult(response)
                    StoryParseQualityPolicy.qualityIssue(candidate)?.let { issue ->
                        response = aiService.chatComplete(
                            messages = listOf(ChatMessage(
                                role = "user",
                                content = buildParseRepairPrompt(prompt, response, issue)
                            )),
                            temperature = 0.1f,
                            maxTokens = 4800,
                            jsonMode = true
                        )
                        candidate = jsonParser.parseStoryParseResult(response)
                    }
                    if (!StoryParseQualityPolicy.hasSemanticContent(candidate)) {
                        throw IllegalArgumentException("AI 没有返回可用的故事结构，请重试")
                    }
                    candidate
                }
                val result = normalizeParseResult(
                    result = parsed,
                    storyText = storyText
                )
                StoryParseQualityPolicy.requireUsable(result)
                _state.value = _state.value.copy(parseResult = result)
            } catch (e: Exception) {
                e.rethrowCancellation()
                _state.value = _state.value.copy(
                    parseError = UserVisibleTextPolicy.failure("解析失败", e.message)
                )
            } finally {
                if (storyParseGeneration == generation) {
                    _state.value = _state.value.copy(isParsing = false)
                    storyParseJob = null
                }
            }
        }
    }

    fun parseAdvancedStory() {
        if (_state.value.isParsing || _state.value.isInitializing) return
        try {
            val parsed = AdvancedStoryImportPolicy.parse(_state.value.advancedStoryJson)
            val fallbackSource = listOf(
                parsed.worldSetting.description,
                parsed.worldSetting.lore.premise,
                parsed.worldSetting.name
            ).firstOrNull(String::isNotBlank).orEmpty()
            val result = normalizeParseResult(parsed, fallbackSource)
            StoryParseQualityPolicy.requireUsable(result)
            _state.value = _state.value.copy(
                parseResult = result,
                parseError = null,
                advancedImportSummary = AdvancedStoryImportPolicy.summary(result),
                createdCharacterId = null
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                parseResult = null,
                parseError = UserVisibleTextPolicy.failure("校验失败", e.message),
                advancedImportSummary = null
            )
        }
    }

    fun cancelStoryParsing() {
        storyParseGeneration++
        storyParseJob?.cancel()
        storyParseJob = null
        _state.value = _state.value.copy(isParsing = false, parseError = null)
    }

    fun confirmAndCreateStory(onCreated: (Long) -> Unit) {
        if (_state.value.isInitializing) return
        _state.value.createdCharacterId?.let { existingId ->
            try {
                onCreated(existingId)
            } catch (e: Exception) {
                e.rethrowCancellation()
                _state.value = _state.value.copy(
                    parseError = UserVisibleTextPolicy.failure("故事已创建，但页面打开失败", e.message)
                )
            }
            return
        }
        viewModelScope.launch {
            val result = _state.value.parseResult ?: return@launch
            _state.value = _state.value.copy(isInitializing = true, parseError = null)
            var characterId = 0L
            var indexed = false

            try {
                characterId = fileStorage.createConversationStorage()
                val createdAt = fileStorage.getCurrentTimeString()

                // 保存世界设定
                val ws = result.worldSetting
                fileStorage.saveWorldSetting(characterId, WorldSetting(
                    name = ws.name, genre = ws.genre, techLevel = ws.techLevel,
                    magicLevel = ws.magicLevel, tone = ws.tone,
                    forbidden = ws.forbidden, description = ws.description,
                    lore = ws.lore
                ))

                // 保存主角
                val p = result.player
                val playerName = p.name.ifBlank { CharacterNamingPolicy.DEFAULT_PLAYER_NAME }
                fileStorage.savePlayerState(characterId, PlayerState(
                    name = playerName, age = p.age, gender = p.gender,
                    race = p.race, class_ = p.class_,
                    personality = p.personality, background = p.background,
                    appearance = p.appearance,
                    skills = p.skills.map { PlayerSkill(name = it, level = 1) },
                    lore = p.lore,
                    physicalState = p.physicalState,
                    mentalState = p.mentalState,
                    inventory = p.inventory
                ))

                // 保存NPC
                result.npcs.forEach { npc ->
                    val card = ImportedCharacterPolicy.createCard(
                        npc = npc,
                        playerName = playerName,
                        createdAt = createdAt
                    )
                    fileStorage.saveCharacterCard(characterId, card)
                }

                val worldbookItems = result.worldbook.map { item ->
                    item.copy(keywords = normalizeKeywords(item.keywords, item.name, ws.name, playerName))
                }

                // 保存世界书条目
                worldbookItems.forEachIndexed { idx, item ->
                    fileStorage.saveWorldbookEntry(characterId, WorldbookEntry(
                        id = "wb_${idx + 1}", type = item.type, name = item.name,
                        description = item.description, keywords = item.keywords,
                        relatedCharacters = item.relatedCharacters,
                        relatedEvents = item.relatedEvents,
                        relatedLocations = item.relatedLocations,
                        isSecret = item.isSecret,
                        knownByPlayer = item.knownByPlayer,
                        isActive = true, createdBy = "user",
                        status = item.status,
                        createdAt = createdAt,
                        lastModified = createdAt
                    ))
                }

                fileStorage.saveStoryState(
                    characterId,
                    ImportedOpeningPolicy.createStoryState(result.opening)
                )
                fileStorage.completeConversationStorage(characterId)

                saveConversationIndex(ConversationEntry(
                    id = characterId,
                    characterName = playerName.ifBlank { ws.name.ifBlank { "新故事" } },
                    characterDesc = ws.description.ifBlank { _state.value.storyText.take(100) },
                    type = "story",
                    createdAt = createdAt,
                    lastMessageAt = createdAt,
                    lastMessage = "等待开始剧情"
                ))
                indexed = true

                _state.value = _state.value.copy(isInitializing = false, createdCharacterId = characterId)
                try {
                    onCreated(characterId)
                } catch (e: Exception) {
                    e.rethrowCancellation()
                    _state.value = _state.value.copy(
                        parseError = UserVisibleTextPolicy.failure("故事已创建，但页面打开失败", e.message)
                    )
                }
            } catch (e: Exception) {
                if (!indexed && characterId != 0L) {
                    withContext(NonCancellable) {
                        runCatching {
                            indexStore.update { entries ->
                                entries.filterNot { it.id == characterId }
                            }
                        }
                        runCatching { fileStorage.deleteConversation(characterId) }
                    }
                }
                e.rethrowCancellation()
                _state.value = _state.value.copy(
                    isInitializing = false,
                    parseError = UserVisibleTextPolicy.failure("创建故事失败", e.message)
                )
            }
        }
    }

    private suspend fun saveConversationIndex(entry: ConversationEntry) {
        indexStore.update { entries ->
            listOf(entry) + entries.filterNot { it.id == entry.id }
        }
    }

    private suspend fun saveConnection(state: SetupState) {
        val normalizedUrl = ApiEndpointPolicy.normalizeBaseUrl(state.apiUrl)
        val normalizedKey = state.apiKey.trim()
        val normalizedModel = state.model.trim()
        val matchingProfileName = settingsRepository.getApiProfiles()
            .firstOrNull { profile ->
                ApiEndpointPolicy.normalizeBaseUrl(profile.baseUrl) == normalizedUrl &&
                    profile.apiKey.trim() == normalizedKey &&
                    profile.model.trim() == normalizedModel
            }
            ?.name
            .orEmpty()
        settingsRepository.saveApiConnection(
            url = normalizedUrl,
            key = normalizedKey,
            model = normalizedModel,
            currentProfileName = matchingProfileName
        )
        _state.value = _state.value.copy(
            apiUrl = normalizedUrl,
            apiKey = normalizedKey,
            model = normalizedModel,
            profileName = matchingProfileName
        )
    }

    private fun showConfigurationError(message: String) {
        showSetupError("配置无效：$message")
    }

    private fun showSetupError(message: String) {
        _state.value = _state.value.copy(
            isTesting = false,
            isContinuing = false,
            isProfileMutating = false,
            testResult = message
        )
    }

    private fun isConnectionBusy(): Boolean =
        _state.value.isTesting ||
            _state.value.isContinuing ||
            _state.value.isProfileMutating

    private fun normalizeParseResult(result: StoryParseResult, storyText: String): StoryParseResult {
        val cleanedStory = storyText.trim()
        val fallbackDescription = cleanedStory.take(600).ifBlank { defaultModernWorldSetting.description }
        val world = result.worldSetting.let { world ->
            world.copy(
                name = world.name.ifBlank { inferTitle(cleanedStory) },
                genre = world.genre.ifBlank { "互动叙事" },
                techLevel = world.techLevel.ifBlank { "未明确" },
                magicLevel = world.magicLevel.ifBlank { "未明确" },
                tone = world.tone.ifBlank { "沉浸、连贯" },
                description = world.description.ifBlank { fallbackDescription }
            )
        }
        val player = result.player.let { player ->
            player.copy(
                name = StoryParseQualityPolicy.concretePlayerName(player.name),
                gender = player.gender.ifBlank { "未明确" },
                race = player.race.ifBlank { "未明确" },
                class_ = player.class_.ifBlank { "探索者" },
                personality = player.personality.ifBlank { "由玩家行动逐步塑造" },
                background = player.background.ifBlank { fallbackDescription.take(240) },
                appearance = player.appearance.ifBlank { "尚未详细描写" },
                skills = player.skills.ifEmpty { listOf("观察", "交涉") }
            )
        }
        val normalizedWorldbook = result.worldbook
            .filter { it.name.isNotBlank() || it.description.isNotBlank() }
            .mapIndexed { index, item ->
                item.copy(
                    type = normalizeWorldbookType(item.type),
                    name = item.name.ifBlank { "设定条目${index + 1}" },
                    description = item.description.ifBlank { fallbackDescription.take(240) },
                    keywords = normalizeKeywords(item.keywords, item.name, world.name, player.name)
                )
            }
            .ifEmpty {
                listOf(
                    ParsedWorldbookItem(
                        type = "event",
                        name = "初始设定",
                        description = fallbackDescription,
                        keywords = normalizeKeywords(emptyList(), world.name, player.name, "初始设定")
                    )
                )
            }

        val normalizedNpcs = result.npcs
            .filter { npc ->
                npc.name.isNotBlank() || npc.occupation.isNotBlank() || npc.background.isNotBlank()
            }
            .map { npc ->
                val normalized = CharacterNamingPolicy.normalizeNpcCard(
                    CharacterCard(name = npc.name, occupation = npc.occupation)
                )
                npc.copy(
                    name = normalized.name,
                    occupation = npc.occupation.ifBlank { normalized.occupation }
                )
            }

        val normalizedOpening = ImportedOpeningPolicy.normalize(
            opening = result.opening,
            worldbook = normalizedWorldbook,
            npcs = normalizedNpcs
        )

        return StoryParseContentPolicy.normalize(StoryParseResult(
            worldSetting = world,
            player = player,
            npcs = normalizedNpcs,
            worldbook = normalizedWorldbook,
            opening = normalizedOpening
        ))
    }

    private fun inferTitle(storyText: String): String {
        val firstLine = storyText.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return firstLine
            .removePrefix("标题：")
            .removePrefix("故事：")
            .take(24)
            .ifBlank { "未命名故事" }
    }

    private fun normalizeWorldbookType(type: String): String {
        return when (type.lowercase()) {
            "location", "地点", "place" -> "location"
            "item", "物品", "道具" -> "item"
            "organization", "组织", "faction" -> "organization"
            "event", "事件" -> "event"
            "character", "角色" -> "character"
            else -> "event"
        }
    }

    private fun normalizeKeywords(source: List<String>, vararg fallbacks: String): List<String> {
        return (source + fallbacks.toList())
            .flatMap { it.split(',', '，', '、', '/', '|') }
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()
            .take(12)
    }

    private fun buildParsePrompt(storyText: String): String {
        return """【系统提示】
你是一个故事设定解析AI。用户会提供一段文字，你需要解析成标准格式。
要求：
- 严格遵守输出格式，只输出一个JSON对象，不要Markdown代码块，不要解释
- 从文字中提取：世界整体设定、主角信息、NPC角色、世界书条目和可直接开始游玩的开局状态
- 优先保留用户明确给出的事实；未提到的长期秘密、关系冲突和历史不要擅自编造，允许使用空字符串、[] 或 {}
- lore 保存不应被短期剧情覆盖的长期规则和人物动机；瞬时身心状态放 physical_state / mental_state
- 主角必须有自然的具体姓名；每个 NPC 也必须有具体姓名，禁止“主角、玩家、邻居A、店员、陌生人”等泛称
- 角色身份必须与具体姓名同时存在，例如“邻居王常”，不能只保留身份
- 世界总体规则放 world_setting.lore；具体地点、组织、事件、物品和可检索资料拆成 worldbook，避免把全部资料塞进一个长 description
- 输出JSON格式

【输出格式】
{
  "world_setting": {
    "name": "世界名称", "genre": "类型", "tech_level": "科技水平",
    "magic_level": "魔法水平", "tone": "风格基调",
    "forbidden": ["禁止元素"], "description": "世界观摘要",
    "lore": {
      "era": "时代", "premise": "核心前提", "history": "历史概要",
      "geography": "地理概要", "cosmology": "宇宙或现实规则",
      "society": "社会结构", "culture": "文化", "politics": "政治与权力",
      "economy": "经济规则", "law": "法律规则", "religion": "宗教",
      "languages": ["语言"], "calendar": "历法与时间规则",
      "power_system": "力量体系", "technology_rules": "技术边界",
      "factions": ["主要阵营"], "conflicts": ["长期矛盾"],
      "rules": ["不可违背的世界规则"], "themes": ["主题"],
      "narrative_style": "叙事风格", "custom": {"自定义字段": "值"}
    }
  },
  "player": {
    "name": "主角名字", "age": 20, "gender": "性别", "race": "种族",
    "class": "职业", "personality": "性格描述",
    "background": "背景故事", "appearance": "外貌描述",
    "skills": ["技能列表"],
    "lore": {
      "aliases": ["别名"], "role": "故事角色", "pronouns": "代词",
      "birth_date": "生日", "nationality": "国籍", "residence": "住处",
      "education": "教育", "values": ["价值观"], "goals": ["长期目标"],
      "motivations": ["动机"], "fears": ["恐惧"], "weaknesses": ["弱点"],
      "secrets": ["秘密"], "habits": ["习惯"], "speech_style": "说话风格",
      "abilities": ["能力"], "knowledge": ["知识"], "boundaries": ["边界"],
      "character_arc": "成长方向",
      "relationship_notes": {"人物真实姓名": "关系事实"},
      "custom": {"自定义字段": "值"}
    },
    "physical_state": {"stamina": 100, "health": "良好", "injuries": [], "fatigue": "无", "hunger": "不饿", "pain": "无"},
    "mental_state": {"mood": "情绪", "stress": "压力", "focus": "关注点", "current_thoughts": "当前想法"},
    "inventory": [{"name": "物品", "quantity": 1, "type": "类型", "description": "描述", "origin": "来源", "acquired_at": "获得时间"}]
  },
  "opening": {
    "current_location": "开局实际地点", "time_of_day": "开局时间",
    "weather": "天气或空字符串", "mood": "现场基调", "scene_description": "眼前场景事实",
    "active_characters": ["物理同场NPC真实姓名"],
    "pending_events": ["开局时尚未解决、需要主角回应的具体事项"],
    "global_flags": {"已成立事实": "状态"}
  },
  "npcs": [
    {"name": "NPC名字", "age": 25, "gender": "男", "race": "人类",
     "occupation": "职业", "personality": "性格描述",
     "background": "背景故事", "appearance": "外貌描述",
     "relationship_to_player": "与主角的关系",
     "lore": {"aliases": [], "role": "故事作用", "values": [], "goals": [],
       "motivations": [], "fears": [], "weaknesses": [], "secrets": [],
       "habits": [], "speech_style": "说话风格", "abilities": [],
       "knowledge": [], "boundaries": [], "relationship_notes": {}, "custom": {}},
     "physical_state": {"stamina": 100, "health": "良好", "injuries": [], "fatigue": "无", "hunger": "不饿", "pain": "无"},
     "mental_state": {"mood": "情绪", "stress": "压力", "focus": "关注点", "current_thoughts": "当前想法"},
     "inventory": []}
  ],
  "worldbook": [
    {"type": "location/item/organization/event/character", "name": "名称",
     "description": "可独立理解的完整事实", "keywords": ["名称、别名和触发词"],
     "related_characters": ["角色真实姓名"], "related_events": ["事件"],
     "related_locations": ["地点"], "is_secret": false,
     "known_by_player": true, "status": "当前状态"}
  ]
}

【关键词生成规则】
对于每个世界书条目和角色卡，必须生成足够的关键词：
- 地点类：名称、别名、所在区域、相关人物、相关物品
- 物品类：名称、别名、用途、相关人物、相关地点
- 组织类：名称、别名、成员、相关地点、相关事件
- 事件类：名称、时间、地点、人物、原因、结果

【开局规则】
- opening 只写故事开始那一刻，不把久远背景史当成当前待办
- active_characters 只列开局结尾与主角物理同场且已存在于 npcs 的真实姓名
- pending_events 每项都写清具体人物真实姓名、尚未解决的事实或请求，不能只写“开始故事”“继续剧情”
- 若原文没有明确开局，依据设定生成一个克制、具体、可立即回应的开局，不替主角预先作重大决定

【用户提供的文字】
$storyText

【请输出JSON】"""
    }

    private fun buildParseRepairPrompt(originalPrompt: String, rawResponse: String, issue: String): String {
        return buildString {
            appendLine(originalPrompt)
            appendLine()
            appendLine("【必须重写】")
            appendLine("上次解析结果不可用：$issue。请重新输出完整 JSON，补全具体姓名、可检索的设定条目和可直接游玩的 opening。")
            appendLine("【上次输出，仅用于定位遗漏】")
            appendLine(rawResponse.take(6000))
        }
    }
}
