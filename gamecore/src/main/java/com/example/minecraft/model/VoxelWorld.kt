package com.example.minecraft.model

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class RaycastHit(
  val blockX: Int,
  val blockY: Int,
  val blockZ: Int,
  val block: BlockType,
  val normalX: Int,
  val normalY: Int,
  val normalZ: Int,
  val distance: Float
)

class VoxelWorld(
  val width: Int = 48,
  val height: Int = 32,
  val length: Int = 48
) {
  val blocks = ByteArray(width * height * length)

  // Track modification for saving & fast mesh dirty flags
  var isDirty: Boolean = true
  val mobs = mutableListOf<Mob>()
  val particles = mutableListOf<Particle>()

  fun getIndex(x: Int, y: Int, z: Int): Int {
    if (x !in 0 until width || y !in 0 until height || z !in 0 until length) return -1
    return x + z * width + y * (width * length)
  }

  fun getBlock(x: Int, y: Int, z: Int): BlockType {
    val idx = getIndex(x, y, z)
    if (idx < 0) return BlockType.AIR
    return BlockType.fromId(blocks[idx])
  }

  fun setBlock(x: Int, y: Int, z: Int, type: BlockType): Boolean {
    val idx = getIndex(x, y, z)
    if (idx < 0) return false
    blocks[idx] = type.id
    isDirty = true
    return true
  }

  fun raycast(
    originX: Float,
    originY: Float,
    originZ: Float,
    dirX: Float,
    dirY: Float,
    dirZ: Float,
    maxDistance: Float = 6.0f
  ): RaycastHit? {
    // 3D DDA (Digital Differential Analyzer) voxel traversal
    var x = floor(originX).toInt()
    var y = floor(originY).toInt()
    var z = floor(originZ).toInt()

    val stepX = if (dirX > 0) 1 else if (dirX < 0) -1 else 0
    val stepY = if (dirY > 0) 1 else if (dirY < 0) -1 else 0
    val stepZ = if (dirZ > 0) 1 else if (dirZ < 0) -1 else 0

    val tDeltaX = if (stepX != 0) abs(1.0f / dirX) else Float.MAX_VALUE
    val tDeltaY = if (stepY != 0) abs(1.0f / dirY) else Float.MAX_VALUE
    val tDeltaZ = if (stepZ != 0) abs(1.0f / dirZ) else Float.MAX_VALUE

    var tMaxX = if (stepX > 0) ((x + 1) - originX) * tDeltaX else if (stepX < 0) (originX - x) * tDeltaX else Float.MAX_VALUE
    var tMaxY = if (stepY > 0) ((y + 1) - originY) * tDeltaY else if (stepY < 0) (originY - y) * tDeltaY else Float.MAX_VALUE
    var tMaxZ = if (stepZ > 0) ((z + 1) - originZ) * tDeltaZ else if (stepZ < 0) (originZ - z) * tDeltaZ else Float.MAX_VALUE

    var normalX = 0
    var normalY = 0
    var normalZ = 0
    var distance = 0f

    while (distance <= maxDistance) {
      val block = getBlock(x, y, z)
      if (block != BlockType.AIR && block != BlockType.WATER) {
        return RaycastHit(x, y, z, block, normalX, normalY, normalZ, distance)
      }

      if (tMaxX < tMaxY) {
        if (tMaxX < tMaxZ) {
          x += stepX
          distance = tMaxX
          tMaxX += tDeltaX
          normalX = -stepX
          normalY = 0
          normalZ = 0
        } else {
          z += stepZ
          distance = tMaxZ
          tMaxZ += tDeltaZ
          normalX = 0
          normalY = 0
          normalZ = -stepZ
        }
      } else {
        if (tMaxY < tMaxZ) {
          y += stepY
          distance = tMaxY
          tMaxY += tDeltaY
          normalX = 0
          normalY = -stepY
          normalZ = 0
        } else {
          z += stepZ
          distance = tMaxZ
          tMaxZ += tDeltaZ
          normalX = 0
          normalY = 0
          normalZ = -stepZ
        }
      }
    }
    return null
  }

  fun explode(centerX: Int, centerY: Int, centerZ: Int, radius: Float = 3.5f) {
    val rInt = (radius + 1).toInt()
    for (dx in -rInt..rInt) {
      for (dy in -rInt..rInt) {
        for (dz in -rInt..rInt) {
          val dist = sqrt((dx * dx + dy * dy + dz * dz).toFloat())
          if (dist <= radius) {
            val bx = centerX + dx
            val by = centerY + dy
            val bz = centerZ + dz
            val b = getBlock(bx, by, bz)
            if (b != BlockType.AIR && !b.isUnbreakable) {
              setBlock(bx, by, bz, BlockType.AIR)
              // Spawn particles
              if (particles.size < 120 && dist < radius * 0.8f) {
                particles.add(
                  Particle(
                    bx + 0.5f, by + 0.5f, bz + 0.5f,
                    dx * 0.08f + (Math.random().toFloat() - 0.5f) * 0.1f,
                    dy * 0.08f + (Math.random().toFloat() - 0.5f) * 0.1f + 0.1f,
                    dz * 0.08f + (Math.random().toFloat() - 0.5f) * 0.1f,
                    0.9f, 0.4f, 0.1f, 0.15f, 25
                  )
                )
              }
            }
          }
        }
      }
    }

    // Damage nearby mobs
    for (mob in mobs) {
      val d = sqrt((mob.x - centerX) * (mob.x - centerX) + (mob.y - centerY) * (mob.y - centerY) + (mob.z - centerZ) * (mob.z - centerZ))
      if (d < radius + 1f) {
        val dmg = (radius + 1f - d) * 6f
        mob.takeDamage(dmg)
      }
    }
  }

  fun spawnBreakParticles(bx: Int, by: Int, bz: Int, block: BlockType) {
    val count = 8
    val (r, g, b) = when (block) {
      BlockType.GRASS -> Triple(0.2f, 0.7f, 0.2f)
      BlockType.DIRT -> Triple(0.5f, 0.35f, 0.2f)
      BlockType.STONE, BlockType.COBBLESTONE -> Triple(0.55f, 0.55f, 0.55f)
      BlockType.OAK_LOG, BlockType.OAK_PLANKS -> Triple(0.6f, 0.45f, 0.25f)
      BlockType.LEAVES -> Triple(0.15f, 0.6f, 0.15f)
      BlockType.SAND -> Triple(0.85f, 0.8f, 0.5f)
      BlockType.DIAMOND_ORE -> Triple(0.2f, 0.85f, 0.9f)
      BlockType.COAL_ORE -> Triple(0.2f, 0.2f, 0.2f)
      BlockType.TNT -> Triple(0.8f, 0.15f, 0.15f)
      else -> Triple(0.5f, 0.5f, 0.5f)
    }

    for (i in 0 until count) {
      particles.add(
        Particle(
          bx + 0.2f + Math.random().toFloat() * 0.6f,
          by + 0.2f + Math.random().toFloat() * 0.6f,
          bz + 0.2f + Math.random().toFloat() * 0.6f,
          (Math.random().toFloat() - 0.5f) * 0.12f,
          Math.random().toFloat() * 0.12f + 0.05f,
          (Math.random().toFloat() - 0.5f) * 0.12f,
          r, g, b, 0.07f, 20
        )
      )
    }
  }

  fun updateParticles() {
    val iter = particles.iterator()
    while (iter.hasNext()) {
      val p = iter.next()
      if (!p.update(this)) {
        iter.remove()
      }
    }
  }
}
