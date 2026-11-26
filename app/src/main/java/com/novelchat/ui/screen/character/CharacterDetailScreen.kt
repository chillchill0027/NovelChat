package com.novelchat.ui.screen.character

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelchat.data.model.CharacterFieldDefinition
import com.novelchat.ui.theme.AmberContainer
import com.novelchat.ui.theme.AmberPrimary
import com.novelchat.ui.theme.CenteredAdaptiveBox
import com.novelchat.ui.theme.EditorialAvatar
import com.novelchat.ui.theme.Hairline
import com.novelchat.ui.theme.InkMuted
import com.novelchat.ui.theme.InkPrimary
import com.novelchat.ui.theme.InkSecondary
import com.novelchat.ui.theme.NovelFieldShape
import com.novelchat.ui.theme.ObsidianCanvas
import com.novelchat.ui.theme.OnAmberContainer
import com.novelchat.ui.theme.OnSageContainer
import com.novelchat.ui.theme.PrimaryPillButton
import com.novelchat.ui.theme.SageContainer
import com.novelchat.ui.theme.SageTertiary
import com.novelchat.ui.theme.SurfaceBase
import com.novelchat.ui.theme.SurfaceHigh
import com.novelchat.ui.theme.SurfaceLow
import com.novelchat.ui.theme.TonalBadge
import com.novelchat.ui.theme.novelTextFieldColors
import com.novelchat.ui.theme.rememberAdaptiveSpec
import com.novelchat.ui.viewmodel.CharacterFieldDisplay
import com.novelchat.ui.viewmodel.CharacterFieldDisplayPolicy
import com.novelchat.ui.viewmodel.CharacterViewModel
import com.novelchat.ui.viewmodel.NpcCardPolicy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterDetailScreen(
    characterId: Long,
    cardName: String,
    onBack: () -> Unit,
    viewModel: CharacterViewModel
) {
    LaunchedEffect(characterId) {
        viewModel.loadCharacters(characterId)
    }

    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSpeechDialog by remember { mutableStateOf(false) }
    val card = remember(cardName, state) {
        viewModel.getCardByReference(cardName)
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = card?.name ?: "角色详情",
                        color = InkPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, "返回", tint = InkSecondary)
                    }
                },
                actions = {
                    if (card != null && characterId > 0L) {
                        Surface(
                            onClick = { showSpeechDialog = true },
                            shape = CircleShape,
                            color = SageContainer,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = OnSageContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "编辑口吻",
                                    color = OnSageContainer,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianCanvas)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = ObsidianCanvas
    ) { padding ->
        if (state.isLoading && card == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AmberPrimary, strokeWidth = 2.5.dp)
            }
        } else if (card == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("未找到角色", color = InkSecondary)
            }
        } else {
            val relationship = NpcCardPolicy.relationshipSnapshot(card, state.playerName)
            val adaptive = rememberAdaptiveSpec()
            val statusLabel = when (card.status) {
                "active" -> "活跃"
                "pool" -> "待机"
                else -> card.status.ifBlank { "未知" }
            }
            val heroCard: @Composable () -> Unit = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = SurfaceBase,
                    border = BorderStroke(1.dp, Hairline)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EditorialAvatar(
                                name = card.name,
                                size = 54.dp,
                                shape = RoundedCornerShape(16.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = card.name,
                                        color = InkPrimary,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    TonalBadge(
                                        text = statusLabel,
                                        containerColor = if (card.status == "active") SageContainer else SurfaceHigh,
                                        contentColor = if (card.status == "active") OnSageContainer else InkMuted
                                    )
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = listOfNotNull(
                                        card.occupation.takeIf { it.isNotBlank() },
                                        card.age.takeIf { it > 0 }?.let { "${it}岁" },
                                        card.gender.takeIf { it.isNotBlank() }
                                    ).joinToString(" · ").ifBlank { "身份未知" },
                                    color = InkSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TonalBadge(
                                text = relationship.stage,
                                containerColor = SurfaceHigh,
                                contentColor = SageTertiary
                            )
                            val favorPrefix = if (relationship.favor >= 0) "+" else ""
                            TonalBadge(
                                text = "好感 $favorPrefix${relationship.favor}",
                                containerColor = SurfaceHigh,
                                contentColor = InkPrimary
                            )
                            TonalBadge(
                                text = relationship.trust?.let { "信任 $it" } ?: "信任 --",
                                containerColor = SurfaceHigh,
                                contentColor = InkSecondary
                            )
                        }
                    }
                }
            }
            val secretsCard: @Composable () -> Unit = {
                if (card.lore.secrets.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = AmberContainer.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, AmberPrimary.copy(alpha = 0.28f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "幕后设定（角色不会主动透露）",
                                color = AmberPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                            card.lore.secrets.forEach { secret ->
                                Text(
                                    text = "· $secret",
                                    color = OnAmberContainer,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
            }
            val detailCards: @Composable () -> Unit = {
                CharacterFieldSections(
                    CharacterFieldDisplayPolicy.npcDetails(
                        card,
                        state.playerName,
                        state.characterFields
                    )
                )

                if (card.lore.hasContent()) {
                    Section("长期角色设定") {
                        DetailRow("故事作用", card.lore.role)
                        DetailRow("别名", card.lore.aliases.joinToString("、"))
                        DetailRow("代词", card.lore.pronouns)
                        DetailRow("生日", card.lore.birthDate)
                        DetailRow("国籍", card.lore.nationality)
                        DetailRow("住处", card.lore.residence)
                        DetailBlock("教育", card.lore.education)
                        DetailBlock("价值观", card.lore.values.joinToString("；"))
                        DetailBlock("目标", card.lore.goals.joinToString("；"))
                        DetailBlock("动机", card.lore.motivations.joinToString("；"))
                        DetailBlock("恐惧", card.lore.fears.joinToString("；"))
                        DetailBlock("弱点", card.lore.weaknesses.joinToString("；"))
                        DetailBlock("习惯", card.lore.habits.joinToString("；"))
                        DetailBlock("说话风格", card.lore.speechStyle)
                        if (card.lore.sampleDialogues.isNotEmpty()) {
                            DetailBlock(
                                "代表台词",
                                card.lore.sampleDialogues.joinToString("\n") { "「$it」" }
                            )
                        }
                        DetailBlock("能力", card.lore.abilities.joinToString("；"))
                        DetailBlock("知识", card.lore.knowledge.joinToString("；"))
                        DetailBlock("行为边界", card.lore.boundaries.joinToString("；"))
                        DetailBlock("角色弧", card.lore.characterArc)
                        card.lore.relationshipNotes.forEach { (name, note) ->
                            DetailBlock("与$name", note)
                        }
                        card.lore.custom.forEach { (label, value) ->
                            DetailBlock(label, value)
                        }
                    }
                    if (!adaptive.isTwoColumnDetail) {
                        secretsCard()
                    }
                }

                val otherRelationships = card.relationships.filterKeys { name ->
                    val normalizedName = name.trim()
                    normalizedName.isNotBlank() &&
                        (
                            state.playerName.isBlank() ||
                                (!normalizedName.contains(state.playerName) &&
                                    !state.playerName.contains(normalizedName))
                            )
                }
                if (otherRelationships.isNotEmpty()) {
                    Section("其他关系") {
                        otherRelationships.forEach { (name, rel) ->
                            DetailRow(name, "${rel.stage.ifBlank { "未知" }} · 好感 ${rel.favor} · 信任 ${rel.trust}")
                        }
                    }
                }

                if (card.inventory.isNotEmpty()) {
                    Section("随身物品") {
                        card.inventory.forEach { item ->
                            DetailRow(
                                item.name.ifBlank { "未命名物品" },
                                "×${item.quantity.coerceAtLeast(1)}${item.type.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}"
                            )
                        }
                    }
                }
            }

            if (adaptive.isTwoColumnDetail) {
                CenteredAdaptiveBox(
                    maxWidth = adaptive.contentMaxWidth,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = adaptive.horizontalPadding, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .width(340.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            heroCard()
                            secretsCard()
                            Spacer(Modifier.height(24.dp))
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            detailCards()
                            Spacer(Modifier.height(28.dp))
                        }
                    }
                }
            } else {
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
                        heroCard()
                        detailCards()
                        Spacer(Modifier.height(28.dp))
                    }
                }
            }
        }
    }

    if (showSpeechDialog && card != null) {
        SpeechProfileEditSheet(
            characterName = card.name,
            initialStyle = card.lore.speechStyle,
            initialSamples = card.lore.sampleDialogues,
            onSave = { style, samples ->
                viewModel.updateSpeechProfile(cardName, style, samples) {
                    showSpeechDialog = false
                }
            },
            onDismiss = { showSpeechDialog = false }
        )
    }
}

