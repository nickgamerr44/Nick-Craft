package com.example.minecraft.model

/**
 * Historical feature and behavior profile for a specific Nick Craft release version.
 * Ensures launching older versions inside the Launcher actually reproduces that release's
 * gameplay, visuals, and feature set rather than only changing the version label.
 */
data class GameVersionProfile(
  val versionTag: String,
  val useCustomPixelIcons: Boolean,
  val enableMultiplayer: Boolean,
  val fastBreakParticles: Boolean,
  val fixLimbStopAnimation: Boolean,
  val fixStrafeDirection: Boolean
) {
  companion object {
    val LATEST = GameVersionProfile(
      versionTag = "0.03",
      useCustomPixelIcons = true,
      enableMultiplayer = true,
      fastBreakParticles = true,
      fixLimbStopAnimation = true,
      fixStrafeDirection = true
    )

    fun fromVersionTag(rawTag: String?): GameVersionProfile {
      val clean = rawTag?.trim().orEmpty().removePrefix("v").removePrefix("V")
      if (clean.isBlank()) return LATEST

      val match = Regex("(\\d+)\\.(\\d+)").find(clean)
      if (match == null) {
        return LATEST.copy(versionTag = rawTag?.trim().orEmpty().ifBlank { "0.03" })
      }
      val major = match.groupValues[1].toIntOrNull() ?: 0
      val minor = match.groupValues[2].toIntOrNull() ?: 3
      val normalizedTag = rawTag!!.trim()

      return if (major > 0 || minor >= 3) {
        GameVersionProfile(
          versionTag = normalizedTag,
          useCustomPixelIcons = true,
          enableMultiplayer = true,
          fastBreakParticles = true,
          fixLimbStopAnimation = true,
          fixStrafeDirection = true
        )
      } else if (minor == 2) {
        GameVersionProfile(
          versionTag = normalizedTag,
          useCustomPixelIcons = false,
          enableMultiplayer = false,
          fastBreakParticles = true,
          fixLimbStopAnimation = true,
          fixStrafeDirection = true
        )
      } else if (minor == 1) {
        GameVersionProfile(
          versionTag = normalizedTag,
          useCustomPixelIcons = false,
          enableMultiplayer = false,
          fastBreakParticles = true,
          fixLimbStopAnimation = true,
          fixStrafeDirection = false
        )
      } else {
        GameVersionProfile(
          versionTag = normalizedTag,
          useCustomPixelIcons = false,
          enableMultiplayer = false,
          fastBreakParticles = false,
          fixLimbStopAnimation = false,
          fixStrafeDirection = false
        )
      }
    }
  }
}
