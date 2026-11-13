package com.example.minecraft.model

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

enum class GameMode(val displayName: String) {
  SURVIVAL("Sobrevivência"),
  CREATIVE("Criativo")
}

enum class CameraMode(val displayName: String) {
  FIRST_PERSON("1ª Pessoa"),
  THIRD_PERSON_BACK("3ª Pessoa (Costas)"),
  THIRD_PERSON_FRONT("2ª Pessoa (Frente)")
}

class Player(
  var x: Float = 24.5f,
  var y: Float = 20.0f,
  var z: Float = 24.5f
) {
  var yaw: Float = 0f
  var pitch: Float = 0f

  var vx: Float = 0f
  var vy: Float = 0f
  var vz: Float = 0f

  var isOnGround: Boolean = false
  var isFlying: Boolean = false
  var isSneaking: Boolean = false

  var gameMode: GameMode = GameMode.SURVIVAL
  var health: Float = 20f
  var maxHealth: Float = 20f
  var hunger: Float = 20f
  var oxygen: Float = 20f
  var hurtTime: Int = 0

  var swingProgress: Float = 0f
  var walkBobbing: Float = 0f
  var limbSwingWeight: Float = 0f
  var cameraMode: CameraMode = CameraMode.FIRST_PERSON
  var isThirdPerson: Boolean
    get() = (cameraMode != CameraMode.FIRST_PERSON)
    set(value) {
      cameraMode = if (value) CameraMode.THIRD_PERSON_BACK else CameraMode.FIRST_PERSON
    }

  fun cycleCameraMode(): CameraMode {
    cameraMode = when (cameraMode) {
      CameraMode.FIRST_PERSON -> CameraMode.THIRD_PERSON_BACK
      CameraMode.THIRD_PERSON_BACK -> CameraMode.THIRD_PERSON_FRONT
      CameraMode.THIRD_PERSON_FRONT -> CameraMode.FIRST_PERSON
    }
    return cameraMode
  }

  val eyeHeight: Float = 1.62f
  val eyeY: Float get() = y + eyeHeight
  val halfW: Float = 0.28f
  val height: Float = 1.8f

  val inventory = Inventory()

  fun triggerSwing() {
    if (swingProgress <= 0f) {
      swingProgress = 1.0f
    }
  }

  fun takeDamage(amount: Float) {
    if (gameMode == GameMode.CREATIVE) return
    hurtTime = 12
    health = (health - amount).coerceAtLeast(0f)
  }

  fun heal(amount: Float) {
    health = (health + amount).coerceAtMost(maxHealth)
  }

  fun eat(item: ItemType): Boolean {
    if (hunger < 20f || health < maxHealth) {
      hunger = (hunger + item.foodRestoration).coerceAtMost(20f)
      heal(item.foodRestoration * 0.5f)
      return true
    }
    return false
  }

  fun update(world: VoxelWorld, moveForward: Float, moveStrafe: Float, jumpHeld: Boolean, descendHeld: Boolean) {
    if (hurtTime > 0) hurtTime--
    if (swingProgress > 0f) {
      swingProgress = (swingProgress - 0.12f).coerceAtLeast(0f)
    }

    // Direction vectors based on yaw
    val radYaw = Math.toRadians(yaw.toDouble())
    val forwardX = -sin(radYaw).toFloat()
    val forwardZ = cos(radYaw).toFloat()
    val rightX = cos(radYaw).toFloat()
    val rightZ = sin(radYaw).toFloat()

    val baseSpeed = when {
      isFlying -> 0.18f
      isSneaking -> 0.045f
      else -> 0.10f
    }

    val wishX = (forwardX * moveForward + rightX * moveStrafe) * baseSpeed
    val wishZ = (forwardZ * moveForward + rightZ * moveStrafe) * baseSpeed

    val prevX = x
    val prevZ = z

    val isMovingInput = (moveForward != 0f || moveStrafe != 0f)
    val isUnderwater = world.getBlock(x.toInt(), eyeY.toInt(), z.toInt()) == BlockType.WATER

    if (isFlying) {
      // Flight movement (Creative)
      vx = wishX
      vz = wishZ
      vy = when {
        jumpHeld -> 0.14f
        descendHeld -> -0.14f
        else -> 0f
      }
      x = (x + vx).coerceIn(0.5f, world.width - 0.5f)
      y += vy
      z = (z + vz).coerceIn(0.5f, world.length - 0.5f)
      updateLimbAnimation(isMovingInput, prevX, prevZ, moveForward, airborne = true)
      return
    }

    // Ground and water physics
    if (isUnderwater) {
      oxygen = (oxygen - 0.03f).coerceAtLeast(0f)
      if (oxygen <= 0f && hurtTime <= 0) takeDamage(2f)
      vy = if (jumpHeld) 0.07f else -0.02f
      vx = wishX * 0.65f
      vz = wishZ * 0.65f
    } else {
      oxygen = (oxygen + 0.1f).coerceAtMost(20f)
      // Gravity
      vy -= 0.016f
      if (vy < -0.55f) vy = -0.55f

      if (isOnGround && jumpHeld) {
        vy = 0.26f // jump impulse
        isOnGround = false
      }

      // Smooth horizontal acceleration and friction
      vx = vx * 0.4f + wishX * 0.6f
      vz = vz * 0.4f + wishZ * 0.6f
    }

    // Smooth movement with step-up & collision
    moveWithCollision(world)
    updateLimbAnimation(isMovingInput, prevX, prevZ, moveForward, airborne = !isOnGround)
  }

  private fun updateLimbAnimation(
    isMovingInput: Boolean,
    prevX: Float,
    prevZ: Float,
    moveForward: Float,
    airborne: Boolean
  ) {
    val dx = x - prevX
    val dz = z - prevZ
    val movedHorizontally = (dx * dx + dz * dz) > 0.00008f

    if (isMovingInput && movedHorizontally && !airborne) {
      limbSwingWeight = (limbSwingWeight + 0.28f).coerceAtMost(1.0f)
      walkBobbing += if (moveForward < 0f) -0.22f else 0.22f
    } else {
      limbSwingWeight = (limbSwingWeight - 0.28f).coerceAtLeast(0.0f)
      if (limbSwingWeight <= 0f) {
        walkBobbing = 0f
      }
    }
  }

  private fun moveWithCollision(world: VoxelWorld) {
    // 1. Move Y with gravity
    val targetY = y + vy
    if (!checkCollision(world, x, targetY, z, halfW, height)) {
      y = targetY
      isOnGround = false
    } else {
      if (vy < 0f) {
        isOnGround = true
        y = floor(targetY).toInt() + 1.0f
      }
      vy = 0f
    }

    // 2. Move X with step-up
    if (vx != 0f) {
      val targetX = x + vx
      if (!checkCollision(world, targetX, y, z, halfW, height)) {
        x = targetX
      } else if (isOnGround) {
        // Try stepping up 1 block
        val stepY = (floor(y).toInt() + 1.05f)
        if (!checkCollision(world, targetX, stepY, z, halfW, height) && !checkCollision(world, x, stepY, z, halfW, height)) {
          y = stepY
          x = targetX
        } else {
          vx = 0f
        }
      } else {
        vx = 0f
      }
    }

    // 3. Move Z with step-up
    if (vz != 0f) {
      val targetZ = z + vz
      if (!checkCollision(world, x, y, targetZ, halfW, height)) {
        z = targetZ
      } else if (isOnGround) {
        // Try stepping up 1 block
        val stepY = (floor(y).toInt() + 1.05f)
        if (!checkCollision(world, x, stepY, targetZ, halfW, height) && !checkCollision(world, x, stepY, z, halfW, height)) {
          y = stepY
          z = targetZ
        } else {
          vz = 0f
        }
      } else {
        vz = 0f
      }
    }

    // Bedrock floor barrier at y=1 & keep inside world bounds
    if (y < 1.0f) {
      y = 1.0f
      vy = 0f
      isOnGround = true
    }
    x = x.coerceIn(0.5f, world.width - 0.5f)
    z = z.coerceIn(0.5f, world.length - 0.5f)

    // Mob physical solid collision: Steve cannot be passed through by mobs
    for (mob in world.mobs) {
      if (mob.isDead) continue
      val dx = x - mob.x
      val dz = z - mob.z
      val dy = abs(y - mob.y)
      if (dy < 1.6f) {
        val dist = sqrt(dx * dx + dz * dz)
        val minDist = 0.65f
        if (dist in 0.001f..minDist) {
          val push = (minDist - dist) * 0.5f
          val nx = dx / dist
          val nz = dz / dist
          x += nx * push
          z += nz * push
          mob.x -= nx * push
          mob.z -= nz * push
        }
      }
    }
  }

  private fun checkCollision(world: VoxelWorld, cx: Float, cy: Float, cz: Float, hw: Float, h: Float): Boolean {
    val minX = floor(cx - hw).toInt()
    val maxX = floor(cx + hw).toInt()
    val minY = floor(cy).toInt()
    val maxY = floor(cy + h - 0.05f).toInt()
    val minZ = floor(cz - hw).toInt()
    val maxZ = floor(cz + hw).toInt()

    for (bx in minX..maxX) {
      for (by in minY..maxY) {
        for (bz in minZ..maxZ) {
          val block = world.getBlock(bx, by, bz)
          if (block.isSolid) {
            return true
          }
        }
      }
    }
    return false
  }

  fun getLookDirection(): Triple<Float, Float, Float> {
    val radYaw = Math.toRadians(yaw.toDouble())
    val radPitch = Math.toRadians(pitch.toDouble())

    val dx = (-sin(radYaw) * cos(radPitch)).toFloat()
    val dy = (-sin(radPitch)).toFloat()
    val dz = (cos(radYaw) * cos(radPitch)).toFloat()

    return Triple(dx, dy, dz)
  }
}
