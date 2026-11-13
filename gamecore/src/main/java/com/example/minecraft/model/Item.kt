package com.example.minecraft.model

enum class ItemCategory {
  BLOCK, TOOL, WEAPON, MATERIAL, FOOD
}

enum class ToolTier(val miningSpeedMultiplier: Float, val attackDamage: Float, val durability: Int) {
  NONE(1.0f, 1.0f, 0),
  WOOD(2.0f, 4.0f, 60),
  STONE(4.0f, 5.0f, 132),
  IRON(6.0f, 6.0f, 251),
  DIAMOND(8.0f, 7.0f, 1562)
}

enum class ItemType(
  val id: Int,
  val displayName: String,
  val category: ItemCategory,
  val maxStackSize: Int = 64,
  val blockType: BlockType? = null,
  val toolTier: ToolTier = ToolTier.NONE,
  val isPickaxe: Boolean = false,
  val isSword: Boolean = false,
  val foodRestoration: Int = 0,
  val iconEmoji: String = ""
) {
  // Block items
  BLOCK_GRASS(1, "Bloco de Grama", ItemCategory.BLOCK, blockType = BlockType.GRASS, iconEmoji = "🟩"),
  BLOCK_DIRT(2, "Terra", ItemCategory.BLOCK, blockType = BlockType.DIRT, iconEmoji = "🟫"),
  BLOCK_STONE(3, "Pedra", ItemCategory.BLOCK, blockType = BlockType.STONE, iconEmoji = "🪨"),
  BLOCK_COBBLESTONE(4, "Pedregulho", ItemCategory.BLOCK, blockType = BlockType.COBBLESTONE, iconEmoji = "🧱"),
  BLOCK_OAK_LOG(5, "Tronco de Carvalho", ItemCategory.BLOCK, blockType = BlockType.OAK_LOG, iconEmoji = "🪵"),
  BLOCK_OAK_PLANKS(6, "Tábuas de Carvalho", ItemCategory.BLOCK, blockType = BlockType.OAK_PLANKS, iconEmoji = "🟫"),
  BLOCK_LEAVES(7, "Folhas", ItemCategory.BLOCK, blockType = BlockType.LEAVES, iconEmoji = "🍃"),
  BLOCK_SAND(8, "Areia", ItemCategory.BLOCK, blockType = BlockType.SAND, iconEmoji = "🏖️"),
  BLOCK_GLASS(9, "Vidro", ItemCategory.BLOCK, blockType = BlockType.GLASS, iconEmoji = "🪟"),
  BLOCK_BRICK(10, "Tijolos", ItemCategory.BLOCK, blockType = BlockType.BRICK, iconEmoji = "🧱"),
  BLOCK_CRAFTING_TABLE(11, "Bancada de Trabalho", ItemCategory.BLOCK, blockType = BlockType.CRAFTING_TABLE, iconEmoji = "⚒️"),
  BLOCK_FURNACE(12, "Fornalha", ItemCategory.BLOCK, blockType = BlockType.FURNACE, iconEmoji = "🔥"),
  BLOCK_TNT(13, "TNT", ItemCategory.BLOCK, blockType = BlockType.TNT, iconEmoji = "💣"),
  BLOCK_TORCH(14, "Tocha", ItemCategory.BLOCK, blockType = BlockType.TORCH, iconEmoji = "🕯️"),

  // Materials
  STICK(20, "Graveto", ItemCategory.MATERIAL, iconEmoji = "🥢"),
  COAL(21, "Carvão", ItemCategory.MATERIAL, iconEmoji = "⚫"),
  IRON_INGOT(22, "Barra de Ferro", ItemCategory.MATERIAL, iconEmoji = "🥈"),
  GOLD_INGOT(23, "Barra de Ouro", ItemCategory.MATERIAL, iconEmoji = "🥇"),
  DIAMOND(24, "Diamante", ItemCategory.MATERIAL, iconEmoji = "💎"),

  // Tools & Weapons
  WOODEN_PICKAXE(30, "Picareta de Madeira", ItemCategory.TOOL, maxStackSize = 1, toolTier = ToolTier.WOOD, isPickaxe = true, iconEmoji = "⛏️"),
  STONE_PICKAXE(31, "Picareta de Pedra", ItemCategory.TOOL, maxStackSize = 1, toolTier = ToolTier.STONE, isPickaxe = true, iconEmoji = "⛏️"),
  IRON_PICKAXE(32, "Picareta de Ferro", ItemCategory.TOOL, maxStackSize = 1, toolTier = ToolTier.IRON, isPickaxe = true, iconEmoji = "⛏️"),
  DIAMOND_PICKAXE(33, "Picareta de Diamante", ItemCategory.TOOL, maxStackSize = 1, toolTier = ToolTier.DIAMOND, isPickaxe = true, iconEmoji = "⛏️"),

  WOODEN_SWORD(40, "Espada de Madeira", ItemCategory.WEAPON, maxStackSize = 1, toolTier = ToolTier.WOOD, isSword = true, iconEmoji = "🗡️"),
  STONE_SWORD(41, "Espada de Pedra", ItemCategory.WEAPON, maxStackSize = 1, toolTier = ToolTier.STONE, isSword = true, iconEmoji = "🗡️"),
  IRON_SWORD(42, "Espada de Ferro", ItemCategory.WEAPON, maxStackSize = 1, toolTier = ToolTier.IRON, isSword = true, iconEmoji = "🗡️"),
  DIAMOND_SWORD(43, "Espada de Diamante", ItemCategory.WEAPON, maxStackSize = 1, toolTier = ToolTier.DIAMOND, isSword = true, iconEmoji = "🗡️"),

  // Foods
  APPLE(50, "Maçã", ItemCategory.FOOD, foodRestoration = 4, iconEmoji = "🍎"),
  RAW_PORKCHOP(51, "Costela Crua", ItemCategory.FOOD, foodRestoration = 3, iconEmoji = "🥩"),
  COOKED_PORKCHOP(52, "Costela Assada", ItemCategory.FOOD, foodRestoration = 8, iconEmoji = "🍖"),
  ROTTEN_FLESH(53, "Carne Podre", ItemCategory.FOOD, foodRestoration = 2, iconEmoji = "🧟");

  companion object {
    fun fromBlock(block: BlockType): ItemType? {
      return when (block) {
        BlockType.GRASS -> BLOCK_DIRT
        BlockType.DIRT -> BLOCK_DIRT
        BlockType.STONE -> BLOCK_COBBLESTONE
        BlockType.COBBLESTONE -> BLOCK_COBBLESTONE
        BlockType.OAK_LOG -> BLOCK_OAK_LOG
        BlockType.OAK_PLANKS -> BLOCK_OAK_PLANKS
        BlockType.LEAVES -> APPLE // chance to drop apple
        BlockType.SAND -> BLOCK_SAND
        BlockType.GLASS -> null // breaks into nothing without silk touch
        BlockType.BRICK -> BLOCK_BRICK
        BlockType.CRAFTING_TABLE -> BLOCK_CRAFTING_TABLE
        BlockType.FURNACE -> BLOCK_FURNACE
        BlockType.TNT -> BLOCK_TNT
        BlockType.TORCH -> BLOCK_TORCH
        BlockType.COAL_ORE -> COAL
        BlockType.IRON_ORE -> BLOCK_COBBLESTONE // or iron ingot
        BlockType.DIAMOND_ORE -> DIAMOND
        else -> null
      }
    }
  }
}

