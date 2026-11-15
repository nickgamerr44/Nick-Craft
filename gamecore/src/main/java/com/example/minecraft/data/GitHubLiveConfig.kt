package com.example.minecraft.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class LiveGameConfig(
  val configVersion: Int = 1,
  val livePatchTitle: String = "",
  val announcement: String = "",
  val motd: String = "",
  val pvpEnabled: Boolean = true,
  val pvpDamagesCreative: Boolean = true,
  val pvpBaseFistDamage: Float = 2.0f,
  val pvpDamageMultiplier: Float = 1.0f,
  val moveSpeed: Float = 0.10f,
  val jumpVelocity: Float = 0.26f,
  val gravity: Float = 0.016f,
  val spawnX: Float = 24.5f,
  val spawnZ: Float = 24.5f,
  val respawnHealth: Float = 20.0f,
  val timeSpeedMultiplier: Float = 1.0f,
  val remoteAnimationsEnabled: Boolean = true,
  val remoteWalkSwingDegrees: Float = 30.0f,
  val remoteAttackSwingDegrees: Float = 70.0f,
  val remotePoseSyncIntervalMs: Long = 120L
) {
  companion object {
    val DEFAULT = LiveGameConfig()
  }
}

object LiveGameConfigParser {
  fun parse(jsonText: String): LiveGameConfig? {
    return runCatching {
      val map = parseJsonObject(jsonText) ?: return null
      fun str(key: String): String = (map[key] as? String)?.trim() ?: ""
      fun intVal(key: String, def: Int): Int = (map[key] as? Number)?.toInt() ?: def
      fun longVal(key: String, def: Long): Long = (map[key] as? Number)?.toLong() ?: def
      fun floatVal(key: String, def: Float): Float = (map[key] as? Number)?.toFloat() ?: def
      fun boolVal(key: String, def: Boolean): Boolean = (map[key] as? Boolean) ?: def

      LiveGameConfig(
        configVersion = intVal("configVersion", 1).coerceAtLeast(1),
        livePatchTitle = str("livePatchTitle"),
        announcement = str("announcement"),
        motd = str("motd"),
        pvpEnabled = boolVal("pvpEnabled", true),
        pvpDamagesCreative = boolVal("pvpDamagesCreative", true),
        pvpBaseFistDamage = floatVal("pvpBaseFistDamage", 2.0f).coerceIn(0.5f, 20f),
        pvpDamageMultiplier = floatVal("pvpDamageMultiplier", 1.0f).coerceIn(0.1f, 5f),
        moveSpeed = floatVal("moveSpeed", 0.10f).coerceIn(0.03f, 0.35f),
        jumpVelocity = floatVal("jumpVelocity", 0.26f).coerceIn(0.12f, 0.60f),
        gravity = floatVal("gravity", 0.016f).coerceIn(0.004f, 0.05f),
        spawnX = floatVal("spawnX", 24.5f).coerceIn(2f, 46f),
        spawnZ = floatVal("spawnZ", 24.5f).coerceIn(2f, 46f),
        respawnHealth = floatVal("respawnHealth", 20.0f).coerceIn(2f, 20f),
        timeSpeedMultiplier = floatVal("timeSpeedMultiplier", 1.0f).coerceIn(0f, 10f),
        remoteAnimationsEnabled = boolVal("remoteAnimationsEnabled", true),
        remoteWalkSwingDegrees = floatVal("remoteWalkSwingDegrees", 30.0f).coerceIn(0f, 65f),
        remoteAttackSwingDegrees = floatVal("remoteAttackSwingDegrees", 70.0f).coerceIn(0f, 110f),
        remotePoseSyncIntervalMs = longVal("remotePoseSyncIntervalMs", 120L).coerceIn(60L, 500L)
      )
    }.getOrNull()
  }

