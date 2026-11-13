package com.example.minecraft.engine

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.minecraft.model.BlockFace
import com.example.minecraft.model.BlockType
import com.example.minecraft.model.CameraMode
import com.example.minecraft.model.Mob
import com.example.minecraft.model.MobType
import com.example.minecraft.model.Particle
import com.example.minecraft.model.Player
import com.example.minecraft.model.RaycastHit
import com.example.minecraft.model.VoxelWorld
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class VoxelRenderer(
  private val context: Context,
  val world: VoxelWorld,
  val player: Player
) : GLSurfaceView.Renderer {

  var currentTargetHit: RaycastHit? = null
  var breakProgress: Float = 0f
  var timeOfDay: Float = 6000f // 0..24000 (6000 = noon, 12000 = sunset, 18000 = midnight, 24000 = dawn)
  var viewDistanceFog: Float = 32f
  private var screenAspect: Float = 1.777f

  private val mvpMatrix = FloatArray(16)
  private val projectionMatrix = FloatArray(16)
  private val viewMatrix = FloatArray(16)
  private val modelMatrix = FloatArray(16)
  private val tempMatrix = FloatArray(16)

  private var programId: Int = 0
  private var uMVPMatrixLoc: Int = 0
  private var uTextureLoc: Int = 0
  private var uFogColorLoc: Int = 0
  private var uSunlightLoc: Int = 0
  private var aPositionLoc: Int = 0
  private var aTexCoordLoc: Int = 0
  private var aShadeLoc: Int = 0

  // Wireframe program for block selection outline
  private var lineProgramId: Int = 0
  private var uLineMVPLoc: Int = 0
  private var uLineColorLoc: Int = 0
  private var aLinePosLoc: Int = 0

  private var chunkMeshBuffer: FloatBuffer? = null
  private var chunkVertexCount: Int = 0

  private var wireframeBuffer: FloatBuffer? = null
  private var mobCubeBuffer: FloatBuffer? = null

  override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
    GLES20.glClearColor(0.45f, 0.65f, 0.95f, 1.0f)
    GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    GLES20.glDepthFunc(GLES20.GL_LEQUAL)
    GLES20.glEnable(GLES20.GL_CULL_FACE)
    GLES20.glCullFace(GLES20.GL_BACK)
    GLES20.glEnable(GLES20.GL_BLEND)
    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

    TextureAtlas.init()
    initShaders()
    initWireframeCube()
    initMobCube()

    rebuildWorldMesh()
  }

  override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
    GLES20.glViewport(0, 0, width, height)
    screenAspect = width.toFloat() / height.toFloat()
    Matrix.perspectiveM(projectionMatrix, 0, 70f, screenAspect, 0.1f, 100f)
  }

  override fun onDrawFrame(gl: GL10?) {
    // 1. Advance day/night cycle
    timeOfDay = (timeOfDay + 1.2f) % 24000f
    val sunAngle = (timeOfDay / 24000f) * 2f * Math.PI.toFloat()
    val sunlight = (sin(sunAngle) * 0.7f + 0.3f).coerceIn(0.18f, 1.0f)

    // Sky colors: day blue, sunset orange, night dark navy
    val (fogR, fogG, fogB) = when {
      timeOfDay in 11000f..13500f -> {
        // Sunset
        val f = (timeOfDay - 11000f) / 2500f
        Triple(0.85f * (1f - f) + 0.05f * f, 0.45f * (1f - f) + 0.05f * f, 0.30f * (1f - f) + 0.15f * f)
      }
      timeOfDay in 13500f..22500f -> {
        // Night
        Triple(0.04f, 0.04f, 0.10f)
      }
      timeOfDay in 22500f..24000f -> {
        // Sunrise
        val f = (timeOfDay - 22500f) / 1500f
        Triple(0.05f * (1f - f) + 0.85f * f, 0.05f * (1f - f) + 0.55f * f, 0.15f * (1f - f) + 0.45f * f)
      }
      else -> {
        // Bright day
        Triple(0.45f, 0.65f, 0.95f)
      }
    }

    GLES20.glClearColor(fogR, fogG, fogB, 1.0f)
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

    // 2. Setup Camera for 1st person, 3rd person back, and 2nd person (front view)
    val eyeX: Float
    val eyeY: Float
    val eyeZ: Float
    val targetX: Float
    val targetY: Float
    val targetZ: Float

    when (player.cameraMode) {
      CameraMode.FIRST_PERSON -> {
        val clampedPitch = player.pitch.coerceIn(-85f, 85f)
        val pRad = Math.toRadians(clampedPitch.toDouble())
        val yRad = Math.toRadians(player.yaw.toDouble())
        val fDx = (-sin(yRad) * cos(pRad)).toFloat()
        val fDy = (-sin(pRad)).toFloat()
        val fDz = (cos(yRad) * cos(pRad)).toFloat()

        eyeX = player.x
        eyeY = player.eyeY + sin(player.walkBobbing.toDouble()).toFloat() * 0.05f
        eyeZ = player.z
        targetX = eyeX + fDx
        targetY = eyeY + fDy
        targetZ = eyeZ + fDz
      }
      CameraMode.THIRD_PERSON_BACK -> {
        val orbitPitch = player.pitch.coerceIn(-70f, 70f)
        val pRad = Math.toRadians(orbitPitch.toDouble())
        val yRad = Math.toRadians(player.yaw.toDouble())
        val camDx = (-sin(yRad) * cos(pRad)).toFloat()
        val camDy = (-sin(pRad)).toFloat()
        val camDz = (cos(yRad) * cos(pRad)).toFloat()

        val dist = getCameraObstacleDistance(player.x, player.eyeY, player.z, -camDx, -camDy, -camDz, 3.2f)
        eyeX = player.x - camDx * dist
        eyeY = player.eyeY - camDy * dist
        eyeZ = player.z - camDz * dist
        targetX = player.x
        targetY = player.eyeY
        targetZ = player.z
      }
      CameraMode.THIRD_PERSON_FRONT -> {
        // Camera in front of Steve looking back at his face ("segunda pessoa / frente")
        val orbitPitch = player.pitch.coerceIn(-70f, 70f)
        val pRad = Math.toRadians(orbitPitch.toDouble())
        val yRad = Math.toRadians(player.yaw.toDouble())
        val camDx = (-sin(yRad) * cos(pRad)).toFloat()
        val camDy = (-sin(pRad)).toFloat()
        val camDz = (cos(yRad) * cos(pRad)).toFloat()

        val dist = getCameraObstacleDistance(player.x, player.eyeY, player.z, camDx, camDy, camDz, 2.8f)
        eyeX = player.x + camDx * dist
        eyeY = player.eyeY + camDy * dist
        eyeZ = player.z + camDz * dist
        targetX = player.x
        targetY = player.eyeY
        targetZ = player.z
      }
    }

    Matrix.setLookAtM(viewMatrix, 0, eyeX, eyeY, eyeZ, targetX, targetY, targetZ, 0f, 1f, 0f)

    // 3. Rebuild chunk mesh if world was modified
    if (world.isDirty) {
      rebuildWorldMesh()
      world.isDirty = false
    }

    // 4. Render 3D World Blocks
    GLES20.glUseProgram(programId)
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, TextureAtlas.textureId)
    GLES20.glUniform1i(uTextureLoc, 0)
    GLES20.glUniform4f(uFogColorLoc, fogR, fogG, fogB, 1.0f)
    GLES20.glUniform1f(uSunlightLoc, sunlight)

    Matrix.setIdentityM(modelMatrix, 0)
    Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
    Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)
    GLES20.glUniformMatrix4fv(uMVPMatrixLoc, 1, false, mvpMatrix, 0)

    chunkMeshBuffer?.let { buf ->
      buf.position(0)
      GLES20.glEnableVertexAttribArray(aPositionLoc)
      GLES20.glVertexAttribPointer(aPositionLoc, 3, GLES20.GL_FLOAT, false, 6 * 4, buf)

      buf.position(3)
      GLES20.glEnableVertexAttribArray(aTexCoordLoc)
      GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 6 * 4, buf)

      buf.position(5)
      GLES20.glEnableVertexAttribArray(aShadeLoc)
      GLES20.glVertexAttribPointer(aShadeLoc, 1, GLES20.GL_FLOAT, false, 6 * 4, buf)

      GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, chunkVertexCount)
    }

    // 5. Render Mobs
    renderMobs(sunlight, fogR, fogG, fogB)

    // 6. Render Particles
    renderParticles(sunlight, fogR, fogG, fogB)

    // 7. Render Block Breaking Crack
    val hit = currentTargetHit
    if (hit != null && breakProgress > 0f) {
      renderBreakCrack(hit, breakProgress, sunlight)
    }

    // 8. Render Wireframe selection box around targeted block
    if (hit != null) {
      renderBlockOutline(hit.blockX, hit.blockY, hit.blockZ)
    }

    // 9. Render Steve in 3rd Person or Hand in 1st Person
    if (player.isThirdPerson) {
      renderStevePlayer(sunlight)
    } else {
      renderHandTool(sunlight)
    }
  }

  private fun getCameraObstacleDistance(
    ox: Float, oy: Float, oz: Float,
    dx: Float, dy: Float, dz: Float,
    maxDist: Float
  ): Float {
    var d = 0.5f
    while (d <= maxDist) {
      val cx = ox + dx * d
      val cy = oy + dy * d
      val cz = oz + dz * d
      val bx = kotlin.math.floor(cx.toDouble()).toInt()
      val by = kotlin.math.floor(cy.toDouble()).toInt()
      val bz = kotlin.math.floor(cz.toDouble()).toInt()
      val block = world.getBlock(bx, by, bz)
      if (block.isSolid) {
        return (d - 0.25f).coerceAtLeast(0.5f)
      }
      d += 0.25f
    }
    return maxDist
  }

  private fun rebuildWorldMesh() {
    val rawFloats = ArrayList<Float>(20000)

    for (x in 0 until world.width) {
      for (y in 0 until world.height) {
        for (z in 0 until world.length) {
          val block = world.getBlock(x, y, z)
          if (block == BlockType.AIR) continue

          if (block == BlockType.RED_FLOWER || block == BlockType.YELLOW_FLOWER) {
            addPlantCross(rawFloats, x, y, z, block)
            continue
          }

          // Top face
          val topB = world.getBlock(x, y + 1, z)
          if (!topB.isSolid || topB.isTransparent) {
            addQuad(rawFloats, x, y, z, BlockFace.TOP, block, 1.0f)
          }

          // Bottom face
          val botB = world.getBlock(x, y - 1, z)
          if (!botB.isSolid || botB.isTransparent) {
            addQuad(rawFloats, x, y, z, BlockFace.BOTTOM, block, 0.5f)
          }

          // North face (z - 1)
          val northB = world.getBlock(x, y, z - 1)
          if (!northB.isSolid || northB.isTransparent) {
            addQuad(rawFloats, x, y, z, BlockFace.NORTH, block, 0.8f)
          }

          // South face (z + 1)
          val southB = world.getBlock(x, y, z + 1)
          if (!southB.isSolid || southB.isTransparent) {
            addQuad(rawFloats, x, y, z, BlockFace.SOUTH, block, 0.8f)
          }

          // West face (x - 1)
          val westB = world.getBlock(x - 1, y, z)
          if (!westB.isSolid || westB.isTransparent) {
            addQuad(rawFloats, x, y, z, BlockFace.WEST, block, 0.65f)
          }

          // East face (x + 1)
          val eastB = world.getBlock(x + 1, y, z)
          if (!eastB.isSolid || eastB.isTransparent) {
            addQuad(rawFloats, x, y, z, BlockFace.EAST, block, 0.65f)
          }
        }
      }
    }

    chunkVertexCount = rawFloats.size / 6
    val floatArray = FloatArray(rawFloats.size)
    for (i in rawFloats.indices) {
      floatArray[i] = rawFloats[i]
    }

    val bb = ByteBuffer.allocateDirect(floatArray.size * 4)
    bb.order(ByteOrder.nativeOrder())
    chunkMeshBuffer = bb.asFloatBuffer().apply {
      put(floatArray)
      position(0)
    }
  }

  private fun addQuad(
    list: ArrayList<Float>,
    x: Int,
    y: Int,
    z: Int,
    face: BlockFace,
    block: BlockType,
    shade: Float
  ) {
    val texIdx = block.getTextureIndex(face)
    val uv = TextureAtlas.getUV(texIdx)
    val u0 = uv[0]; val v0 = uv[1]; val u1 = uv[2]; val v1 = uv[3]

    val fx = x.toFloat()
    val fy = y.toFloat()
    val fz = z.toFloat()

    // 2 triangles = 6 vertices (x, y, z, u, v, shade)
    when (face) {
      BlockFace.TOP -> {
        // (fx, fy+1, fz) -> (fx, fy+1, fz+1) -> (fx+1, fy+1, fz+1)
        // (fx, fy+1, fz) -> (fx+1, fy+1, fz+1) -> (fx+1, fy+1, fz)
        addVertex(list, fx, fy + 1f, fz, u0, v0, shade)
        addVertex(list, fx, fy + 1f, fz + 1f, u0, v1, shade)
        addVertex(list, fx + 1f, fy + 1f, fz + 1f, u1, v1, shade)

        addVertex(list, fx, fy + 1f, fz, u0, v0, shade)
        addVertex(list, fx + 1f, fy + 1f, fz + 1f, u1, v1, shade)
        addVertex(list, fx + 1f, fy + 1f, fz, u1, v0, shade)
      }
      BlockFace.BOTTOM -> {
        addVertex(list, fx, fy, fz + 1f, u0, v1, shade)
        addVertex(list, fx, fy, fz, u0, v0, shade)
        addVertex(list, fx + 1f, fy, fz, u1, v0, shade)

        addVertex(list, fx, fy, fz + 1f, u0, v1, shade)
        addVertex(list, fx + 1f, fy, fz, u1, v0, shade)
        addVertex(list, fx + 1f, fy, fz + 1f, u1, v1, shade)
      }
      BlockFace.NORTH -> { // z = fz
        addVertex(list, fx + 1f, fy, fz, u0, v1, shade)
        addVertex(list, fx, fy, fz, u1, v1, shade)
        addVertex(list, fx, fy + 1f, fz, u1, v0, shade)

        addVertex(list, fx + 1f, fy, fz, u0, v1, shade)
        addVertex(list, fx, fy + 1f, fz, u1, v0, shade)
        addVertex(list, fx + 1f, fy + 1f, fz, u0, v0, shade)
      }
      BlockFace.SOUTH -> { // z = fz + 1
        addVertex(list, fx, fy, fz + 1f, u0, v1, shade)
        addVertex(list, fx + 1f, fy, fz + 1f, u1, v1, shade)
        addVertex(list, fx + 1f, fy + 1f, fz + 1f, u1, v0, shade)

        addVertex(list, fx, fy, fz + 1f, u0, v1, shade)
        addVertex(list, fx + 1f, fy + 1f, fz + 1f, u1, v0, shade)
        addVertex(list, fx, fy + 1f, fz + 1f, u0, v0, shade)
      }
      BlockFace.WEST -> { // x = fx
        addVertex(list, fx, fy, fz, u0, v1, shade)
        addVertex(list, fx, fy, fz + 1f, u1, v1, shade)
        addVertex(list, fx, fy + 1f, fz + 1f, u1, v0, shade)

        addVertex(list, fx, fy, fz, u0, v1, shade)
        addVertex(list, fx, fy + 1f, fz + 1f, u1, v0, shade)
        addVertex(list, fx, fy + 1f, fz, u0, v0, shade)
      }
      BlockFace.EAST -> { // x = fx + 1
        addVertex(list, fx + 1f, fy, fz + 1f, u0, v1, shade)
        addVertex(list, fx + 1f, fy, fz, u1, v1, shade)
        addVertex(list, fx + 1f, fy + 1f, fz, u1, v0, shade)

        addVertex(list, fx + 1f, fy, fz + 1f, u0, v1, shade)
        addVertex(list, fx + 1f, fy + 1f, fz, u1, v0, shade)
        addVertex(list, fx + 1f, fy + 1f, fz + 1f, u0, v0, shade)
      }
    }
  }

  private fun addVertex(list: ArrayList<Float>, x: Float, y: Float, z: Float, u: Float, v: Float, shade: Float) {
    list.add(x); list.add(y); list.add(z)
    list.add(u); list.add(v); list.add(shade)
  }

  private fun addPlantCross(list: ArrayList<Float>, x: Int, y: Int, z: Int, block: BlockType) {
    val uv = TextureAtlas.getUV(block.topTextureIndex)
    val u0 = uv[0]; val v0 = uv[1]; val u1 = uv[2]; val v1 = uv[3]
    val fx = x.toFloat()
    val fy = y.toFloat()
    val fz = z.toFloat()
    val shade = 0.9f

    // Diagonal 1: (fx, fz) to (fx+1, fz+1)
    addVertex(list, fx, fy, fz, u0, v1, shade)
    addVertex(list, fx + 1f, fy, fz + 1f, u1, v1, shade)
    addVertex(list, fx + 1f, fy + 1f, fz + 1f, u1, v0, shade)

    addVertex(list, fx, fy, fz, u0, v1, shade)
    addVertex(list, fx + 1f, fy + 1f, fz + 1f, u1, v0, shade)
    addVertex(list, fx, fy + 1f, fz, u0, v0, shade)

    // Reverse side of Diagonal 1
    addVertex(list, fx + 1f, fy, fz + 1f, u1, v1, shade)
    addVertex(list, fx, fy, fz, u0, v1, shade)
    addVertex(list, fx, fy + 1f, fz, u0, v0, shade)

    addVertex(list, fx + 1f, fy, fz + 1f, u1, v1, shade)
    addVertex(list, fx, fy + 1f, fz, u0, v0, shade)
    addVertex(list, fx + 1f, fy + 1f, fz + 1f, u1, v0, shade)

    // Diagonal 2: (fx+1, fz) to (fx, fz+1)
    addVertex(list, fx + 1f, fy, fz, u0, v1, shade)
    addVertex(list, fx, fy, fz + 1f, u1, v1, shade)
    addVertex(list, fx, fy + 1f, fz + 1f, u1, v0, shade)

    addVertex(list, fx + 1f, fy, fz, u0, v1, shade)
    addVertex(list, fx, fy + 1f, fz + 1f, u1, v0, shade)
    addVertex(list, fx + 1f, fy + 1f, fz, u0, v0, shade)

    // Reverse side of Diagonal 2
    addVertex(list, fx, fy, fz + 1f, u1, v1, shade)
    addVertex(list, fx + 1f, fy, fz, u0, v1, shade)
    addVertex(list, fx + 1f, fy + 1f, fz, u0, v0, shade)

    addVertex(list, fx, fy, fz + 1f, u1, v1, shade)
    addVertex(list, fx + 1f, fy + 1f, fz, u0, v0, shade)
    addVertex(list, fx, fy + 1f, fz + 1f, u1, v0, shade)
  }

  private fun renderMobs(sunlight: Float, fogR: Float, fogG: Float, fogB: Float) {
    GLES20.glUseProgram(programId)
    for (mob in world.mobs) {
      if (mob.isDead) continue

      Matrix.setIdentityM(modelMatrix, 0)
      Matrix.translateM(modelMatrix, 0, mob.x, mob.y + 0.35f, mob.z)
      Matrix.rotateM(modelMatrix, 0, mob.yaw, 0f, 1f, 0f)

      Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
      Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)

      if (mob.type == MobType.PIG) {
        // Pig Head (front face 27) + Body
        drawTexturedBox(mvpMatrix, 0.28f, 0.28f, 0.28f, 27, 27, 27, 27, 27, 27)
      } else {
        // Zombie Head (28) + Torso (30) + Legs (33)
        drawTexturedBox(mvpMatrix, 0.25f, 0.55f, 0.25f, 28, 33, 28, 30, 30, 30)
      }
    }
  }

  private fun renderStevePlayer(sunlight: Float) {
    GLES20.glUseProgram(programId)
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, TextureAtlas.textureId)
    GLES20.glUniform1i(uTextureLoc, 0)
    GLES20.glUniform1f(uSunlightLoc, max(0.40f, sunlight))

    val legSwing = sin(player.walkBobbing.toDouble()).toFloat() * 28f
    val armSwing = sin(player.walkBobbing.toDouble()).toFloat() * 28f
    val attackSwing = player.swingProgress * 65f

    // 1. Steve Head (articulated with pitch and yaw)
    Matrix.setIdentityM(modelMatrix, 0)
    Matrix.translateM(modelMatrix, 0, player.x, player.y + 1.48f, player.z)
    Matrix.rotateM(modelMatrix, 0, -player.yaw, 0f, 1f, 0f)
    Matrix.rotateM(modelMatrix, 0, player.pitch, 1f, 0f, 0f)
    Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
    Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)
    // top=34(hair top), bot=34, north=35(hair back), south=29(face), west=36(side), east=36(side)
    drawTexturedBox(mvpMatrix, 0.20f, 0.20f, 0.20f, 34, 34, 35, 29, 36, 36)

    // 2. Steve Torso (Cyan T-Shirt)
    Matrix.setIdentityM(modelMatrix, 0)
    Matrix.translateM(modelMatrix, 0, player.x, player.y + 0.90f, player.z)
    Matrix.rotateM(modelMatrix, 0, -player.yaw, 0f, 1f, 0f)
    Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
    Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)
    // top=30, bot=30, north=31(back), south=30(front), west=31, east=31
    drawTexturedBox(mvpMatrix, 0.20f, 0.30f, 0.11f, 30, 30, 31, 30, 31, 31)

    // 3. Left Arm (hinged at shoulder 1.15m height)
    Matrix.setIdentityM(modelMatrix, 0)
    Matrix.translateM(modelMatrix, 0, player.x, player.y + 1.15f, player.z)
    Matrix.rotateM(modelMatrix, 0, -player.yaw, 0f, 1f, 0f)
    Matrix.translateM(modelMatrix, 0, -0.28f, 0f, 0f)
    Matrix.rotateM(modelMatrix, 0, -armSwing, 1f, 0f, 0f)
    Matrix.translateM(modelMatrix, 0, 0f, -0.26f, 0f)
    Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
    Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)
    drawTexturedBox(mvpMatrix, 0.08f, 0.26f, 0.08f, 32, 32, 32, 32, 32, 32)

    // 4. Right Arm (hinged at shoulder 1.15m height, with attack swing & walking swing)
    Matrix.setIdentityM(modelMatrix, 0)
    Matrix.translateM(modelMatrix, 0, player.x, player.y + 1.15f, player.z)
    Matrix.rotateM(modelMatrix, 0, -player.yaw, 0f, 1f, 0f)
    Matrix.translateM(modelMatrix, 0, 0.28f, 0f, 0f)
    Matrix.rotateM(modelMatrix, 0, armSwing - attackSwing, 1f, 0f, 0f)
    Matrix.translateM(modelMatrix, 0, 0f, -0.26f, 0f)
    Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
    Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)
    drawTexturedBox(mvpMatrix, 0.08f, 0.26f, 0.08f, 32, 32, 32, 32, 32, 32)

    // 3D Held Item in Steve's Right Hand
    val held = player.inventory.selectedItem?.item
    val block = held?.blockType
    if (block != null && block != BlockType.AIR) {
      val itemMatrix = FloatArray(16)
      System.arraycopy(modelMatrix, 0, itemMatrix, 0, 16)
      Matrix.translateM(itemMatrix, 0, 0f, -0.20f, 0.16f)
      Matrix.rotateM(itemMatrix, 0, 30f, 0f, 1f, 0f)
      Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, itemMatrix, 0)
      Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)
      val topT = block.topTextureIndex
      val botT = block.bottomTextureIndex
      val sideT = block.sideTextureIndex
      drawTexturedBox(mvpMatrix, 0.09f, 0.09f, 0.09f, topT, botT, sideT, sideT, sideT, sideT)
    }

    // 5. Left Leg (hinged at hip 0.60m height)
    Matrix.setIdentityM(modelMatrix, 0)
    Matrix.translateM(modelMatrix, 0, player.x, player.y + 0.60f, player.z)
    Matrix.rotateM(modelMatrix, 0, -player.yaw, 0f, 1f, 0f)
    Matrix.translateM(modelMatrix, 0, -0.10f, 0f, 0f)
    Matrix.rotateM(modelMatrix, 0, legSwing, 1f, 0f, 0f)
    Matrix.translateM(modelMatrix, 0, 0f, -0.30f, 0f)
    Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
    Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)
    drawTexturedBox(mvpMatrix, 0.09f, 0.30f, 0.09f, 33, 33, 33, 33, 33, 33)

    // 6. Right Leg (hinged at hip 0.60m height)
    Matrix.setIdentityM(modelMatrix, 0)
    Matrix.translateM(modelMatrix, 0, player.x, player.y + 0.60f, player.z)
    Matrix.rotateM(modelMatrix, 0, -player.yaw, 0f, 1f, 0f)
    Matrix.translateM(modelMatrix, 0, 0.10f, 0f, 0f)
    Matrix.rotateM(modelMatrix, 0, -legSwing, 1f, 0f, 0f)
    Matrix.translateM(modelMatrix, 0, 0f, -0.30f, 0f)
    Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
    Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)
    drawTexturedBox(mvpMatrix, 0.09f, 0.30f, 0.09f, 33, 33, 33, 33, 33, 33)
  }

  private fun renderHandTool(sunlight: Float) {
    GLES20.glUseProgram(programId)
    // Clear depth buffer so first-person hand and held item are ALWAYS drawn in foreground
    GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT)
    GLES20.glUniform1f(uSunlightLoc, max(0.65f, sunlight))

    val handMatrix = FloatArray(16)
    val handProj = FloatArray(16)
    Matrix.perspectiveM(handProj, 0, 58f, screenAspect, 0.05f, 10f)

    Matrix.setIdentityM(handMatrix, 0)
    val swingOffset = sin(player.swingProgress * Math.PI.toFloat()) * 0.22f
    val bobOffset = sin(player.walkBobbing.toDouble()).toFloat() * 0.02f

    val held = player.inventory.selectedItem?.item
    val block = held?.blockType

    if (block != null && block != BlockType.AIR) {
      // 1st Person Held 3D Block in bottom-right with perspective foreshortening
      Matrix.translateM(
        handMatrix, 0,
        0.26f - swingOffset * 0.08f,
        -0.22f + swingOffset * 0.10f + bobOffset,
        -0.45f - swingOffset * 0.08f
      )
      Matrix.rotateM(handMatrix, 0, 20f - swingOffset * 40f, 1f, 0f, 0f)
      Matrix.rotateM(handMatrix, 0, 42f, 0f, 1f, 0f)
      Matrix.rotateM(handMatrix, 0, -10f, 0f, 0f, 1f)
      Matrix.multiplyMM(mvpMatrix, 0, handProj, 0, handMatrix, 0)

      val topT = block.topTextureIndex
      val botT = block.bottomTextureIndex
      val sideT = block.sideTextureIndex
      drawTexturedBox(mvpMatrix, 0.085f, 0.085f, 0.085f, topT, botT, sideT, sideT, sideT, sideT)
    } else {
      // 1st Person Steve 3D Arm (cyan sleeve + skin hand) with perspective foreshortening
      Matrix.translateM(
        handMatrix, 0,
        0.28f - swingOffset * 0.10f,
        -0.26f + swingOffset * 0.12f + bobOffset,
        -0.46f - swingOffset * 0.08f
      )
      Matrix.rotateM(handMatrix, 0, -20f - swingOffset * 45f, 1f, 0f, 0f)
      Matrix.rotateM(handMatrix, 0, -20f + swingOffset * 20f, 0f, 1f, 0f)
      Matrix.rotateM(handMatrix, 0, 12f, 0f, 0f, 1f)
      Matrix.multiplyMM(mvpMatrix, 0, handProj, 0, handMatrix, 0)

      // Solid 3D arm (0.055 wide, 0.15 long, 0.055 deep)
      // Tile 38 is skin (top/knuckles), tile 37 is arm side with sleeve at base and skin at fist
      drawTexturedBox(mvpMatrix, 0.055f, 0.15f, 0.055f, 38, 38, 37, 37, 37, 37)
    }
  }

  private fun drawTexturedBox(
    mvp: FloatArray,
    hw: Float, hh: Float, hd: Float,
    topTile: Int, botTile: Int, northTile: Int, southTile: Int, westTile: Int, eastTile: Int
  ) {
    GLES20.glUniformMatrix4fv(uMVPMatrixLoc, 1, false, mvp, 0)

    // Disable culling to ensure 100% solid geometry from any perspective
    GLES20.glDisable(GLES20.GL_CULL_FACE)

    val list = ArrayList<Float>(36 * 6)

    fun addQuad(
      x0: Float, y0: Float, z0: Float, u0: Float, v0: Float,
      x1: Float, y1: Float, z1: Float, u1: Float, v1: Float,
      x2: Float, y2: Float, z2: Float, u2: Float, v2: Float,
      x3: Float, y3: Float, z3: Float, u3: Float, v3: Float,
      shade: Float
    ) {
      // Triangle 1: 0, 1, 2
      list.add(x0); list.add(y0); list.add(z0); list.add(u0); list.add(v0); list.add(shade)
      list.add(x1); list.add(y1); list.add(z1); list.add(u1); list.add(v1); list.add(shade)
      list.add(x2); list.add(y2); list.add(z2); list.add(u2); list.add(v2); list.add(shade)
      // Triangle 2: 0, 2, 3
      list.add(x0); list.add(y0); list.add(z0); list.add(u0); list.add(v0); list.add(shade)
      list.add(x2); list.add(y2); list.add(z2); list.add(u2); list.add(v2); list.add(shade)
      list.add(x3); list.add(y3); list.add(z3); list.add(u3); list.add(v3); list.add(shade)
    }

    // 1. Top (+Y, shade 1.0)
    val uvTop = TextureAtlas.getUV(topTile)
    addQuad(
      -hw, hh, -hd, uvTop[0], uvTop[1],
      -hw, hh,  hd, uvTop[0], uvTop[3],
       hw, hh,  hd, uvTop[2], uvTop[3],
       hw, hh, -hd, uvTop[2], uvTop[1],
      1.0f
    )

    // 2. Bottom (-Y, shade 0.5)
    val uvBot = TextureAtlas.getUV(botTile)
    addQuad(
      -hw, -hh,  hd, uvBot[0], uvBot[1],
      -hw, -hh, -hd, uvBot[0], uvBot[3],
       hw, -hh, -hd, uvBot[2], uvBot[3],
       hw, -hh,  hd, uvBot[2], uvBot[1],
      0.5f
    )

    // 3. South / Front (+Z, shade 0.8)
    val uvSouth = TextureAtlas.getUV(southTile)
    addQuad(
      -hw, -hh, hd, uvSouth[0], uvSouth[3],
       hw, -hh, hd, uvSouth[2], uvSouth[3],
       hw,  hh, hd, uvSouth[2], uvSouth[1],
      -hw,  hh, hd, uvSouth[0], uvSouth[1],
      0.8f
    )

    // 4. North / Back (-Z, shade 0.8)
    val uvNorth = TextureAtlas.getUV(northTile)
    addQuad(
       hw, -hh, -hd, uvNorth[0], uvNorth[3],
      -hw, -hh, -hd, uvNorth[2], uvNorth[3],
      -hw,  hh, -hd, uvNorth[2], uvNorth[1],
       hw,  hh, -hd, uvNorth[0], uvNorth[1],
      0.8f
    )

    // 5. West / Left (-X, shade 0.65)
    val uvWest = TextureAtlas.getUV(westTile)
    addQuad(
      -hw, -hh, -hd, uvWest[0], uvWest[3],
      -hw, -hh,  hd, uvWest[2], uvWest[3],
      -hw,  hh,  hd, uvWest[2], uvWest[1],
      -hw,  hh, -hd, uvWest[0], uvWest[1],
      0.65f
    )

    // 6. East / Right (+X, shade 0.65)
    val uvEast = TextureAtlas.getUV(eastTile)
    addQuad(
      hw, -hh,  hd, uvEast[0], uvEast[3],
      hw, -hh, -hd, uvEast[2], uvEast[3],
      hw,  hh, -hd, uvEast[2], uvEast[1],
      hw,  hh,  hd, uvEast[0], uvEast[1],
      0.65f
    )

    val arr = FloatArray(list.size)
    for (i in list.indices) arr[i] = list[i]
    val bb = ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder())
    val buf = bb.asFloatBuffer()
    buf.put(arr)
    buf.position(0)

    buf.position(0)
    GLES20.glEnableVertexAttribArray(aPositionLoc)
    GLES20.glVertexAttribPointer(aPositionLoc, 3, GLES20.GL_FLOAT, false, 6 * 4, buf)
    buf.position(3)
    GLES20.glEnableVertexAttribArray(aTexCoordLoc)
    GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, 6 * 4, buf)
    buf.position(5)
    GLES20.glEnableVertexAttribArray(aShadeLoc)
    GLES20.glVertexAttribPointer(aShadeLoc, 1, GLES20.GL_FLOAT, false, 6 * 4, buf)

    GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36)

    // Re-enable backface culling for world terrain
    GLES20.glEnable(GLES20.GL_CULL_FACE)
  }

  private fun renderParticles(sunlight: Float, fogR: Float, fogG: Float, fogB: Float) {
    if (world.particles.isEmpty()) return
    GLES20.glUseProgram(programId)

    val snapshot = synchronized(world.particles) { ArrayList(world.particles) }
    for (p in snapshot) {
      Matrix.setIdentityM(modelMatrix, 0)
      Matrix.translateM(modelMatrix, 0, p.x, p.y, p.z)
      Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
      Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)

      val half = p.size * 0.5f
      drawTexturedBox(mvpMatrix, half, half, half, 2, 2, 2, 2, 2, 2)
    }
  }

  private fun renderBreakCrack(hit: RaycastHit, progress: Float, sunlight: Float) {
    GLES20.glUseProgram(programId)
    Matrix.setIdentityM(modelMatrix, 0)
    Matrix.translateM(modelMatrix, 0, hit.blockX + 0.5f, hit.blockY + 0.5f, hit.blockZ + 0.5f)
    Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
    Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)

    drawTexturedBox(mvpMatrix, 0.504f, 0.504f, 0.504f, 26, 26, 26, 26, 26, 26)
  }

  private fun renderBlockOutline(bx: Int, by: Int, bz: Int) {
    val wf = wireframeBuffer ?: return
    GLES20.glUseProgram(lineProgramId)

    Matrix.setIdentityM(modelMatrix, 0)
    Matrix.translateM(modelMatrix, 0, bx.toFloat() - 0.002f, by.toFloat() - 0.002f, bz.toFloat() - 0.002f)
    Matrix.scaleM(modelMatrix, 0, 1.004f, 1.004f, 1.004f)

    Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
    Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)

    GLES20.glUniformMatrix4fv(uLineMVPLoc, 1, false, mvpMatrix, 0)
    GLES20.glUniform4f(uLineColorLoc, 0f, 0f, 0f, 0.65f)

    GLES20.glLineWidth(3.0f)
    wf.position(0)
    GLES20.glEnableVertexAttribArray(aLinePosLoc)
    GLES20.glVertexAttribPointer(aLinePosLoc, 3, GLES20.GL_FLOAT, false, 0, wf)

    GLES20.glDrawArrays(GLES20.GL_LINES, 0, 24)
    GLES20.glDisableVertexAttribArray(aLinePosLoc)
  }

  private fun initWireframeCube() {
    val lines = floatArrayOf(
      // Bottom 4 lines
      0f, 0f, 0f, 1f, 0f, 0f,
      1f, 0f, 0f, 1f, 0f, 1f,
      1f, 0f, 1f, 0f, 0f, 1f,
      0f, 0f, 1f, 0f, 0f, 0f,
      // Top 4 lines
      0f, 1f, 0f, 1f, 1f, 0f,
      1f, 1f, 0f, 1f, 1f, 1f,
      1f, 1f, 1f, 0f, 1f, 1f,
      0f, 1f, 1f, 0f, 1f, 0f,
      // 4 Vertical pillars
      0f, 0f, 0f, 0f, 1f, 0f,
      1f, 0f, 0f, 1f, 1f, 0f,
      1f, 0f, 1f, 1f, 1f, 1f,
      0f, 0f, 1f, 0f, 1f, 1f
    )
    val bb = ByteBuffer.allocateDirect(lines.size * 4).order(ByteOrder.nativeOrder())
    wireframeBuffer = bb.asFloatBuffer().apply {
      put(lines)
      position(0)
    }
  }

  private fun initMobCube() {
    val list = ArrayList<Float>(36 * 6)
    // Unit cube centered at origin from -0.35 to +0.35
    val s = 0.35f
    // Top
    addCubeFace(list, -s, s, -s, s, s, s, 0f, 1f, 0f, 1.0f)
    // Bottom
    addCubeFace(list, -s, -s, s, s, -s, -s, 0f, -1f, 0f, 0.5f)
    // North
    addCubeFace(list, s, -s, -s, -s, s, -s, 0f, 0f, -1f, 0.8f)
    // South
    addCubeFace(list, -s, -s, s, s, s, s, 0f, 0f, 1f, 0.8f)
    // West
    addCubeFace(list, -s, -s, -s, -s, s, s, -1f, 0f, 0f, 0.65f)
    // East
    addCubeFace(list, s, -s, s, s, s, -s, 1f, 0f, 0f, 0.65f)

    val arr = FloatArray(list.size)
    for (i in list.indices) arr[i] = list[i]
    val bb = ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder())
    mobCubeBuffer = bb.asFloatBuffer().apply {
      put(arr)
      position(0)
    }
  }

  private fun addCubeFace(
    list: ArrayList<Float>,
    x0: Float, y0: Float, z0: Float,
    x1: Float, y1: Float, z1: Float,
    nx: Float, ny: Float, nz: Float,
    shade: Float
  ) {
    val u0 = 0f; val v0 = 0f; val u1 = 1f; val v1 = 1f
    if (ny != 0f) {
      list.add(x0); list.add(y0); list.add(z0); list.add(u0); list.add(v0); list.add(shade)
      list.add(x0); list.add(y0); list.add(z1); list.add(u0); list.add(v1); list.add(shade)
      list.add(x1); list.add(y0); list.add(z1); list.add(u1); list.add(v1); list.add(shade)

      list.add(x0); list.add(y0); list.add(z0); list.add(u0); list.add(v0); list.add(shade)
      list.add(x1); list.add(y0); list.add(z1); list.add(u1); list.add(v1); list.add(shade)
      list.add(x1); list.add(y0); list.add(z0); list.add(u1); list.add(v0); list.add(shade)
    } else if (nz != 0f) {
      list.add(x0); list.add(y0); list.add(z0); list.add(u0); list.add(v1); list.add(shade)
      list.add(x1); list.add(y0); list.add(z0); list.add(u1); list.add(v1); list.add(shade)
      list.add(x1); list.add(y1); list.add(z0); list.add(u1); list.add(v0); list.add(shade)

      list.add(x0); list.add(y0); list.add(z0); list.add(u0); list.add(v1); list.add(shade)
      list.add(x1); list.add(y1); list.add(z0); list.add(u1); list.add(v0); list.add(shade)
      list.add(x0); list.add(y1); list.add(z0); list.add(u0); list.add(v0); list.add(shade)
    } else {
      list.add(x0); list.add(y0); list.add(z0); list.add(u0); list.add(v1); list.add(shade)
      list.add(x0); list.add(y0); list.add(z1); list.add(u1); list.add(v1); list.add(shade)
      list.add(x0); list.add(y1); list.add(z1); list.add(u1); list.add(v0); list.add(shade)

      list.add(x0); list.add(y0); list.add(z0); list.add(u0); list.add(v1); list.add(shade)
      list.add(x0); list.add(y1); list.add(z1); list.add(u1); list.add(v0); list.add(shade)
      list.add(x0); list.add(y1); list.add(z0); list.add(u0); list.add(v0); list.add(shade)
    }
  }

  private fun initShaders() {
    val vertexShaderCode = """
      uniform mat4 uMVPMatrix;
      attribute vec4 aPosition;
      attribute vec2 aTexCoord;
      attribute float aShade;
      varying vec2 vTexCoord;
      varying float vShade;
      varying float vFogFactor;
      void main() {
        gl_Position = uMVPMatrix * aPosition;
        vTexCoord = aTexCoord;
        vShade = aShade;
        float dist = length(gl_Position.xyz);
        vFogFactor = clamp((dist - 14.0) / 18.0, 0.0, 1.0);
      }
    """.trimIndent()

    val fragmentShaderCode = """
      precision mediump float;
      uniform sampler2D uTexture;
      uniform vec4 uFogColor;
      uniform float uSunlight;
      varying vec2 vTexCoord;
      varying float vShade;
      varying float vFogFactor;
      void main() {
        vec4 texColor = texture2D(uTexture, vTexCoord);
        if (texColor.a < 0.1) discard;
        vec3 lit = texColor.rgb * vShade * max(0.20, uSunlight);
        gl_FragColor = vec4(mix(lit, uFogColor.rgb, vFogFactor), texColor.a);
      }
    """.trimIndent()

    val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
    val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

    programId = GLES20.glCreateProgram().also { prog ->
      GLES20.glAttachShader(prog, vs)
      GLES20.glAttachShader(prog, fs)
      GLES20.glLinkProgram(prog)
    }

    uMVPMatrixLoc = GLES20.glGetUniformLocation(programId, "uMVPMatrix")
    uTextureLoc = GLES20.glGetUniformLocation(programId, "uTexture")
    uFogColorLoc = GLES20.glGetUniformLocation(programId, "uFogColor")
    uSunlightLoc = GLES20.glGetUniformLocation(programId, "uSunlight")
    aPositionLoc = GLES20.glGetAttribLocation(programId, "aPosition")
    aTexCoordLoc = GLES20.glGetAttribLocation(programId, "aTexCoord")
    aShadeLoc = GLES20.glGetAttribLocation(programId, "aShade")

    // Wireframe Shader
    val lineVs = """
      uniform mat4 uMVPMatrix;
      attribute vec4 aPosition;
      void main() {
        gl_Position = uMVPMatrix * aPosition;
      }
    """.trimIndent()

    val lineFs = """
      precision mediump float;
      uniform vec4 uColor;
      void main() {
        gl_FragColor = uColor;
      }
    """.trimIndent()

    val lvs = compileShader(GLES20.GL_VERTEX_SHADER, lineVs)
    val lfs = compileShader(GLES20.GL_FRAGMENT_SHADER, lineFs)
    lineProgramId = GLES20.glCreateProgram().also { prog ->
      GLES20.glAttachShader(prog, lvs)
      GLES20.glAttachShader(prog, lfs)
      GLES20.glLinkProgram(prog)
    }
    uLineMVPLoc = GLES20.glGetUniformLocation(lineProgramId, "uMVPMatrix")
    uLineColorLoc = GLES20.glGetUniformLocation(lineProgramId, "uColor")
    aLinePosLoc = GLES20.glGetAttribLocation(lineProgramId, "aPosition")
  }

  private fun compileShader(type: Int, shaderCode: String): Int {
    return GLES20.glCreateShader(type).also { shader ->
      GLES20.glShaderSource(shader, shaderCode)
      GLES20.glCompileShader(shader)
    }
  }
}
