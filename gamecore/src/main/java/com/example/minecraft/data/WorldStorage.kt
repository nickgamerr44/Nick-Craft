package com.example.minecraft.data

import android.content.Context
import com.example.minecraft.model.BlockType
import com.example.minecraft.model.GameMode
import com.example.minecraft.model.ItemStack
import com.example.minecraft.model.ItemType
import com.example.minecraft.model.Player
import com.example.minecraft.model.VoxelWorld
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class WorldInfo(
  val id: String,
  val name: String,
  val gameMode: GameMode,
  val seed: Long,
  val lastPlayed: Long
)

class WorldStorage(private val context: Context) {
  private val worldsDir = File(context.filesDir, "minecraft_worlds").apply { mkdirs() }

  fun listWorlds(): List<WorldInfo> {
    val list = mutableListOf<WorldInfo>()
    val files = worldsDir.listFiles { f -> f.extension == "json" } ?: return emptyList()
    for (f in files) {
      try {
        val json = JSONObject(f.readText())
        val id = json.optString("id", f.nameWithoutExtension)
        val name = json.optString("name", "Mundo")
        val modeStr = json.optString("gameMode", GameMode.SURVIVAL.name)
        val gameMode = try { GameMode.valueOf(modeStr) } catch (_: Exception) { GameMode.SURVIVAL }
        val seed = json.optLong("seed", 12345L)
        val lastPlayed = json.optLong("lastPlayed", f.lastModified())
        list.add(WorldInfo(id, name, gameMode, seed, lastPlayed))
      } catch (_: Exception) {
      }
    }
    return list.sortedByDescending { it.lastPlayed }
  }

  fun saveWorld(
    worldInfo: WorldInfo,
    world: VoxelWorld,
    player: Player
  ) {
    try {
      val json = JSONObject()
      json.put("id", worldInfo.id)
      json.put("name", worldInfo.name)
      json.put("gameMode", worldInfo.gameMode.name)
      json.put("seed", worldInfo.seed)
      json.put("lastPlayed", System.currentTimeMillis())

      // Player data
      val playerJson = JSONObject()
      playerJson.put("x", player.x.toDouble())
      playerJson.put("y", player.y.toDouble())
      playerJson.put("z", player.z.toDouble())
      playerJson.put("yaw", player.yaw.toDouble())
      playerJson.put("pitch", player.pitch.toDouble())
      playerJson.put("health", player.health.toDouble())
      playerJson.put("hunger", player.hunger.toDouble())

      // Inventory
      val invArray = JSONArray()
      for (i in 0 until 36) {
        val slot = player.inventory.slots[i]
        if (slot != null) {
          val slotJson = JSONObject()
          slotJson.put("slot", i)
          slotJson.put("item", slot.item.name)
          slotJson.put("count", slot.count)
          invArray.put(slotJson)
        }
      }
      playerJson.put("inventory", invArray)
      json.put("player", playerJson)

      // Store world block diffs or raw blocks
      // For compact speed, store changed blocks as base64 or diff list
      val blockBytes = world.blocks
      val base64Blocks = android.util.Base64.encodeToString(blockBytes, android.util.Base64.NO_WRAP)
      json.put("blocksBase64", base64Blocks)

      val file = File(worldsDir, "${worldInfo.id}.json")
      file.writeText(json.toString())
    } catch (_: Exception) {
    }
  }

  fun loadWorld(
    worldId: String,
    world: VoxelWorld,
    player: Player
  ): Boolean {
    val file = File(worldsDir, "$worldId.json")
    if (!file.exists()) return false

    return try {
      val json = JSONObject(file.readText())
      val b64 = json.optString("blocksBase64", "")
      if (b64.isNotEmpty()) {
        val bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
        System.arraycopy(bytes, 0, world.blocks, 0, minOf(bytes.size, world.blocks.size))
        world.isDirty = true
      }

      val playerJson = json.optJSONObject("player")
      if (playerJson != null) {
        player.x = playerJson.optDouble("x", 24.5).toFloat()
        player.y = playerJson.optDouble("y", 20.0).toFloat()
        player.z = playerJson.optDouble("z", 24.5).toFloat()
        player.yaw = playerJson.optDouble("yaw", 0.0).toFloat()
        player.pitch = playerJson.optDouble("pitch", 0.0).toFloat()
        player.health = playerJson.optDouble("health", 20.0).toFloat()
        player.hunger = playerJson.optDouble("hunger", 20.0).toFloat()

        // Clear inventory and load
        for (i in 0 until 36) player.inventory.slots[i] = null
        val invArray = playerJson.optJSONArray("inventory")
        if (invArray != null) {
          for (i in 0 until invArray.length()) {
            val itemObj = invArray.getJSONObject(i)
            val slot = itemObj.getInt("slot")
            val itemName = itemObj.getString("item")
            val count = itemObj.getInt("count")
            try {
              val itemType = ItemType.valueOf(itemName)
              if (slot in 0 until 36) {
                player.inventory.slots[slot] = ItemStack(itemType, count)
              }
            } catch (_: Exception) {
            }
          }
        }
      }
      true
    } catch (_: Exception) {
      false
    }
  }

  fun deleteWorld(worldId: String): Boolean {
    val file = File(worldsDir, "$worldId.json")
    return file.delete()
  }
}
