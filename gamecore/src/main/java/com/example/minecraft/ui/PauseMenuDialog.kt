package com.example.minecraft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun PauseMenuDialog(
  onResume: () -> Unit,
  onSaveAndQuit: () -> Unit,
  versionTag: String = "0.00"
) {
  Dialog(onDismissRequest = onResume) {
    Box(
      modifier = Modifier
        .width(320.dp)
        .border(2.dp, Color(0xFF555555), RoundedCornerShape(4.dp))
        .background(BedrockColors.DialogBg, RoundedCornerShape(4.dp))
        .padding(20.dp)
        .testTag("pause_menu_dialog")
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        ClassicShadowText(
          text = "Nick Craft - $versionTag",
          color = BedrockColors.TextWhite,
          shadowColor = BedrockColors.TextShadow,
          fontSize = 17
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Resume Button
        BedrockButton(
          text = "Back to game",
          isGreen = true,
          onClick = onResume,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("resume_button")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Save & Quit
        BedrockButton(
          text = "Quit to title",
          onClick = onSaveAndQuit,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("save_and_quit_button")
        )
      }
    }
  }
}
