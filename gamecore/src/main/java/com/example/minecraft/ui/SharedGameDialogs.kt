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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.gamecore.R
import com.example.minecraft.data.WorldInfo
import com.example.minecraft.data.WorldStorage
import com.example.minecraft.engine.AudioEngine
import com.example.minecraft.model.GameMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

val NickCraftPixelFont = FontFamily(
  Font(R.font.press_start_2p, FontWeight.Normal)
)

@Composable
fun BedrockNewWorldDialog(
  onDismiss: () -> Unit,
  onCreate: (name: String, mode: GameMode, seed: Long) -> Unit
) {
  var worldName by remember { mutableStateOf("Meu Mundo") }
  var gameMode by remember { mutableStateOf(GameMode.SURVIVAL) }
  var seedText by remember { mutableStateOf("${Random.nextInt(10000, 99999)}") }

  Dialog(onDismissRequest = onDismiss) {
    Box(
      modifier = Modifier
        .fillMaxWidth(0.75f)
        .border(2.dp, BedrockColors.CardBorder, RoundedCornerShape(8.dp))
        .background(BedrockColors.DialogBg, RoundedCornerShape(8.dp))
        .padding(18.dp)
        .testTag("create_world_dialog")
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Criar Novo Mundo",
            color = BedrockColors.TextWhite,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
          Text(
            text = "✕",
            color = Color.LightGray,
            fontSize = 18.sp,
            modifier = Modifier.clickable { onDismiss() }
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Column(modifier = Modifier.weight(1f)) {
            OutlinedTextField(
              value = worldName,
              onValueChange = { worldName = it },
              label = { Text("Nome do Mundo", color = Color.Gray, fontSize = 11.sp) },
              colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = BedrockColors.ButtonGreenTop,
                unfocusedBorderColor = BedrockColors.CardBorder
              ),
              modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
              value = seedText,
              onValueChange = { seedText = it },
              label = { Text("Semente (Seed)", color = Color.Gray, fontSize = 11.sp) },
              colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = BedrockColors.ButtonGreenTop,
                unfocusedBorderColor = BedrockColors.CardBorder
              ),
              modifier = Modifier.fillMaxWidth()
            )
          }

          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Modo de Jogo:",
              color = BedrockColors.TextGray,
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              BedrockButton(
                text = "Sobrevivência",
                isGreen = gameMode == GameMode.SURVIVAL,
                onClick = { gameMode = GameMode.SURVIVAL },
                fontSize = 11,
                modifier = Modifier.weight(1f)
              )
              BedrockButton(
                text = "Criativo",
                isGreen = gameMode == GameMode.CREATIVE,
                onClick = { gameMode = GameMode.CREATIVE },
                fontSize = 11,
                modifier = Modifier.weight(1f)
              )
            }
            Text(
              text = if (gameMode == GameMode.SURVIVAL)
                "Vida ❤️, Fome 🍖, monstros à noite e coleta de recursos."
              else
                "Blocos ilimitados, voo livre e quebra instantânea.",
              color = Color.LightGray,
              fontSize = 10.sp,
              fontFamily = FontFamily.Monospace,
              modifier = Modifier.padding(top = 6.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End
        ) {
          BedrockButton(
            text = "Cancelar",
            onClick = onDismiss,
            modifier = Modifier.padding(end = 8.dp)
          )
          BedrockButton(
            text = "Criar Mundo!",
            isGreen = true,
            onClick = {
              val seed = seedText.toLongOrNull() ?: seedText.hashCode().toLong()
              onCreate(worldName.ifBlank { "Mundo" }, gameMode, seed)
            },
            modifier = Modifier.testTag("confirm_create_world_button")
          )
        }
      }
    }
  }
}

