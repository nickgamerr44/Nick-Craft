package com.example.minecraft.ui

import android.annotation.SuppressLint
import android.opengl.GLSurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.minecraft.data.GitHubLiveConfigRepository
import com.example.minecraft.data.LiveGameConfig
import com.example.minecraft.data.WorldInfo
import com.example.minecraft.data.WorldStorage
import com.example.minecraft.engine.AudioEngine
import com.example.minecraft.engine.VoxelRenderer
import com.example.minecraft.model.BlockType
import com.example.minecraft.model.GameMode
import com.example.minecraft.model.GameVersionProfile
import com.example.minecraft.model.ItemType
import com.example.minecraft.model.Player
import com.example.minecraft.model.PlayerCombat
import com.example.minecraft.model.VoxelWorld
import com.example.minecraft.model.WorldGenerator
import com.example.minecraft.multiplayer.MultiplayerGateway
import com.example.minecraft.multiplayer.MultiplayerSessionState
import com.example.minecraft.multiplayer.RemotePlayerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@SuppressLint("ClickableViewAccessibility")
@Composable
fun GameScreen(
  worldInfo: WorldInfo,
  storage: WorldStorage,
  onExitToTitle: () -> Unit,
  versionTag: String = "0.03",
  multiplayerGateway: MultiplayerGateway? = null,
  liveConfigRepository: GitHubLiveConfigRepository? = null,
  initialStartAsServer: Boolean = false,
  preloadedWorld: VoxelWorld? = null,
  isRemoteClient: Boolean = false
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val lifecycleOwner = LocalLifecycleOwner.current

  // 1. World, Version Profile, Live GitHub Config, and Player instances
  val versionProfile = remember(versionTag) { GameVersionProfile.fromVersionTag(versionTag) }
  val liveRepo = remember(liveConfigRepository, context) {
    liveConfigRepository ?: GitHubLiveConfigRepository(cacheDir = context.filesDir)
  }
  val liveConfig by liveRepo.config.collectAsStateWithLifecycle()

  val world = remember(worldInfo.id) { preloadedWorld ?: VoxelWorld() }
  val player = remember(worldInfo.id) { Player() }

  val mpState by (multiplayerGateway?.sessionState?.collectAsStateWithLifecycle()
    ?: remember { mutableStateOf(MultiplayerSessionState()) })
  val mpUserUid by (multiplayerGateway?.currentUserUid?.collectAsStateWithLifecycle()
    ?: remember { mutableStateOf<String?>(null) })

  // Automatic GitHub live sync on game launch & every foreground resume without needing version bump
  LaunchedEffect(liveRepo) {
    liveRepo.syncFromGitHub()
  }
  androidx.compose.runtime.DisposableEffect(lifecycleOwner, liveRepo) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        scope.launch { liveRepo.syncIfStale(minIntervalMs = 8_000L) }
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  // Initialize or load world
  LaunchedEffect(worldInfo.id) {
    player.gameMode = worldInfo.gameMode
    if (!isRemoteClient) {
      val loaded = storage.loadWorld(worldInfo.id, world, player)
      if (!loaded) {
        WorldGenerator.generate(world, worldInfo.seed)
        player.inventory.setupStartingKit(worldInfo.gameMode == GameMode.CREATIVE)
        player.x = liveConfig.spawnX
        player.z = liveConfig.spawnZ
        player.y = PlayerCombat.findSurfaceSpawnY(world, liveConfig.spawnX, liveConfig.spawnZ)
      }
      if (initialStartAsServer && multiplayerGateway != null) {
        if (!multiplayerGateway.requiresSignIn || multiplayerGateway.currentUserUid.value != null) {
          multiplayerGateway.hostWorldAsServer(worldInfo, world, player)
        }
      }
    } else {
      player.inventory.setupStartingKit(worldInfo.gameMode == GameMode.CREATIVE)
      player.x = liveConfig.spawnX
      player.z = liveConfig.spawnZ
      player.y = PlayerCombat.findSurfaceSpawnY(world, liveConfig.spawnX, liveConfig.spawnZ)
    }
  }

  // 2. Renderer instance
  val renderer = remember(world, player) { VoxelRenderer(context, world, player) }

  // Sync remote players and live config parameters into OpenGL renderer & multiplayer gateway
  LaunchedEffect(mpState.remotePlayers, versionProfile.enableMultiplayer) {
    renderer.remotePlayers = if (versionProfile.enableMultiplayer) mpState.remotePlayers else emptyList()
  }
  LaunchedEffect(liveConfig, multiplayerGateway) {
    renderer.timeSpeedMultiplier = liveConfig.timeSpeedMultiplier
    renderer.remoteAnimationsEnabled = liveConfig.remoteAnimationsEnabled
    renderer.remoteWalkSwingDegrees = liveConfig.remoteWalkSwingDegrees
    renderer.remoteAttackSwingDegrees = liveConfig.remoteAttackSwingDegrees
    multiplayerGateway?.updatePoseSyncIntervalMs(liveConfig.remotePoseSyncIntervalMs)
  }

  // 3. UI & Combat States
  var showPauseMenu by remember { mutableStateOf(false) }
  var showInventory by remember { mutableStateOf(false) }
  var hasCraftingTableOpen by remember { mutableStateOf(false) }
  var showF3Debug by remember { mutableStateOf(false) }
  var activeCameraMode by remember { mutableStateOf(player.cameraMode) }

  var isPlayerDead by remember { mutableStateOf(false) }
  var lastAttackerName by remember { mutableStateOf<String?>(null) }
  var playerHealthHud by remember { mutableFloatStateOf(player.health) }
  var playerHungerHud by remember { mutableFloatStateOf(player.hunger) }
  var damageFlashAlpha by remember { mutableFloatStateOf(0f) }
  var lastMeleeAttackMs by remember { mutableLongStateOf(0L) }

  // Receive live block edits from remote players
  LaunchedEffect(multiplayerGateway, versionProfile.enableMultiplayer) {
    if (!versionProfile.enableMultiplayer) return@LaunchedEffect
    multiplayerGateway?.remoteBlockEdits?.collect { ev ->
      val blockType = BlockType.fromId(ev.blockId)
      world.setBlock(ev.x, ev.y, ev.z, blockType)
    }
  }

  // Receive incoming PvP damage from remote players
  LaunchedEffect(multiplayerGateway, versionProfile.enableMultiplayer) {
    if (!versionProfile.enableMultiplayer) return@LaunchedEffect
    multiplayerGateway?.remotePlayerHits?.collect { hit ->
      if (!liveConfig.pvpEnabled || isPlayerDead) return@collect
      player.takeDamage(hit.damage, bypassCreative = liveConfig.pvpDamagesCreative)
      playerHealthHud = player.health
      damageFlashAlpha = 0.45f
      lastAttackerName = hit.attackerName
      AudioEngine.playHurt()
      if (player.health <= 0f) {
        isPlayerDead = true
      }
      multiplayerGateway.publishLocalPlayerState(player, forceImmediate = true)
    }
  }

  // Controls input states
  var moveForward by remember { mutableFloatStateOf(0f) }
  var moveStrafe by remember { mutableFloatStateOf(0f) }
  var jumpHeld by remember { mutableStateOf(false) }
  var descendHeld by remember { mutableStateOf(false) }

  // Direct Touch to Mine & Place states
  var isTouchHolding by remember { mutableStateOf(false) }
  var touchDownTime by remember { mutableLongStateOf(0L) }
  var touchDragAccumX by remember { mutableFloatStateOf(0f) }
  var touchDragAccumY by remember { mutableFloatStateOf(0f) }

  // Targeted block / remote player and break progress
  var targetedBlockName by remember { mutableStateOf("") }
  var targetedRemotePlayer by remember { mutableStateOf<RemotePlayerState?>(null) }
  var breakProgressState by remember { mutableFloatStateOf(0f) }
  var inventoryVersion by remember { mutableIntStateOf(0) }

  val isMoving = (moveForward != 0f || moveStrafe != 0f)

  val handleRespawn: () -> Unit = {
    val spawnX = liveConfig.spawnX
    val spawnZ = liveConfig.spawnZ
    val spawnY = PlayerCombat.findSurfaceSpawnY(world, spawnX, spawnZ)
    player.respawn(spawnX, spawnY, spawnZ, liveConfig.respawnHealth)
    playerHealthHud = player.health
    playerHungerHud = player.hunger
    isPlayerDead = false
    lastAttackerName = null
    damageFlashAlpha = 0f
    breakProgressState = 0f
    renderer.breakProgress = 0f
    AudioEngine.playDing()
    if (versionProfile.enableMultiplayer) {
      multiplayerGateway?.publishLocalPlayerState(player, forceImmediate = true)
    }
  }

  BackHandler {
    when {
      isPlayerDead -> handleRespawn()
      showInventory -> showInventory = false
      showPauseMenu -> showPauseMenu = false
      else -> showPauseMenu = true
    }
  }

  // Helper to attack remote players, place block, or interact on tap
  val handleTapInteraction: () -> Unit = {
    if (!isPlayerDead) {
      val prevSwingSeq = player.swingSeq
      player.triggerSwing()
      if (versionProfile.enableMultiplayer && player.swingSeq != prevSwingSeq) {
        multiplayerGateway?.publishLocalPlayerState(player, forceImmediate = true)
      }

      val remoteHit = targetedRemotePlayer
      val blockHit = renderer.currentTargetHit
      val nowMs = System.currentTimeMillis()
      val canHitRemote = remoteHit != null &&
        versionProfile.enableMultiplayer &&
        liveConfig.pvpEnabled &&
        !remoteHit.isDead &&
        remoteHit.health > 0f &&
        nowMs - lastMeleeAttackMs >= 320L

      if (canHitRemote && remoteHit != null) {
        lastMeleeAttackMs = nowMs
        val heldItem = player.inventory.selectedItem?.item
        val damage = PlayerCombat.calculatePvpDamage(heldItem, liveConfig)
        renderer.flashRemotePlayer(remoteHit.playerId)
        AudioEngine.playHurt()
        multiplayerGateway?.publishPlayerHit(remoteHit.playerId, damage)
      } else if (blockHit != null) {
        val hit = blockHit
        if (hit.block == BlockType.CRAFTING_TABLE) {
          hasCraftingTableOpen = true
          showInventory = true
        } else if (hit.block == BlockType.TNT) {
          world.explode(hit.blockX, hit.blockY, hit.blockZ, 3.5f)
          if (versionProfile.enableMultiplayer) {
            multiplayerGateway?.publishBlockEdit(hit.blockX, hit.blockY, hit.blockZ, BlockType.AIR)
          }
          AudioEngine.playExplosion()
        } else {
          val selected = player.inventory.selectedItem
          val blockToPlace = selected?.item?.blockType
          if (blockToPlace != null && blockToPlace != BlockType.AIR) {
            val px = hit.blockX + hit.normalX
            val py = hit.blockY + hit.normalY
            val pz = hit.blockZ + hit.normalZ

            val playerMinX = player.x - player.halfW
            val playerMaxX = player.x + player.halfW
            val playerMinY = player.y
            val playerMaxY = player.y + player.height
            val playerMinZ = player.z - player.halfW
            val playerMaxZ = player.z + player.halfW

            val collides = (px + 1 > playerMinX && px < playerMaxX) &&
              (py + 1 > playerMinY && py < playerMaxY) &&
              (pz + 1 > playerMinZ && pz < playerMaxZ)

            if (!collides) {
              if (world.setBlock(px, py, pz, blockToPlace)) {
                if (versionProfile.enableMultiplayer) {
                  multiplayerGateway?.publishBlockEdit(px, py, pz, blockToPlace)
                }
              }
              AudioEngine.playPlace()
              if (player.gameMode == GameMode.SURVIVAL) {
                player.inventory.consumeSelectedItem()
                inventoryVersion++
              }
            }
          }
        }
      }
    }
  }

  // 60 FPS Game Tick Loop
  LaunchedEffect(worldInfo.id) {
    var digSoundTimer = 0
    var stepSoundTimer = 0
    var wasMoving = false

    while (isActive) {
      if (damageFlashAlpha > 0f) {
        damageFlashAlpha = (damageFlashAlpha - 0.03f).coerceAtLeast(0f)
      }

      if (!showPauseMenu && !showInventory && !isPlayerDead) {
        player.update(
          world = world,
          moveForward = moveForward,
          moveStrafe = moveStrafe,
          jumpHeld = jumpHeld,
          descendHeld = descendHeld,
          fixLimbStopAnimation = versionProfile.fixLimbStopAnimation,
          fixStrafeDirection = versionProfile.fixStrafeDirection,
          walkSpeed = liveConfig.moveSpeed,
          jumpVelocity = liveConfig.jumpVelocity,
          gravity = liveConfig.gravity
        )
        playerHealthHud = player.health
        playerHungerHud = player.hunger
        if (player.health <= 0f) {
          isPlayerDead = true
          if (versionProfile.enableMultiplayer) {
            multiplayerGateway?.publishLocalPlayerState(player, forceImmediate = true)
          }
        }

        world.updateParticles()
        val movementEdge = (wasMoving != isMoving)
        wasMoving = isMoving
        if (versionProfile.enableMultiplayer) {
          multiplayerGateway?.publishLocalPlayerState(player, forceImmediate = movementEdge)
        }

        if (player.isOnGround && isMoving) {
          stepSoundTimer++
          if (stepSoundTimer > 18) {
            AudioEngine.playStep()
            stepSoundTimer = 0
          }
        }

        // Raycasting (check remote player melee target vs block target)
        val (lookDx, lookDy, lookDz) = player.getLookDirection()
        val hit = world.raycast(player.x, player.eyeY, player.z, lookDx, lookDy, lookDz, 5.5f)
        renderer.currentTargetHit = hit

        val remoteHit = if (versionProfile.enableMultiplayer && liveConfig.pvpEnabled) {
          PlayerCombat.raycastRemotePlayer(
            originX = player.x,
            originY = player.eyeY,
            originZ = player.z,
            dirX = lookDx,
            dirY = lookDy,
            dirZ = lookDz,
            players = mpState.remotePlayers
          )
        } else null

        val blockDist = hit?.distance ?: Float.MAX_VALUE
        val activeRemoteTarget = if (remoteHit != null && remoteHit.distance <= blockDist + 0.15f) {
          remoteHit.player
        } else null
        targetedRemotePlayer = activeRemoteTarget
        targetedBlockName = if (activeRemoteTarget != null) {
          "⚔ ${activeRemoteTarget.displayName} (${activeRemoteTarget.health.toInt()}/20 ❤)"
        } else {
          hit?.block?.displayName ?: ""
        }

        // Check if holding finger to break block
        val now = System.currentTimeMillis()
        val isHoldingLongEnough = isTouchHolding && (now - touchDownTime > 200L) && (touchDragAccumX * touchDragAccumX + touchDragAccumY * touchDragAccumY < 3600f)

        if (isHoldingLongEnough && hit != null) {
          val prevDigSwingSeq = player.swingSeq
          player.triggerSwing()
          if (versionProfile.enableMultiplayer && player.swingSeq != prevDigSwingSeq) {
            multiplayerGateway?.publishLocalPlayerState(player, forceImmediate = true)
          }
          digSoundTimer++
          if (digSoundTimer % 4 == 0) {
            AudioEngine.playDig(hit.block)
          }

          val heldItem = player.inventory.selectedItem?.item
          val speedMultiplier = heldItem?.toolTier?.miningSpeedMultiplier ?: 1.0f
          val hardness = if (hit.block.hardness <= 0.05f) 0.05f else hit.block.hardness
          val breakDelta = if (player.gameMode == GameMode.CREATIVE) 0.35f else (0.06f * speedMultiplier / hardness)

          breakProgressState += breakDelta
          renderer.breakProgress = breakProgressState

          if (breakProgressState >= 1.0f) {
            if (world.setBlock(hit.blockX, hit.blockY, hit.blockZ, BlockType.AIR)) {
              if (versionProfile.enableMultiplayer) {
                multiplayerGateway?.publishBlockEdit(hit.blockX, hit.blockY, hit.blockZ, BlockType.AIR)
              }
            }
            world.spawnBreakParticles(
              hit.blockX, hit.blockY, hit.blockZ, hit.block,
              fastDecay = versionProfile.fastBreakParticles
            )
            AudioEngine.playBreak(hit.block)

            if (player.gameMode == GameMode.SURVIVAL) {
              val drop = ItemType.fromBlock(hit.block)
              if (drop != null) {
                player.inventory.addItem(drop, 1)
                AudioEngine.playPop()
              }
            }

            breakProgressState = 0f
            renderer.breakProgress = 0f
            inventoryVersion++
          }
        } else if (!isTouchHolding) {
          breakProgressState = 0f
          renderer.breakProgress = 0f
          digSoundTimer = 0
        }
      } else {
        moveForward = 0f
        moveStrafe = 0f
        jumpHeld = false
        descendHeld = false
        player.limbSwingWeight = 0f
      }
      delay(16)
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    // 1. OpenGL 3D SurfaceView
    AndroidView(
      factory = { ctx ->
        GLSurfaceView(ctx).apply {
          setEGLContextClientVersion(2)
          setRenderer(renderer)
          renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
      },
      modifier = Modifier.fillMaxSize()
    )

    // 2. Direct Touch Interaction Area (Instant response, Look, Tap to Place, Hold to Break)
    Box(
      modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
          awaitPointerEventScope {
            while (true) {
              val down = awaitFirstDown(requireUnconsumed = false)
              touchDownTime = System.currentTimeMillis()
              isTouchHolding = true
              touchDragAccumX = 0f
              touchDragAccumY = 0f
              val pointerId = down.id

              while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                if (!change.pressed) {
                  val duration = System.currentTimeMillis() - touchDownTime
                  val distSq = touchDragAccumX * touchDragAccumX + touchDragAccumY * touchDragAccumY
                  if (duration < 280L && distSq < 1000f) {
                    handleTapInteraction()
                  }
                  isTouchHolding = false
                  breakProgressState = 0f
                  renderer.breakProgress = 0f
                  break
                }

                val drag = change.positionChange()
                touchDragAccumX += drag.x
                touchDragAccumY += drag.y

                val sens = 0.20f
                player.yaw = (player.yaw + drag.x * sens) % 360f
                player.pitch = (player.pitch + drag.y * sens).coerceIn(-89f, 89f)
                change.consume()
              }
            }
          }
        }
    )

    // 3. Center Crosshair (+)
    Text(
      text = "+",
      color = Color.White.copy(alpha = 0.85f),
      fontSize = 26.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.align(Alignment.Center)
    )

    // 4. Targeted Block Name Tooltip
    if (targetedBlockName.isNotEmpty()) {
      Box(
        modifier = Modifier
          .align(Alignment.Center)
          .offset(y = (-45).dp)
          .background(Color(0xB3181A20), RoundedCornerShape(4.dp))
          .border(1.dp, Color(0xFF383E4A), RoundedCornerShape(4.dp))
          .padding(horizontal = 10.dp, vertical = 4.dp)
      ) {
        Text(
          text = targetedBlockName,
          color = BedrockColors.TextYellow,
          fontSize = 12.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold
        )
      }
    }

    // 5. Top Bar: Version, Coordinates, Multiplayer Server Badge & Controls
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 8.dp, start = 12.dp, end = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Top
    ) {
      Column(
        modifier = Modifier
          .background(Color(0x88000000), RoundedCornerShape(2.dp))
          .padding(horizontal = 8.dp, vertical = 4.dp)
      ) {
        ClassicShadowText(
          text = "Nick Craft $versionTag",
          color = BedrockColors.TextWhite,
          fontSize = 13,
          fontWeight = FontWeight.Bold
        )
        ClassicShadowText(
          text = "x: ${player.x.toInt()}, y: ${player.y.toInt()}, z: ${player.z.toInt()}",
          color = BedrockColors.TextYellow,
          fontSize = 10,
          fontWeight = FontWeight.Normal
        )
        if (mpState.isActive) {
          ClassicShadowText(
            text = "Servidor ${mpState.serverCode} (${mpState.currentPlayers}/${mpState.maxPlayers})",
            color = BedrockColors.TextGreen,
            fontSize = 10,
            fontWeight = FontWeight.Bold
          )
        }
        if (showF3Debug) {
          ClassicShadowText(
            text = "fps: 60, yaw: ${player.yaw.toInt()}° pitch: ${player.pitch.toInt()}°",
            color = BedrockColors.TextGreen,
            fontSize = 9,
            fontWeight = FontWeight.Normal
          )
        }
      }

      // Top Center: Game Menu Button
      Box(
        modifier = Modifier
          .background(Color(0xAA333333), RoundedCornerShape(2.dp))
          .border(1.5.dp, Color(0xFF777777), RoundedCornerShape(2.dp))
          .clickable { showPauseMenu = true }
          .padding(horizontal = 14.dp, vertical = 6.dp)
          .testTag("bedrock_pause_button"),
        contentAlignment = Alignment.Center
      ) {
        ClassicShadowText(
          text = "Menu ($versionTag)",
          color = Color.White,
          fontSize = 11,
          fontWeight = FontWeight.Bold
        )
      }

      // Top Right: 3D Steve Camera & Debug
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
          modifier = Modifier
            .background(if (activeCameraMode != com.example.minecraft.model.CameraMode.FIRST_PERSON) Color(0xFF2E7D32) else Color(0xAA333333), RoundedCornerShape(2.dp))
            .border(1.5.dp, Color(0xFF777777), RoundedCornerShape(2.dp))
            .clickable { activeCameraMode = player.cycleCameraMode() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
          contentAlignment = Alignment.Center
        ) {
          ClassicShadowText(
            text = "Câmera: ${activeCameraMode.displayName}",
            color = Color.White,
            fontSize = 11,
            fontWeight = FontWeight.Bold
          )
        }

        Box(
          modifier = Modifier
            .background(if (showF3Debug) Color(0xFF1565C0) else Color(0xAA333333), RoundedCornerShape(2.dp))
            .border(1.5.dp, Color(0xFF777777), RoundedCornerShape(2.dp))
            .clickable { showF3Debug = !showF3Debug }
            .padding(horizontal = 10.dp, vertical = 6.dp),
          contentAlignment = Alignment.Center
        ) {
          ClassicShadowText(
            text = "F3",
            color = Color.White,
            fontSize = 11,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }

    // 6. Bottom-Left: Authentic Bedrock Touch D-Pad
    Box(
      modifier = Modifier
        .align(Alignment.BottomStart)
        .padding(start = 20.dp, bottom = 16.dp)
        .size(136.dp)
        .testTag("bedrock_dpad")
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(BedrockColors.DpadBg, RoundedCornerShape(14.dp))
          .border(1.5.dp, BedrockColors.DpadBorder, RoundedCornerShape(14.dp))
      )

      BedrockDpadKey(
        text = "▲",
        modifier = Modifier
          .align(Alignment.TopCenter)
          .size(42.dp),
        onPressed = { moveForward = 1f },
        onReleased = { moveForward = 0f }
      )

      BedrockDpadKey(
        text = "▼",
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .size(42.dp),
        onPressed = { moveForward = -1f },
        onReleased = { moveForward = 0f }
      )

      BedrockDpadKey(
        text = "◀",
        modifier = Modifier
          .align(Alignment.CenterStart)
          .size(42.dp),
        onPressed = { moveStrafe = -1f },
        onReleased = { moveStrafe = 0f }
      )

      BedrockDpadKey(
        text = "▶",
        modifier = Modifier
          .align(Alignment.CenterEnd)
          .size(42.dp),
        onPressed = { moveStrafe = 1f },
        onReleased = { moveStrafe = 0f }
      )

      Box(
        modifier = Modifier
          .align(Alignment.Center)
          .size(34.dp)
          .background(
            if (player.isSneaking) Color(0xFF4CAF50) else Color(0x992B303B),
            CircleShape
          )
          .border(1.dp, Color(0x99FFFFFF), CircleShape)
          .clickable { player.isSneaking = !player.isSneaking },
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "◆",
          color = Color.White,
          fontSize = 14.sp
        )
      }
    }

    // 7. Bottom-Right: Large Jump Button & Flight Toggle
    Box(
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(end = 22.dp, bottom = 18.dp)
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom
      ) {
        if (player.gameMode == GameMode.CREATIVE) {
          BedrockActionButton(
            text = if (player.isFlying) "Voar" else "Chão",
            color = if (player.isFlying) Color(0xCC00897B) else Color(0xCC455A64),
            onClick = {
              player.isFlying = !player.isFlying
              player.vy = 0f
            }
          )
        }

        BedrockHoldActionButton(
          text = "▲",
          color = Color(0xCC374151),
          isCircle = true,
          modifier = Modifier.testTag("jump_button"),
          onHoldChanged = { jumpHeld = it }
        )
      }
    }

    // 8. Bottom Center: Bedrock Hotbar (with custom ItemIcon per item) & Status Bars
    Column(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      if (player.gameMode == GameMode.SURVIVAL || mpState.isActive || playerHealthHud < player.maxHealth) {
        Row(
          modifier = Modifier
            .width(360.dp)
            .padding(bottom = 3.dp)
            .testTag("player_status_bars"),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            val hearts = (playerHealthHud / 2f).coerceIn(0f, 10f)
            for (i in 1..10) {
              Text(
                text = if (i <= hearts) "❤️" else if (i - 0.5f <= hearts) "💔" else "🖤",
                fontSize = 11.sp
              )
            }
          }

          if (player.gameMode == GameMode.SURVIVAL) {
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
              val drumsticks = (playerHungerHud / 2f).coerceIn(0f, 10f)
              for (i in 1..10) {
                Text(
                  text = if (i <= drumsticks) "🍖" else "🦴",
                  fontSize = 11.sp
                )
              }
            }
          }
        }
      }

      // Trigger recomposition when inventory changes
      val invTick = inventoryVersion

      Row(
        modifier = Modifier
          .background(BedrockColors.HudBg, RoundedCornerShape(6.dp))
          .border(1.5.dp, BedrockColors.CardBorder, RoundedCornerShape(6.dp))
          .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        for (i in 0 until 9) {
          val stack = if (invTick >= 0) player.inventory.slots[i] else null
          val isSelected = (i == player.inventory.selectedHotbarIndex)

          Box(
            modifier = Modifier
              .size(36.dp)
              .background(
                if (isSelected) Color(0x99444444) else BedrockColors.SlotBg,
                RoundedCornerShape(2.dp)
              )
              .border(
                if (isSelected) 2.5.dp else 1.5.dp,
                if (isSelected) Color.White else BedrockColors.SlotBorder,
                RoundedCornerShape(2.dp)
              )
              .clickable {
                player.inventory.selectedHotbarIndex = i
                AudioEngine.playPop()
              }
              .testTag("hotbar_slot_$i"),
            contentAlignment = Alignment.Center
          ) {
            if (stack != null) {
              if (versionProfile.useCustomPixelIcons) {
                ItemIcon(item = stack.item, size = 24.dp)
              } else {
                Text(text = stack.item.iconEmoji, fontSize = 18.sp)
              }
              if (stack.count > 1) {
                ClassicShadowText(
                  text = "${stack.count}",
                  color = Color.White,
                  shadowColor = Color.Black,
                  fontSize = 10,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 2.dp, bottom = 1.dp)
                )
              }
            }
          }
        }

        Box(
          modifier = Modifier
            .size(36.dp)
            .background(Color(0xFF333333), RoundedCornerShape(2.dp))
            .border(1.5.dp, Color(0xFF777777), RoundedCornerShape(2.dp))
            .clickable {
              hasCraftingTableOpen = false
              showInventory = true
            }
            .testTag("open_inventory_button"),
          contentAlignment = Alignment.Center
        ) {
          ClassicShadowText(
            text = "B",
            color = BedrockColors.TextYellow,
            fontSize = 15,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }

    // 9. Dialogs: Bedrock Inventory & Pause Menu (with live 5-player Server Toggle)
    if (showInventory) {
      InventoryDialog(
        player = player,
        hasCraftingTable = hasCraftingTableOpen,
        versionTag = versionTag,
        useCustomPixelIcons = versionProfile.useCustomPixelIcons,
        onDismiss = {
          inventoryVersion++
          showInventory = false
        }
      )
    }

    if (showPauseMenu) {
      val needsGoogleSignIn = multiplayerGateway != null &&
          versionProfile.enableMultiplayer &&
          multiplayerGateway.requiresSignIn &&
          mpUserUid == null

      PauseMenuDialog(
        versionTag = versionTag,
        sessionState = mpState,
        canHostOnline = multiplayerGateway != null && versionProfile.enableMultiplayer,
        needsGoogleSignInForServer = needsGoogleSignIn,
        onGoogleSignInClick = if (multiplayerGateway != null) {
          {
            scope.launch {
              multiplayerGateway.signInWithGoogle(context)
            }
          }
        } else null,
        onToggleServer = if (multiplayerGateway != null) {
          {
            scope.launch {
              if (mpState.isActive) {
                multiplayerGateway.closeOrLeaveServer()
              } else {
                multiplayerGateway.hostWorldAsServer(worldInfo, world, player)
              }
            }
          }
        } else null,
        onResume = { showPauseMenu = false },
        onSaveAndQuit = {
          if (!isRemoteClient) {
            storage.saveWorld(worldInfo, world, player)
          }
          showPauseMenu = false
          scope.launch {
            multiplayerGateway?.closeOrLeaveServer()
            onExitToTitle()
          }
        }
      )
    }

    // 10. Red Hurt Flash Overlay
    if (damageFlashAlpha > 0.01f && !isPlayerDead) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Red.copy(alpha = damageFlashAlpha))
      )
    }

    // 11. Death & Respawn Overlay when health reaches 0
    if (isPlayerDead) {
      DeathRespawnOverlay(
        attackerName = lastAttackerName,
        spawnX = liveConfig.spawnX,
        spawnZ = liveConfig.spawnZ,
        onRespawn = handleRespawn,
        onExitToTitle = {
          scope.launch {
            multiplayerGateway?.closeOrLeaveServer()
            onExitToTitle()
          }
        }
      )
    }
  }
}

