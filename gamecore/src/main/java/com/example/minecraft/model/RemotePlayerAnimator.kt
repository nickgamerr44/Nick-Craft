package com.example.minecraft.model

import com.example.minecraft.multiplayer.RemotePlayerState
import kotlin.math.sin

data class RemotePlayerAnimPose(
  val player: RemotePlayerState,
  val renderX: Float,
  val renderY: Float,
  val renderZ: Float,
  val renderYaw: Float,
  val renderPitch: Float,
  val legSwingDeg: Float,
  val armSwingDeg: Float,
  val attackSwingDeg: Float
)

/**
 * Pure-Kotlin per-peer animation & pose-smoothing tracker so remote players smoothly animate
 * walking (arms + legs) and hitting/mining (right-arm swing) across network snapshots.
 */
class RemotePlayerAnimator {

  private class PeerTrack(
    var renderX: Float,
    var renderY: Float,
    var renderZ: Float,
    var renderYaw: Float,
    var renderPitch: Float,
    var lastTargetX: Float,
    var lastTargetZ: Float,
    var walkBobbing: Float = 0f,
    var limbSwingWeight: Float = 0f,
    var swingProgress: Float = 0f,
    var lastSwingSeq: Int = 0,
    var movingGraceFrames: Int = 0
  )

  private val tracks = LinkedHashMap<String, PeerTrack>()

  fun clear() {
    tracks.clear()
  }

  /**
   * Triggers a visible arm swing locally on a specific remote player immediately
   * (e.g. when a player_hit event arrives before the next presence snapshot).
   */
  fun triggerSwingFor(playerId: String) {
    tracks[playerId]?.let { track ->
      track.swingProgress = 1.0f
    }
  }

  fun step(
    peers: List<RemotePlayerState>,
    animationsEnabled: Boolean = true,
    walkSwingDegrees: Float = 30.0f,
    attackSwingDegrees: Float = 70.0f
  ): List<RemotePlayerAnimPose> {
    if (peers.isEmpty()) {
      tracks.clear()
      return emptyList()
    }

    val activeIds = HashSet<String>(peers.size)
    val result = ArrayList<RemotePlayerAnimPose>(peers.size)

    for (rp in peers) {
      activeIds.add(rp.playerId)
      val existing = tracks[rp.playerId]
      val track = if (existing == null) {
        PeerTrack(
          renderX = rp.x,
          renderY = rp.y,
          renderZ = rp.z,
          renderYaw = rp.yaw,
          renderPitch = rp.pitch,
          lastTargetX = rp.x,
          lastTargetZ = rp.z,
          lastSwingSeq = rp.swingSeq,
          movingGraceFrames = if (rp.isWalking) 12 else 0
        ).also { tracks[rp.playerId] = it }
      } else {
        existing
      }

      // 1. Detect remote movement from either explicit synced flag or target coordinate delta
      val targetDx = rp.x - track.lastTargetX
      val targetDz = rp.z - track.lastTargetZ
      val targetMoved = (targetDx * targetDx + targetDz * targetDz) > 0.0004f
      track.lastTargetX = rp.x
      track.lastTargetZ = rp.z

      if (targetMoved || rp.isWalking) {
        track.movingGraceFrames = 12
      } else if (track.movingGraceFrames > 0) {
        track.movingGraceFrames--
      }

      // 2. Smoothly interpolate rendered position & rotation toward latest snapshot
      val interp = 0.28f
      val prevRenderX = track.renderX
      val prevRenderZ = track.renderZ
      val teleportDistSq = (rp.x - track.renderX) * (rp.x - track.renderX) +
        (rp.y - track.renderY) * (rp.y - track.renderY) +
        (rp.z - track.renderZ) * (rp.z - track.renderZ)
      if (teleportDistSq > 64f) {
        track.renderX = rp.x
        track.renderY = rp.y
        track.renderZ = rp.z
      } else {
        track.renderX += (rp.x - track.renderX) * interp
        track.renderY += (rp.y - track.renderY) * interp
        track.renderZ += (rp.z - track.renderZ) * interp
      }
      track.renderYaw = lerpAngleDeg(track.renderYaw, rp.yaw, 0.35f)
      track.renderPitch += (rp.pitch - track.renderPitch) * 0.35f

      val renderDx = track.renderX - prevRenderX
      val renderDz = track.renderZ - prevRenderZ
      val isVisuallyMoving = !rp.isDead && rp.health > 0f &&
        (rp.isWalking || track.movingGraceFrames > 0 || (renderDx * renderDx + renderDz * renderDz) > 0.00004f)

      // 3. Detect remote attack/mine swings via monotonic swingSeq
      if (rp.swingSeq > track.lastSwingSeq) {
        track.lastSwingSeq = rp.swingSeq
        if (animationsEnabled && !rp.isDead && rp.health > 0f) {
          track.swingProgress = 1.0f
        }
      } else if (rp.swingSeq < track.lastSwingSeq) {
        // Peer reconnected or reset state
        track.lastSwingSeq = rp.swingSeq
      }

      if (animationsEnabled && !rp.isDead && rp.health > 0f) {
        if (isVisuallyMoving) {
          track.limbSwingWeight = (track.limbSwingWeight + 0.24f).coerceAtMost(1.0f)
          track.walkBobbing += 0.22f
        } else {
          track.limbSwingWeight = (track.limbSwingWeight - 0.24f).coerceAtLeast(0.0f)
          if (track.limbSwingWeight <= 0f) {
            track.walkBobbing = 0f
          }
        }
        if (track.swingProgress > 0f) {
          track.swingProgress = (track.swingProgress - 0.11f).coerceAtLeast(0f)
        }
      } else {
        track.limbSwingWeight = 0f
        track.walkBobbing = 0f
        track.swingProgress = 0f
      }

      val walkWave = sin(track.walkBobbing.toDouble()).toFloat()
      val legSwing = walkWave * walkSwingDegrees * track.limbSwingWeight
      val armSwing = walkWave * walkSwingDegrees * track.limbSwingWeight
      val attackSwing = sin(track.swingProgress * Math.PI.toFloat()) * attackSwingDegrees

      result.add(
        RemotePlayerAnimPose(
          player = rp,
          renderX = track.renderX,
          renderY = track.renderY,
          renderZ = track.renderZ,
          renderYaw = track.renderYaw,
          renderPitch = track.renderPitch,
          legSwingDeg = legSwing,
          armSwingDeg = armSwing,
          attackSwingDeg = attackSwing
        )
      )
    }

    val it = tracks.keys.iterator()
    while (it.hasNext()) {
      if (it.next() !in activeIds) {
        it.remove()
      }
    }

    return result
  }

  private fun lerpAngleDeg(from: Float, to: Float, alpha: Float): Float {
    var diff = (to - from) % 360f
    if (diff > 180f) diff -= 360f
    if (diff < -180f) diff += 360f
    return from + diff * alpha
  }
}
