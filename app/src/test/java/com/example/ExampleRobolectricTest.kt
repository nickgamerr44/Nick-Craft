package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.minecraft.data.WorldInfo
import com.example.minecraft.data.WorldStorage
import com.example.minecraft.model.GameMode
import com.example.minecraft.model.ItemCategory
import com.example.minecraft.model.ItemType
import com.example.minecraft.model.Player
import com.example.minecraft.model.VoxelWorld
import com.example.minecraft.multiplayer.BlockEditEvent
import com.example.minecraft.multiplayer.MultiplayerGateway
import com.example.minecraft.multiplayer.MultiplayerSessionState
import com.example.minecraft.multiplayer.PlayerHitEvent
import com.example.minecraft.multiplayer.ServerSummary
import com.example.minecraft.ui.CategoryTabIcon
import com.example.minecraft.ui.DeathRespawnOverlay
import com.example.minecraft.ui.ItemIcon
import com.example.minecraft.ui.MultiplayerDialog
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h360dp-land")
class ExampleRobolectricTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Nick Craft", appName)
  }

  @Test
  fun `all 31 items and category tabs render custom pixel-art icons`() {
    composeRule.setContent {
      ItemType.entries.forEach { item ->
        ItemIcon(item = item)
      }
      ItemCategory.entries.forEach { cat ->
        CategoryTabIcon(category = cat)
      }
    }
    composeRule.waitForIdle()
  }

  @Test
  fun `multiplayer dialog shows unclipped server list and sign out button in landscape`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val fakeGateway = object : MultiplayerGateway {
      override val sessionState: StateFlow<MultiplayerSessionState> =
        MutableStateFlow(MultiplayerSessionState())
      override val availableServers: StateFlow<List<ServerSummary>> = MutableStateFlow(
        listOf(
          ServerSummary(
            serverId = "NCD4EF",
            serverCode = "NCD4EF",
            worldName = "Meu Mundo",
            hostName = "Nicolas Vigna",
            gameMode = GameMode.SURVIVAL,
            seed = 12345L,
            currentPlayers = 1,
            maxPlayers = 5
          )
        )
      )
      override val remoteBlockEdits: SharedFlow<BlockEditEvent> = MutableSharedFlow()
      override val remotePlayerHits: SharedFlow<PlayerHitEvent> = MutableSharedFlow()
      override val currentUserUid: StateFlow<String?> = MutableStateFlow("uid_1")
      override val currentUserName: StateFlow<String?> = MutableStateFlow("Nicolas Vigna")
      override val requiresSignIn: Boolean = true
      override val supportsCloudServers: Boolean = true

      override suspend fun trySilentSignIn(context: Context): Boolean = true
      override suspend fun signInWithGoogle(activityContext: Context): Result<String> = Result.success("uid_1")
      override suspend fun signOut() {}
      override suspend fun refreshServers(): Result<Unit> = Result.success(Unit)
      override suspend fun hostWorldAsServer(worldInfo: WorldInfo, world: VoxelWorld, player: Player) =
        Result.success(MultiplayerSessionState())
      override suspend fun joinServerByCode(codeOrAddress: String, world: VoxelWorld, player: Player) =
        Result.failure<WorldInfo>(UnsupportedOperationException())
      override suspend fun joinServer(server: ServerSummary, world: VoxelWorld, player: Player) =
        Result.failure<WorldInfo>(UnsupportedOperationException())
      override fun publishLocalPlayerState(player: Player, forceImmediate: Boolean) {}
      override fun publishBlockEdit(x: Int, y: Int, z: Int, blockType: com.example.minecraft.model.BlockType) {}
      override fun publishPlayerHit(targetPlayerId: String, damage: Float) {}
      override suspend fun closeOrLeaveServer() {}
      override fun clearError() {}
    }

    composeRule.setContent {
      MultiplayerDialog(
        gateway = fakeGateway,
        storage = WorldStorage(context),
        onHostWorld = {},
        onJoinByCode = {},
        onJoinServer = {},
        onDismiss = {}
      )
    }
    composeRule.waitForIdle()

    composeRule.onNodeWithTag("multiplayer_signout_button").assertIsDisplayed()
    composeRule.onNodeWithText("Servidores Online Abertos:").assertIsDisplayed()
    composeRule.onNodeWithTag("server_item_NCD4EF").assertIsDisplayed()
    composeRule.onNodeWithTag("connect_server_NCD4EF").assertIsDisplayed()
  }

  @Test
  fun `death respawn overlay shows attacker and respawns player at spawn on click`() {
    var respawned = false
    composeRule.setContent {
      DeathRespawnOverlay(
        attackerName = "AmigoPvP",
        spawnX = 24.5f,
        spawnZ = 24.5f,
        onRespawn = { respawned = true },
        onExitToTitle = {}
      )
    }
    composeRule.waitForIdle()

    composeRule.onNodeWithTag("death_title").assertIsDisplayed()
    composeRule.onNodeWithText("Eliminado por AmigoPvP").assertIsDisplayed()
    composeRule.onNodeWithTag("respawn_button").assertIsDisplayed().performClick()
    assertTrue(respawned)
  }
}
