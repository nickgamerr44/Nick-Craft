package com.example.minecraft.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.opengl.GLES20
import android.opengl.GLUtils
import kotlin.random.Random

object TextureAtlas {
  const val ATLAS_SIZE = 256
  const val TILE_SIZE = 16
  const val TILES_PER_ROW = ATLAS_SIZE / TILE_SIZE // 16 tiles per row

  var textureId: Int = 0
    private set

  fun init(): Int {
    val bitmap = generateAtlasBitmap()
    val textures = IntArray(1)
    GLES20.glGenTextures(1, textures, 0)
    textureId = textures[0]

    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
    // GL_NEAREST is essential for crisp, pixelated Minecraft aesthetic!
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
    bitmap.recycle()
    return textureId
  }

  fun getUV(tileIndex: Int): FloatArray {
    val tx = tileIndex % TILES_PER_ROW
    val ty = tileIndex / TILES_PER_ROW

    val uMin = tx.toFloat() / TILES_PER_ROW.toFloat()
    val vMin = ty.toFloat() / TILES_PER_ROW.toFloat()
    val uMax = (tx + 1).toFloat() / TILES_PER_ROW.toFloat()
    val vMax = (ty + 1).toFloat() / TILES_PER_ROW.toFloat()

    return floatArrayOf(uMin, vMin, uMax, vMax)
  }