  fun toJson(config: LiveGameConfig): String = buildString {
    appendLine("{")
    appendLine("""  "configVersion": ${config.configVersion},""")
    appendLine("""  "livePatchTitle": "${escapeJson(config.livePatchTitle)}",""")
    appendLine("""  "announcement": "${escapeJson(config.announcement)}",""")
    appendLine("""  "motd": "${escapeJson(config.motd)}",""")
    appendLine("""  "pvpEnabled": ${config.pvpEnabled},""")
    appendLine("""  "pvpDamagesCreative": ${config.pvpDamagesCreative},""")
    appendLine("""  "pvpBaseFistDamage": ${config.pvpBaseFistDamage},""")
    appendLine("""  "pvpDamageMultiplier": ${config.pvpDamageMultiplier},""")
    appendLine("""  "moveSpeed": ${config.moveSpeed},""")
    appendLine("""  "jumpVelocity": ${config.jumpVelocity},""")
    appendLine("""  "gravity": ${config.gravity},""")
    appendLine("""  "spawnX": ${config.spawnX},""")
    appendLine("""  "spawnZ": ${config.spawnZ},""")
    appendLine("""  "respawnHealth": ${config.respawnHealth},""")
    appendLine("""  "timeSpeedMultiplier": ${config.timeSpeedMultiplier},""")
    appendLine("""  "remoteAnimationsEnabled": ${config.remoteAnimationsEnabled},""")
    appendLine("""  "remoteWalkSwingDegrees": ${config.remoteWalkSwingDegrees},""")
    appendLine("""  "remoteAttackSwingDegrees": ${config.remoteAttackSwingDegrees},""")
    appendLine("""  "remotePoseSyncIntervalMs": ${config.remotePoseSyncIntervalMs}""")
    append("}")
  }

  private fun escapeJson(value: String): String = buildString(value.length) {
    for (ch in value) {
      when (ch) {
        '\\' -> append("\\\\")
        '"' -> append("\\\"")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> append(ch)
      }
    }
  }

  private fun parseJsonObject(input: String): Map<String, Any?>? {
    var pos = 0
    val len = input.length

    fun skipWs() {
      while (pos < len && input[pos].isWhitespace()) pos++
    }

    fun parseString(): String {
      if (pos >= len || input[pos] != '"') error("Expected '\"'")
      pos++
      val sb = StringBuilder()
      while (pos < len) {
        val c = input[pos++]
        when (c) {
          '"' -> return sb.toString()
          '\\' -> {
            if (pos >= len) error("Unterminated escape")
            when (val esc = input[pos++]) {
              '"' -> sb.append('"')
              '\\' -> sb.append('\\')
              '/' -> sb.append('/')
              'b' -> sb.append('\b')
              'f' -> sb.append('\u000C')
              'n' -> sb.append('\n')
              'r' -> sb.append('\r')
              't' -> sb.append('\t')
              'u' -> {
                if (pos + 4 > len) error("Invalid unicode escape")
                sb.append(input.substring(pos, pos + 4).toInt(16).toChar())
                pos += 4
              }
              else -> sb.append(esc)
            }
          }
          else -> sb.append(c)
        }
      }
      error("Unterminated string")
    }

    fun parseValue(): Any? {
      skipWs()
      if (pos >= len) error("Unexpected EOF")
      return when (val c = input[pos]) {
        '"' -> parseString()
        '{' -> {
          pos++
          val obj = linkedMapOf<String, Any?>()
          skipWs()
          if (pos < len && input[pos] == '}') {
            pos++
            return obj
          }
          while (pos < len) {
            skipWs()
            val key = parseString()
            skipWs()
            if (pos >= len || input[pos] != ':') error("Expected ':'")
            pos++
            obj[key] = parseValue()
            skipWs()
            if (pos < len && input[pos] == ',') {
              pos++
            } else if (pos < len && input[pos] == '}') {
              pos++
              break
            } else {
              error("Expected ',' or '}'")
            }
          }
          obj
        }
        '[' -> {
          pos++
          val list = mutableListOf<Any?>()
          skipWs()
          if (pos < len && input[pos] == ']') {
            pos++
            return list
          }
          while (pos < len) {
            list.add(parseValue())
            skipWs()
            if (pos < len && input[pos] == ',') {
              pos++
            } else if (pos < len && input[pos] == ']') {
              pos++
              break
            } else {
              error("Expected ',' or ']'")
            }
          }
          list
        }
        't' -> {
          if (!input.startsWith("true", pos)) error("Expected true")
          pos += 4
          true
        }
        'f' -> {
          if (!input.startsWith("false", pos)) error("Expected false")
          pos += 5
          false
        }
        'n' -> {
          if (!input.startsWith("null", pos)) error("Expected null")
          pos += 4
          null
        }
        '-', in '0'..'9' -> {
          val start = pos
          while (pos < len && input[pos] in "0123456789+-.eE") pos++
          val numStr = input.substring(start, pos)
          if ('.' in numStr || 'e' in numStr || 'E' in numStr) {
            numStr.toDouble()
          } else {
            numStr.toLong()
          }
        }
        else -> error("Unexpected char: $c")
      }
    }

    return parseValue() as? Map<String, Any?>
  }
}

