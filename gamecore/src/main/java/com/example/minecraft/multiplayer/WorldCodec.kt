package com.example.minecraft.multiplayer

import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object WorldCodec {
  fun compressBlocks(blocks: ByteArray): String {
    val baos = ByteArrayOutputStream()
    GZIPOutputStream(baos).use { it.write(blocks) }
    return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
  }

  fun decompressBlocks(encoded: String, target: ByteArray): Boolean {
    return try {
      val compressed = Base64.decode(encoded, Base64.NO_WRAP)
      GZIPInputStream(ByteArrayInputStream(compressed)).use { gis ->
        val decoded = gis.readBytes()
        System.arraycopy(decoded, 0, target, 0, minOf(decoded.size, target.size))
      }
      true
    } catch (_: Exception) {
      false
    }
  }
}
