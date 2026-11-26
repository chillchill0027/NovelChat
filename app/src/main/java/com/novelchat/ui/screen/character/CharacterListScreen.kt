package com.novelchat.ui.screen.character

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelchat.data.model.CharacterCard
import com.novelchat.ui.theme.AmberPrimary
import com.novelchat.ui.theme.BarPositive
import com.novelchat.ui.theme.CenteredAdaptiveBox
import com.novelchat.ui.theme.CvRed
import com.novelchat.ui.theme.CvYellow
import com.novelchat.ui.theme.EditorialAvatar
import com.novelchat.ui.theme.Hairline
import com.novelchat.ui.theme.InkMuted
import com.novelchat.ui.theme.InkPrimary
import com.novelchat.ui.theme.InkSecondary
import com.novelchat.ui.theme.ObsidianCanvas
import com.novelchat.ui.theme.OnSageContainer
import com.novelchat.ui.theme.SageContainer
import com.novelchat.ui.theme.SageTertiary
import com.novelchat.ui.theme.SectionOverline
import com.novelchat.ui.theme.SurfaceBase
import com.novelchat.ui.theme.SurfaceHigh
import com.novelchat.ui.theme.SurfaceLow
import com.novelchat.ui.theme.TonalBadge
import com.novelchat.ui.theme.rememberAdaptiveSpec
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
    val adaptive = rememberAdaptiveSpec()
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
                        text = "角色列表",
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianCanvas)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = ObsidianCanvas
    ) { padding ->
        CenteredAdaptiveBox(
            maxWidth = adaptive.contentMaxWidth,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val columns = adaptive.cardGridColumns
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = adaptive.horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 36.dp)
            ) {
                if (state.activeCharacters.isNotEmpty()) {
                    item {
                        SectionOverline(
                            title = "活跃角色",
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                            trailing = {
                                TonalBadge(
                                    text = "${state.activeCharacters.size} 位",
                                    containerColor = SurfaceLow,
                                    contentColor = InkMuted
                                )
                            }
                        )
                    }
                    if (columns > 1) {
                        val rows = state.activeCharacters.chunked(columns)
                        itemsIndexed(
                            items = rows,
                            key = { rowIdx, row -> "active-row-$rowIdx-${row.firstOrNull()?.name.orEmpty()}" }
                        ) { _, rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowItems.forEach { card ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        CharacterCardItem(
                                            card = card,
                                            playerName = state.playerName,
                                            onClick = { onCharacterClick(card) }
                                        )
                                    }
                                }
                                repeat(columns - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
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
                }

                if (state.poolCharacters.isNotEmpty()) {
                    item {
                        SectionOverline(
                            title = "待机角色",
                            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                            trailing = {
                                TonalBadge(
                                    text = "${state.poolCharacters.size} 位",
                                    containerColor = SurfaceLow,
                                    contentColor = InkMuted
                                )
                            }
                        )
                    }
                    if (columns > 1) {
                        val rows = state.poolCharacters.chunked(columns)
                        itemsIndexed(
                            items = rows,
                            key = { rowIdx, row -> "pool-row-$rowIdx-${row.firstOrNull()?.name.orEmpty()}" }
                        ) { _, rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowItems.forEach { card ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        CharacterCardItem(
                                            card = card,
                                            playerName = state.playerName,
                                            onClick = { onCharacterClick(card) },
                                            dimmed = true
                                        )
                                    }
                                }
                                repeat(columns - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
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
                }

                if (state.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = AmberPrimary, strokeWidth = 2.5.dp)
                        }
                    }
                } else if (state.activeCharacters.isEmpty() && state.poolCharacters.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("暂无已出场角色", color = InkSecondary, fontSize = 14.sp)
                        }
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
    val identity = listOf(card.occupation, card.race.takeIf { it != "人类" })
        .filterNotNull()
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .ifBlank { "身份未知" }
    val favorText = if (relationship.favor >= 0) "+${relationship.favor}" else relationship.favor.toString()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (dimmed) SurfaceLow else SurfaceBase,
        border = BorderStroke(1.dp, Hairline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EditorialAvatar(
                name = card.name,
                size = 46.dp,
                shape = RoundedCornerShape(14.dp),
                dimmed = dimmed
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = card.name,
                        color = if (dimmed) InkSecondary else InkPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TonalBadge(
                        text = if (dimmed) "待机" else relationship.stage,
                        containerColor = if (dimmed) SurfaceHigh else SageContainer,
                        contentColor = if (dimmed) InkMuted else OnSageContainer
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = identity,
                    color = InkSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TonalBadge(
                        text = "好感 $favorText",
                        containerColor = SurfaceHigh,
                        contentColor = if (dimmed) InkMuted else when {
                            relationship.favor >= 40 -> SageTertiary
                            relationship.favor <= -20 -> CvRed
                            else -> BarPositive
                        }
                    )
                    TonalBadge(
                        text = relationship.trust?.let { "信任 $it" } ?: "信任 --",
                        containerColor = SurfaceHigh,
                        contentColor = if (dimmed) InkMuted else when {
                            relationship.trust == null -> InkMuted
                            relationship.trust >= 70 -> SageTertiary
                            relationship.trust >= 35 -> BarPositive
                            else -> CvYellow
                        }
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = InkMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
