package com.example.minecraft.model

import com.example.minecraft.data.LiveGameConfig
import com.example.minecraft.multiplayer.RemotePlayerState
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class RemotePlayerHit(
  val player: RemotePlayerState,
  val distance: Float
)

object PlayerCombat {
  const val MELEE_RANGE_BLOCKS = 4.5f
  private const val PLAYER_HALF_WIDTH = 0.32f
  private const val PLAYER_HEIGHT = 1.80f

  /**
   * Slab ray-vs-AABB intersection against living remote multiplayer players.
   * Returns the closest hit remote player within [maxDistance].
   */
  fun raycastRemotePlayer(
    originX: Float,
    originY: Float,
    originZ: Float,
    dirX: Float,
    dirY: Float,
    dirZ: Float,
    players: List<RemotePlayerState>,
    maxDistance: Float = MELEE_RANGE_BLOCKS,
    hitPadding: Float = 0.22f
  ): RemotePlayerHit? {
    var bestHit: RemotePlayerHit? = null
    var bestDist = maxDistance

    for (rp in players) {
      if (rp.isDead || rp.health <= 0f) continue

      val hw = PLAYER_HALF_WIDTH + hitPadding
      val minX = rp.x - hw
      val maxX = rp.x + hw
      val minY = rp.y - 0.05f
      val maxY = rp.y + PLAYER_HEIGHT + hitPadding * 0.5f
      val minZ = rp.z - hw
      val maxZ = rp.z + hw

      val hitDist = intersectRayAabb(
        originX, originY, originZ,
        dirX, dirY, dirZ,
        minX, minY, minZ,
        maxX, maxY, maxZ,
        bestDist
      )
      if (hitDist != null && hitDist < bestDist) {
        bestDist = hitDist
        bestHit = RemotePlayerHit(rp, hitDist)
      }
    }
    return bestHit
  }

  fun calculatePvpDamage(heldItem: ItemType?, config: LiveGameConfig = LiveGameConfig.DEFAULT): Float {
    val base = if (heldItem == null || heldItem.toolTier == ToolTier.NONE) {
      config.pvpBaseFistDamage
    } else {
      heldItem.toolTier.attackDamage
    }
    return (base * config.pvpDamageMultiplier).coerceIn(0.5f, 20.0f)
  }

  fun findSurfaceSpawnY(world: VoxelWorld, spawnX: Float, spawnZ: Float): Float {
    val bx = floor(spawnX.toDouble()).toInt().coerceIn(0, world.width - 1)
    val bz = floor(spawnZ.toDouble()).toInt().coerceIn(0, world.length - 1)
    for (y in (world.height - 2) downTo 1) {
      if (world.getBlock(bx, y, bz).isSolid) {
        return (y + 1).toFloat()
      }
    }
    return 14.0f
  }

  private fun intersectRayAabb(
    ox: Float, oy: Float, oz: Float,
    dx: Float, dy: Float, dz: Float,
    minX: Float, minY: Float, minZ: Float,
    maxX: Float, maxY: Float, maxZ: Float,
    maxDist: Float
  ): Float? {
    var tMin = 0.0f
    var tMax = maxDist

    val axes = arrayOf(
      Triple(ox, dx, minX to maxX),
      Triple(oy, dy, minY to maxY),
      Triple(oz, dz, minZ to maxZ)
    )

    for ((origin, dir, bounds) in axes) {
      val (bMin, bMax) = bounds
      if (kotlin.math.abs(dir) < 1e-6f) {
        if (origin < bMin || origin > bMax) return null
      } else {
        val invD = 1.0f / dir
        var t1 = (bMin - origin) * invD
        var t2 = (bMax - origin) * invD
        if (t1 > t2) {
          val tmp = t1
          t1 = t2
          t2 = tmp
        }
        tMin = max(tMin, t1)
        tMax = min(tMax, t2)
        if (tMin > tMax) return null
      }
    }

    return if (tMin >= 0f && tMin <= maxDist) tMin else null
  }
}
