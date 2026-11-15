package com.example.minecraft.multiplayer

import com.example.minecraft.model.GameMode

const val MAX_MULTIPLAYER_PLAYERS = 5
const val DEFAULT_LAN_PORT = 19132

enum class MultiplayerRole {
  NONE,
  HOST,
  CLIENT
}

data class RemotePlayerState(
  val playerId: String,
  val displayName: String,
  val x: Float,
  val y: Float,
  val z: Float,
  val yaw: Float,
  val pitch: Float,
  val slotIndex: Int = 0,
  val health: Float = 20f,
  val isDead: Boolean = false,
  val isWalking: Boolean = false,
  val swingSeq: Int = 0,
  val heldBlockId: Int = 0
)

data class PlayerHitEvent(
  val targetPlayerId: String,
  val attackerId: String,
  val attackerName: String,
  val damage: Float,
  val timestamp: Long = System.currentTimeMillis()
)

data class BlockEditEvent(
  val x: Int,
  val y: Int,
  val z: Int,
  val blockId: Byte,
  val authorId: String,
  val timestamp: Long = System.currentTimeMillis()
)

data class ServerSummary(
  val serverId: String,
  val serverCode: String,
  val worldName: String,
  val hostName: String,
  val gameMode: GameMode,
  val seed: Long,
  val currentPlayers: Int,
  val maxPlayers: Int = MAX_MULTIPLAYER_PLAYERS,
  val isOpen: Boolean = true,
  val hostAddress: String = "",
  val hostPort: Int = DEFAULT_LAN_PORT
) {
  val isFull: Boolean get() = currentPlayers >= maxPlayers
}

data class MultiplayerSessionState(
  val isActive: Boolean = false,
  val role: MultiplayerRole = MultiplayerRole.NONE,
  val serverId: String = "",
  val serverCode: String = "",
  val worldName: String = "",
  val maxPlayers: Int = MAX_MULTIPLAYER_PLAYERS,
  val currentPlayers: Int = 1,
  val remotePlayers: List<RemotePlayerState> = emptyList(),
  val allPlayerNames: List<String> = emptyList(),
  val connectionHint: String = "",
  val isBusy: Boolean = false,
  val errorMessage: String? = null
)
