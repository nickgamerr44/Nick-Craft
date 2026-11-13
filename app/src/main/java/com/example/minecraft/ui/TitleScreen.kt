package com.example.minecraft.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

  val openExternalUrl: (String) -> Unit = { url ->
    runCatching {
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
      context.startActivity(intent)
    }
  }

  val playVersion: (String) -> Unit = { versionTag ->
    AudioEngine.playPop()
    onLaunchGame(versionTag)
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
          // Left Column: Minimal Launcher Identity & Footer (game starts directly from the versions list)
          Column(
            modifier = Modifier
              .widthIn(min = 210.dp, max = 270.dp)
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
              text = "Launcher",
              color = Color(0xFF9EA2B0),
              fontSize = 13.sp,
              modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
              text = "Baixe e inicie qualquer versão diretamente pela lista ao lado.",
              color = Color(0xFF7D8292),
              fontSize = 12.sp,
              modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.weight(1f))
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
              verticalAlignment = Alignment.Bottom,
              horizontalArrangement = Arrangement.Start
            ) {
              Text(
                text = "Versão 1.1",
                color = Color(0xFFFFFFFF),
                fontSize = 14.sp,
                modifier = Modifier
                  .padding(8.dp)
                  .testTag("textview2")
              )
            }
          }

          // Right Column: Live GitHub Versions Browser (direct Baixar / Jogar per version card)
          LauncherVersionPanel(
            uiState = uiState,
            onSelectVersion = launcherViewModel::selectVersion,
            onSelectTab = launcherViewModel::selectTab,
            onRefresh = launcherViewModel::refreshVersions,
            onDownloadVersion = launcherViewModel::downloadVersion,
            onPlayVersion = playVersion,
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
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Nick Craft",
              color = Color(0xFFFFFFFF),
              fontSize = 18.sp,
              fontFamily = NickCraftPixelFont,
              modifier = Modifier.testTag("textview1")
            )
            Text(
              text = "Launcher",
              color = Color(0xFF9EA2B0),
              fontSize = 12.sp
            )
          }

          LauncherVersionPanel(
            uiState = uiState,
            onSelectVersion = launcherViewModel::selectVersion,
            onSelectTab = launcherViewModel::selectTab,
            onRefresh = launcherViewModel::refreshVersions,
            onDownloadVersion = launcherViewModel::downloadVersion,
            onPlayVersion = playVersion,
            onDeleteVersion = launcherViewModel::deleteDownloadedVersion,
            onDismissBanner = launcherViewModel::dismissBanner,
            onOpenExternalUrl = openExternalUrl,
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
          )

          Text(
            text = "Versão 1.1",
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
