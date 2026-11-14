package com.example.minecraft.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.minecraft.model.ItemCategory
import com.example.minecraft.model.ItemType

/**
 * Custom 16x16 pixel-art icon renderer giving every single ItemType its own bespoke sprite.
 * Replaces generic system emojis across the hotbar, inventory, recipes, and selection preview.
 */
@Composable
fun ItemIcon(
  item: ItemType,
  modifier: Modifier = Modifier,
  size: Dp = 24.dp
) {
  Canvas(modifier = modifier.size(size)) {
    when (item) {
      // Blocks (14 unique icons)
      ItemType.BLOCK_GRASS -> drawBlockGrass()
      ItemType.BLOCK_DIRT -> drawBlockDirt()
      ItemType.BLOCK_STONE -> drawBlockStone()
      ItemType.BLOCK_COBBLESTONE -> drawBlockCobblestone()
      ItemType.BLOCK_OAK_LOG -> drawBlockOakLog()
      ItemType.BLOCK_OAK_PLANKS -> drawBlockOakPlanks()
      ItemType.BLOCK_LEAVES -> drawBlockLeaves()
      ItemType.BLOCK_SAND -> drawBlockSand()
      ItemType.BLOCK_GLASS -> drawBlockGlass()
      ItemType.BLOCK_BRICK -> drawBlockBrick()
      ItemType.BLOCK_CRAFTING_TABLE -> drawBlockCraftingTable()
      ItemType.BLOCK_FURNACE -> drawBlockFurnace()
      ItemType.BLOCK_TNT -> drawBlockTnt()
      ItemType.BLOCK_TORCH -> drawTorchItem()

      // Materials (5 unique icons)
      ItemType.STICK -> drawStickItem()
      ItemType.COAL -> drawCoalItem()
      ItemType.IRON_INGOT -> drawIngotItem(
        highlight = Color(0xFFF3F6FA),
        mid = Color(0xFFBDC6D1),
        shadow = Color(0xFF6B7582),
        outline = Color(0xFF2B313A)
      )
      ItemType.GOLD_INGOT -> drawIngotItem(
        highlight = Color(0xFFFFF59D),
        mid = Color(0xFFFFD54F),
        shadow = Color(0xFFC68400),
        outline = Color(0xFF4A3000)
      )
      ItemType.DIAMOND -> drawDiamondItem()

      // Pickaxes (4 unique icons)
      ItemType.WOODEN_PICKAXE -> drawPickaxeItem(
        headLight = Color(0xFFBC8A4F),
        headMid = Color(0xFF8F6232),
        headDark = Color(0xFF5D3E1C)
      )
      ItemType.STONE_PICKAXE -> drawPickaxeItem(
        headLight = Color(0xFFB5BAC4),
        headMid = Color(0xFF828894),
        headDark = Color(0xFF4E535C)
      )
      ItemType.IRON_PICKAXE -> drawPickaxeItem(
        headLight = Color(0xFFFFFFFF),
        headMid = Color(0xFFC9D1DB),
        headDark = Color(0xFF7B8594)
      )
      ItemType.DIAMOND_PICKAXE -> drawPickaxeItem(
        headLight = Color(0xFF9FF8FF),
        headMid = Color(0xFF33D8E6),
        headDark = Color(0xFF138696)
      )

      // Swords (4 unique icons)
      ItemType.WOODEN_SWORD -> drawSwordItem(
        bladeLight = Color(0xFFC79558),
        bladeMid = Color(0xFF966734),
        bladeDark = Color(0xFF61401B),
        guardColor = Color(0xFF5C3A1B)
      )
      ItemType.STONE_SWORD -> drawSwordItem(
        bladeLight = Color(0xFFC2C7D1),
        bladeMid = Color(0xFF8A909C),
        bladeDark = Color(0xFF555B66),
        guardColor = Color(0xFF4B4F57)
      )
      ItemType.IRON_SWORD -> drawSwordItem(
        bladeLight = Color(0xFFFFFFFF),
        bladeMid = Color(0xFFCCD4DF),
        bladeDark = Color(0xFF7E8896),
        guardColor = Color(0xFF5D6673)
      )
      ItemType.DIAMOND_SWORD -> drawSwordItem(
        bladeLight = Color(0xFFA8FAFF),
        bladeMid = Color(0xFF34DCEB),
        bladeDark = Color(0xFF148999),
        guardColor = Color(0xFF106C78)
      )

      // Foods (4 unique icons)
      ItemType.APPLE -> drawAppleItem()
      ItemType.RAW_PORKCHOP -> drawPorkchopItem(cooked = false)
      ItemType.COOKED_PORKCHOP -> drawPorkchopItem(cooked = true)
      ItemType.ROTTEN_FLESH -> drawRottenFleshItem()
    }
  }
}

