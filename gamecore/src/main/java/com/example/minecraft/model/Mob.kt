package com.example.minecraft.model

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class MobType(
  val displayName: String,
  val maxHealth: Float,
  val isHostile: Boolean,
  val dropItem: ItemType,
  val dropCount: Int
) {
  PIG("Porco", 10f, false, ItemType.RAW_PORKCHOP, 2),
  ZOMBIE("Zumbi", 20f, true, ItemType.ROTTEN_FLESH, 1)
}

class Mob(
  val type: MobType,
  var x: Float,
  var y: Float,
  var z: Float
) {
  var health: Float = type.maxHealth
  var yaw: Float = Random.nextFloat() * 360f
  var pitch: Float = 0f
  var vx: Float = 0f
  var vy: Float = 0f
  var vz: Float = 0f
  var isOnGround: Boolean = true
  var legSwing: Float = 0f
  var isDead: Boolean = false
  var hurtTime: Int = 0

  private var aiTimer: Int = Random.nextInt(20, 80)
  private var isWalking: Boolean = false

  fun update(world: VoxelWorld, playerX: Float, playerY: Float, playerZ: Float) {
    if (isDead) return
    if (hurtTime > 0) hurtTime--

    // Gravity
    vy -= 0.015f
    if (vy < -0.4f) vy = -0.4f

    aiTimer--
    if (aiTimer <= 0) {
      aiTimer = Random.nextInt(40, 120)
      if (type.isHostile) {
        // Zombie tracks player
        val dx = playerX - x
        val dz = playerZ - z
        val distSq = dx * dx + dz * dz
        if (distSq < 16f * 16f) {
          yaw = Math.toDegrees(kotlin.math.atan2(dz.toDouble(), dx.toDouble())).toFloat() - 90f
          isWalking = true
        } else {
          isWalking = Random.nextBoolean()
          yaw = Random.nextFloat() * 360f
        }
      } else {
        // Peaceful wander
        isWalking = Random.nextFloat() < 0.6f
        yaw = Random.nextFloat() * 360f
      }
    }

    if (isWalking) {
      val speed = if (type.isHostile) 0.045f else 0.03f
      val rad = Math.toRadians((yaw + 90f).toDouble())
      vx = (cos(rad) * speed).toFloat()
      vz = (sin(rad) * speed).toFloat()
      legSwing += 0.2f
    } else {
      vx *= 0.5f
      vz *= 0.5f
    }

    // Step / jump over 1 block if stuck
    val nextX = x + vx
    val nextZ = z + vz
    val blockAhead = world.getBlock(nextX.toInt(), y.toInt(), nextZ.toInt())
    val blockAbove = world.getBlock(nextX.toInt(), (y + 1).toInt(), nextZ.toInt())
    if (blockAhead.isSolid && !blockAbove.isSolid && isOnGround) {
      vy = 0.22f
      isOnGround = false
    }

    // Move Y
    val newY = y + vy
    val blockBelow = world.getBlock(x.toInt(), (newY).toInt(), z.toInt())
    if (blockBelow.isSolid) {
      y = (newY.toInt() + 1).toFloat()
      vy = 0f
      isOnGround = true
    } else {
      y = newY
      isOnGround = false
    }

    // Move XZ if not blocked by world
    val testBlock = world.getBlock((x + vx).toInt(), y.toInt(), (z + vz).toInt())
    if (!testBlock.isSolid) {
      x += vx
      z += vz
    }

    // Physical collision with player: Mob cannot phase through Steve
    val pDx = x - playerX
    val pDz = z - playerZ
    val pDy = abs(y - playerY)
    if (pDy < 1.6f) {
      val pDist = sqrt(pDx * pDx + pDz * pDz)
      val minDist = 0.65f
      if (pDist in 0.001f..minDist) {
        val push = (minDist - pDist) * 0.7f
        x += (pDx / pDist) * push
        z += (pDz / pDist) * push
      }
    }
  }

  fun takeDamage(amount: Float): Boolean {
    hurtTime = 10
    health -= amount
    if (health <= 0f) {
      isDead = true
      return true
    }
    return false
  }
}
