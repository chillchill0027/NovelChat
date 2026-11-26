package com.novelchat.ui.screen.welcome

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelchat.data.model.CharacterFieldDefinition
import com.novelchat.data.model.CharacterFieldPolicy
import com.novelchat.data.model.CharacterLore
import com.novelchat.data.model.WorldLore
import com.novelchat.data.remote.model.ParsedNpc
import com.novelchat.data.remote.model.ParsedWorldbookItem
import com.novelchat.data.remote.model.StoryParseResult
import com.novelchat.ui.theme.AmberContainer
import com.novelchat.ui.theme.AmberPrimary
import com.novelchat.ui.theme.CenteredAdaptiveBox
import com.novelchat.ui.theme.ErrorContainer
import com.novelchat.ui.theme.Hairline
import com.novelchat.ui.theme.InkPrimary
import com.novelchat.ui.theme.InkSecondary
import com.novelchat.ui.theme.MistContainer
import com.novelchat.ui.theme.ObsidianCanvas
import com.novelchat.ui.theme.OnAmberContainer
import com.novelchat.ui.theme.OnErrorContainer
import com.novelchat.ui.theme.OnMistContainer
import com.novelchat.ui.theme.OnSageContainer
import com.novelchat.ui.theme.PrimaryPillButton
import com.novelchat.ui.theme.SageContainer
import com.novelchat.ui.theme.SurfaceBase
import com.novelchat.ui.theme.SurfaceHigh
import com.novelchat.ui.theme.TonalBadge
import com.novelchat.ui.theme.rememberAdaptiveSpec
import com.novelchat.ui.viewmodel.SetupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryConfirmScreen(
    viewModel: SetupViewModel,
    onBack: () -> Unit,
    onStartChat: (Long) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val result = state.parseResult
    if (result == null) {
        LaunchedEffect(Unit) { onBack() }
        Box(modifier = Modifier.fillMaxSize().background(ObsidianCanvas))
        return
    }
    var worldExpanded by remember { mutableStateOf(false) }
    var playerExpanded by remember { mutableStateOf(false) }
    var openingExpanded by remember { mutableStateOf(true) }
    var npcsExpanded by remember { mutableStateOf(false) }
    var wbExpanded by remember { mutableStateOf(false) }
    val adaptive = rememberAdaptiveSpec()

    BackHandler(enabled = state.isInitializing) {}

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "确认故事设定",
                        color = InkPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.isInitializing) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, "返回", tint = InkSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianCanvas)
            )
        },
        containerColor = ObsidianCanvas
    ) { padding ->
        CenteredAdaptiveBox(
            maxWidth = adaptive.readingMaxWidth,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = adaptive.horizontalPadding, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            // Story overview hero banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = SurfaceBase,
                border = BorderStroke(1.dp, Hairline)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = result.worldSetting.name.ifBlank { "新故事" },
                        color = InkPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (result.worldSetting.description.isNotBlank()) {
                        Text(
                            text = result.worldSetting.description,
                            color = InkSecondary,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TonalBadge(
                            text = "主角 ${result.player.name}",
                            containerColor = AmberContainer,
                            contentColor = OnAmberContainer
                        )
                        TonalBadge(
                            text = "${result.npcs.size} 位角色",
                            containerColor = MistContainer,
                            contentColor = OnMistContainer
                        )
                        TonalBadge(
                            text = "${result.worldbook.size} 条世界书",
                            containerColor = SageContainer,
                            contentColor = OnSageContainer
                        )
                    }
                }
            }

            ExpandableSection("开局场景", openingExpanded, onToggle = { openingExpanded = !openingExpanded }) {
                Text(renderOpening(result), color = InkPrimary, fontSize = 13.sp, lineHeight = 21.sp)
            }
            ExpandableSection("世界整体设定", worldExpanded, onToggle = { worldExpanded = !worldExpanded }) {
                Text(renderWorldSetting(result), color = InkPrimary, fontSize = 13.sp, lineHeight = 21.sp)
            }
            ExpandableSection("主角档案", playerExpanded, onToggle = { playerExpanded = !playerExpanded }) {
                Text(renderPlayer(result), color = InkPrimary, fontSize = 13.sp, lineHeight = 21.sp)
            }
            ExpandableSection("NPC 角色（${result.npcs.size} 位）", npcsExpanded, onToggle = { npcsExpanded = !npcsExpanded }) {
                if (result.npcs.isEmpty()) {
                    Text("无初始 NPC", color = InkSecondary, fontSize = 13.sp)
                }
                result.npcs.forEachIndexed { index, npc ->
                    if (index > 0) {
                        HorizontalDivider(color = Hairline, modifier = Modifier.padding(vertical = 12.dp))
                    }
                    Text(
                        text = "${npc.name} · ${npc.occupation.ifBlank { npc.lore.role.ifBlank { "身份未定" } }}",
                        color = AmberPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = renderNpc(npc, result.worldSetting.characterFields),
                        color = InkSecondary,
                        fontSize = 12.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            ExpandableSection("世界书条目（${result.worldbook.size} 条）", wbExpanded, onToggle = { wbExpanded = !wbExpanded }) {
                result.worldbook.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider(color = Hairline, modifier = Modifier.padding(vertical = 12.dp))
                    }
                    Text(
                        text = "${worldbookTypeLabel(item.type)} · ${item.name}",
                        color = AmberPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = renderWorldbook(item),
                        color = InkSecondary,
                        fontSize = 12.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            state.parseError?.let { error ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = ErrorContainer
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(14.dp),
                        color = OnErrorContainer,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            }

            if (state.isInitializing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AmberPrimary, strokeWidth = 2.5.dp)
                }
            } else {
                PrimaryPillButton(onClick = { viewModel.confirmAndCreateStory(onStartChat) }) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("确认并开始", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(20.dp))
        }
        }
    }
}