@Composable
fun CategoryTabIcon(
  category: ItemCategory,
  modifier: Modifier = Modifier,
  size: Dp = 18.dp
) {
  val sampleItem = when (category) {
    ItemCategory.BLOCK -> ItemType.BLOCK_BRICK
    ItemCategory.TOOL -> ItemType.DIAMOND_PICKAXE
    ItemCategory.WEAPON -> ItemType.DIAMOND_SWORD
    ItemCategory.MATERIAL -> ItemType.DIAMOND
    ItemCategory.FOOD -> ItemType.COOKED_PORKCHOP
  }
  ItemIcon(item = sampleItem, modifier = modifier, size = size)
}

private fun DrawScope.px(x: Int, y: Int, w: Int = 1, h: Int = 1, color: Color) {
  val cellW = size.width / 16f
  val cellH = size.height / 16f
  drawRect(
    color = color,
    topLeft = Offset(x * cellW, y * cellH),
    size = Size(w * cellW + 0.4f, h * cellH + 0.4f)
  )
}

private fun DrawScope.drawBlockShell(
  topColor: Color,
  frontColor: Color,
  sideColor: Color,
  borderColor: Color = Color(0xFF1D1F24)
) {
  // Clean 14x14 framed block tile with beveled 3D top/front/right edge highlights
  px(1, 1, 14, 14, borderColor)
  px(2, 2, 12, 12, frontColor)
  px(2, 2, 12, 3, topColor)
  px(11, 5, 3, 9, sideColor)
}

private fun DrawScope.drawBlockGrass() {
  drawBlockShell(
    topColor = Color(0xFF66C240),
    frontColor = Color(0xFF866043),
    sideColor = Color(0xFF6B4B33)
  )
  // Grass fringe
  px(2, 4, 12, 2, Color(0xFF4EA02E))
  px(3, 6, 2, 1, Color(0xFF4EA02E))
  px(7, 6, 3, 2, Color(0xFF4EA02E))
  px(12, 6, 2, 1, Color(0xFF3E8223))
  // Dirt speckles
  px(4, 9, 2, 1, Color(0xFF63442E))
  px(9, 10, 1, 2, Color(0xFF9F7554))
  px(5, 12, 2, 1, Color(0xFF5A3E29))
}

private fun DrawScope.drawBlockDirt() {
  drawBlockShell(
    topColor = Color(0xFF9E7250),
    frontColor = Color(0xFF866043),
    sideColor = Color(0xFF6B4B33)
  )
  px(3, 5, 2, 2, Color(0xFF63442E))
  px(8, 7, 2, 1, Color(0xFFA97B58))
  px(5, 10, 3, 2, Color(0xFF5C3E2A))
  px(10, 11, 2, 2, Color(0xFF523724))
  px(4, 3, 3, 1, Color(0xFFB38664))
}

private fun DrawScope.drawBlockStone() {
  drawBlockShell(
    topColor = Color(0xFF9E9E9E),
    frontColor = Color(0xFF7D7D7D),
    sideColor = Color(0xFF616161)
  )
  px(3, 6, 4, 1, Color(0xFF666666))
  px(6, 9, 5, 1, Color(0xFF939393))
  px(3, 11, 3, 1, Color(0xFF595959))
  px(9, 6, 2, 2, Color(0xFF8C8C8C))
}

private fun DrawScope.drawBlockCobblestone() {
  drawBlockShell(
    topColor = Color(0xFF8B8B8B),
    frontColor = Color(0xFF707070),
    sideColor = Color(0xFF555555)
  )
  val mortar = Color(0xFF3E3E3E)
  px(2, 5, 12, 1, mortar)
  px(2, 10, 12, 1, mortar)
  px(7, 2, 1, 8, mortar)
  px(4, 6, 1, 4, mortar)
  px(10, 6, 1, 8, mortar)
  px(3, 3, 3, 1, Color(0xFFA2A2A2))
  px(8, 7, 2, 2, Color(0xFF8F8F8F))
  px(3, 11, 5, 2, Color(0xFF838383))
}