data class ItemStack(
  val item: ItemType,
  var count: Int = 1
)

class Inventory {
  // 9 Hotbar slots + 27 Main inventory slots = 36 total slots
  val slots = Array<ItemStack?>(36) { null }
  var selectedHotbarIndex: Int = 0

  val selectedItem: ItemStack?
    get() = slots[selectedHotbarIndex]

  fun addItem(item: ItemType, count: Int = 1): Boolean {
    var remaining = count

    // Try stacking into existing slots first
    if (item.maxStackSize > 1) {
      for (i in 0 until 36) {
        val current = slots[i]
        if (current != null && current.item == item && current.count < item.maxStackSize) {
          val space = item.maxStackSize - current.count
          val toAdd = minOf(space, remaining)
          current.count += toAdd
          remaining -= toAdd
          if (remaining <= 0) return true
        }
      }
    }

    // Put into empty slots
    for (i in 0 until 36) {
      if (slots[i] == null) {
        val toAdd = minOf(item.maxStackSize, remaining)
        slots[i] = ItemStack(item, toAdd)
        remaining -= toAdd
        if (remaining <= 0) return true
      }
    }

    return remaining < count
  }

  fun consumeSelectedItem(): Boolean {
    val current = slots[selectedHotbarIndex] ?: return false
    if (current.count > 1) {
      current.count--
    } else {
      slots[selectedHotbarIndex] = null
    }
    return true
  }