@Composable
private fun CharacterFieldSections(fields: List<CharacterFieldDisplay>) {
    val titles = linkedMapOf(
        CharacterFieldDefinition.GROUP_STATUS to "当前状态",
        CharacterFieldDefinition.GROUP_RELATIONSHIP to "与主角的关系",
        CharacterFieldDefinition.GROUP_ABILITY to "能力与成长",
        CharacterFieldDefinition.GROUP_PROFILE to "人物档案",
        CharacterFieldDefinition.GROUP_IDENTITY to "基本信息"
    )
    val grouped = fields.groupBy { it.definition.group }
    titles.forEach { (group, title) ->
        val groupFields = grouped[group].orEmpty()
        if (groupFields.isEmpty()) return@forEach
        Section(title) {
            groupFields.forEach { field ->
                val useBlockLayout = field.definition.valueType in setOf(
                    CharacterFieldDefinition.TYPE_TEXT,
                    CharacterFieldDefinition.TYPE_LIST
                ) && (
                    field.rawValue.length > 16 ||
                        field.definition.key in blockLayoutFieldKeys
                    )
                if (useBlockLayout) {
                    DetailBlock(field.definition.label, field.rawValue)
                } else {
                    DetailRow(field.definition.label, field.value)
                }
            }
        }
    }
}

