package com.example.minecraft.model

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

object WorldGenerator {

  // Simple multi-octave pseudo-perlin noise
  private fun noise2D(x: Float, z: Float, seed: Long): Float {
    val s = (seed % 10000).toFloat()
    val v1 = sin(x * 0.08f + s) * cos(z * 0.08f + s)
    val v2 = sin(x * 0.16f - s * 0.5f) * cos(z * 0.14f + s * 0.3f) * 0.5f
    val v3 = sin(x * 0.32f + s * 0.2f) * sin(z * 0.32f - s * 0.7f) * 0.25f
    return (v1 + v2 + v3) / 1.75f
  }

  fun generate(world: VoxelWorld, seed: Long = 12345L) {
    val rng = Random(seed)
    val seaLevel = 7
    val baseHeight = 11

    // 1. Terrain generation (Bedrock, Stone, Dirt, Grass, Sand, Water)
    for (x in 0 until world.width) {
      for (z in 0 until world.length) {
        val n = noise2D(x.toFloat(), z.toFloat(), seed)
        // Height between 8 and 20
        val surfaceHeight = (baseHeight + n * 6f).toInt().coerceIn(7, world.height - 8)

        // Bedrock layer
        world.setBlock(x, 0, z, BlockType.BEDROCK)

        // Underground layers
        for (y in 1 until surfaceHeight) {
          if (y < surfaceHeight - 3) {
            // Stone layer + Caves + Ores
            val caveNoise = sin(x * 0.3f + seed.toFloat()) * cos(y * 0.4f) * sin(z * 0.3f)
            if (y > 2 && y < 15 && caveNoise > 0.45f) {
              world.setBlock(x, y, z, BlockType.AIR) // Cave pocket
            } else {
              // Ore generation
              val oreRoll = rng.nextFloat()
              val block = when {
                y in 1..4 && oreRoll < 0.02f -> BlockType.DIAMOND_ORE
                y in 1..7 && oreRoll < 0.04f -> BlockType.GOLD_ORE
                y in 1..12 && oreRoll < 0.07f -> BlockType.IRON_ORE
                y in 1..15 && oreRoll < 0.10f -> BlockType.COAL_ORE
                else -> BlockType.STONE
              }
              world.setBlock(x, y, z, block)
            }
          } else {
            // Dirt layer
            world.setBlock(x, y, z, BlockType.DIRT)
          }
        }

        // Surface block
        if (surfaceHeight <= seaLevel) {
          // Beach / underwater sand
          world.setBlock(x, surfaceHeight, z, BlockType.SAND)
          // Fill water up to sea level
          for (wy in (surfaceHeight + 1)..seaLevel) {
            world.setBlock(x, wy, z, BlockType.WATER)
          }
        } else {
          // Lush grass surface
          world.setBlock(x, surfaceHeight, z, BlockType.GRASS)

          // Flora: Red and yellow flowers
          val flowerRoll = rng.nextFloat()
          if (flowerRoll < 0.03f && surfaceHeight + 1 < world.height) {
            world.setBlock(x, surfaceHeight + 1, z, BlockType.RED_FLOWER)
          } else if (flowerRoll < 0.06f && surfaceHeight + 1 < world.height) {
            world.setBlock(x, surfaceHeight + 1, z, BlockType.YELLOW_FLOWER)
          }
        }
      }
    }

    // 2. Generate Oak Trees
    val treeMargin = 4
    for (x in treeMargin until (world.width - treeMargin) step 5) {
      for (z in treeMargin until (world.length - treeMargin) step 5) {
        val treeChance = rng.nextFloat()
        if (treeChance < 0.45f) {
          val offsetX = x + rng.nextInt(-1, 2)
          val offsetZ = z + rng.nextInt(-1, 2)
          if (offsetX in 2 until (world.width - 2) && offsetZ in 2 until (world.length - 2)) {
            // Find surface Y
            var surfaceY = -1
            for (y in (world.height - 8) downTo 5) {
              if (world.getBlock(offsetX, y, offsetZ) == BlockType.GRASS) {
                surfaceY = y
                break
              }
            }

            if (surfaceY > seaLevel && surfaceY + 7 < world.height) {
              plantTree(world, offsetX, surfaceY + 1, offsetZ, rng)
            }
          }
        }
      }
    }

    // 3. Spawn Initial Mobs (Pigs and Zombies)
    world.mobs.clear()
    // Spawn 4 pigs
    for (i in 0 until 4) {
      val mx = rng.nextInt(6, world.width - 6).toFloat()
      val mz = rng.nextInt(6, world.length - 6).toFloat()
      var my = 15f
      for (y in 25 downTo 5) {
        if (world.getBlock(mx.toInt(), y, mz.toInt()).isSolid) {
          my = (y + 1).toFloat()
          break
        }
      }
      world.mobs.add(Mob(MobType.PIG, mx, my, mz))
    }

    // Spawn 2 initial zombies in shaded areas
    for (i in 0 until 2) {
      val mx = rng.nextInt(6, world.width - 6).toFloat()
      val mz = rng.nextInt(6, world.length - 6).toFloat()
      var my = 12f
      for (y in 20 downTo 4) {
        if (world.getBlock(mx.toInt(), y, mz.toInt()).isSolid) {
          my = (y + 1).toFloat()
          break
        }
      }
      world.mobs.add(Mob(MobType.ZOMBIE, mx, my, mz))
    }

    world.isDirty = true
  }

  private fun plantTree(world: VoxelWorld, tx: Int, ty: Int, tz: Int, rng: Random) {
    val trunkHeight = rng.nextInt(4, 6)

    // Wood Trunk
    for (h in 0 until trunkHeight) {
      world.setBlock(tx, ty + h, tz, BlockType.OAK_LOG)
    }

    // Leaves Canopy (3x3x2 + top cross)
    val leafStart = ty + trunkHeight - 2
    for (ly in leafStart until (leafStart + 3)) {
      val radius = if (ly == leafStart + 2) 1 else 2
      for (dx in -radius..radius) {
        for (dz in -radius..radius) {
          if (dx == 0 && dz == 0 && ly < ty + trunkHeight) continue
          // Don't cut corners on upper layer
          if (abs(dx) == 2 && abs(dz) == 2 && rng.nextBoolean()) continue

          val leafX = tx + dx
          val leafZ = tz + dz
          if (world.getBlock(leafX, ly, leafZ) == BlockType.AIR) {
            world.setBlock(leafX, ly, leafZ, BlockType.LEAVES)
          }
        }
      }
    }
    // Top tip
    if (world.getBlock(tx, leafStart + 3, tz) == BlockType.AIR) {
      world.setBlock(tx, leafStart + 3, tz, BlockType.LEAVES)
    }
  }
}
