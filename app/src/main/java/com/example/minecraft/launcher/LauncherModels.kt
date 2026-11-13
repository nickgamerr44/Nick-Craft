package com.example.minecraft.launcher

enum class VersionSource(val label: String) {
  GITHUB_RELEASE("Release GitHub"),
  GITHUB_VERSIONS_DIR("Pasta /versions"),
  GITHUB_TAG("Tag Git"),
  GITHUB_BRANCH("Branch GitHub")
}

enum class LauncherTab {
  VERSIONS,
  CHANGELOG
}

data class GameVersionEntry(
  val id: String,
  val versionTag: String,
  val title: String,
  val source: VersionSource,
  val description: String,
  val dateLabel: String,
  val isDownloaded: Boolean = false,
  val downloadProgress: Float? = null,
  val isPreRelease: Boolean = false,
  val htmlUrl: String? = null,
  val dataDownloadUrl: String? = null,
  val assetName: String? = null,
  val assetSizeBytes: Long? = null,
  val localSizeLabel: String? = null
)

data class DownloadedVersionManifest(
  val id: String,
  val versionTag: String,
  val title: String,
  val description: String,
  val sourceLabel: String,
  val downloadedAt: Long,
  val sizeBytes: Long,
  val repoUrl: String
)

data class GitHubRepoInfo(
  val fullName: String,
  val description: String,
  val htmlUrl: String,
  val stars: Int,
  val forks: Int,
  val defaultBranch: String,
  val licenseName: String?
)

data class CommitEntry(
  val shaShort: String,
  val message: String,
  val authorName: String,
  val dateLabel: String,
  val htmlUrl: String
)

sealed interface RepoSyncStatus {
  data object Loading : RepoSyncStatus
  data class Loaded(val isOnline: Boolean) : RepoSyncStatus
  data class Error(val message: String, val isNotFound: Boolean = false) : RepoSyncStatus
}

data class LauncherUiState(
  val syncStatus: RepoSyncStatus = RepoSyncStatus.Loading,
  val repoInfo: GitHubRepoInfo? = null,
  val versions: List<GameVersionEntry> = emptyList(),
  val selectedVersionId: String = "",
  val commits: List<CommitEntry> = emptyList(),
  val activeTab: LauncherTab = LauncherTab.VERSIONS,
  val lastUpdatedLabel: String? = null,
  val statusBannerMessage: String? = null
) {
  val selectedVersion: GameVersionEntry?
    get() = versions.firstOrNull { it.id == selectedVersionId } ?: versions.firstOrNull()

  val firstDownloadedVersion: GameVersionEntry?
    get() = versions.firstOrNull { it.isDownloaded }

  companion object {
    const val REPO_OWNER = "nickgamerr44"
    const val REPO_NAME = "Nick-Craft"
    const val REPO_HTML_URL = "https://github.com/nickgamerr44/Nick-Craft"
  }
}