private fun DrawScope.drawBlockOakLog() {
  drawBlockShell(
    topColor = Color(0xFFB8945F),
    frontColor = Color(0xFF6B5032),
    sideColor = Color(0xFF523C24)
  )
  // Concentric ring center on top edge
  px(4, 2, 8, 2, Color(0xFF9C7948))
  px(6, 2, 4, 1, Color(0xFFD2B078))
  // Vertical bark grooves
  px(4, 5, 1, 9, Color(0xFF4E3921))
  px(7, 5, 1, 9, Color(0xFF856440))
  px(10, 5, 1, 9, Color(0xFF47331D))
}

private fun DrawScope.drawBlockOakPlanks() {
  drawBlockShell(
    topColor = Color(0xFFC29B62),
    frontColor = Color(0xFFA27C47),
    sideColor = Color(0xFF846337)
  )
  val seam = Color(0xFF6C4F29)
  px(2, 5, 12, 1, seam)
  px(2, 9, 12, 1, seam)
  px(2, 12, 12, 1, seam)
  px(8, 2, 1, 3, seam)
  px(5, 6, 1, 3, seam)
  px(10, 10, 1, 2, seam)
}

private fun DrawScope.drawBlockLeaves() {
  px(1, 1, 14, 14, Color(0xFF173612))
  px(2, 2, 12, 12, Color(0xFF2D6E22))
  // Leaf highlights & natural holes
  px(3, 3, 3, 2, Color(0xFF4BA137))
  px(8, 2, 4, 3, Color(0xFF5BB844))
  px(6, 5, 2, 2, Color(0xFF173612))
  px(10, 8, 2, 2, Color(0xFF173612))
  px(3, 9, 4, 3, Color(0xFF4BA137))
  px(8, 11, 5, 2, Color(0xFF3E8C2D))
}

private fun DrawScope.drawBlockSand() {
  drawBlockShell(
    topColor = Color(0xFFF0E4A8),
    frontColor = Color(0xFFDCC98A),
    sideColor = Color(0xFFBFAE72)
  )
  px(4, 4, 2, 1, Color(0xFFF6ECC0))
  px(8, 7, 3, 1, Color(0xFFC5B173))
  px(3, 10, 2, 1, Color(0xFFEDE0A2))
  px(9, 12, 2, 1, Color(0xFFA9975E))
}

private fun DrawScope.drawBlockGlass() {
  px(1, 1, 14, 14, Color(0xFF24495C))
  px(2, 2, 12, 12, Color(0xFFA8E2F5))
  px(3, 3, 10, 10, Color(0x44DCF5FF))
  // Crisp white glare streaks
  px(4, 4, 2, 2, Color.White)
  px(6, 6, 2, 2, Color.White)
  px(10, 9, 2, 2, Color(0xDDFFFFFF))
  px(3, 3, 10, 1, Color(0xFFE3F9FF))
  px(3, 3, 1, 10, Color(0xFFE3F9FF))
}

private fun DrawScope.drawBlockBrick() {
  drawBlockShell(
    topColor = Color(0xFFB55A49),
    frontColor = Color(0xFF9C4334),
    sideColor = Color(0xFF7D3327)
  )
  val mortar = Color(0xFFCCC6C0)
  px(2, 5, 12, 1, mortar)
  px(2, 9, 12, 1, mortar)
  px(7, 2, 1, 3, mortar)
  px(4, 6, 1, 3, mortar)
  px(11, 6, 1, 3, mortar)
  px(8, 10, 1, 4, mortar)
}