@Composable
private fun ExpandableSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = SurfaceBase,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = InkPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = InkSecondary
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    HorizontalDivider(color = Hairline, modifier = Modifier.padding(bottom = 12.dp))
                    content()
                }
            }
        }
    }
}

private fun renderWorldSetting(r: StoryParseResult): String = buildString {
    val ws = r.worldSetting
    appendField("名称", ws.name)
    appendField("类型", ws.genre)
    appendField("科技", ws.techLevel)
    appendField("力量/魔法", ws.magicLevel)
    appendField("基调", ws.tone)
    appendList("禁止事项", ws.forbidden)
    appendField("世界摘要", ws.description)
    appendList(
        "角色卡字段",
        CharacterFieldPolicy.effectiveDefinitions(ws.characterFields).map { field ->
            "${field.label}(${field.key} · ${field.group} · ${field.updateMode})"
        },
        "；"
    )
    appendWorldLore(ws.lore)
}

private fun renderPlayer(r: StoryParseResult): String = buildString {
    val p = r.player
    val fields = r.worldSetting.characterFields
    appendField(labelFor(fields, "name", "姓名"), p.name)
    appendField(labelFor(fields, "age", "年龄"), p.age.toString())
    appendField(labelFor(fields, "gender", "性别"), p.gender)
    appendField(labelFor(fields, "race", "种族"), p.race)
    appendField(labelFor(fields, "occupation", "身份"), p.class_)
    appendField(labelFor(fields, "personality", "人物特征"), p.personality)
    appendField(labelFor(fields, "appearance", "外貌"), p.appearance)
    appendField(labelFor(fields, "background", "经历"), p.background)
    appendList(labelFor(fields, "skills", "技能"), p.skills)
    appendField(labelFor(fields, "level", "等级"), p.level.toString())
    appendField(labelFor(fields, "charm", "魅力"), p.charm.toString())
    appendField(labelFor(fields, "action_skill", "行动技巧"), p.actionSkill.toString())
    appendField(labelFor(fields, "social_rank", "社交等级"), p.socialRank)
    appendField(labelFor(fields, "luck", "运气"), p.luck.toString())
    appendField(labelFor(fields, "willpower", "意志"), p.willpower.toString())
    appendField(labelFor(fields, "resilience", "体魄抗性"), p.resilience.toString())
    appendCharacterLore(p.lore)
    appendField("初始身体", physicalSummary(p.physicalState, fields))
    appendField("初始心理", mentalSummary(p.mentalState, fields))
    p.customAttributes.forEach { (key, value) ->
        appendField(labelFor(fields, key, key), value)
    }
    if (p.inventory.isNotEmpty()) {
        appendList("初始物品", p.inventory.map { "${it.name} ×${it.quantity}" })
    }
}

