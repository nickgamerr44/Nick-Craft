package com.example.minecraft.ui

import android.annotation.SuppressLint
import android.opengl.GLSurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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
import com.example.minecraft.data.WorldInfo
import com.example.minecraft.data.WorldStorage
import com.example.minecraft.engine.AudioEngine
import com.example.minecraft.engine.VoxelRenderer
import com.example.minecraft.model.BlockType
import com.example.minecraft.model.GameMode
import com.example.minecraft.model.ItemType
import com.example.minecraft.model.Player
import com.example.minecraft.model.VoxelWorld
import com.example.minecraft.model.WorldGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.sqrt

@SuppressLint("ClickableViewAccessibility")
@Composable
fun GameScreen(
  worldInfo: WorldInfo,
  storage: WorldStorage,
  onExitToTitle: () -> Unit,
  versionTag: String = "0.00"
) {
  val context = LocalContext.current

  // 1. World and Player instances
  val world = remember { VoxelWorld() }
  val player = remember { Player() }

  // Initialize or load world
  LaunchedEffect(worldInfo.id) {
    player.gameMode = worldInfo.gameMode
    val loaded = storage.loadWorld(worldInfo.id, world, player)
    if (!loaded) {
      WorldGenerator.generate(world, worldInfo.seed)
      player.inventory.setupStartingKit(worldInfo.gameMode == GameMode.CREATIVE)
      for (y in 28 downTo 5) {
        if (world.getBlock(24, y, 24).isSolid) {
          player.y = (y + 1).toFloat()
          break
        }
      }
    }
  }

  // 2. Renderer instance
  val renderer = remember { VoxelRenderer(context, world, player) }

  // 3. UI States
  var showPauseMenu by remember { mutableStateOf(false) }
  var showInventory by remember { mutableStateOf(false) }
  var hasCraftingTableOpen by remember { mutableStateOf(false) }
  var showF3Debug by remember { mutableStateOf(false) }
  var activeCameraMode by remember { mutableStateOf(player.cameraMode) }

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

  // Targeted block and break progress
  var targetedBlockName by remember { mutableStateOf("") }
  var breakProgressState by remember { mutableFloatStateOf(0f) }
  var inventoryVersion by remember { mutableIntStateOf(0) }

  // Paper doll walking bobbing
  val isMoving = (moveForward != 0f || moveStrafe != 0f)
  val infiniteTransition = rememberInfiniteTransition(label = "paperdoll")
  val paperDollTilt by infiniteTransition.animateFloat(
    initialValue = -8f,
    targetValue = 8f,
    animationSpec = infiniteRepeatable(
      animation = tween(280),
      repeatMode = RepeatMode.Reverse
    ),
    label = "tilt"
  )

  BackHandler {
    when {
      showInventory -> showInventory = false
      showPauseMenu -> showPauseMenu = false
      else -> showPauseMenu = true
    }
  }

  // Helper to place block or interact on tap
  val handleTapInteraction: () -> Unit = {
    player.triggerSwing()

    // 1. Check if aiming at a mob to attack
    var hitMob = false
    for (mob in world.mobs) {
      val dist = sqrt((mob.x - player.x) * (mob.x - player.x) + (mob.y - player.y) * (mob.y - player.y) + (mob.z - player.z) * (mob.z - player.z))
      if (dist < 3.2f && !mob.isDead) {
        val heldTier = player.inventory.selectedItem?.item?.toolTier ?: com.example.minecraft.model.ToolTier.NONE
        val dmg = heldTier.attackDamage * 2.5f + 2f
        val killed = mob.takeDamage(dmg)
        AudioEngine.playDing()
        if (killed) {
          player.inventory.addItem(mob.type.dropItem, mob.type.dropCount)
          AudioEngine.playPop()
          inventoryVersion++
        }
        hitMob = true
        break
      }
    }

    // 2. If no mob hit, interact with targeted block
    if (!hitMob) {
      val hit = renderer.currentTargetHit
      if (hit != null) {
        if (hit.block == BlockType.CRAFTING_TABLE) {
          hasCraftingTableOpen = true
          showInventory = true
        } else if (hit.block == BlockType.TNT) {
          world.explode(hit.blockX, hit.blockY, hit.blockZ, 3.5f)
          AudioEngine.playExplosion()
        } else {
          // Place held block on tapped ground / block face
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
              world.setBlock(px, py, pz, blockToPlace)
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
  LaunchedEffect(Unit) {
    var digSoundTimer = 0
    var stepSoundTimer = 0

    while (isActive) {
      if (!showPauseMenu && !showInventory) {
        player.update(world, moveForward, moveStrafe, jumpHeld, descendHeld)

        if (player.isOnGround && isMoving) {
          stepSoundTimer++
          if (stepSoundTimer > 18) {
            AudioEngine.playStep()
            stepSoundTimer = 0
          }
        }

        // Update mobs
        for (mob in world.mobs) {
          mob.update(world, player.x, player.y, player.z)
          if (mob.type.isHostile && !mob.isDead && player.gameMode == GameMode.SURVIVAL) {
            val dist = sqrt((mob.x - player.x) * (mob.x - player.x) + (mob.y - player.y) * (mob.y - player.y) + (mob.z - player.z) * (mob.z - player.z))
            if (dist < 1.2f && player.hurtTime <= 0) {
              player.takeDamage(3f)
              AudioEngine.playHurt()
            }
          }
        }

        // Raycasting
        val (lookDx, lookDy, lookDz) = player.getLookDirection()
        val hit = world.raycast(player.x, player.eyeY, player.z, lookDx, lookDy, lookDz, 5.5f)
        renderer.currentTargetHit = hit
        targetedBlockName = hit?.block?.displayName ?: ""

        // Check if holding finger to break block
        val now = System.currentTimeMillis()
        val isHoldingLongEnough = isTouchHolding && (now - touchDownTime > 200L) && (touchDragAccumX * touchDragAccumX + touchDragAccumY * touchDragAccumY < 3600f)

        if (isHoldingLongEnough && hit != null) {
          player.triggerSwing()
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
            world.setBlock(hit.blockX, hit.blockY, hit.blockZ, BlockType.AIR)
            world.spawnBreakParticles(hit.blockX, hit.blockY, hit.blockZ, hit.block)
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
                  // Finger lifted!
                  val duration = System.currentTimeMillis() - touchDownTime
                  val distSq = touchDragAccumX * touchDragAccumX + touchDragAccumY * touchDragAccumY
                  // If it was a quick tap (< 280ms and small movement) -> Place block or interact!
                  if (duration < 280L && distSq < 1000f) {
                    handleTapInteraction()
                  }
                  isTouchHolding = false
                  breakProgressState = 0f
                  renderer.breakProgress = 0f
                  break
                }

                // Drag movement
                val drag = change.positionChange()
                touchDragAccumX += drag.x
                touchDragAccumY += drag.y

                // Smooth look rotation
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

    // 5. Top Bar: Minecraft 0.0.0 Classic Header & Controls
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 8.dp, start = 12.dp, end = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Top
    ) {
      // Top Left: Minecraft 0.0.0 Classic Header & Debug Coordinates
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

    // 6. Bottom-Left: Authentic Bedrock Touch D-Pad (Instantaneous response)
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

      // Forward (▲)
      BedrockDpadKey(
        text = "▲",
        modifier = Modifier
          .align(Alignment.TopCenter)
          .size(42.dp),
        onPressed = { moveForward = 1f },
        onReleased = { moveForward = 0f }
      )

      // Backward (▼)
      BedrockDpadKey(
        text = "▼",
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .size(42.dp),
        onPressed = { moveForward = -1f },
        onReleased = { moveForward = 0f }
      )

      // Left Strafe (◀)
      BedrockDpadKey(
        text = "◀",
        modifier = Modifier
          .align(Alignment.CenterStart)
          .size(42.dp),
        onPressed = { moveStrafe = -1f },
        onReleased = { moveStrafe = 0f }
      )

      // Right Strafe (▶)
      BedrockDpadKey(
        text = "▶",
        modifier = Modifier
          .align(Alignment.CenterEnd)
          .size(42.dp),
        onPressed = { moveStrafe = 1f },
        onReleased = { moveStrafe = 0f }
      )

      // Center: Bedrock Sneak (◆)
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
        // Flight toggle in Creative
        if (player.gameMode == GameMode.CREATIVE) {
          BedrockActionButton(
            text = if (player.isFlying) "🪶 Voar" else "🚶 Chão",
            color = if (player.isFlying) Color(0xCC00897B) else Color(0xCC455A64),
            onClick = {
              player.isFlying = !player.isFlying
              player.vy = 0f
            }
          )
        }

        // Circular Jump Button (Instant press)
        BedrockHoldActionButton(
          text = "▲",
          color = Color(0xCC374151),
          isCircle = true,
          modifier = Modifier.testTag("jump_button"),
          onHoldChanged = { jumpHeld = it }
        )
      }
    }

    // 8. Bottom Center: Bedrock Hotbar & Status Bars (Hearts, Hunger)
    Column(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      if (player.gameMode == GameMode.SURVIVAL) {
        Row(
          modifier = Modifier
            .width(360.dp)
            .padding(bottom = 3.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // 10 Hearts
          Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            val hearts = (player.health / 2f).coerceIn(0f, 10f)
            for (i in 1..10) {
              Text(
                text = if (i <= hearts) "❤️" else if (i - 0.5f <= hearts) "💔" else "🖤",
                fontSize = 11.sp
              )
            }
          }

          // 10 Drumsticks
          Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            val drumsticks = (player.hunger / 2f).coerceIn(0f, 10f)
            for (i in 1..10) {
              Text(
                text = if (i <= drumsticks) "🍖" else "🦴",
                fontSize = 11.sp
              )
            }
          }
        }
      }

      // 9-Slot Bedrock Hotbar + Inventory Button [...]
      Row(
        modifier = Modifier
          .background(BedrockColors.HudBg, RoundedCornerShape(6.dp))
          .border(1.5.dp, BedrockColors.CardBorder, RoundedCornerShape(6.dp))
          .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        for (i in 0 until 9) {
          val stack = player.inventory.slots[i]
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
              Text(
                text = stack.item.iconEmoji,
                fontSize = 18.sp
              )
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

        // Classic Minecraft 0.0.0 "B" (Block Selector / Inventory) Button
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

    // 9. Dialogs: Bedrock Inventory & Minimalist Pause Menu
    if (showInventory) {
      InventoryDialog(
        player = player,
        hasCraftingTable = hasCraftingTableOpen,
        onDismiss = { showInventory = false }
      )
    }

    if (showPauseMenu) {
      PauseMenuDialog(
        versionTag = versionTag,
        onResume = { showPauseMenu = false },
        onSaveAndQuit = {
          storage.saveWorld(worldInfo, world, player)
          showPauseMenu = false
          onExitToTitle()
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