@Composable
fun BedrockDpadKey(
  text: String,
  modifier: Modifier = Modifier,
  onPressed: () -> Unit,
  onReleased: () -> Unit
) {
  var isPressed by remember { mutableStateOf(false) }

  Box(
    modifier = modifier
      .background(if (isPressed) BedrockColors.DpadBtnPressed else BedrockColors.DpadBtn, RoundedCornerShape(8.dp))
      .pointerInput(Unit) {
        awaitPointerEventScope {
          while (true) {
            awaitFirstDown(requireUnconsumed = false)
            isPressed = true
            onPressed()
            waitForUpOrCancellation()
            isPressed = false
            onReleased()
          }
        }
      },
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = text,
      color = Color.White,
      fontSize = 18.sp,
      fontWeight = FontWeight.Bold
    )
  }
}

@Composable
fun BedrockActionButton(
  text: String,
  color: Color,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  Box(
    modifier = modifier
      .background(color, RoundedCornerShape(6.dp))
      .border(1.5.dp, Color(0xAAFFFFFF), RoundedCornerShape(6.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 9.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = text,
      color = Color.White,
      fontSize = 13.sp,
      fontWeight = FontWeight.Bold,
      fontFamily = FontFamily.Monospace
    )
  }
}

@Composable
fun BedrockHoldActionButton(
  text: String,
  color: Color,
  isCircle: Boolean = false,
  modifier: Modifier = Modifier,
  onHoldChanged: (Boolean) -> Unit
) {
  var isHeld by remember { mutableStateOf(false) }

  Box(
    modifier = modifier
      .size(if (isCircle) 56.dp else 44.dp)
      .background(
        if (isHeld) Color(0xFF4CAF50) else color,
        if (isCircle) CircleShape else RoundedCornerShape(6.dp)
      )
      .border(
        1.5.dp,
        Color(0xAAFFFFFF),
        if (isCircle) CircleShape else RoundedCornerShape(6.dp)
      )
      .pointerInput(Unit) {
        awaitPointerEventScope {
          while (true) {
            awaitFirstDown(requireUnconsumed = false)
            isHeld = true
            onHoldChanged(true)
            waitForUpOrCancellation()
            isHeld = false
            onHoldChanged(false)
          }
        }
      },
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = text,
      color = Color.White,
      fontSize = if (isCircle) 22.sp else 13.sp,
      fontWeight = FontWeight.Bold,
      fontFamily = FontFamily.Monospace
    )
  }
}