private fun renderOpening(r: StoryParseResult): String = buildString {
    val opening = r.opening
    appendField("地点", opening.currentLocation)
    appendField("时间", opening.timeOfDay)
    appendField("天气", opening.weather)
    appendField("基调", opening.mood)
    appendField("现场", opening.sceneDescription)
    appendList("同场角色", opening.activeCharacters)
    appendList("待处理", opening.pendingEvents, "；")
    if (opening.globalFlags.isNotEmpty()) {
        appendList("已成立事实", opening.globalFlags.map { "${it.key}=${it.value}" }, "；")
    }
}

private fun renderNpc(
    npc: ParsedNpc,
    fields: List<CharacterFieldDefinition>
): String = buildString {
    appendField(labelFor(fields, "age", "年龄"), npc.age.takeIf { it > 0 }?.toString().orEmpty())
    appendField(labelFor(fields, "gender", "性别"), npc.gender)
    appendField(labelFor(fields, "race", "种族"), npc.race)
    appendField("与主角关系", npc.relationshipToPlayer)
    appendField(labelFor(fields, "personality", "人物特征"), npc.personality)
    appendField(labelFor(fields, "appearance", "外貌"), npc.appearance)
    appendField(labelFor(fields, "background", "经历"), npc.background)
    appendCharacterLore(npc.lore)
    appendField("初始身体", physicalSummary(npc.physicalState, fields))
    appendField("初始心理", mentalSummary(npc.mentalState, fields))
    npc.customAttributes.forEach { (key, value) ->
        appendField(labelFor(fields, key, key), value)
    }
    if (npc.inventory.isNotEmpty()) {
        appendList("初始物品", npc.inventory.map { "${it.name} ×${it.quantity}" })
    }
}

private fun renderWorldbook(item: ParsedWorldbookItem): String = buildString {
    appendField("状态", item.status)
    appendField("描述", item.description)
    appendList("关键词", item.keywords)
    appendList("相关角色", item.relatedCharacters)
    appendList("相关事件", item.relatedEvents)
    appendList("相关地点", item.relatedLocations)
    if (item.isSecret) {
        appendField("可见性", if (item.knownByPlayer) "秘密资料，主角已知" else "秘密资料，主角未知")
    }
}

private fun StringBuilder.appendWorldLore(lore: WorldLore) {
    appendField("时代", lore.era)
    appendField("核心前提", lore.premise)
    appendField("历史", lore.history)
    appendField("地理", lore.geography)
    appendField("宇宙/现实规则", lore.cosmology)
    appendField("社会", lore.society)
    appendField("文化", lore.culture)
    appendField("政治", lore.politics)
    appendField("经济", lore.economy)
    appendField("法律", lore.law)
    appendField("宗教", lore.religion)
    appendList("语言", lore.languages)
    appendField("历法", lore.calendar)
    appendField("力量体系", lore.powerSystem)
    appendField("技术边界", lore.technologyRules)
    appendList("阵营", lore.factions)
    appendList("长期冲突", lore.conflicts)
    appendList("世界规则", lore.rules)
    appendList("主题", lore.themes)
    appendField("叙事风格", lore.narrativeStyle)
    lore.custom.forEach { (label, value) -> appendField(label, value) }
}

