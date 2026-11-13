package com.example.minecraft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.minecraft.model.RecipeBook

@Composable
fun HelpGuideDialog(
  onDismiss: () -> Unit
) {
  Dialog(onDismissRequest = onDismiss) {
    Box(
      modifier = Modifier
        .fillMaxWidth(0.90f)
        .fillMaxHeight(0.88f)
        .border(2.dp, BedrockColors.CardBorder, RoundedCornerShape(8.dp))
        .background(BedrockColors.DialogBg, RoundedCornerShape(8.dp))
        .padding(16.dp)
        .testTag("help_guide_dialog")
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        Text(
          text = "Guia do CraftCraft (Bedrock Edition)",
          color = BedrockColors.TextYellow,
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace,
          modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
        ) {
          item {
            Text(
              text = "🎮 Controles na Tela Deitada:",
              color = BedrockColors.TextAqua,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace,
              fontSize = 14.sp
            )
            Text(
              text = "• D-Pad em Cruz (Esquerda): Andar e esquivar\n" +
                  "• Botão Central ◆ do D-Pad: Alternar Agachar (Sneak)\n" +
                  "• Arrastar o Dedo na Tela: Girar a câmera suavemente\n" +
                  "• Segurar o Dedo no Bloco: Começa a quebrar o bloco até coletar!\n" +
                  "• Toque Rápido no Bloco/Chão: Coloca o bloco da mão ou interage com bancadas!\n" +
                  "• Toque Rápido no Monstro: Ataca com sua espada ou punho!\n" +
                  "• Botão ▲ Pular: Pula 1 bloco (ou voa no Criativo)\n" +
                  "• Botão [•••] na Barra Rápida: Abre o inventário e receitas Bedrock!\n" +
                  "• Botão ❚❚ Superior: Pausa o jogo e permite Sair do Mundo!",
              color = Color.White,
              fontFamily = FontFamily.Monospace,
              fontSize = 12.sp,
              modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
              text = "📜 Receitas de Fabricação:",
              color = BedrockColors.TextGold,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace,
              fontSize = 14.sp
            )
          }

          items(RecipeBook.RECIPES) { recipe ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .background(BedrockColors.CardBg, RoundedCornerShape(4.dp))
                .border(1.dp, BedrockColors.CardBorder, RoundedCornerShape(4.dp))
                .padding(8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  text = "${recipe.result.item.iconEmoji} ${recipe.name}",
                  color = BedrockColors.TextGreen,
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp,
                  fontFamily = FontFamily.Monospace
                )
                val ingText = recipe.ingredients.joinToString(", ") { "${it.count}x ${it.item.displayName}" }
                Text(
                  text = "Requer: $ingText",
                  color = BedrockColors.TextGray,
                  fontSize = 10.sp,
                  fontFamily = FontFamily.Monospace
                )
              }
              if (recipe.requiresCraftingTable) {
                Text(
                  text = "[Bancada]",
                  color = BedrockColors.TextGold,
                  fontSize = 10.sp,
                  fontFamily = FontFamily.Monospace
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        BedrockButton(
          text = "Concluído",
          isGreen = true,
          onClick = onDismiss,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("close_help_button")
        )
      }
    }
  }
}
