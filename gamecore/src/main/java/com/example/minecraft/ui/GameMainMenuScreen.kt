package com.example.minecraft.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.minecraft.data.WorldStorage
import com.example.minecraft.engine.AudioEngine
import com.example.minecraft.model.GameMode

@Composable
fun GameMainMenuScreen(
  storage: WorldStorage,
  versionTag: String = "0.00",
  onStartGame: (worldName: String, gameMode: GameMode, seed: Long, worldId: String?) -> Unit,
  onExitToLauncher: (() -> Unit)? = null
) {
  var showNewWorldDialog by remember { mutableStateOf(false) }
  var showLoadWorldDialog by remember { mutableStateOf(false) }
  var showHelpDialog by remember { mutableStateOf(false) }
  var showOptionsDialog by remember { mutableStateOf(false) }
  var sensitivity by remember { mutableFloatStateOf(1.0f) }

  val worlds = remember { storage.listWorlds() }

  if (onExitToLauncher != null) {
    BackHandler(enabled = !showNewWorldDialog && !showLoadWorldDialog && !showHelpDialog && !showOptionsDialog) {
      onExitToLauncher()
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF0B0C0F))
      .windowInsetsPadding(WindowInsets.safeDrawing)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(8.dp)
        .testTag("game_main_menu_screen"),
      verticalArrangement = Arrangement.Top,
      horizontalAlignment = Alignment.Start
    ) {
      Text(
        text = "Nick Craft",
        color = Color(0xFFFFFFFF),
        fontSize = 18.sp,
        fontFamily = NickCraftPixelFont,
        modifier = Modifier
          .padding(8.dp)
          .testTag("textview1")
      )

      Box(
        modifier = Modifier
          .widthIn(max = 300.dp)
          .fillMaxWidth()
          .minimumInteractiveComponentSize()
          .clip(RoundedCornerShape(4.dp))
          .background(Color(0xFF26242E))
          .clickable {
            AudioEngine.playPop()
            if (worlds.isNotEmpty()) {
              showLoadWorldDialog = true
            } else {
              showNewWorldDialog = true
            }
          }
          .padding(8.dp)
          .testTag("button1"),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "Jogar",
          fontSize = 16.sp,
          color = Color(0xFFFFFFFF),
          modifier = Modifier.padding(vertical = 4.dp)
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      Box(
        modifier = Modifier
          .widthIn(max = 300.dp)
          .fillMaxWidth()
          .minimumInteractiveComponentSize()
          .clip(RoundedCornerShape(4.dp))
          .background(Color(0xFF26242E))
          .clickable {
            AudioEngine.playPop()
            showOptionsDialog = true
          }
          .padding(8.dp)
          .testTag("button2"),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "Configuração",
          fontSize = 16.sp,
          color = Color(0xFFFFFFFF),
          modifier = Modifier.padding(vertical = 4.dp)
        )
      }

      if (worlds.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Box(
          modifier = Modifier
            .widthIn(max = 300.dp)
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF26242E))
            .clickable {
              AudioEngine.playPop()
              showLoadWorldDialog = true
            }
            .padding(8.dp)
            .testTag("saved_worlds_button"),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "Mundos Salvos (${worlds.size})",
            fontSize = 15.sp,
            color = Color(0xFFFFFFFF),
            modifier = Modifier.padding(vertical = 4.dp)
          )
        }
      }

      if (onExitToLauncher != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Box(
          modifier = Modifier
            .widthIn(max = 300.dp)
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF1A1B22))
            .clickable {
              AudioEngine.playPop()
              onExitToLauncher()
            }
            .padding(8.dp)
            .testTag("back_to_launcher_button"),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "Voltar ao Launcher",
            fontSize = 14.sp,
            color = Color(0xFFB0B4C2),
            modifier = Modifier.padding(vertical = 4.dp)
          )
        }
      }

      Spacer(modifier = Modifier.weight(1f))

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Start
      ) {
        Text(
          text = "Versão $versionTag",
          color = Color(0xFFFFFFFF),
          fontSize = 14.sp,
          modifier = Modifier
            .padding(8.dp)
            .testTag("textview2")
        )
      }
    }

    if (showNewWorldDialog) {
      BedrockNewWorldDialog(
        onDismiss = { showNewWorldDialog = false },
        onCreate = { name, mode, seed ->
          showNewWorldDialog = false
          onStartGame(name, mode, seed, null)
        }
      )
    }

    if (showLoadWorldDialog) {
      BedrockLoadWorldDialog(
        storage = storage,
        onDismiss = { showLoadWorldDialog = false },
        onCreateNew = {
          showLoadWorldDialog = false
          showNewWorldDialog = true
        },
        onSelectWorld = { world ->
          showLoadWorldDialog = false
          onStartGame(world.name, world.gameMode, world.seed, world.id)
        }
      )
    }

    if (showHelpDialog) {
      HelpGuideDialog(onDismiss = { showHelpDialog = false })
    }

    if (showOptionsDialog) {
      BedrockOptionsDialog(
        sensitivity = sensitivity,
        onSensitivityChange = { sensitivity = it },
        onOpenHelp = {
          showOptionsDialog = false
          showHelpDialog = true
        },
        onDismiss = { showOptionsDialog = false }
      )
    }
  }
}