@Composable
fun BedrockLoadWorldDialog(
  storage: WorldStorage,
  onDismiss: () -> Unit,
  onCreateNew: () -> Unit,
  onSelectWorld: (WorldInfo) -> Unit
) {
  var worlds by remember { mutableStateOf(storage.listWorlds()) }
  val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

  Dialog(onDismissRequest = onDismiss) {
    Box(
      modifier = Modifier
        .fillMaxWidth(0.85f)
        .fillMaxHeight(0.85f)
        .border(2.dp, BedrockColors.CardBorder, RoundedCornerShape(8.dp))
        .background(BedrockColors.DialogBg, RoundedCornerShape(8.dp))
        .padding(16.dp)
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Mundos Salvos",
            color = BedrockColors.TextWhite,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
          Text(
            text = "✕",
            color = Color.LightGray,
            fontSize = 18.sp,
            modifier = Modifier.clickable { onDismiss() }
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (worlds.isEmpty()) {
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth(),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = "Nenhum mundo salvo encontrado.",
                color = BedrockColors.TextGray,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
              )
              Spacer(modifier = Modifier.height(10.dp))
              BedrockButton(
                text = "Criar Meu Primeiro Mundo",
                isGreen = true,
                onClick = onCreateNew
              )
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            items(worlds) { w ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(BedrockColors.CardBg, RoundedCornerShape(4.dp))
                  .border(1.dp, BedrockColors.CardBorder, RoundedCornerShape(4.dp))
                  .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = w.name,
                    color = BedrockColors.TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                  )
                  Text(
                    text = "${w.gameMode.displayName} • Semente: ${w.seed} • ${dateFormat.format(Date(w.lastPlayed))}",
                    color = BedrockColors.TextGray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                  )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  BedrockButton(
                    text = "Jogar",
                    isGreen = true,
                    onClick = { onSelectWorld(w) }
                  )
                  BedrockButton(
                    text = "🗑️",
                    onClick = {
                      storage.deleteWorld(w.id)
                      worlds = storage.listWorlds()
                    }
                  )
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          BedrockButton(
            text = "+ Criar Novo",
            onClick = onCreateNew
          )
          BedrockButton(
            text = "Voltar",
            onClick = onDismiss
          )
        }
      }
    }
  }
}

@Composable
fun BedrockOptionsDialog(
  sensitivity: Float,
  onSensitivityChange: (Float) -> Unit,
  onOpenHelp: () -> Unit,
  onDismiss: () -> Unit
) {
  var soundOn by remember { mutableStateOf(AudioEngine.isSoundEnabled) }

  Dialog(onDismissRequest = onDismiss) {
    Box(
      modifier = Modifier
        .fillMaxWidth(0.65f)
        .border(2.dp, BedrockColors.CardBorder, RoundedCornerShape(8.dp))
        .background(BedrockColors.DialogBg, RoundedCornerShape(8.dp))
        .padding(18.dp)
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Configurações",
            color = BedrockColors.TextWhite,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
          Text(
            text = "✕",
            color = Color.LightGray,
            fontSize = 18.sp,
            modifier = Modifier.clickable { onDismiss() }
          )
        }

        Spacer(modifier = Modifier.height(14.dp))

        BedrockButton(
          text = if (soundOn) "Sons do Jogo: Ativados" else "Sons do Jogo: Desativados",
          isGreen = soundOn,
          onClick = {
            soundOn = !soundOn
            AudioEngine.isSoundEnabled = soundOn
          },
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        BedrockButton(
          text = "Como Jogar / Controles",
          onClick = onOpenHelp,
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
          text = "Sensibilidade do Toque (Câmera): ${(sensitivity * 100).toInt()}%",
          color = Color.White,
          fontSize = 12.sp,
          fontFamily = FontFamily.Monospace
        )

        Slider(
          value = sensitivity,
          onValueChange = onSensitivityChange,
          valueRange = 0.5f..2.5f,
          colors = SliderDefaults.colors(
            thumbColor = BedrockColors.ButtonGreenTop,
            activeTrackColor = BedrockColors.ButtonGreenTop
          )
        )

        Spacer(modifier = Modifier.height(14.dp))

        BedrockButton(
          text = "Concluído",
          isGreen = true,
          onClick = onDismiss,
          modifier = Modifier.fillMaxWidth()
        )
      }
    }
  }
}
