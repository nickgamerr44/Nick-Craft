package com.example.minecraft.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LauncherViewModel(
  private val repository: GitHubLauncherRepository
) : ViewModel() {

  private val initialOfflineVersions = repository.listDownloadedVersionsOffline()
  private val initialCachedNews = repository.listCachedNewsOffline()

  private val _uiState = MutableStateFlow(
    LauncherUiState(
      syncStatus = RepoSyncStatus.Loading,
      versions = initialOfflineVersions,
      selectedVersionId = initialOfflineVersions.firstOrNull()?.id.orEmpty(),
      news = initialCachedNews
    )
  )
  val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

  init {
    refreshVersions()
  }

  fun refreshVersions() {
    viewModelScope.launch {
      _uiState.update { it.copy(syncStatus = RepoSyncStatus.Loading) }
      val result = repository.fetchVersions()
      val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
      _uiState.update { current ->
        val stillValidSelection = result.versions.any { it.id == current.selectedVersionId }
        val fallbackSelection = result.versions.firstOrNull { it.isDownloaded }?.id
          ?: result.versions.firstOrNull()?.id.orEmpty()
        current.copy(
          syncStatus = result.syncStatus,
          repoInfo = result.repoInfo,
          versions = result.versions,
          selectedVersionId = if (stillValidSelection) current.selectedVersionId else fallbackSelection,
          news = result.news,
          commits = result.commits,
          lastUpdatedLabel = timeStr
        )
      }
    }
  }

  fun selectVersion(versionId: String) {
    _uiState.update { it.copy(selectedVersionId = versionId, statusBannerMessage = null) }
  }

  fun selectTab(tab: LauncherTab) {
    _uiState.update { it.copy(activeTab = tab) }
  }

  fun downloadVersion(versionId: String) {
    val entry = _uiState.value.versions.firstOrNull { it.id == versionId } ?: return
    if (entry.downloadProgress != null) return

    viewModelScope.launch {
      updateVersionEntry(versionId) { it.copy(downloadProgress = 0.05f) }
      val result = repository.downloadVersionIntoLauncher(entry) { progress ->
        updateVersionEntry(versionId) { it.copy(downloadProgress = progress) }
      }
      result.onSuccess { manifest ->
        val kb = (manifest.sizeBytes / 1024.0).coerceAtLeast(1.0)
        val sizeStr = String.format(Locale.US, "%.0f KB", kb)
        updateVersionEntry(versionId) {
          it.copy(
            isDownloaded = true,
            downloadProgress = null,
            localSizeLabel = sizeStr
          )
        }
        _uiState.update {
          it.copy(
            selectedVersionId = versionId,
            statusBannerMessage = "Versão ${entry.versionTag} baixada no Launcher! Toque em Jogar para iniciar."
          )
        }
      }.onFailure { err ->
        updateVersionEntry(versionId) { it.copy(downloadProgress = null) }
        _uiState.update {
          it.copy(
            statusBannerMessage = "Erro ao baixar ${entry.versionTag}: ${err.localizedMessage ?: "verifique sua conexão"}"
          )
        }
      }
    }
  }

  fun deleteDownloadedVersion(versionId: String) {
    viewModelScope.launch {
      repository.deleteDownloadedVersion(versionId)
      updateVersionEntry(versionId) {
        it.copy(isDownloaded = false, downloadProgress = null, localSizeLabel = null)
      }
      _uiState.update {
        it.copy(statusBannerMessage = "Versão removida do Launcher. Baixe novamente pelo GitHub quando quiser jogar.")
      }
    }
  }

  fun showDownloadRequiredBanner() {
    _uiState.update {
      it.copy(statusBannerMessage = "Baixe uma versão pelo GitHub antes de iniciar o jogo no Launcher.")
    }
  }

  fun dismissBanner() {
    _uiState.update { it.copy(statusBannerMessage = null) }
  }

  private fun updateVersionEntry(versionId: String, transform: (GameVersionEntry) -> GameVersionEntry) {
    _uiState.update { state ->
      state.copy(
        versions = state.versions.map { v ->
          if (v.id == versionId) transform(v) else v
        }
      )
    }
  }

  companion object {
    fun factory(filesDir: File): ViewModelProvider.Factory {
      return object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
          val repo = GitHubLauncherRepository(
            versionsDir = File(filesDir, "nickcraft_versions")
          )
          return LauncherViewModel(repo) as T
        }
      }
    }
  }
}