  private fun generateAtlasBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(ATLAS_SIZE, ATLAS_SIZE, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Helper to draw 16x16 pixel art
    fun drawTile(tileIndex: Int, pixelBlock: (px: Int, py: Int) -> Int) {
      val ox = (tileIndex % TILES_PER_ROW) * TILE_SIZE
      val oy = (tileIndex / TILES_PER_ROW) * TILE_SIZE
      for (y in 0 until TILE_SIZE) {
        for (x in 0 until TILE_SIZE) {
          paint.color = pixelBlock(x, y)
          canvas.drawRect(
            (ox + x).toFloat(),
            (oy + y).toFloat(),
            (ox + x + 1).toFloat(),
            (oy + y + 1).toFloat(),
            paint
          )
        }
      }
    }

    val rng = Random(42)

    // 0: Grass Top (Rich green mosaic)
    drawTile(0) { x, y ->
      val n = rng.nextInt(-15, 16)
      Color.rgb(80 + n, 155 + n, 45 + n)
    }

    // 1: Grass Side (Green overhang over dirt)
    drawTile(1) { x, y ->
      val fringe = 3 + (if (x % 3 == 0) 2 else if (x % 2 == 0) 1 else 0)
      if (y < fringe) {
        val n = rng.nextInt(-12, 12)
        Color.rgb(80 + n, 155 + n, 45 + n)
      } else {
        val n = rng.nextInt(-15, 16)
        Color.rgb(134 + n, 96 + n, 67 + n)
      }
    }

    // 2: Dirt (Rich brown earth)
    drawTile(2) { _, _ ->
      val n = rng.nextInt(-16, 17)
      Color.rgb(134 + n, 96 + n, 67 + n)
    }

    // 3: Stone (Natural gray rock)
    drawTile(3) { _, _ ->
      val n = rng.nextInt(-18, 19)
      Color.rgb(125 + n, 125 + n, 125 + n)
    }

    // 4: Cobblestone (Cracked stones with mortar)
    drawTile(4) { x, y ->
      val isMortar = (x == 0 || x == 8 || y == 0 || y == 8 || (y == 4 && x > 8) || (y == 12 && x < 8))
      if (isMortar) {
        Color.rgb(65, 65, 65)
      } else {
        val n = rng.nextInt(-15, 16)
        Color.rgb(115 + n, 115 + n, 115 + n)
      }
    }

    // 5: Oak Log Side (Tree bark with vertical ridges)
    drawTile(5) { x, _ ->
      val ridge = if (x % 4 == 0) -25 else if (x % 2 == 0) 15 else 0
      val n = rng.nextInt(-10, 11)
      Color.rgb(105 + ridge + n, 80 + ridge + n, 50 + ridge + n)
    }

    // 6: Oak Log Top (Concentric wood rings)
    drawTile(6) { x, y ->
      val dx = x - 7.5f
      val dy = y - 7.5f
      val dist = kotlin.math.sqrt(dx * dx + dy * dy)
      val ring = ((dist * 2.2f).toInt() % 2 == 0)
      if (dist > 7f) {
        Color.rgb(90, 70, 45) // bark ring
      } else if (ring) {
        Color.rgb(175, 140, 95)
      } else {
        Color.rgb(155, 125, 80)
      }
    }

    // 7: Oak Planks (Wooden floorboards)
    drawTile(7) { x, y ->
      val isSeam = (y % 4 == 0) || ((y / 4) % 2 == 0 && x == 7) || ((y / 4) % 2 == 1 && x == 15)
      if (isSeam) {
        Color.rgb(110, 85, 50)
      } else {
        val n = rng.nextInt(-10, 11)
        Color.rgb(162 + n, 130 + n, 85 + n)
      }
    }

    // 8: Leaves (Lush green foliage with semi-transparent lattice)
    drawTile(8) { x, y ->
      val isHole = (x % 3 == 0 && y % 3 == 0)
      if (isHole) {
        Color.argb(0, 0, 0, 0)
      } else {
        val n = rng.nextInt(-20, 21)
        Color.argb(255, 45 + n, 130 + n, 30 + n)
      }
    }

    // 9: Sand (Warm golden sand grains)
    drawTile(9) { _, _ ->
      val n = rng.nextInt(-12, 13)
      Color.rgb(215 + n, 205 + n, 150 + n)
    }

    // 10: Water (Animated azure ripples)
    drawTile(10) { x, y ->
      val ripple = (kotlin.math.sin((x + y) * 0.8) * 15).toInt()
      Color.argb(210, 40, 100 + ripple, 220 + ripple)
    }

    // 11: Bedrock (High-contrast indestructible black stone)
    drawTile(11) { _, _ ->
      val n = rng.nextInt(0, 3)
      when (n) {
        0 -> Color.rgb(20, 20, 20)
        1 -> Color.rgb(55, 55, 55)
        else -> Color.rgb(80, 80, 80)
      }
    }

    // 12: Coal Ore
    drawTile(12) { x, y ->
      val isOre = (x in 4..7 && y in 3..6) || (x in 9..12 && y in 8..11)
      if (isOre) {
        Color.rgb(25, 25, 25)
      } else {
        val n = rng.nextInt(-15, 16)
        Color.rgb(125 + n, 125 + n, 125 + n)
      }
    }

    // 13: Iron Ore
    drawTile(13) { x, y ->
      val isOre = (x in 5..8 && y in 4..7) || (x in 10..13 && y in 9..12)
      if (isOre) {
        Color.rgb(210, 180, 150)
      } else {
        val n = rng.nextInt(-15, 16)
        Color.rgb(125 + n, 125 + n, 125 + n)
      }
    }

    // 14: Gold Ore
    drawTile(14) { x, y ->
      val isOre = (x in 3..6 && y in 4..7) || (x in 9..13 && y in 7..11)
      if (isOre) {
        Color.rgb(245, 215, 50)
      } else {
        val n = rng.nextInt(-15, 16)
        Color.rgb(125 + n, 125 + n, 125 + n)
      }
    }

    // 15: Diamond Ore (Glowing cyan gems!)
    drawTile(15) { x, y ->
      val isGem = (x in 4..7 && y in 4..7) || (x in 9..12 && y in 8..11)
      if (isGem) {
        val highlight = (x == 4 || y == 4 || x == 9 || y == 8)
        if (highlight) Color.rgb(150, 255, 255) else Color.rgb(45, 210, 220)
      } else {
        val n = rng.nextInt(-15, 16)
        Color.rgb(125 + n, 125 + n, 125 + n)
      }
    }

    // 16: Glass (Transparent block with blue rim and glare)
    drawTile(16) { x, y ->
      val isBorder = (x == 0 || x == 15 || y == 0 || y == 15)
      val isStreak = (x == y && x in 3..6) || (x == y - 5 && x in 9..12)
      when {
        isBorder -> Color.argb(230, 200, 230, 240)
        isStreak -> Color.argb(160, 255, 255, 255)
        else -> Color.argb(40, 210, 240, 255)
      }
    }

    // 17: Brick (Red clay bricks with mortar)
    drawTile(17) { x, y ->
      val isMortar = (y % 4 == 0) || ((y / 4) % 2 == 0 && x == 7) || ((y / 4) % 2 == 1 && (x == 3 || x == 11))
      if (isMortar) {
        Color.rgb(190, 185, 180)
      } else {
        val n = rng.nextInt(-15, 16)
        Color.rgb(160 + n, 70 + n, 55 + n)
      }
    }

    // 18: Crafting Table Top (Grid with tool icons)
    drawTile(18) { x, y ->
      val isBorder = (x == 0 || x == 15 || y == 0 || y == 15)
      val isDivider = (x == 7 || y == 7)
      if (isBorder || isDivider) {
        Color.rgb(90, 65, 40)
      } else {
        Color.rgb(175, 140, 95)
      }
    }

    // 19: Crafting Table Side (Wood planks with hanging saw & hammer)
    drawTile(19) { x, y ->
      val isTool = (x in 4..6 && y in 5..12) || (x in 10..12 && y in 6..10)
      if (isTool) {
        Color.rgb(70, 70, 80)
      } else {
        val n = rng.nextInt(-10, 11)
        Color.rgb(162 + n, 130 + n, 85 + n)
      }
    }

    // 20: Furnace Front (Stone with fire arch)
    drawTile(20) { x, y ->
      val isOpening = (x in 4..11 && y in 6..13)
      if (isOpening) {
        if (y > 10) Color.rgb(255, 130, 20) else Color.rgb(40, 20, 15)
      } else {
        val n = rng.nextInt(-15, 16)
        Color.rgb(115 + n, 115 + n, 115 + n)
      }
    }

    // 21: TNT Top (Red with center black fuse)
    drawTile(21) { x, y ->
      val dist = kotlin.math.sqrt(((x - 7.5f) * (x - 7.5f) + (y - 7.5f) * (y - 7.5f)))
      if (dist < 2.5f) {
        Color.rgb(30, 30, 30) // fuse
      } else {
        Color.rgb(215, 45, 35)
      }
    }

    // 22: TNT Side (Red band, white band with bold "TNT", red band)
    drawTile(22) { x, y ->
      if (y in 5..10) {
        // White band with black TNT letters
        val isT1 = (y == 6 && x in 2..4) || (x == 3 && y in 6..9)
        val isN = (x == 6 && y in 6..9) || (x == 8 && y in 6..9) || (x == 7 && y == 7)
        val isT2 = (y == 6 && x in 10..12) || (x == 11 && y in 6..9)
        if (isT1 || isN || isT2) Color.BLACK else Color.rgb(240, 240, 240)
      } else {
        val n = rng.nextInt(-10, 11)
        Color.rgb(210 + n, 40 + n, 30 + n)
      }
    }

    // 23: Torch (Stick with fire flame)
    drawTile(23) { x, y ->
      if (x in 7..8 && y in 5..14) {
        Color.rgb(130, 95, 55) // stick
      } else if (x in 6..9 && y in 1..4) {
        if (x in 7..8 && y in 2..3) Color.rgb(255, 255, 160) else Color.rgb(255, 140, 20)
      } else {
        Color.argb(0, 0, 0, 0)
      }
    }

    // 24: Red Flower (Poppy)
    drawTile(24) { x, y ->
      if (x in 7..8 && y in 8..15) {
        Color.rgb(40, 130, 30) // stem
      } else if (x in 5..10 && y in 3..7) {
        if (x in 7..8 && y in 4..5) Color.rgb(40, 30, 10) else Color.rgb(220, 35, 35)
      } else {
        Color.argb(0, 0, 0, 0)
      }
    }

    // 25: Yellow Flower (Dandelion)
    drawTile(25) { x, y ->
      if (x in 7..8 && y in 8..15) {
        Color.rgb(40, 130, 30)
      } else if (x in 5..10 && y in 3..7) {
        Color.rgb(250, 220, 20)
      } else {
        Color.argb(0, 0, 0, 0)
      }
    }

    // 26: Crack texture (Break stage)
    drawTile(26) { x, y ->
      val isCrack = (x == y) || (x + y == 15) || (x == 8 && y in 2..12) || (y == 8 && x in 2..12)
      if (isCrack) Color.argb(190, 20, 20, 20) else Color.argb(0, 0, 0, 0)
    }

    // 27: Mob Pig Face
    drawTile(27) { x, y ->
      val isEye = (x == 4 && y == 6) || (x == 11 && y == 6)
      val isPupil = (x == 5 && y == 6) || (x == 12 && y == 6)
      val isSnout = (x in 5..10 && y in 8..11)
      val isNostril = (x == 6 && y == 9) || (x == 9 && y == 9)
      when {
        isPupil -> Color.BLACK
        isEye -> Color.WHITE
        isNostril -> Color.rgb(160, 80, 80)
        isSnout -> Color.rgb(235, 140, 140)
        else -> Color.rgb(240, 165, 165)
      }
    }

    // 28: Mob Zombie Face
    drawTile(28) { x, y ->
      val isEye = (x in 3..5 && y == 6) || (x in 10..12 && y == 6)
      val isMouth = (x in 6..9 && y == 11)
      when {
        isEye -> Color.BLACK
        isMouth -> Color.rgb(20, 45, 20)
        else -> {
          val n = rng.nextInt(-10, 11)
          Color.rgb(60 + n, 120 + n, 60 + n)
        }
      }
    }

    // 29: Steve Face
    drawTile(29) { x, y ->
      val isHair = y < 5 || (y == 5 && (x < 2 || x > 13))
      val isEyeWhite = (y == 8 && (x == 3 || x == 12))
      val isPupil = (y == 8 && (x == 4 || x == 11))
      val isNose = (y in 9..10 && x in 7..8)
      val isMouth = (y in 11..12 && x in 6..9)
      when {
        isHair -> Color.rgb(65, 42, 28) // brown hair
        isPupil -> Color.rgb(45, 60, 160) // blue/purple eye
        isEyeWhite -> Color.WHITE
        isNose -> Color.rgb(180, 120, 95)
        isMouth -> Color.rgb(105, 55, 35) // beard/mouth
        else -> Color.rgb(205, 145, 115) // skin tone
      }
    }

    // 30: Steve Torso Front (Teal/Cyan T-shirt with neck opening)
    drawTile(30) { x, y ->
      val isNeck = (y < 4 && x in 6..9)
      if (isNeck) {
        Color.rgb(205, 145, 115) // skin
      } else {
        val n = rng.nextInt(-8, 9)
        Color.rgb(0 + n, 145 + n, 155 + n) // cyan shirt
      }
    }

    // 31: Steve Torso Back
    drawTile(31) { _, _ ->
      val n = rng.nextInt(-8, 9)
      Color.rgb(0 + n, 145 + n, 155 + n)
    }

    // 32: Steve Arm (Cyan sleeve at top, skin at bottom)
    drawTile(32) { _, y ->
      if (y < 5) {
        Color.rgb(0, 145, 155) // sleeve
      } else {
        Color.rgb(205, 145, 115) // skin
      }
    }

    // 33: Steve Leg (Blue jeans with dark shoes)
    drawTile(33) { _, y ->
      if (y < 12) {
        val n = rng.nextInt(-8, 9)
        Color.rgb(40 + n, 50 + n, 130 + n) // blue jeans
      } else {
        Color.rgb(75, 75, 80) // shoes
      }
    }

    // 34: Steve Head Top / Hair
    drawTile(34) { _, _ ->
      val n = rng.nextInt(-6, 7)
      Color.rgb(65 + n, 42 + n, 28 + n)
    }

    // 35: Steve Head Back (Hair)
    drawTile(35) { _, y ->
      if (y < 12) {
        val n = rng.nextInt(-6, 7)
        Color.rgb(65 + n, 42 + n, 28 + n) // brown hair
      } else {
        Color.rgb(205, 145, 115) // skin on neck
      }
    }

    // 36: Steve Head Side (Hair on top, ear and skin on bottom)
    drawTile(36) { x, y ->
      val isHair = y < 6 || (y in 6..7 && x > 7)
      val isEar = (y == 8 && x in 7..8)
      when {
        isHair -> {
          val n = rng.nextInt(-6, 7)
          Color.rgb(65 + n, 42 + n, 28 + n)
        }
        isEar -> Color.rgb(180, 120, 95)
        else -> Color.rgb(205, 145, 115)
      }
    }

    // 37: Steve First-Person Arm (Skin at top y=0..10, Cyan sleeve at bottom y=11..15)
    drawTile(37) { _, y ->
      if (y > 10) {
        Color.rgb(0, 145, 155) // sleeve
      } else {
        val n = rng.nextInt(-4, 5)
        Color.rgb(205 + n, 145 + n, 115 + n) // skin
      }
    }

    // 38: Steve Hand Knuckles / Palm / Top (Skin tone)
    drawTile(38) { _, _ ->
      val n = rng.nextInt(-4, 5)
      Color.rgb(205 + n, 145 + n, 115 + n)
    }

    return bitmap
  }
}
