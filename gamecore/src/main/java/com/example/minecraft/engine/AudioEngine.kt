package com.example.minecraft.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.minecraft.model.BlockType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

object AudioEngine {
  private const val SAMPLE_RATE = 22050
  private val scope = CoroutineScope(Dispatchers.Default)
  var isSoundEnabled: Boolean = true

  private fun playPcm(buffer: ShortArray) {
    if (!isSoundEnabled) return
    scope.launch {
      try {
        val track = AudioTrack.Builder()
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_GAME)
              .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
              .build()
          )
          .setAudioFormat(
            AudioFormat.Builder()
              .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
              .setSampleRate(SAMPLE_RATE)
              .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
              .build()
          )
          .setBufferSizeInBytes(buffer.size * 2)
          .setTransferMode(AudioTrack.MODE_STATIC)
          .build()

        track.write(buffer, 0, buffer.size)
        track.play()
        // Release after finish
        val durationMs = (buffer.size.toFloat() / SAMPLE_RATE * 1000).toLong() + 50
        kotlinx.coroutines.delay(durationMs)
        track.release()
      } catch (_: Exception) {
      }
    }
  }

  fun playDig(block: BlockType) {
    val samples = (SAMPLE_RATE * 0.08).toInt()
    val buf = ShortArray(samples)
    val rng = Random
    for (i in 0 until samples) {
      val env = 1.0f - (i.toFloat() / samples)
      val noise = (rng.nextFloat() * 2f - 1f)
      buf[i] = (noise * env * 12000).toInt().toShort()
    }
    playPcm(buf)
  }

  fun playBreak(block: BlockType) {
    val samples = (SAMPLE_RATE * 0.18).toInt()
    val buf = ShortArray(samples)
    val rng = Random
    for (i in 0 until samples) {
      val env = (1.0f - (i.toFloat() / samples)) * (1.0f - (i.toFloat() / samples))
      val noise = (rng.nextFloat() * 2f - 1f)
      val tone = sin(2.0 * Math.PI * (200.0 - i * 0.3) * (i.toDouble() / SAMPLE_RATE))
      val sample = (noise * 0.7 + tone * 0.3) * env
      buf[i] = (sample * 24000).toInt().coerceIn(-32767, 32767).toShort()
    }
    playPcm(buf)
  }

  fun playPlace() {
    val samples = (SAMPLE_RATE * 0.09).toInt()
    val buf = ShortArray(samples)
    for (i in 0 until samples) {
      val env = 1.0f - (i.toFloat() / samples)
      val freq = 320.0 - (i * 0.8)
      val tone = sin(2.0 * Math.PI * freq * (i.toDouble() / SAMPLE_RATE))
      val noise = (Random.nextFloat() * 2f - 1f) * 0.3f
      buf[i] = ((tone + noise) * env * 18000).toInt().toShort()
    }
    playPcm(buf)
  }

  fun playStep() {
    val samples = (SAMPLE_RATE * 0.05).toInt()
    val buf = ShortArray(samples)
    for (i in 0 until samples) {
      val env = 1.0f - (i.toFloat() / samples)
      val noise = (Random.nextFloat() * 2f - 1f)
      buf[i] = (noise * env * 6000).toInt().toShort()
    }
    playPcm(buf)
  }

  fun playHurt() {
    // Classic Minecraft "OOF!" vocaloid grunt
    val samples = (SAMPLE_RATE * 0.22).toInt()
    val buf = ShortArray(samples)
    for (i in 0 until samples) {
      val t = i.toDouble() / SAMPLE_RATE
      val env = 1.0f - (i.toFloat() / samples)
      // Pitch slides from 180Hz down to 90Hz
      val freq = 180.0 - (t * 400.0)
      val tone = sin(2.0 * Math.PI * freq * t)
      val harmonic = sin(4.0 * Math.PI * freq * t) * 0.5
      val sample = (tone + harmonic) * env
      buf[i] = (sample * 26000).toInt().coerceIn(-32767, 32767).toShort()
    }
    playPcm(buf)
  }

  fun playExplosion() {
    val samples = (SAMPLE_RATE * 0.7).toInt()
    val buf = ShortArray(samples)
    val rng = Random
    var lastNoise = 0f
    for (i in 0 until samples) {
      val t = i.toFloat() / samples
      val env = (1.0f - t) * (1.0f - t)
      val rawNoise = rng.nextFloat() * 2f - 1f
      // Low pass filter
      lastNoise = lastNoise * 0.85f + rawNoise * 0.15f
      val rumble = sin(2.0 * Math.PI * 65.0 * (i.toDouble() / SAMPLE_RATE))
      val sample = (lastNoise * 0.75 + rumble * 0.25) * env
      buf[i] = (sample * 30000).toInt().coerceIn(-32767, 32767).toShort()
    }
    playPcm(buf)
  }

  fun playPop() {
    val samples = (SAMPLE_RATE * 0.08).toInt()
    val buf = ShortArray(samples)
    for (i in 0 until samples) {
      val t = i.toDouble() / SAMPLE_RATE
      val env = 1.0f - (i.toFloat() / samples)
      val freq = 600.0 + (t * 4000.0) // pitch up sweep
      val tone = sin(2.0 * Math.PI * freq * t)
      buf[i] = (tone * env * 20000).toInt().toShort()
    }
    playPcm(buf)
  }

  fun playDing() {
    val samples = (SAMPLE_RATE * 0.4).toInt()
    val buf = ShortArray(samples)
    for (i in 0 until samples) {
      val t = i.toDouble() / SAMPLE_RATE
      val env = Math.exp(-t * 6.0).toFloat()
      val tone = sin(2.0 * Math.PI * 1046.5 * t) // C6
      buf[i] = (tone * env * 22000).toInt().toShort()
    }
    playPcm(buf)
  }
}
