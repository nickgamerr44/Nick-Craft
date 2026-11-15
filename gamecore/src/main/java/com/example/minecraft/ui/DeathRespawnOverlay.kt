package com.example.minecraft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DeathRespawnOverlay(
  attackerName: String?,
  spawnX: Float,
  spawnZ: Float,
  onRespawn: () -> Unit,
  onExitToTitle: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color(0xB85C0B0B))
      .pointerInput(Unit) {
        detectTapGestures { /* Consume touch inputs while dead */ }
      }
      .padding(24.dp)
      .testTag("death_overlay"),
    contentAlignment = Alignment.Center
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 340.dp)
        .fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      ClassicShadowText(
        text = "Você morreu!",
        color = Color.White,
        shadowColor = Color(0xFF2B0000),
        fontSize = 24,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag("death_title")
      )

      Text(
        text = if (!attackerName.isNullOrBlank()) {
          "Eliminado por $attackerName"
        } else {
          "Sua vida chegou a 0"
        },
        color = Color(0xFFFFD6D6),
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag("death_reason")
      )

      Text(
        text = "Ponto de Spawn: x=${spawnX.toInt()}, z=${spawnZ.toInt()}",
        color = BedrockColors.TextYellow,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(6.dp))

      BedrockButton(
        text = "Renascer no Spawn",
        onClick = onRespawn,
        isGreen = true,
        singleLine = true,
        fontSize = 14,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("respawn_button")
      )

      BedrockButton(
        text = "Sair para o Menu",
        onClick = onExitToTitle,
        isGreen = false,
        singleLine = true,
        fontSize = 13,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("death_exit_button")
      )
    }
  }
}
