package com.example.minecraft.model

class Particle(
  var x: Float,
  var y: Float,
  var z: Float,
  var vx: Float,
  var vy: Float,
  var vz: Float,
  val r: Float,
  val g: Float,
  val b: Float,
  val size: Float = 0.08f,
  var lifetime: Int = 30
) {
  val maxLifetime = lifetime

  fun update(world: VoxelWorld): Boolean {
    lifetime--
    if (lifetime <= 0) return false

    vy -= 0.012f // gravity
    x += vx
    y += vy
    z += vz

    vx *= 0.95f
    vz *= 0.95f

    // Stop if inside solid block
    val b = world.getBlock(x.toInt(), y.toInt(), z.toInt())
    if (b.isSolid) {
      vy = 0f
      vx = 0f
      vz = 0f
    }
    return true
  }
}