  fun removeItem(item: ItemType, count: Int): Boolean {
    var needed = count
    // Check if we have enough
    var available = 0
    for (i in 0 until 36) {
      val s = slots[i]
      if (s != null && s.item == item) available += s.count
    }
    if (available < count) return false

    // Deduct
    for (i in 0 until 36) {
      val s = slots[i]
      if (s != null && s.item == item) {
        val take = minOf(s.count, needed)
        s.count -= take
        needed -= take
        if (s.count <= 0) slots[i] = null
        if (needed <= 0) break
      }
    }
    return true
  }

  fun hasItem(item: ItemType, count: Int = 1): Boolean {
    var found = 0
    for (i in 0 until 36) {
      val s = slots[i]
      if (s != null && s.item == item) found += s.count
    }
    return found >= count
  }

  fun setupStartingKit(creative: Boolean) {
    if (creative) {
      // Provide endless creative blocks
      slots[0] = ItemStack(ItemType.BLOCK_GRASS, 64)
      slots[1] = ItemStack(ItemType.BLOCK_COBBLESTONE, 64)
      slots[2] = ItemStack(ItemType.BLOCK_OAK_PLANKS, 64)
      slots[3] = ItemStack(ItemType.BLOCK_GLASS, 64)
      slots[4] = ItemStack(ItemType.BLOCK_BRICK, 64)
      slots[5] = ItemStack(ItemType.BLOCK_TNT, 64)
      slots[6] = ItemStack(ItemType.BLOCK_TORCH, 64)
      slots[7] = ItemStack(ItemType.DIAMOND_SWORD, 1)
      slots[8] = ItemStack(ItemType.DIAMOND_PICKAXE, 1)

      // Storage
      slots[9] = ItemStack(ItemType.BLOCK_OAK_LOG, 64)
      slots[10] = ItemStack(ItemType.BLOCK_SAND, 64)
      slots[11] = ItemStack(ItemType.BLOCK_LEAVES, 64)
      slots[12] = ItemStack(ItemType.BLOCK_CRAFTING_TABLE, 64)
      slots[13] = ItemStack(ItemType.BLOCK_FURNACE, 64)
      slots[14] = ItemStack(ItemType.DIAMOND, 64)
      slots[15] = ItemStack(ItemType.APPLE, 64)
    } else {
      // Survival kit
      slots[0] = ItemStack(ItemType.WOODEN_PICKAXE, 1)
      slots[1] = ItemStack(ItemType.BLOCK_OAK_LOG, 8)
      slots[2] = ItemStack(ItemType.APPLE, 6)
      slots[3] = ItemStack(ItemType.BLOCK_TORCH, 12)
    }
  }
}
