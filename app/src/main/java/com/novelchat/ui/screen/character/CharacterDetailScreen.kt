package com.novelchat.ui.screen.character

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.CharacterFieldDefinition
import com.novelchat.ui.theme.*
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
                title = { Text(card?.name ?: "角色详情", color = GoldColor) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, "返回", tint = DimText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBg
    ) { padding ->
        if (state.isLoading && card == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                CircularProgressIndicator(color = GoldColor)
            }
        } else if (card == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("未找到角色", color = DimText)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CharacterFieldSections(
                    CharacterFieldDisplayPolicy.npcDetails(
                        card,
                        state.playerName,
                        state.characterFields
                    )
                )

                Section("系统状态") {
                    DetailRow("出场状态", when (card.status) {
                        "active" -> "活跃"
                        "pool" -> "待机"
                        else -> card.status.ifBlank { "未知" }
                    })
                }

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

                    if (card.lore.secrets.isNotEmpty()) {
                        Section("幕后设定") {
                            card.lore.secrets.forEach { secret ->
                                Text("· $secret", color = GoldColor, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // 关系
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

                // 物品
                if (card.inventory.isNotEmpty()) {
                    Section("物品") {
                        card.inventory.forEach { item ->
                            DetailRow(
                                item.name.ifBlank { "未命名物品" },
                                "×${item.quantity.coerceAtLeast(1)}${item.type.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}"
                            )
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
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
                if (
                    field.definition.valueType in setOf(
                        CharacterFieldDefinition.TYPE_TEXT,
                        CharacterFieldDefinition.TYPE_LIST
                    ) && field.rawValue.length > 48
                ) {
                    DetailBlock(field.definition.label, field.rawValue)
                } else {
                    DetailRow(field.definition.label, field.value)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PanelBg),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, color = GoldColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = DimText, fontSize = 13.sp, modifier = Modifier.weight(0.9f))
        Spacer(Modifier.width(12.dp))
        Text(
            value.ifBlank { "未知" },
            color = GoldColor,
            fontSize = 13.sp,
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
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = DimText, fontSize = 12.sp)
        Text(
            value,
            color = GoldColor,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
