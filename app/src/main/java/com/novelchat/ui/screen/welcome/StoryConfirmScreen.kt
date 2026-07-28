package com.novelchat.ui.screen.welcome

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelchat.data.model.CharacterLore
import com.novelchat.data.model.WorldLore
import com.novelchat.data.remote.model.ParsedNpc
import com.novelchat.data.remote.model.ParsedWorldbookItem
import com.novelchat.data.remote.model.StoryParseResult
import com.novelchat.ui.theme.*
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
        Box(modifier = Modifier.fillMaxSize().background(DarkBg))
        return
    }
    var worldExpanded by remember { mutableStateOf(false) }
    var playerExpanded by remember { mutableStateOf(false) }
    var openingExpanded by remember { mutableStateOf(true) }
    var npcsExpanded by remember { mutableStateOf(false) }
    var wbExpanded by remember { mutableStateOf(false) }

    BackHandler(enabled = state.isInitializing) {}

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("确认故事设定", color = GoldColor) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.isInitializing) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, "返回", tint = DimText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        containerColor = DarkBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExpandableSection("世界整体设定", worldExpanded, onToggle = { worldExpanded = !worldExpanded }) {
                Text(renderWorldSetting(result), color = GoldColor, fontSize = 13.sp, lineHeight = 20.sp)
            }
            ExpandableSection("主角", playerExpanded, onToggle = { playerExpanded = !playerExpanded }) {
                Text(renderPlayer(result), color = GoldColor, fontSize = 13.sp, lineHeight = 20.sp)
            }
            ExpandableSection("开局", openingExpanded, onToggle = { openingExpanded = !openingExpanded }) {
                Text(renderOpening(result), color = GoldColor, fontSize = 13.sp, lineHeight = 20.sp)
            }
            ExpandableSection("NPC 角色（${result.npcs.size}个）", npcsExpanded, onToggle = { npcsExpanded = !npcsExpanded }) {
                if (result.npcs.isEmpty()) {
                    Text("无初始 NPC", color = DimText, fontSize = 13.sp)
                }
                result.npcs.forEachIndexed { index, npc ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = SurfaceLight,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }
                    Text(
                        "${npc.name} · ${npc.occupation.ifBlank { npc.lore.role.ifBlank { "身份未定" } }}",
                        color = GoldColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        renderNpc(npc),
                        color = DimText,
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            ExpandableSection("世界书条目（${result.worldbook.size}个）", wbExpanded, onToggle = { wbExpanded = !wbExpanded }) {
                result.worldbook.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = SurfaceLight,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }
                    Text(
                        "${worldbookTypeLabel(item.type)} · ${item.name}",
                        color = GoldColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        renderWorldbook(item),
                        color = DimText,
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            state.parseError?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CvRed.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(12.dp),
                        color = CvRed,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            if (state.isInitializing) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GoldColor)
                }
            } else {
                Button(
                    onClick = { viewModel.confirmAndCreateStory(onStartChat) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldColor)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("确认并开始")
                }
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
    Card(
        colors = CardDefaults.cardColors(containerColor = PanelBg),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = GoldColor, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = DimText
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                content()
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
    appendWorldLore(ws.lore)
}

private fun renderPlayer(r: StoryParseResult): String = buildString {
    val p = r.player
    appendField("姓名", p.name)
    appendField("年龄", p.age.toString())
    appendField("性别", p.gender)
    appendField("种族", p.race)
    appendField("职业", p.class_)
    appendField("性格", p.personality)
    appendField("外貌", p.appearance)
    appendField("背景", p.background)
    appendList("技能", p.skills)
    appendCharacterLore(p.lore)
    appendField("初始身体", physicalSummary(p.physicalState))
    appendField("初始心理", mentalSummary(p.mentalState))
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

private fun renderNpc(npc: ParsedNpc): String = buildString {
    appendField("年龄", npc.age.takeIf { it > 0 }?.toString().orEmpty())
    appendField("性别", npc.gender)
    appendField("种族", npc.race)
    appendField("与主角关系", npc.relationshipToPlayer)
    appendField("性格", npc.personality)
    appendField("外貌", npc.appearance)
    appendField("背景", npc.background)
    appendCharacterLore(npc.lore)
    appendField("初始身体", physicalSummary(npc.physicalState))
    appendField("初始心理", mentalSummary(npc.mentalState))
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

private fun physicalSummary(state: com.novelchat.data.model.PhysicalState): String {
    return listOf(
        "体力 ${state.stamina.coerceIn(0, 100)}",
        state.health,
        state.fatigue.takeIf { it.isNotBlank() }?.let { "疲劳 $it" },
        state.hunger.takeIf { it.isNotBlank() }?.let { "饥饿 $it" },
        state.pain.takeIf { it.isNotBlank() && it != "无" }?.let { "疼痛 $it" },
        state.injuries.takeIf { it.isNotEmpty() }?.joinToString("、", prefix = "伤势 ")
    ).filterNotNull().filter(String::isNotBlank).joinToString(" · ")
}

private fun mentalSummary(state: com.novelchat.data.model.MentalState): String {
    return listOf(
        state.mood,
        state.stress.takeIf { it.isNotBlank() }?.let { "压力 $it" },
        state.focus.takeIf { it.isNotBlank() }?.let { "关注 $it" },
        state.currentThoughts.takeIf { it.isNotBlank() }?.let { "想法 $it" }
    ).filterNotNull().filter(String::isNotBlank).joinToString(" · ")
}

private fun worldbookTypeLabel(type: String): String {
    return when (type) {
        "location" -> "地点"
        "item" -> "物品"
        "organization" -> "组织"
        "character" -> "角色资料"
        else -> "事件"
    }
}
