package com.example.minecraft.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Authentic Minecraft 0.0.0 (Pre-Classic / Cave Game) Retro Palette and Components
 */
object BedrockColors {
  // Classic 0.0.0 Dark Slate & Dirt UI colors
  val DialogBg = Color(0xF21C1C1C)
  val CardBg = Color(0xD92B2B2B)
  val CardBorder = Color(0xFF555555)
  val HeaderBg = Color(0xFF141414)
  val DirtBg = Color(0xFF2C1E14)
  val DirtPattern = Color(0xFF3D2A1C)
  
  // Classic 0.0.0 Stone Button Styles
  val ButtonGrayTop = Color(0xFF828282)
  val ButtonGrayBottom = Color(0xFF4C4C4C)
  val ButtonBorder = Color(0xFF222222)
  val ButtonHighlight = Color(0xFFAAAAAA)
  val ButtonShadow = Color(0xFF202020)
  
  // Green "Play" Button
  val ButtonGreenTop = Color(0xFF4CAF50)
  val ButtonGreenBottom = Color(0xFF2E7D32)
  val ButtonGreenBorder = Color(0xFF1B5E20)
  
  // Classic 0.0.0 Slots
  val SlotBg = Color(0x99000000)
  val SlotBorder = Color(0xFF3F3F3F)
  val SlotSelectedBorder = Color(0xFFFFFFFF)
  val SlotSelectedGlow = Color(0x88FFFFFF)

  // Texts
  val TextWhite = Color(0xFFFFFFFF)
  val TextYellow = Color(0xFFFFFF55)
  val TextGreen = Color(0xFF55FF55)
  val TextAqua = Color(0xFF55FFFF)
  val TextGold = Color(0xFFFFAA00)
  val TextGray = Color(0xFFAAAAAA)
  val TextShadow = Color(0xFF282828)
  
  // HUD
  val HudBg = Color(0xAA000000)
  val DpadBg = Color(0x77101010)
  val DpadBtn = Color(0xAA454545)
  val DpadBtnPressed = Color(0xDD3A8E3D)
  val DpadBorder = Color(0xBB686868)
}

@Composable
fun ClassicShadowText(
  text: String,
  modifier: Modifier = Modifier,
  color: Color = BedrockColors.TextWhite,
  shadowColor: Color = BedrockColors.TextShadow,
  fontSize: Int = 14,
  fontWeight: FontWeight = FontWeight.Bold,
  textAlign: TextAlign = TextAlign.Start
) {
  Box(modifier = modifier) {
    Text(
      text = text,
      color = shadowColor,
      fontSize = fontSize.sp,
      fontWeight = fontWeight,
      fontFamily = FontFamily.Monospace,
      textAlign = textAlign,
      modifier = Modifier.offset(x = 1.5.dp, y = 1.5.dp)
    )
    Text(
      text = text,
      color = color,
      fontSize = fontSize.sp,
      fontWeight = fontWeight,
      fontFamily = FontFamily.Monospace,
      textAlign = textAlign
    )
  }
}

@Composable
fun BedrockButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  isGreen: Boolean = false,
  enabled: Boolean = true,
  fontSize: Int = 13
) {
  val interactionSource = remember { MutableInteractionSource() }

  val gradient = if (!enabled) {
    Brush.verticalGradient(listOf(Color(0xFF333333), Color(0xFF242424)))
  } else if (isGreen) {
    Brush.verticalGradient(listOf(BedrockColors.ButtonGreenTop, BedrockColors.ButtonGreenBottom))
  } else {
    Brush.verticalGradient(listOf(BedrockColors.ButtonGrayTop, BedrockColors.ButtonGrayBottom))
  }

  val borderColor = if (!enabled) Color(0xFF1E1E1E) else if (isGreen) BedrockColors.ButtonGreenBorder else BedrockColors.ButtonBorder

  // Classic Minecraft 0.0.0 double-beveled stone button style
  Box(
    modifier = modifier
      .shadow(2.dp, RoundedCornerShape(2.dp))
      .border(BorderStroke(2.dp, borderColor), RoundedCornerShape(2.dp))
      .background(gradient, RoundedCornerShape(2.dp))
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClick = onClick
      )
      .padding(horizontal = 14.dp, vertical = 8.dp),
    contentAlignment = Alignment.Center
  ) {
    ClassicShadowText(
      text = text,
      color = if (enabled) BedrockColors.TextWhite else BedrockColors.TextGray,
      shadowColor = if (enabled) Color(0xFF202020) else Color(0xFF151515),
      fontSize = fontSize,
      textAlign = TextAlign.Center
    )
  }
}