sealed interface LiveSyncStatus {
  data object Idle : LiveSyncStatus
  data object Syncing : LiveSyncStatus
  data class Synced(val fromGitHub: Boolean, val configVersion: Int, val patchTitle: String) : LiveSyncStatus
  data class OfflineCached(val configVersion: Int) : LiveSyncStatus
}

/**
 * Fetches declarative live game configuration directly from GitHub `main` branch (`live_config.json`)
 * whenever the app starts or resumes, updating gameplay parameters, spawn, PvP tuning, and announcements
 * automatically without requiring an app version bump or APK rebuild.
 *
 * Strictly declarative JSON only (100% Google Play Policy compliant — no dynamic code loading).
 */
class GitHubLiveConfigRepository(
  private val cacheDir: File? = null,
  private val rawFetcher: (() -> String?)? = null
) {
  private val cacheFile: File? = cacheDir?.let { File(it, "live_config_cache.json") }

  private val _config = MutableStateFlow(loadCachedConfig() ?: LiveGameConfig.DEFAULT)
  val config: StateFlow<LiveGameConfig> = _config.asStateFlow()

  private val _syncStatus = MutableStateFlow<LiveSyncStatus>(LiveSyncStatus.Idle)
  val syncStatus: StateFlow<LiveSyncStatus> = _syncStatus.asStateFlow()

  private var lastSyncAttemptMs: Long = 0L

  private fun loadCachedConfig(): LiveGameConfig? {
    val file = cacheFile ?: return null
    if (!file.exists()) return null
    return runCatching { LiveGameConfigParser.parse(file.readText()) }.getOrNull()
  }

  private fun saveCachedConfig(cfg: LiveGameConfig) {
    val file = cacheFile ?: return
    runCatching {
      file.parentFile?.mkdirs()
      file.writeText(LiveGameConfigParser.toJson(cfg))
    }
  }

  suspend fun syncIfStale(minIntervalMs: Long = 12_000L): Result<LiveGameConfig> {
    val now = System.currentTimeMillis()
    if (now - lastSyncAttemptMs < minIntervalMs && _syncStatus.value is LiveSyncStatus.Synced) {
      return Result.success(_config.value)
    }
    return syncFromGitHub()
  }

  suspend fun syncFromGitHub(): Result<LiveGameConfig> = withContext(Dispatchers.IO) {
    lastSyncAttemptMs = System.currentTimeMillis()
    _syncStatus.value = LiveSyncStatus.Syncing

    val rawJson = runCatching {
      if (rawFetcher != null) {
        rawFetcher.invoke()
      } else {
        fetchFromRawGitHub()
      }
    }.getOrNull()

    val parsed = rawJson?.let { LiveGameConfigParser.parse(it) }
    if (parsed != null) {
      saveCachedConfig(parsed)
      _config.value = parsed
      _syncStatus.value = LiveSyncStatus.Synced(
        fromGitHub = true,
        configVersion = parsed.configVersion,
        patchTitle = parsed.livePatchTitle
      )
      Result.success(parsed)
    } else {
      val fallback = loadCachedConfig() ?: _config.value
      _config.value = fallback
      _syncStatus.value = LiveSyncStatus.OfflineCached(fallback.configVersion)
      Result.failure(IllegalStateException("Não foi possível buscar live_config.json no GitHub"))
    }
  }

  private fun fetchFromRawGitHub(): String? {
    // Query timestamp bypasses CDN edge caching so GitHub commits take effect immediately
    val urlStr = "$LIVE_CONFIG_RAW_URL?t=${System.currentTimeMillis() / 1000}"
    val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
      connectTimeout = 7000
      readTimeout = 7000
      useCaches = false
      setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
      setRequestProperty("Pragma", "no-cache")
      setRequestProperty("User-Agent", "NickCraftLiveSync/1.0")
    }
    return try {
      if (conn.responseCode in 200..299) {
        conn.inputStream.bufferedReader().use { it.readText() }
      } else {
        null
      }
    } finally {
      conn.disconnect()
    }
  }

  companion object {
    const val LIVE_CONFIG_RAW_URL =
      "https://raw.githubusercontent.com/nickgamerr44/Nick-Craft/main/live_config.json"
  }
}
