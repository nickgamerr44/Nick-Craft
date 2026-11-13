package com.example.minecraft.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.minecraft.launcher.CommitEntry
import com.example.minecraft.launcher.GameVersionEntry
import com.example.minecraft.launcher.LauncherTab
import com.example.minecraft.launcher.LauncherUiState
import com.example.minecraft.launcher.RepoSyncStatus
import com.example.minecraft.launcher.VersionSource

@Composable
fun LauncherVersionPanel(
  uiState: LauncherUiState,
  onSelectVersion: (String) -> Unit,
  onSelectTab: (LauncherTab) -> Unit,
  onRefresh: () -> Unit,
  onDownloadVersion: (String) -> Unit,
  onPlayVersion: (String) -> Unit,
  onDeleteVersion: (String) -> Unit,
  onDismissBanner: () -> Unit,
  onOpenExternalUrl: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(8.dp))
      .background(Color(0xFF14151B))
      .border(1.dp, Color(0xFF2D2B38), RoundedCornerShape(8.dp))
      .padding(12.dp)
      .testTag("launcher_version_panel")
  ) {
    // Top Header: Repo Title + Refresh + Open GitHub
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = uiState.repoInfo?.fullName ?: "${LauncherUiState.REPO_OWNER}/${LauncherUiState.REPO_NAME}",
          color = Color.White,
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        val subtitleText = when (uiState.syncStatus) {
          is RepoSyncStatus.Loading -> "Sincronizando versões do GitHub..."
          is RepoSyncStatus.Loaded -> {
            val stars = uiState.repoInfo?.stars ?: 0
            val license = uiState.repoInfo?.licenseName ?: "Open Source"
            "★ $stars • $license • Atualizado ${uiState.lastUpdatedLabel ?: ""}".trim()
          }
          is RepoSyncStatus.Error -> "Modo Offline / Aviso de Rede"
        }
        Text(
          text = subtitleText,
          color = Color(0xFF9EA2B0),
          fontSize = 11.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Box(
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF26242E))
            .clickable { onOpenExternalUrl(uiState.repoInfo?.htmlUrl ?: LauncherUiState.REPO_HTML_URL) }
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("open_github_repo_button"),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "GitHub ↗",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
          )
        }

        IconButton(
          onClick = onRefresh,
          modifier = Modifier
            .size(48.dp)
            .testTag("refresh_versions_button")
        ) {
          if (uiState.syncStatus is RepoSyncStatus.Loading) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              strokeWidth = 2.dp,
              color = Color(0xFF4CAF50)
            )
          } else {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "Atualizar versões do GitHub",
              tint = Color.White
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Tab Switcher: Versões vs Commits
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFF0B0C0F), RoundedCornerShape(6.dp))
        .padding(4.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      LauncherTabButton(
        text = "Versões (${uiState.versions.size})",
        selected = uiState.activeTab == LauncherTab.VERSIONS,
        onClick = { onSelectTab(LauncherTab.VERSIONS) },
        modifier = Modifier
          .weight(1f)
          .testTag("tab_versions")
      )
      LauncherTabButton(
        text = "Commits (${uiState.commits.size})",
        selected = uiState.activeTab == LauncherTab.CHANGELOG,
        onClick = { onSelectTab(LauncherTab.CHANGELOG) },
        modifier = Modifier
          .weight(1f)
          .testTag("tab_commits")
      )
    }

    // Status Banner (e.g., download complete or download error)
    val bannerMessage = uiState.statusBannerMessage
    if (!bannerMessage.isNullOrBlank()) {
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(6.dp))
          .background(Color(0xFF1B2E24))
          .border(1.dp, Color(0xFF3E6E52), RoundedCornerShape(6.dp))
          .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
          .testTag("launcher_status_banner"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = bannerMessage,
          color = Color(0xFFC8E6C9),
          fontSize = 11.sp,
          modifier = Modifier.weight(1f)
        )
        IconButton(
          onClick = onDismissBanner,
          modifier = Modifier.size(48.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Fechar aviso",
            tint = Color(0xFFC8E6C9),
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }

    // Error banner if GitHub returned an error (keeps local & downloaded versions playable!)
    if (uiState.syncStatus is RepoSyncStatus.Error) {
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(6.dp))
          .background(Color(0xFF2B1C20))
          .border(1.dp, Color(0xFF7A3844), RoundedCornerShape(6.dp))
          .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = uiState.syncStatus.message,
          color = Color(0xFFFFCDD2),
          fontSize = 11.sp,
          modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF442830))
            .clickable { onRefresh() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "Tentar",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Main Scrollable Content
    when (uiState.activeTab) {
      LauncherTab.VERSIONS -> {
        if (uiState.versions.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .clip(RoundedCornerShape(6.dp))
              .background(Color(0xFF1B1C24))
              .padding(16.dp)
              .testTag("versions_empty_state"),
            contentAlignment = Alignment.Center
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Text(
                text = if (uiState.syncStatus is RepoSyncStatus.Loading)
                  "Buscando versões em ${LauncherUiState.REPO_OWNER}/${LauncherUiState.REPO_NAME}..."
                else
                  "Nenhuma versão baixada no Launcher ainda. Este Launcher inicia apenas versões baixadas de ${LauncherUiState.REPO_OWNER}/${LauncherUiState.REPO_NAME}.",
                color = Color(0xFFB0B4C2),
                fontSize = 12.sp
              )
              if (uiState.syncStatus !is RepoSyncStatus.Loading) {
                Box(
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF2E7D32))
                    .clickable(onClick = onRefresh)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = "Sincronizar GitHub",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .fillMaxSize()
              .testTag("versions_list"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(uiState.versions, key = { it.id }) { version ->
              VersionEntryCard(
                entry = version,
                isSelected = version.id == uiState.selectedVersionId,
                onSelect = { onSelectVersion(version.id) },
                onDownload = { onDownloadVersion(version.id) },
                onPlay = { onPlayVersion(version.versionTag) },
                onDelete = { onDeleteVersion(version.id) },
                onOpenUrl = { url -> onOpenExternalUrl(url) }
              )
            }
          }
        }
      }

      LauncherTab.CHANGELOG -> {
        if (uiState.commits.isEmpty()) {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = if (uiState.syncStatus is RepoSyncStatus.Loading)
                "Carregando histórico de commits..."
              else
                "Nenhum commit carregado do repositório.",
              color = Color(0xFF9EA2B0),
              fontSize = 12.sp
            )
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .fillMaxSize()
              .testTag("commits_list"),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            items(uiState.commits, key = { it.shaShort + it.message }) { commit ->
              CommitEntryCard(
                commit = commit,
                onClick = { onOpenExternalUrl(commit.htmlUrl) }
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LauncherTabButton(
  text: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .minimumInteractiveComponentSize()
      .clip(RoundedCornerShape(4.dp))
      .background(if (selected) Color(0xFF26242E) else Color.Transparent)
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 8.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = text,
      color = if (selected) Color.White else Color(0xFF8C90A0),
      fontSize = 12.sp,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
private fun VersionEntryCard(
  entry: GameVersionEntry,
  isSelected: Boolean,
  onSelect: () -> Unit,
  onDownload: () -> Unit,
  onPlay: () -> Unit,
  onDelete: () -> Unit,
  onOpenUrl: (String) -> Unit
) {
  val isReady = entry.isDownloaded
  val borderColor = when {
    isReady -> Color(0xFF4CAF50)
    isSelected -> Color(0xFF8E85FF)
    else -> Color(0xFF2D2B38)
  }
  val bgColor = if (isSelected) Color(0xFF26242E) else Color(0xFF1A1B22)

  val badgeColor = when (entry.source) {
    VersionSource.GITHUB_RELEASE -> Color(0xFF3949AB)
    VersionSource.GITHUB_VERSIONS_DIR -> Color(0xFF00838F)
    VersionSource.GITHUB_TAG -> Color(0xFF455A64)
    VersionSource.GITHUB_BRANCH -> Color(0xFF00695C)
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(6.dp))
      .background(bgColor)
      .border(if (isSelected || isReady) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(6.dp))
      .clickable(onClick = onSelect)
      .padding(10.dp)
      .testTag("version_card_${entry.id}")
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.weight(1f)
      ) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(badgeColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
          Text(
            text = entry.versionTag,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
        }

        Text(
          text = entry.title,
          color = Color.White,
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      if (isReady) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF1B5E20))
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag("installed_pill_${entry.id}")
        ) {
          Text(
            text = if (!entry.localSizeLabel.isNullOrBlank())
              "Instalada (${entry.localSizeLabel})"
            else
              "Instalada",
            color = Color(0xFFC8E6C9),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
          )
        }
      } else if (isSelected) {
        Icon(
          imageVector = Icons.Default.CheckCircle,
          contentDescription = "Versão selecionada",
          tint = Color(0xFFB39DDB),
          modifier = Modifier.size(18.dp)
        )
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "${entry.source.label} • ${entry.dateLabel}",
        color = Color(0xFFB0B4C2),
        fontSize = 11.sp,
        modifier = Modifier.weight(1f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        if (entry.downloadProgress == null && !entry.isDownloaded) {
          Box(
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .clip(RoundedCornerShape(4.dp))
              .background(Color(0xFF26242E))
              .border(1.dp, Color(0xFF4A4858), RoundedCornerShape(4.dp))
              .clickable(onClick = onDownload)
              .padding(horizontal = 14.dp, vertical = 8.dp)
              .testTag("download_version_${entry.id}"),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "Baixar",
              color = Color.White,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold
            )
          }
        } else if (entry.isDownloaded) {
          Row(
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .clip(RoundedCornerShape(4.dp))
              .background(Color(0xFF2E7D32))
              .clickable(onClick = onPlay)
              .padding(horizontal = 14.dp, vertical = 8.dp)
              .testTag("play_version_${entry.id}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = Icons.Default.PlayArrow,
              contentDescription = "Jogar esta versão",
              tint = Color.White,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = "Jogar",
              color = Color.White,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold
            )
          }

          IconButton(
            onClick = onDelete,
            modifier = Modifier
              .size(48.dp)
              .testTag("delete_version_${entry.id}")
          ) {
            Icon(
              imageVector = Icons.Default.Delete,
              contentDescription = "Excluir versão baixada",
              tint = Color(0xFFEF9A9A),
              modifier = Modifier.size(18.dp)
            )
          }
        }

        val externalUrl = entry.htmlUrl
        if (!externalUrl.isNullOrBlank()) {
          Box(
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .clip(RoundedCornerShape(4.dp))
              .clickable { onOpenUrl(externalUrl) }
              .padding(horizontal = 8.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "GitHub ↗",
              color = Color(0xFF90CAF9),
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold
            )
          }
        }
      }
    }

    val progress = entry.downloadProgress
    if (progress != null) {
      Spacer(modifier = Modifier.height(6.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        LinearProgressIndicator(
          progress = { progress.coerceIn(0f, 1f) },
          modifier = Modifier
            .weight(1f)
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .testTag("download_progress_${entry.id}"),
          color = Color(0xFF4CAF50),
          trackColor = Color(0xFF2D2B38)
        )
        Text(
          text = "${(progress * 100).toInt()}%",
          color = Color(0xFFA5D6A7),
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold
        )
      }
    }

    if (entry.description.isNotBlank()) {
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = entry.description,
        color = Color(0xFF9094A4),
        fontSize = 11.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
private fun CommitEntryCard(
  commit: CommitEntry,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(6.dp))
      .background(Color(0xFF1A1B22))
      .border(1.dp, Color(0xFF2D2B38), RoundedCornerShape(6.dp))
      .clickable(onClick = onClick)
      .padding(10.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = commit.message.ifBlank { "Atualização no repositório" },
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = "${commit.authorName} • ${commit.dateLabel}",
        color = Color(0xFF9EA2B0),
        fontSize = 10.sp
      )
    }
    Spacer(modifier = Modifier.width(8.dp))
    Box(
      modifier = Modifier
        .clip(RoundedCornerShape(4.dp))
        .background(Color(0xFF26242E))
        .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
      Text(
        text = commit.shaShort,
        color = Color(0xFF90CAF9),
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace
      )
    }
  }
}
