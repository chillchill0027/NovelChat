package com.novelchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import android.net.Uri
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.PlayerState
import com.novelchat.ui.navigation.Routes
import com.novelchat.ui.screen.chat.ChatScreen
import com.novelchat.ui.screen.character.CharacterDetailScreen
import com.novelchat.ui.screen.character.CharacterListScreen
import com.novelchat.ui.screen.lobby.CharacterCustomScreen
import com.novelchat.ui.screen.lobby.MainLobbyScreen
import com.novelchat.ui.screen.lobby.PresetSelectScreen
import com.novelchat.ui.screen.welcome.ApiSetupScreen
import com.novelchat.ui.screen.welcome.CharacterConfirmScreen
import com.novelchat.ui.screen.welcome.StoryConfirmScreen
import com.novelchat.ui.screen.welcome.StoryImportScreen
import com.novelchat.ui.screen.welcome.WelcomeScreen
import com.novelchat.ui.screen.welcome.WelcomeViewModel
import com.novelchat.ui.screen.worldbook.WorldbookScreen
import com.novelchat.ui.theme.NovelChatTheme
import com.novelchat.ui.theme.DarkBg
import com.novelchat.ui.theme.PrimaryPink
import com.novelchat.ui.viewmodel.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent { NovelChatTheme { NovelChatNavGraph() } }
    }
}

