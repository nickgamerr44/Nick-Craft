package com.example.minecraft.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.minecraft.engine.AudioEngine
import com.example.minecraft.launcher.LauncherViewModel

@Composable
fun TitleScreen(
  onLaunchGame: (versionTag: String) -> Unit
) {
  val context = LocalContext.current
  val launcherViewModel: LauncherViewModel = viewModel(
    factory = remember(context.filesDir) { LauncherViewModel.factory(context.filesDir) }
  )
  val uiState by launcherViewModel.uiState.collectAsStateWithLifecycle()

  val selectedVersion = uiState.selectedVersion
  val isSelectedReadyToPlay = selectedVersion != null && selectedVersion.isDownloaded
  val selectedDownloadProgress = selectedVersion?.downloadProgress

  val primaryButtonLabel = when {
    selectedVersion == null -> "Sincronizar GitHub"
    isSelectedReadyToPlay -> "Iniciar Jogo (${selectedVersion.versionTag})"
    selectedDownloadProgress != null -> "Baixando (${(selectedDownloadProgress * 100).toInt()}%)..."
    else -> "Baixar via GitHub (${selectedVersion.versionTag})"
  }

  val openExternalUrl: (String) -> Unit = { url ->
    runCatching {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
      context.startActivity(intent)
    }
  }

  val onPrimaryButtonClick: () -> Unit = {
    when {
      selectedVersion == null -> {
        AudioEngine.playPop()
        launcherViewModel.refreshVersions()
      }
      selectedVersion.isDownloaded -> {
        AudioEngine.playPop()
        onLaunchGame(selectedVersion.versionTag)
      }
      selectedDownloadProgress == null -> {
        AudioEngine.playPop()
        launcherViewModel.downloadVersion(selectedVersion.id)
      }
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFF0B0C0F))
      .windowInsetsPadding(WindowInsets.safeDrawing)
  ) {
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .padding(8.dp)
        .testTag("title_screen")
    ) {
      val isWideLayout = maxWidth >= 600.dp

      if (isWideLayout) {
        Row(
          modifier = Modifier.fillMaxSize(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          // Left Column: Pure Launcher Controls (no game menu until game is launched)
          Column(
            modifier = Modifier
              .widthIn(min = 260.dp, max = 330.dp)
              .fillMaxHeight(),
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

            Text(
              text = if (selectedVersion != null)
                "Launcher • ${selectedVersion.versionTag} (${if (isSelectedReadyToPlay) "Pronta para iniciar" else "Baixar pelo GitHub"})"
              else
                "Launcher • Baixe uma versão pelo GitHub",
              color = if (isSelectedReadyToPlay) Color(0xFF81C784) else Color(0xFF90CAF9),
              fontSize = 12.sp,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Box(
              modifier = Modifier
                .width(300.dp)
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(4.dp))
                .background(if (isSelectedReadyToPlay) Color(0xFF2E7D32) else Color(0xFF26242E))
                .clickable(onClick = onPrimaryButtonClick)
                .padding(8.dp)
                .testTag("button1"),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = primaryButtonLabel,
                fontSize = 16.sp,
                color = Color(0xFFFFFFFF),
                modifier = Modifier.padding(vertical = 4.dp)
              )
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
                text = "Versão 0.00",
                color = Color(0xFFFFFFFF),
                fontSize = 14.sp,
                modifier = Modifier
                  .padding(8.dp)
                  .testTag("textview2")
              )
            }
          }

          // Right Column: Live GitHub Versions Browser
          LauncherVersionPanel(
            uiState = uiState,
            onSelectVersion = launcherViewModel::selectVersion,
            onSelectTab = launcherViewModel::selectTab,
            onRefresh = launcherViewModel::refreshVersions,
            onDownloadVersion = launcherViewModel::downloadVersion,
            onDeleteVersion = launcherViewModel::deleteDownloadedVersion,
            onDismissBanner = launcherViewModel::dismissBanner,
            onOpenExternalUrl = openExternalUrl,
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
          )
        }
      } else {
        // Compact/Portrait Launcher layout
        Column(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(8.dp)
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
              .fillMaxWidth()
              .minimumInteractiveComponentSize()
              .clip(RoundedCornerShape(4.dp))
              .background(if (isSelectedReadyToPlay) Color(0xFF2E7D32) else Color(0xFF26242E))
              .clickable(onClick = onPrimaryButtonClick)
              .padding(8.dp)
              .testTag("button1"),
            contentAlignment = Alignment.Center
          ) {
            Text(text = primaryButtonLabel, fontSize = 15.sp, color = Color(0xFFFFFFFF))
          }

          LauncherVersionPanel(
            uiState = uiState,
            onSelectVersion = launcherViewModel::selectVersion,
            onSelectTab = launcherViewModel::selectTab,
            onRefresh = launcherViewModel::refreshVersions,
            onDownloadVersion = launcherViewModel::downloadVersion,
            onDeleteVersion = launcherViewModel::deleteDownloadedVersion,
            onDismissBanner = launcherViewModel::dismissBanner,
            onOpenExternalUrl = openExternalUrl,
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
          )

          Text(
            text = "Versão 0.00",
            color = Color(0xFFFFFFFF),
            fontSize = 14.sp,
            modifier = Modifier
              .padding(8.dp)
              .testTag("textview2")
          )
        }
      }
    }
  }
}
