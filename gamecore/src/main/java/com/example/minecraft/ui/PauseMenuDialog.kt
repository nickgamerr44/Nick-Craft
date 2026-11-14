package com.example.minecraft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.example.minecraft.multiplayer.MAX_MULTIPLAYER_PLAYERS
import com.example.minecraft.multiplayer.MultiplayerRole
import com.example.minecraft.multiplayer.MultiplayerSessionState

@Composable
fun PauseMenuDialog(
  onResume: () -> Unit,
  onSaveAndQuit: () -> Unit,
  versionTag: String = "0.03",
  sessionState: MultiplayerSessionState = MultiplayerSessionState(),
  canHostOnline: Boolean = true,
  needsGoogleSignInForServer: Boolean = false,
  onGoogleSignInClick: (() -> Unit)? = null,
  onToggleServer: (() -> Unit)? = null
) {
  Dialog(onDismissRequest = onResume) {
    Box(
      modifier = Modifier
        .width(350.dp)
        .border(2.dp, Color(0xFF555555), RoundedCornerShape(4.dp))
        .background(BedrockColors.DialogBg, RoundedCornerShape(4.dp))
        .padding(18.dp)
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

        Spacer(modifier = Modifier.height(14.dp))

        // Resume Button
        BedrockButton(
          text = "Voltar ao Jogo",
          isGreen = true,
          onClick = onResume,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("resume_button")
        )

        if (canHostOnline && onToggleServer != null) {
          Spacer(modifier = Modifier.height(10.dp))

          Column(
            modifier = Modifier
              .fillMaxWidth()
              .background(BedrockColors.CardBg, RoundedCornerShape(4.dp))
              .border(1.dp, BedrockColors.CardBorder, RoundedCornerShape(4.dp))
              .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Text(
              text = "Multiplayer • Limite $MAX_MULTIPLAYER_PLAYERS Jogadores",
              color = BedrockColors.TextYellow,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace
            )

            if (sessionState.isActive) {
              Text(
                text = "Código: ${sessionState.serverCode} • ${sessionState.currentPlayers}/${sessionState.maxPlayers} online",
                color = BedrockColors.TextGreen,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
              )
              if (sessionState.allPlayerNames.isNotEmpty()) {
                Text(
                  text = "Conectados: ${sessionState.allPlayerNames.joinToString(", ")}",
                  color = BedrockColors.TextGray,
                  fontSize = 10.sp,
                  fontFamily = FontFamily.Monospace
                )
              }
              BedrockButton(
                text = if (sessionState.role == MultiplayerRole.HOST) "Encerrar Servidor" else "Desconectar do Servidor",
                onClick = onToggleServer,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("close_server_button")
              )
            } else if (needsGoogleSignInForServer && onGoogleSignInClick != null) {
              Text(
                text = "Entre com sua conta Google para abrir este mundo como servidor.",
                color = BedrockColors.TextGray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
              )
              BedrockButton(
                text = "Entrar com Google",
                isGreen = true,
                onClick = onGoogleSignInClick,
                modifier = Modifier.fillMaxWidth()
              )
            } else {
              Text(
                text = "Transforme seu mundo atual em um servidor para até 5 amigos.",
                color = BedrockColors.TextGray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
              )
              BedrockButton(
                text = "Transformar Mundo em Servidor (Máx. 5)",
                isGreen = true,
                enabled = !sessionState.isBusy,
                onClick = onToggleServer,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("host_current_world_button")
              )
            }

            if (sessionState.errorMessage != null) {
              Text(
                text = sessionState.errorMessage,
                color = Color(0xFFFF6B6B),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Save & Quit
        BedrockButton(
          text = "Salvar e Sair para o Menu",
          onClick = onSaveAndQuit,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("save_and_quit_button")
        )
      }
    }
  }
}