private fun DrawScope.drawBlockCraftingTable() {
  drawBlockShell(
    topColor = Color(0xFFC79B5E),
    frontColor = Color(0xFF9E7440),
    sideColor = Color(0xFF7E5B31)
  )
  // 3x3 crafting grid lines on top
  val darkWood = Color(0xFF5B3D1C)
  px(5, 2, 1, 3, darkWood)
  px(9, 2, 1, 3, darkWood)
  px(2, 4, 12, 1, darkWood)
  // Side saw & hammer tools
  px(4, 7, 2, 5, Color(0xFF6B7380))
  px(4, 6, 3, 1, Color(0xFF9AA3B0))
  px(9, 7, 2, 5, Color(0xFF4E545E))
  px(8, 7, 4, 2, Color(0xFF9AA3B0))
}

private fun DrawScope.drawBlockFurnace() {
  drawBlockShell(
    topColor = Color(0xFF999999),
    frontColor = Color(0xFF7A7A7A),
    sideColor = Color(0xFF5E5E5E)
  )
  // Upper firebox mouth & glowing lower embers
  px(4, 5, 7, 3, Color(0xFF2B2B2B))
  px(5, 6, 5, 2, Color(0xFF151515))
  px(4, 10, 7, 3, Color(0xFF261712))
  px(5, 11, 5, 2, Color(0xFFFF7A18))
  px(6, 11, 3, 1, Color(0xFFFFD54F))
}

private fun DrawScope.drawBlockTnt() {
  px(1, 1, 14, 14, Color(0xFF331111))
  // Fuse top
  px(7, 0, 2, 2, Color(0xFF2B2B2B))
  px(8, 0, 1, 1, Color(0xFFFFB74D))
  // Red upper & lower bands
  px(2, 2, 12, 4, Color(0xFFD63226))
  px(2, 10, 12, 4, Color(0xFFD63226))
  px(11, 2, 3, 4, Color(0xFFA82218))
  px(11, 10, 3, 4, Color(0xFFA82218))
  // Center white band
  px(2, 6, 12, 4, Color(0xFFF2F2F2))
  // Crisp "TNT" letters
  px(3, 7, 3, 1, Color.Black)
  px(4, 7, 1, 3, Color.Black)
  px(7, 7, 1, 3, Color.Black)
  px(8, 7, 1, 1, Color.Black)
  px(9, 7, 1, 3, Color.Black)
  px(11, 7, 3, 1, Color.Black)
  px(12, 7, 1, 3, Color.Black)
}

private fun DrawScope.drawTorchItem() {
  // Glow halo
  px(5, 1, 6, 5, Color(0x33FFAA22))
  // Flame
  px(6, 2, 4, 4, Color(0xFFFF7D1A))
  px(7, 1, 2, 2, Color(0xFFFFC107))
  px(7, 3, 2, 2, Color(0xFFFFFFB5))
  // Charcoal collar
  px(7, 6, 2, 1, Color(0xFF3B2C20))
  // Wooden handle
  px(7, 7, 2, 7, Color(0xFF8D6034))
  px(8, 7, 1, 7, Color(0xFF6B4724))
}

private fun DrawScope.drawStickItem() {
  val outline = Color(0xFF362210)
  val woodLight = Color(0xFFB57F49)
  val woodDark = Color(0xFF7D5229)
  for (i in 2..12) {
    val x = 14 - i
    val y = i
    px(x - 1, y, 3, 2, outline)
    px(x, y, 2, 1, woodLight)
    px(x, y + 1, 1, 1, woodDark)
  }
}

private fun DrawScope.drawCoalItem() {
  px(4, 3, 8, 10, Color(0xFF121316))
  px(3, 5, 10, 6, Color(0xFF121316))
  px(4, 4, 8, 8, Color(0xFF282B30))
  px(5, 5, 3, 2, Color(0xFF565C66))
  px(7, 8, 4, 3, Color(0xFF1B1D21))
  px(5, 10, 3, 1, Color(0xFF3E434C))
}

private fun DrawScope.drawIngotItem(
  highlight: Color,
  mid: Color,
  shadow: Color,
  outline: Color
) {
  px(2, 6, 12, 6, outline)
  px(4, 4, 8, 2, outline)
  px(4, 5, 8, 2, highlight)
  px(3, 7, 10, 3, mid)
  px(3, 10, 10, 1, shadow)
  px(10, 7, 3, 3, shadow)
  px(5, 6, 3, 1, Color.White)
}