private val blockLayoutFieldKeys = setOf(
    "appearance",
    "personality",
    "background",
    "current_thoughts",
    "skills"
)

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceBase,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = AmberPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.4.sp
            )
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = InkSecondary, fontSize = 13.sp, modifier = Modifier.weight(0.9f))
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            color = InkPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.4f)
        )
    }
}

@Composable
private fun DetailBlock(label: String, value: String) {
    if (value.isBlank()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(label, color = InkMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(
            text = value,
            color = InkPrimary,
            fontSize = 14.sp,
            lineHeight = 21.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeechProfileEditSheet(
    characterName: String,
    initialStyle: String,
    initialSamples: List<String>,
    onSave: (String, List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var speechStyle by remember(initialStyle) { mutableStateOf(initialStyle) }
    val samples = remember(initialSamples) {
        mutableStateListOf<String>().apply {
            addAll(initialSamples.take(5))
            if (isEmpty()) add("")
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceBase,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "编辑 $characterName 口吻",
                    color = InkPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "关闭", tint = InkSecondary)
                }
            }

            Text("说话风格描述", color = InkSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            OutlinedTextField(
                value = speechStyle,
                onValueChange = { speechStyle = it.take(200) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                placeholder = { Text("如：语速慢、多反问，习惯用短句接话", fontSize = 13.sp) },
                colors = novelTextFieldColors(SurfaceLow),
                shape = NovelFieldShape
            )

            Text("代表台词示例（最多 5 句）", color = InkSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            samples.forEachIndexed { index, value ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { samples[index] = it.take(120) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("第 ${index + 1} 句代表台词", fontSize = 13.sp) },
                        colors = novelTextFieldColors(SurfaceLow),
                        shape = NovelFieldShape
                    )
                    IconButton(
                        onClick = {
                            if (samples.size > 1) {
                                samples.removeAt(index)
                            } else {
                                samples[0] = ""
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, "清空或删除", tint = InkMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (samples.size < 5) {
                TextButton(
                    onClick = { samples.add("") }
                ) {
                    Icon(Icons.Default.Add, null, tint = SageTertiary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("添加一句台词", color = SageTertiary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(4.dp))
            PrimaryPillButton(
                onClick = {
                    val cleaned = samples.map(String::trim).filter(String::isNotBlank).take(5)
                    onSave(speechStyle.trim(), cleaned)
                }
            ) {
                Text("保存口吻", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
