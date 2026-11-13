package com.example.minecraft.model

enum class BlockFace {
  TOP, BOTTOM, NORTH, SOUTH, EAST, WEST
}

enum class BlockType(
  val id: Byte,
  val displayName: String,
  val isSolid: Boolean = true,
  val isTransparent: Boolean = false,
  val hardness: Float = 1.0f,
  val isUnbreakable: Boolean = false,
  val isLightSource: Boolean = false,
  val lightLevel: Int = 0,
  val topTextureIndex: Int = 0,
  val bottomTextureIndex: Int = 0,
  val sideTextureIndex: Int = 0,
  val frontTextureIndex: Int = 0
) {
  AIR(id = 0, displayName = "Ar", isSolid = false, isTransparent = true, hardness = 0f),

  GRASS(
    id = 1,
    displayName = "Bloco de Grama",
    hardness = 0.6f,
    topTextureIndex = 0,
    bottomTextureIndex = 2,
    sideTextureIndex = 1
  ),

  DIRT(
    id = 2,
    displayName = "Terra",
    hardness = 0.5f,
    topTextureIndex = 2,
    bottomTextureIndex = 2,
    sideTextureIndex = 2
  ),

  STONE(
    id = 3,
    displayName = "Pedra",
    hardness = 1.5f,
    topTextureIndex = 3,
    bottomTextureIndex = 3,
    sideTextureIndex = 3
  ),

  COBBLESTONE(
    id = 4,
    displayName = "Pedregulho",
    hardness = 2.0f,
    topTextureIndex = 4,
    bottomTextureIndex = 4,
    sideTextureIndex = 4
  ),

  OAK_LOG(
    id = 5,
    displayName = "Tronco de Carvalho",
    hardness = 2.0f,
    topTextureIndex = 6,
    bottomTextureIndex = 6,
    sideTextureIndex = 5
  ),

  OAK_PLANKS(
    id = 6,
    displayName = "Tábuas de Carvalho",
    hardness = 2.0f,
    topTextureIndex = 7,
    bottomTextureIndex = 7,
    sideTextureIndex = 7
  ),

  LEAVES(
    id = 7,
    displayName = "Folhas de Carvalho",
    hardness = 0.2f,
    isTransparent = true,
    topTextureIndex = 8,
    bottomTextureIndex = 8,
    sideTextureIndex = 8
  ),

  SAND(
    id = 8,
    displayName = "Areia",
    hardness = 0.5f,
    topTextureIndex = 9,
    bottomTextureIndex = 9,
    sideTextureIndex = 9
  ),

  WATER(
    id = 9,
    displayName = "Água",
    isSolid = false,
    isTransparent = true,
    hardness = 100f,
    topTextureIndex = 10,
    bottomTextureIndex = 10,
    sideTextureIndex = 10
  ),

  BEDROCK(
    id = 10,
    displayName = "Rocha Matriz",
    hardness = 9999f,
    isUnbreakable = true,
    topTextureIndex = 11,
    bottomTextureIndex = 11,
    sideTextureIndex = 11
  ),

  COAL_ORE(
    id = 11,
    displayName = "Minério de Carvão",
    hardness = 3.0f,
    topTextureIndex = 12,
    bottomTextureIndex = 12,
    sideTextureIndex = 12
  ),

  IRON_ORE(
    id = 12,
    displayName = "Minério de Ferro",
    hardness = 3.0f,
    topTextureIndex = 13,
    bottomTextureIndex = 13,
    sideTextureIndex = 13
  ),

  GOLD_ORE(
    id = 13,
    displayName = "Minério de Ouro",
    hardness = 3.0f,
    topTextureIndex = 14,
    bottomTextureIndex = 14,
    sideTextureIndex = 14
  ),

  DIAMOND_ORE(
    id = 14,
    displayName = "Minério de Diamante",
    hardness = 3.5f,
    topTextureIndex = 15,
    bottomTextureIndex = 15,
    sideTextureIndex = 15
  ),

  GLASS(
    id = 15,
    displayName = "Vidro",
    hardness = 0.3f,
    isTransparent = true,
    topTextureIndex = 16,
    bottomTextureIndex = 16,
    sideTextureIndex = 16
  ),

  BRICK(
    id = 16,
    displayName = "Tijolos",
    hardness = 2.0f,
    topTextureIndex = 17,
    bottomTextureIndex = 17,
    sideTextureIndex = 17
  ),

  CRAFTING_TABLE(
    id = 17,
    displayName = "Bancada de Trabalho",
    hardness = 2.5f,
    topTextureIndex = 18,
    bottomTextureIndex = 7,
    sideTextureIndex = 19
  ),

  FURNACE(
    id = 18,
    displayName = "Fornalha",
    hardness = 3.5f,
    topTextureIndex = 4,
    bottomTextureIndex = 4,
    sideTextureIndex = 4,
    frontTextureIndex = 20
  ),

  TNT(
    id = 19,
    displayName = "Dinamite (TNT)",
    hardness = 0.0f,
    topTextureIndex = 21,
    bottomTextureIndex = 21,
    sideTextureIndex = 22
  ),

  TORCH(
    id = 20,
    displayName = "Tocha",
    isSolid = false,
    isTransparent = true,
    isLightSource = true,
    lightLevel = 14,
    hardness = 0.0f,
    topTextureIndex = 23,
    bottomTextureIndex = 23,
    sideTextureIndex = 23
  ),

  RED_FLOWER(
    id = 21,
    displayName = "Papoula Vermelha",
    isSolid = false,
    isTransparent = true,
    hardness = 0.0f,
    topTextureIndex = 24,
    bottomTextureIndex = 24,
    sideTextureIndex = 24
  ),

  YELLOW_FLOWER(
    id = 22,
    displayName = "Dente-de-Leão",
    isSolid = false,
    isTransparent = true,
    hardness = 0.0f,
    topTextureIndex = 25,
    bottomTextureIndex = 25,
    sideTextureIndex = 25
  );

  fun getTextureIndex(face: BlockFace): Int {
    return when (face) {
      BlockFace.TOP -> topTextureIndex
      BlockFace.BOTTOM -> bottomTextureIndex
      BlockFace.NORTH -> if (frontTextureIndex != 0) frontTextureIndex else sideTextureIndex
      else -> sideTextureIndex
    }
  }

  companion object {
    private val ID_MAP = entries.associateBy { it.id }
    fun fromId(id: Byte): BlockType = ID_MAP[id] ?: AIR
  }
}
