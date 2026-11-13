package com.example.minecraft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.minecraft.engine.AudioEngine
import com.example.minecraft.model.ItemCategory
import com.example.minecraft.model.ItemStack
import com.example.minecraft.model.Player
import com.example.minecraft.model.RecipeBook

@Composable
fun InventoryDialog(
  player: Player,
  hasCraftingTable: Boolean,
  onDismiss: () -> Unit
) {
  var selectedCategory by remember { mutableStateOf<ItemCategory?>(null) }
  var selectedSlotIndex by remember { mutableStateOf<Int?>(player.inventory.selectedHotbarIndex) }
  var refreshTrigger by remember { mutableIntStateOf(0) }

  val filteredRecipes = remember(selectedCategory) {
    if (selectedCategory == null) RecipeBook.RECIPES
    else RecipeBook.RECIPES.filter { it.category == selectedCategory }
  }

  // Helper to handle slot clicks (select, swap, stack)
  fun handleSlotClick(clickedIndex: Int) {
    val currentSelected = selectedSlotIndex
    if (currentSelected == null) {
      // First select
      selectedSlotIndex = clickedIndex
      if (clickedIndex < 9) {
        player.inventory.selectedHotbarIndex = clickedIndex
      }
      AudioEngine.playPop()
    } else if (currentSelected == clickedIndex) {
      // Clicked same slot -> keep it or select hotbar
      if (clickedIndex < 9) {
        player.inventory.selectedHotbarIndex = clickedIndex
      }
    } else {
      // Clicked different slot -> SWAP or STACK!
      val stackA = player.inventory.slots[currentSelected]
      val stackB = player.inventory.slots[clickedIndex]

      if (stackA != null && stackB != null && stackA.item == stackB.item && stackA.item.maxStackSize > 1) {
        // Stack together
        val space = stackB.item.maxStackSize - stackB.count
        val move = minOf(space, stackA.count)
        stackB.count += move
        stackA.count -= move
        if (stackA.count <= 0) {
          player.inventory.slots[currentSelected] = null
        }
      } else {
        // Direct swap
        player.inventory.slots[currentSelected] = stackB
        player.inventory.slots[clickedIndex] = stackA
      }

      if (clickedIndex < 9) {
        player.inventory.selectedHotbarIndex = clickedIndex
      }
      selectedSlotIndex = clickedIndex
      AudioEngine.playPop()
      refreshTrigger++
    }
  }

  Dialog(onDismissRequest = onDismiss) {
    Box(
      modifier = Modifier
        .fillMaxWidth(0.95f)
        .fillMaxHeight(0.94f)
        .border(2.dp, BedrockColors.CardBorder, RoundedCornerShape(8.dp))
        .background(BedrockColors.DialogBg, RoundedCornerShape(8.dp))
        .padding(12.dp)
        .testTag("inventory_dialog")
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = if (hasCraftingTable) "Bancada 3x3 • Nick Craft 0.01" else "Select Block / Inventário (0.01)",
            color = BedrockColors.TextWhite,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
          Text(
            text = "✕",
            color = Color.LightGray,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
              .clickable { onDismiss() }
              .padding(4.dp)
              .testTag("close_inventory_button")
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Main 2-Column Bedrock Layout
        Row(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          // Left Column: Recipe Book & Categories
          Column(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
              .background(BedrockColors.CardBg, RoundedCornerShape(6.dp))
              .border(1.dp, BedrockColors.CardBorder, RoundedCornerShape(6.dp))
              .padding(8.dp)
          ) {
            // Category Tabs Row
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              CategoryTab(
                icon = "🧱",
                isSelected = selectedCategory == ItemCategory.BLOCK,
                onClick = { selectedCategory = if (selectedCategory == ItemCategory.BLOCK) null else ItemCategory.BLOCK },
                modifier = Modifier.weight(1f)
              )
              CategoryTab(
                icon = "⛏️",
                isSelected = selectedCategory == ItemCategory.TOOL,
                onClick = { selectedCategory = if (selectedCategory == ItemCategory.TOOL) null else ItemCategory.TOOL },
                modifier = Modifier.weight(1f)
              )
              CategoryTab(
                icon = "🗡️",
                isSelected = selectedCategory == ItemCategory.WEAPON,
                onClick = { selectedCategory = if (selectedCategory == ItemCategory.WEAPON) null else ItemCategory.WEAPON },
                modifier = Modifier.weight(1f)
              )
              CategoryTab(
                icon = "🍖",
                isSelected = selectedCategory == ItemCategory.FOOD,
                onClick = { selectedCategory = if (selectedCategory == ItemCategory.FOOD) null else ItemCategory.FOOD },
                modifier = Modifier.weight(1f)
              )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Recipes List
            LazyColumn(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              items(filteredRecipes) { recipe ->
                val canMake = RecipeBook.canCraft(recipe, player.inventory, hasCraftingTable)
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .background(
                      if (canMake) Color(0x334CAF50) else Color(0x221B1E24),
                      RoundedCornerShape(4.dp)
                    )
                    .border(
                      1.dp,
                      if (canMake) BedrockColors.ButtonGreenTop else BedrockColors.CardBorder,
                      RoundedCornerShape(4.dp)
                    )
                    .padding(6.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(text = recipe.result.item.iconEmoji, fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                      Text(
                        text = recipe.name,
                        color = if (canMake) BedrockColors.TextWhite else BedrockColors.TextGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                      )
                      val reqStr = recipe.ingredients.joinToString(", ") { "${it.count}x ${it.item.displayName}" }
                      Text(
                        text = reqStr,
                        color = if (canMake) BedrockColors.TextGreen else Color(0xFFE57373),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                      )
                    }
                  }

                  BedrockButton(
                    text = "Criar",
                    isGreen = canMake,
                    enabled = canMake,
                    fontSize = 10,
                    onClick = {
                      if (RecipeBook.craft(recipe, player.inventory, hasCraftingTable)) {
                        AudioEngine.playDing()
                        refreshTrigger++
                      }
                    }
                  )
                }
              }
            }
          }

          // Right Column: Player Inventory (27) & Hotbar (9) with Tap-to-Swap!
          Column(
            modifier = Modifier
              .weight(1.35f)
              .fillMaxHeight()
              .background(BedrockColors.CardBg, RoundedCornerShape(6.dp))
              .border(1.dp, BedrockColors.CardBorder, RoundedCornerShape(6.dp))
              .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = "Mochila (Toque para selecionar e trocar):",
              color = BedrockColors.TextWhite,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )

            // 27 Backpack Slots
            LazyVerticalGrid(
              columns = GridCells.Fixed(9),
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(3.dp),
              verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
              items(27) { i ->
                val slotIdx = i + 9
                val stack = player.inventory.slots[slotIdx]
                val isSelected = (slotIdx == selectedSlotIndex)

                Box(
                  modifier = Modifier
                    .size(32.dp)
                    .background(
                      if (isSelected) Color(0xDD2E7D32) else BedrockColors.SlotBg,
                      RoundedCornerShape(3.dp)
                    )
                    .border(
                      1.5.dp,
                      if (isSelected) Color(0xFF55FF55) else BedrockColors.SlotBorder,
                      RoundedCornerShape(3.dp)
                    )
                    .clickable { handleSlotClick(slotIdx) },
                  contentAlignment = Alignment.Center
                ) {
                  if (stack != null) {
                    Text(text = stack.item.iconEmoji, fontSize = 16.sp)
                    if (stack.count > 1) {
                      Text(
                        text = "${stack.count}",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                          .align(Alignment.BottomEnd)
                          .padding(end = 1.dp)
                      )
                    }
                  }
                }
              }
            }

            // Bottom 9-Slot Hotbar
            Column {
              Text(
                text = "Barra Rápida (Slots 1 a 9):",
                color = BedrockColors.TextGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
              )
              Spacer(modifier = Modifier.height(2.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
              ) {
                for (i in 0 until 9) {
                  val stack = player.inventory.slots[i]
                  val isSelected = (i == selectedSlotIndex)
                  val isActiveHotbar = (i == player.inventory.selectedHotbarIndex)

                  Box(
                    modifier = Modifier
                      .size(32.dp)
                      .background(
                        if (isSelected) Color(0xDD2E7D32) else if (isActiveHotbar) Color(0xAA3B4554) else BedrockColors.SlotBg,
                        RoundedCornerShape(3.dp)
                      )
                      .border(
                        1.5.dp,
                        if (isSelected) Color(0xFF55FF55) else if (isActiveHotbar) BedrockColors.SlotSelectedBorder else BedrockColors.ButtonGreenBorder,
                        RoundedCornerShape(3.dp)
                      )
                      .clickable { handleSlotClick(i) },
                    contentAlignment = Alignment.Center
                  ) {
                    if (stack != null) {
                      Text(text = stack.item.iconEmoji, fontSize = 16.sp)
                      if (stack.count > 1) {
                        Text(
                          text = "${stack.count}",
                          color = Color.White,
                          fontSize = 8.sp,
                          fontWeight = FontWeight.Bold,
                          fontFamily = FontFamily.Monospace,
                          modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 1.dp)
                        )
                      }
                    }
                  }
                }
              }
            }

            // Selected Item Actions Bar
            val selIdx = selectedSlotIndex
            val active = if (selIdx != null) player.inventory.slots[selIdx] else null

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x66181A20), RoundedCornerShape(4.dp))
                .padding(6.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              if (active != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(text = active.item.iconEmoji, fontSize = 16.sp)
                  Spacer(modifier = Modifier.width(6.dp))
                  Column {
                    Text(
                      text = active.item.displayName,
                      color = BedrockColors.TextYellow,
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      fontFamily = FontFamily.Monospace
                    )
                    Text(
                      text = "Qtd: ${active.count} • ${active.item.category.name}",
                      color = BedrockColors.TextGray,
                      fontSize = 9.sp,
                      fontFamily = FontFamily.Monospace
                    )
                  }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  // If item in backpack, offer quick equip to active hotbar slot
                  if (selIdx != null && selIdx >= 9) {
                    BedrockButton(
                      text = "Equipar na Barra",
                      isGreen = true,
                      fontSize = 10,
                      onClick = {
                        val hotbarIdx = player.inventory.selectedHotbarIndex
                        val temp = player.inventory.slots[hotbarIdx]
                        player.inventory.slots[hotbarIdx] = player.inventory.slots[selIdx]
                        player.inventory.slots[selIdx] = temp
                        selectedSlotIndex = hotbarIdx
                        AudioEngine.playPop()
                        refreshTrigger++
                      }
                    )
                  }

                  if (active.item.category == ItemCategory.FOOD) {
                    BedrockButton(
                      text = "Comer 🍖",
                      isGreen = true,
                      fontSize = 10,
                      onClick = {
                        if (player.eat(active.item)) {
                          AudioEngine.playPop()
                          if (active.count > 1) active.count-- else player.inventory.slots[selIdx!!] = null
                          refreshTrigger++
                        }
                      }
                    )
                  }
                }
              } else {
                Text(
                  text = "Dica: Toque em um item e depois em outro para trocar de lugar!",
                  color = BedrockColors.TextGray,
                  fontSize = 10.sp,
                  fontFamily = FontFamily.Monospace
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CategoryTab(
  icon: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .height(32.dp)
      .background(
        if (isSelected) BedrockColors.ButtonGreenTop else Color(0xFF282C35),
        RoundedCornerShape(4.dp)
      )
      .border(
        1.dp,
        if (isSelected) Color.White else BedrockColors.CardBorder,
        RoundedCornerShape(4.dp)
      )
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    Text(text = icon, fontSize = 14.sp)
  }
}