private fun DrawScope.drawDiamondItem() {
  px(4, 2, 8, 12, Color(0xFF0A4D57))
  px(2, 5, 12, 6, Color(0xFF0A4D57))
  px(5, 3, 6, 10, Color(0xFF25D2E3))
  px(3, 6, 10, 4, Color(0xFF25D2E3))
  px(4, 4, 4, 3, Color(0xFFA6FBFF))
  px(5, 5, 2, 2, Color.White)
  px(9, 8, 3, 3, Color(0xFF129AA8))
}

private fun DrawScope.drawPickaxeItem(
  headLight: Color,
  headMid: Color,
  headDark: Color
) {
  val outline = Color(0xFF1E1F24)
  val handleLight = Color(0xFF9C6937)
  val handleDark = Color(0xFF6B4420)

  // Diagonal wooden handle from bottom-left (2,13) to top-right (11,4)
  for (i in 0..8) {
    val x = 3 + i
    val y = 12 - i
    px(x - 1, y, 2, 2, outline)
    px(x, y, 1, 1, handleLight)
    px(x, y + 1, 1, 1, handleDark)
  }

  // Curved pickaxe head at top-right
  px(6, 2, 6, 2, outline)
  px(11, 3, 3, 6, outline)
  px(7, 2, 4, 2, headMid)
  px(11, 3, 2, 2, headLight)
  px(12, 5, 2, 4, headMid)
  px(12, 8, 1, 2, headDark)
  px(6, 3, 2, 1, headDark)
}

private fun DrawScope.drawSwordItem(
  bladeLight: Color,
  bladeMid: Color,
  bladeDark: Color,
  guardColor: Color
) {
  val outline = Color(0xFF1B1C22)
  val handle = Color(0xFF7B4F28)

  // Handle + pommel bottom-left
  px(2, 12, 2, 2, outline)
  px(3, 11, 2, 2, handle)
  px(4, 10, 2, 2, handle)

  // Crossguard
  px(4, 8, 2, 2, guardColor)
  px(5, 9, 2, 2, guardColor)
  px(6, 10, 2, 2, guardColor)

  // Diagonal blade to top-right
  for (i in 0..6) {
    val x = 6 + i
    val y = 8 - i
    px(x, y, 3, 3, outline)
    px(x + 1, y, 1, 1, bladeLight)
    px(x + 1, y + 1, 1, 1, bladeMid)
    px(x + 2, y + 1, 1, 1, bladeDark)
  }
  px(13, 1, 2, 2, outline)
  px(13, 2, 1, 1, bladeLight)
}

private fun DrawScope.drawAppleItem() {
  // Stem & leaf
  px(7, 1, 2, 3, Color(0xFF5D3A1A))
  px(9, 2, 3, 2, Color(0xFF4CAF50))
  // Outline + body
  px(4, 4, 8, 10, Color(0xFF5E0D0D))
  px(3, 5, 10, 7, Color(0xFF5E0D0D))
  px(4, 5, 8, 8, Color(0xFFD92323))
  px(5, 4, 6, 1, Color(0xFFE53935))
  // Highlight + shadow
  px(5, 6, 2, 3, Color(0xFFFF8A80))
  px(10, 8, 2, 4, Color(0xFF9E1515))
}

private fun DrawScope.drawPorkchopItem(cooked: Boolean) {
  val outline = if (cooked) Color(0xFF472512) else Color(0xFF782834)
  val meat = if (cooked) Color(0xFF9E5A34) else Color(0xFFE88692)
  val highlight = if (cooked) Color(0xFFC98050) else Color(0xFFF6B5BE)
  val fat = if (cooked) Color(0xFFE8C89B) else Color(0xFFFCE6EA)

  px(3, 4, 10, 8, outline)
  px(4, 5, 8, 6, meat)
  px(5, 5, 5, 2, fat)
  px(6, 7, 4, 2, highlight)
  px(10, 8, 2, 2, outline)
}

private fun DrawScope.drawRottenFleshItem() {
  px(3, 4, 10, 8, Color(0xFF2E3B1E))
  px(4, 5, 8, 6, Color(0xFF6B5C3E))
  px(5, 5, 3, 3, Color(0xFF4E7A38))
  px(9, 8, 3, 2, Color(0xFF3E632D))
  px(6, 9, 2, 2, Color(0xFF8A4B3C))
}
