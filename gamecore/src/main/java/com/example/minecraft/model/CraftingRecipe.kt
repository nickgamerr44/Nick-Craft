package com.example.minecraft.model

data class CraftingIngredient(
  val item: ItemType,
  val count: Int = 1
)

data class Recipe(
  val id: String,
  val name: String,
  val result: ItemStack,
  val ingredients: List<CraftingIngredient>,
  val requiresCraftingTable: Boolean = false,
  val category: ItemCategory = ItemCategory.MATERIAL
)

object RecipeBook {
  val RECIPES = listOf(
    // Basic Materials
    Recipe(
      id = "planks_from_log",
      name = "Tábuas de Carvalho (4x)",
      result = ItemStack(ItemType.BLOCK_OAK_PLANKS, 4),
      ingredients = listOf(CraftingIngredient(ItemType.BLOCK_OAK_LOG, 1)),
      category = ItemCategory.BLOCK
    ),
    Recipe(
      id = "sticks_from_planks",
      name = "Gravetos (4x)",
      result = ItemStack(ItemType.STICK, 4),
      ingredients = listOf(CraftingIngredient(ItemType.BLOCK_OAK_PLANKS, 2)),
      category = ItemCategory.MATERIAL
    ),
    Recipe(
      id = "crafting_table",
      name = "Bancada de Trabalho",
      result = ItemStack(ItemType.BLOCK_CRAFTING_TABLE, 1),
      ingredients = listOf(CraftingIngredient(ItemType.BLOCK_OAK_PLANKS, 4)),
      category = ItemCategory.BLOCK
    ),
    Recipe(
      id = "torches",
      name = "Tochas (4x)",
      result = ItemStack(ItemType.BLOCK_TORCH, 4),
      ingredients = listOf(
        CraftingIngredient(ItemType.COAL, 1),
        CraftingIngredient(ItemType.STICK, 1)
      ),
      category = ItemCategory.BLOCK
    ),

    // Tools
    Recipe(
      id = "wooden_pickaxe",
      name = "Picareta de Madeira",
      result = ItemStack(ItemType.WOODEN_PICKAXE, 1),
      ingredients = listOf(
        CraftingIngredient(ItemType.BLOCK_OAK_PLANKS, 3),
        CraftingIngredient(ItemType.STICK, 2)
      ),
      requiresCraftingTable = true,
      category = ItemCategory.TOOL
    ),
    Recipe(
      id = "wooden_sword",
      name = "Espada de Madeira",
      result = ItemStack(ItemType.WOODEN_SWORD, 1),
      ingredients = listOf(
        CraftingIngredient(ItemType.BLOCK_OAK_PLANKS, 2),
        CraftingIngredient(ItemType.STICK, 1)
      ),
      category = ItemCategory.WEAPON
    ),
    Recipe(
      id = "stone_pickaxe",
      name = "Picareta de Pedra",
      result = ItemStack(ItemType.STONE_PICKAXE, 1),
      ingredients = listOf(
        CraftingIngredient(ItemType.BLOCK_COBBLESTONE, 3),
        CraftingIngredient(ItemType.STICK, 2)
      ),
      requiresCraftingTable = true,
      category = ItemCategory.TOOL
    ),
    Recipe(
      id = "stone_sword",
      name = "Espada de Pedra",
      result = ItemStack(ItemType.STONE_SWORD, 1),
      ingredients = listOf(
        CraftingIngredient(ItemType.BLOCK_COBBLESTONE, 2),
        CraftingIngredient(ItemType.STICK, 1)
      ),
      category = ItemCategory.WEAPON
    ),
    Recipe(
      id = "diamond_pickaxe",
      name = "Picareta de Diamante",
      result = ItemStack(ItemType.DIAMOND_PICKAXE, 1),
      ingredients = listOf(
        CraftingIngredient(ItemType.DIAMOND, 3),
        CraftingIngredient(ItemType.STICK, 2)
      ),
      requiresCraftingTable = true,
      category = ItemCategory.TOOL
    ),
    Recipe(
      id = "diamond_sword",
      name = "Espada de Diamante",
      result = ItemStack(ItemType.DIAMOND_SWORD, 1),
      ingredients = listOf(
        CraftingIngredient(ItemType.DIAMOND, 2),
        CraftingIngredient(ItemType.STICK, 1)
      ),
      category = ItemCategory.WEAPON
    ),

    // Advanced Blocks
    Recipe(
      id = "furnace",
      name = "Fornalha",
      result = ItemStack(ItemType.BLOCK_FURNACE, 1),
      ingredients = listOf(CraftingIngredient(ItemType.BLOCK_COBBLESTONE, 8)),
      requiresCraftingTable = true,
      category = ItemCategory.BLOCK
    ),
    Recipe(
      id = "tnt",
      name = "TNT (Explosivo)",
      result = ItemStack(ItemType.BLOCK_TNT, 1),
      ingredients = listOf(
        CraftingIngredient(ItemType.BLOCK_SAND, 4),
        CraftingIngredient(ItemType.COAL, 2)
      ),
      requiresCraftingTable = true,
      category = ItemCategory.BLOCK
    ),
    Recipe(
      id = "glass_smelting",
      name = "Vidro (Fundição)",
      result = ItemStack(ItemType.BLOCK_GLASS, 2),
      ingredients = listOf(
        CraftingIngredient(ItemType.BLOCK_SAND, 2),
        CraftingIngredient(ItemType.COAL, 1)
      ),
      category = ItemCategory.BLOCK
    ),
    Recipe(
      id = "cooked_meat",
      name = "Assar Costela",
      result = ItemStack(ItemType.COOKED_PORKCHOP, 1),
      ingredients = listOf(
        CraftingIngredient(ItemType.RAW_PORKCHOP, 1),
        CraftingIngredient(ItemType.COAL, 1)
      ),
      category = ItemCategory.FOOD
    )
  )

  fun canCraft(recipe: Recipe, inventory: Inventory, hasCraftingTable: Boolean): Boolean {
    if (recipe.requiresCraftingTable && !hasCraftingTable) return false
    for (ing in recipe.ingredients) {
      if (!inventory.hasItem(ing.item, ing.count)) return false
    }
    return true
  }

  fun craft(recipe: Recipe, inventory: Inventory, hasCraftingTable: Boolean): Boolean {
    if (!canCraft(recipe, inventory, hasCraftingTable)) return false
    for (ing in recipe.ingredients) {
      inventory.removeItem(ing.item, ing.count)
    }
    inventory.addItem(recipe.result.item, recipe.result.count)
    return true
  }
}