private fun StringBuilder.appendCharacterLore(lore: CharacterLore) {
    appendList("别名", lore.aliases)
    appendField("故事作用", lore.role)
    appendField("代词", lore.pronouns)
    appendField("生日", lore.birthDate)
    appendField("国籍", lore.nationality)
    appendField("住处", lore.residence)
    appendField("教育", lore.education)
    appendList("价值观", lore.values)
    appendList("目标", lore.goals)
    appendList("动机", lore.motivations)
    appendList("恐惧", lore.fears)
    appendList("弱点", lore.weaknesses)
    appendList("秘密", lore.secrets)
    appendList("习惯", lore.habits)
    appendField("说话风格", lore.speechStyle)
    appendList("代表台词", lore.sampleDialogues, separator = " / ")
    appendList("能力", lore.abilities)
    appendList("知识", lore.knowledge)
    appendList("行为边界", lore.boundaries)
    appendField("角色弧", lore.characterArc)
    lore.relationshipNotes.forEach { (name, note) -> appendField("关系·$name", note) }
    lore.custom.forEach { (label, value) -> appendField(label, value) }
}

private fun StringBuilder.appendField(label: String, value: String) {
    val normalized = value.trim()
    if (normalized.isNotBlank()) appendLine("$label：$normalized")
}

private fun StringBuilder.appendList(
    label: String,
    values: List<String>,
    separator: String = "、"
) {
    val normalized = values.map(String::trim).filter(String::isNotBlank)
    if (normalized.isNotEmpty()) appendLine("$label：${normalized.joinToString(separator)}")
}

private fun physicalSummary(
    state: com.novelchat.data.model.PhysicalState,
    fields: List<CharacterFieldDefinition>
): String {
    return listOf(
        "${labelFor(fields, "stamina", "体力")} ${state.stamina}",
        "${labelFor(fields, "health", "健康")} ${state.health}",
        state.fatigue.takeIf { it.isNotBlank() }?.let { "${labelFor(fields, "fatigue", "疲劳")} $it" },
        state.hunger.takeIf { it.isNotBlank() }?.let { "${labelFor(fields, "hunger", "饥饿")} $it" },
        state.pain.takeIf { it.isNotBlank() && it != "无" }?.let { "${labelFor(fields, "pain", "疼痛")} $it" },
        state.injuries.takeIf { it.isNotEmpty() }?.joinToString("、", prefix = "伤势 ")
    ).filterNotNull().filter(String::isNotBlank).joinToString(" · ")
}

private fun mentalSummary(
    state: com.novelchat.data.model.MentalState,
    fields: List<CharacterFieldDefinition>
): String {
    return listOf(
        state.mood.takeIf { it.isNotBlank() }?.let { "${labelFor(fields, "mood", "情绪")} $it" },
        state.stress.takeIf { it.isNotBlank() }?.let { "${labelFor(fields, "stress", "压力")} $it" },
        state.focus.takeIf { it.isNotBlank() }?.let { "${labelFor(fields, "focus", "关注")} $it" },
        state.currentThoughts.takeIf { it.isNotBlank() }?.let { "${labelFor(fields, "current_thoughts", "想法")} $it" }
    ).filterNotNull().filter(String::isNotBlank).joinToString(" · ")
}

private fun labelFor(
    fields: List<CharacterFieldDefinition>,
    key: String,
    fallback: String
): String = CharacterFieldPolicy.definitionFor(fields, key)?.label?.ifBlank { fallback } ?: fallback

private fun worldbookTypeLabel(type: String): String {
    return when (type) {
        "location" -> "地点"
        "item" -> "物品"
        "organization" -> "组织"
        "character" -> "角色资料"
        else -> "事件"
    }
}
