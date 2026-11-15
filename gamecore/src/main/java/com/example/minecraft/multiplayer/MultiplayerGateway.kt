package com.example.minecraft.multiplayer

import android.content.Context
import com.example.minecraft.data.WorldInfo
import com.example.minecraft.model.BlockType
import com.example.minecraft.model.Player
import com.example.minecraft.model.VoxelWorld
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface MultiplayerGateway {
  val supportsCloudServers: Boolean
  val requiresSignIn: Boolean
  val currentUserName: StateFlow<String?>
  val currentUserUid: StateFlow<String?>
  val sessionState: StateFlow<MultiplayerSessionState>
  val availableServers: StateFlow<List<ServerSummary>>
  val remoteBlockEdits: SharedFlow<BlockEditEvent>
  val remotePlayerHits: SharedFlow<PlayerHitEvent>

  suspend fun trySilentSignIn(context: Context): Boolean
  suspend fun signInWithGoogle(activityContext: Context): Result<String>
  suspend fun signOut()

  suspend fun refreshServers(): Result<Unit>

  suspend fun hostWorldAsServer(
    worldInfo: WorldInfo,
    world: VoxelWorld,
    player: Player
  ): Result<MultiplayerSessionState>

  suspend fun joinServerByCode(
    codeOrAddress: String,
    world: VoxelWorld,
    player: Player
  ): Result<WorldInfo>

  suspend fun joinServer(
    server: ServerSummary,
    world: VoxelWorld,
    player: Player
  ): Result<WorldInfo>

  fun updatePoseSyncIntervalMs(intervalMs: Long) {}
  fun publishLocalPlayerState(player: Player, forceImmediate: Boolean = false)
  fun publishBlockEdit(x: Int, y: Int, z: Int, blockType: BlockType)
  fun publishPlayerHit(targetPlayerId: String, damage: Float)
  suspend fun closeOrLeaveServer()
  fun clearError()
}