@Composable
fun NovelChatNavGraph() {
    val navController = rememberNavController()
    val welcomeVM: WelcomeViewModel = viewModel()
    val setupVM: SetupViewModel = viewModel()
    val lobbyVM: MainLobbyViewModel = viewModel()
    val worldbookVM: WorldbookViewModel = viewModel()
    val characterVM: CharacterViewModel = viewModel()

    NavHost(navController = navController, startDestination = Routes.WELCOME, modifier = Modifier.fillMaxSize()) {
        composable(Routes.WELCOME) {
            val isCfg by welcomeVM.isApiConfigured.collectAsState()
            LaunchedEffect(isCfg) {
                if (isCfg == true) {
                    navController.navigate(Routes.LOBBY) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            when (isCfg) {
                null, true -> Box(
                    modifier = Modifier.fillMaxSize().background(DarkBg),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryPink)
                }
                false -> WelcomeScreen(
                    onApiSetup = { navController.navigate(Routes.API_SETUP) }
                )
            }
        }
        composable(Routes.API_SETUP) {
            val returnsToLobby = navController.previousBackStackEntry?.destination?.route == Routes.LOBBY
            ApiSetupScreen(viewModel = setupVM, onBack = { navController.popBackStack() },
                onNext = {
                    if (returnsToLobby) {
                        navController.popBackStack()
                    } else {
                        setupVM.resetStoryDraft()
                        navController.navigate(Routes.LOBBY) {
                            popUpTo(Routes.WELCOME) { inclusive = true }
                            launchSingleTop = true
                        }
                        navController.navigate(Routes.STORY_IMPORT)
                    }
                },
                continueLabel = if (returnsToLobby) "保存并返回" else "下一步：导入故事"
            )
        }
        composable(Routes.LOBBY) {
            MainLobbyScreen(viewModel = lobbyVM,
                onNewPreset = {
                    setupVM.resetStoryDraft()
                    navController.navigate(Routes.STORY_IMPORT)
                },
                onNewCustom = { navController.navigate(Routes.PRESET_SELECT) },
                onOpenChat = { id -> navController.navigate(Routes.chat(id)) },
                onSettings = { navController.navigate(Routes.API_SETUP) })
        }
        composable(Routes.STORY_IMPORT) {
            StoryImportScreen(
                viewModel = setupVM,
                onBack = { navController.popBackStack() },
                onParsed = { navController.navigate(Routes.STORY_CONFIRM) }
            )
        }
        composable(Routes.STORY_CONFIRM) {
            StoryConfirmScreen(
                viewModel = setupVM,
                onBack = { navController.popBackStack() },
                onStartChat = { id ->
                    lobbyVM.loadConversations()
                    navController.navigate(Routes.characterConfirm(id)) {
                        popUpTo(Routes.STORY_IMPORT) { inclusive = true }
                    }
                }
            )
        }
        composable(route = Routes.CHARACTER_CONFIRM, arguments = listOf(navArgument("characterId") { type = NavType.LongType })) { bs ->
            val cid = bs.arguments?.getLong("characterId") ?: return@composable
            CharacterConfirmScreen(
                characterId = cid,
                viewModel = viewModel(),
                onBack = { navController.popBackStack() },
                onStartStory = {
                    navController.navigate(Routes.chat(cid)) {
                        popUpTo(Routes.characterConfirm(cid)) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.PRESET_SELECT) {
            val operation by lobbyVM.operationState.collectAsState()
            PresetSelectScreen(
                onBack = { navController.popBackStack() },
                isCreating = operation.isCreating,
                errorMessage = operation.errorMessage,
                onErrorShown = lobbyVM::clearOperationError,
                onCustom = { navController.navigate(Routes.CHARACTER_CUSTOM) },
                onPresetSelected = { preset ->
                    lobbyVM.createConversation(
                        name = preset.playerName, desc = "${preset.name} · ${preset.description}",
                        card = null,
                        player = PlayerState(
                            name = preset.playerName,
                            class_ = preset.name,
                            charm = preset.charm, huntingSkill = preset.huntingSkill,
                            socialRank = preset.socialRank, luck = preset.luck,
                            alpha = preset.alpha, resistance = preset.resistance,
                            cv = preset.cv
                        ),
                        presetKey = preset.key,
                        onCreated = { id ->
                            navController.navigate(Routes.chat(id)) {
                                popUpTo(Routes.LOBBY) { inclusive = false }
                            }
                        }
                    )
                }
            )
        }
        composable(Routes.CHARACTER_CUSTOM) {
            val operation by lobbyVM.operationState.collectAsState()
            CharacterCustomScreen(
                onBack = { navController.popBackStack() },
                isCreating = operation.isCreating,
                errorMessage = operation.errorMessage,
                onErrorShown = lobbyVM::clearOperationError,
                onCreate = { draft ->
                    val combinedBackground = listOf(draft.description, draft.background)
                        .filter { it.isNotBlank() }
                        .joinToString("。")
                    lobbyVM.createConversation(
                        name = draft.name,
                        desc = draft.description,
                        card = null,
                        player = PlayerState(
                            name = draft.name,
                            age = draft.age,
                            gender = draft.gender.ifBlank { "未明确" },
                            race = "人类",
                            class_ = draft.occupation.ifBlank { "城市居民" },
                            personality = draft.personality,
                            appearance = draft.appearance,
                            background = combinedBackground,
                            charm = 50, huntingSkill = 30,
                            socialRank = "C", alpha = 40, resistance = 35, cv = 5
                        ),
                        openingPrompt = draft.openingPrompt,
                        onCreated = { id ->
                            navController.navigate(Routes.chat(id)) {
                                popUpTo(Routes.LOBBY) { inclusive = false }
                            }
                        }
                    )
                }
            )
        }
        composable(route = Routes.CHAT, arguments = listOf(navArgument("characterId") { type = NavType.LongType })) { bs ->
            val cid = bs.arguments?.getLong("characterId") ?: return@composable
            ChatScreen(characterId = cid, onBack = { navController.popBackStack() },
                onWorldbook = { navController.navigate(Routes.worldbook(cid)) },
                onCharacterList = { navController.navigate(Routes.characters(cid)) },
                chatViewModel = viewModel())
        }
        composable(route = Routes.WORLDBOOK, arguments = listOf(navArgument("characterId") { type = NavType.LongType })) { bs ->
            val cid = bs.arguments?.getLong("characterId") ?: return@composable
            WorldbookScreen(characterId = cid, onBack = { navController.popBackStack() }, viewModel = worldbookVM)
        }
        composable(route = Routes.CHARACTERS, arguments = listOf(navArgument("characterId") { type = NavType.LongType })) { bs ->
            val cid = bs.arguments?.getLong("characterId") ?: return@composable
            CharacterListScreen(
                characterId = cid,
                onBack = { navController.popBackStack() },
                onCharacterClick = { card ->
                    val cardReference = card.id.ifBlank { card.name }
                    navController.navigate(Routes.characterDetail(cid, Uri.encode(cardReference)))
                },
                viewModel = characterVM
            )
        }
        composable(
            route = Routes.CHARACTER_DETAIL,
            arguments = listOf(
                navArgument("characterId") { type = NavType.LongType },
                navArgument("cardName") { type = NavType.StringType }
            )
        ) { bs ->
            val cid = bs.arguments?.getLong("characterId") ?: return@composable
            val cardName = Uri.decode(bs.arguments?.getString("cardName") ?: "")
            CharacterDetailScreen(
                characterId = cid,
                cardName = cardName,
                onBack = { navController.popBackStack() },
                viewModel = characterVM
            )
        }
    }
}
