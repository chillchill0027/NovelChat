package com.novelchat.ui.screen.character

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelchat.data.model.CharacterCard
import com.novelchat.ui.theme.*
import com.novelchat.ui.viewmodel.CharacterViewModel
import com.novelchat.ui.viewmodel.NpcCardPolicy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterListScreen(
    characterId: Long,
    onBack: () -> Unit,
    onCharacterClick: (CharacterCard) -> Unit,
    viewModel: CharacterViewModel
) {
    LaunchedEffect(characterId) {
        viewModel.loadCharacters(characterId)
    }

    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("角色列表", color = GoldColor) },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // 活跃角色
            if (state.activeCharacters.isNotEmpty()) {
                item {
                    Text("活跃角色", color = GoldColor, fontWeight = FontWeight.Bold,
                        fontSize = 16.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
                itemsIndexed(
                    items = state.activeCharacters,
                    key = { index, card -> "active-${card.id.ifBlank { card.name }}-$index" }
                ) { _, card ->
                    CharacterCardItem(
                        card = card,
                        playerName = state.playerName,
                        onClick = { onCharacterClick(card) }
                    )
                }
            }

            // 待机角色
            if (state.poolCharacters.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("待机角色", color = DimText, fontWeight = FontWeight.Bold,
                        fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
                itemsIndexed(
                    items = state.poolCharacters,
                    key = { index, card -> "pool-${card.id.ifBlank { card.name }}-$index" }
                ) { _, card ->
                    CharacterCardItem(
                        card = card,
                        playerName = state.playerName,
                        onClick = { onCharacterClick(card) },
                        dimmed = true
                    )
                }
            }

            if (state.isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GoldColor)
                    }
                }
            } else if (state.activeCharacters.isEmpty() && state.poolCharacters.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("暂无角色", color = DimText)
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterCardItem(
    card: CharacterCard,
    playerName: String,
    onClick: () -> Unit,
    dimmed: Boolean = false
) {
    val relationship = NpcCardPolicy.relationshipSnapshot(card, playerName)
    val identity = listOf(card.race, card.occupation)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .ifBlank { "身份未知" }
    val favorText = if (relationship.favor >= 0) "+${relationship.favor}" else relationship.favor.toString()

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = PanelBg),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(if (dimmed) SurfaceLight.copy(alpha = 0.5f) else SurfaceLight),
                contentAlignment = Alignment.Center
            ) {
                Text(card.name.take(1), color = if (dimmed) DimText else GoldColor,
                    fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    card.name,
                    color = if (dimmed) DimText else GoldColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    identity,
                    color = DimText, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "好感 $favorText",
                    color = if (dimmed) DimText else when {
                        relationship.favor >= 40 -> NeonCyan
                        relationship.favor <= -20 -> CvRed
                        else -> BarPositive
                    },
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (dimmed) {
                Text("待机", color = DimText, fontSize = 11.sp)
            } else {
                Column(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .widthIn(max = 84.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        relationship.stage,
                        color = NeonCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        relationship.trust?.let { "信任 $it" } ?: "信任 --",
                        color = when {
                            relationship.trust == null -> DimText
                            relationship.trust >= 70 -> NeonCyan
                            relationship.trust >= 35 -> BarPositive
                            else -> CvYellow
                        },
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
