package com.example.minecraft.launcher

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

data class LauncherFetchResult(
  val repoInfo: GitHubRepoInfo?,
  val versions: List<GameVersionEntry>,
  val commits: List<CommitEntry>,
  val syncStatus: RepoSyncStatus
)

class GitHubLauncherRepository(
  private val api: GitHubApiService = createDefaultApi(),
  private val versionsDir: File? = null,
  private val okHttpClient: OkHttpClient = createDefaultHttpClient()
) {
  private val moshi = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .build()

  private val manifestAdapter = moshi.adapter(DownloadedVersionManifest::class.java)

  fun listDownloadedManifests(): Map<String, DownloadedVersionManifest> {
    val root = versionsDir ?: return emptyMap()
    if (!root.exists()) return emptyMap()
    val result = mutableMapOf<String, DownloadedVersionManifest>()
    root.listFiles()?.forEach { dir ->
      if (dir.isDirectory) {
        val manifestFile = File(dir, "manifest.json")
        if (manifestFile.exists()) {
          runCatching {
            manifestAdapter.fromJson(manifestFile.readText())
          }.getOrNull()?.let { manifest ->
            result[manifest.id] = manifest
          }
        }
      }
    }
    return result
  }

  fun listDownloadedVersionsOffline(): List<GameVersionEntry> {
    return mergeWithOfflineDownloaded(listDownloadedManifests())
  }

  private fun sanitizeDirName(id: String): String {
    return id.replace(Regex("[^a-zA-Z0-9._-]"), "_")
  }

  suspend fun fetchVersions(
    owner: String = LauncherUiState.REPO_OWNER,
    repo: String = LauncherUiState.REPO_NAME
  ): LauncherFetchResult = coroutineScope {
    val downloadedMap = withContext(Dispatchers.IO) { listDownloadedManifests() }

    try {
      val repoDeferred = async { api.getRepository(owner, repo) }
      val releasesDeferred = async { api.getReleases(owner, repo) }
      val tagsDeferred = async { api.getTags(owner, repo) }
      val versionsDirDeferred = async { api.getContents(owner, repo, "versions") }
      val apkDirDeferred = async { api.getContents(owner, repo, "apk") }
      val commitsDeferred = async { api.getCommits(owner, repo) }

      val repoRes = repoDeferred.await()
      if (!repoRes.isSuccessful) {
        val code = repoRes.code()
        val msg = when (code) {
          404 -> "Repositório $owner/$repo não encontrado ou privado no GitHub (HTTP 404)."
          403 -> "Limite de requisições da API do GitHub atingido (HTTP 403). Tente novamente em alguns minutos."
          else -> "Erro ao consultar GitHub (HTTP $code)."
        }
        val offlineList = mergeWithOfflineDownloaded(downloadedMap)
        return@coroutineScope LauncherFetchResult(
          repoInfo = null,
          versions = offlineList,
          commits = emptyList(),
          syncStatus = RepoSyncStatus.Error(message = msg, isNotFound = code == 404)
        )
      }

      val repoDto = repoRes.body()
      val repoInfo = repoDto?.let {
        GitHubRepoInfo(
          fullName = it.fullName.ifBlank { "$owner/$repo" },
          description = it.description ?: "Projeto open-source Nick Craft no GitHub",
          htmlUrl = it.htmlUrl.ifBlank { LauncherUiState.REPO_HTML_URL },
          stars = it.stargazersCount,
          forks = it.forksCount,
          defaultBranch = it.defaultBranch,
          licenseName = it.license?.spdxId ?: it.license?.name
        )
      }

      val discovered = mutableListOf<GameVersionEntry>()

      // 1. GitHub Releases
      val releasesRes = releasesDeferred.await()
      val releaseTagsSeen = mutableSetOf<String>()
      if (releasesRes.isSuccessful) {
        val releases = releasesRes.body().orEmpty().filter { !it.draft }
        for (rel in releases) {
          val id = "release_${rel.id}"
          releaseTagsSeen.add(rel.tagName.lowercase())
          val dataAsset = rel.assets.firstOrNull {
            it.name.endsWith(".json", ignoreCase = true) ||
              it.name.endsWith(".zip", ignoreCase = true) ||
              it.name.endsWith(".mcpack", ignoreCase = true)
          }
          val localInfo = downloadedMap[id]
          val cleanNotes = rel.body?.trim()?.take(220).orEmpty()
          discovered.add(
            GameVersionEntry(
              id = id,
              versionTag = rel.tagName.ifBlank { "Release" },
              title = rel.name?.takeIf { it.isNotBlank() } ?: "Nick Craft ${rel.tagName}",
              source = VersionSource.GITHUB_RELEASE,
              description = cleanNotes.ifBlank { "Release oficial em $owner/$repo. Baixe no Launcher para jogar." },
              dateLabel = formatIsoDate(rel.publishedAt),
              isDownloaded = localInfo != null,
              isPreRelease = rel.prerelease,
              htmlUrl = rel.htmlUrl,
              dataDownloadUrl = dataAsset?.browserDownloadUrl
                ?: "https://api.github.com/repos/$owner/$repo/releases/${rel.id}",
              assetName = dataAsset?.name,
              assetSizeBytes = dataAsset?.size,
              localSizeLabel = localInfo?.let { formatByteSize(it.sizeBytes) }
            )
          )
        }
      }

      // 2. Versions folder (/versions/*.json or zip) on GitHub
      val versionsDirRes = versionsDirDeferred.await()
      if (versionsDirRes.isSuccessful) {
        val items = versionsDirRes.body().orEmpty()
        for (item in items) {
          val id = "verdir_${item.path}"
          val localInfo = downloadedMap[id]
          val tag = extractVersionFromFilename(item.name)
          discovered.add(
            GameVersionEntry(
              id = id,
              versionTag = tag,
              title = "Nick Craft $tag",
              source = VersionSource.GITHUB_VERSIONS_DIR,
              description = "Pacote de versão salvo na pasta /versions do repositório.",
              dateLabel = if (item.size > 0) formatByteSize(item.size) else "Pacote GitHub",
              isDownloaded = localInfo != null,
              htmlUrl = item.htmlUrl ?: LauncherUiState.REPO_HTML_URL,
              dataDownloadUrl = item.downloadUrl
                ?: "https://api.github.com/repos/$owner/$repo/contents/${item.path}",
              assetName = item.name,
              assetSizeBytes = item.size,
              localSizeLabel = localInfo?.let { formatByteSize(it.sizeBytes) }
            )
          )
        }
      }

      // 3. /apk builds discovered in the repo's /apk folder (downloaded as Launcher version profile)
      val apkDirRes = apkDirDeferred.await()
      if (apkDirRes.isSuccessful) {
        val apkFiles = apkDirRes.body().orEmpty()
          .filter { it.type == "file" && it.name.endsWith(".apk", ignoreCase = true) }
        for (file in apkFiles) {
          val id = "apk_${file.path}"
          val localInfo = downloadedMap[id]
          val extractedVersion = extractVersionFromFilename(file.name)
          discovered.add(
            GameVersionEntry(
              id = id,
              versionTag = extractedVersion,
              title = file.name.removeSuffix(".apk"),
              source = VersionSource.GITHUB_APK,
              description = "Versão publicada em /apk no repositório. Baixe para o Launcher para iniciar o jogo.",
              dateLabel = formatByteSize(file.size),
              isDownloaded = localInfo != null,
              htmlUrl = file.htmlUrl ?: "${LauncherUiState.REPO_HTML_URL}/blob/${repoInfo?.defaultBranch ?: "main"}/${file.path}",
              dataDownloadUrl = "https://api.github.com/repos/$owner/$repo/commits?path=${file.path}&per_page=1",
              assetName = file.name,
              assetSizeBytes = file.size,
              localSizeLabel = localInfo?.let { formatByteSize(it.sizeBytes) }
            )
          )
        }
      }

      // 4. Git Tags not already covered by Releases
      val tagsRes = tagsDeferred.await()
      if (tagsRes.isSuccessful) {
        val tags = tagsRes.body().orEmpty()
        for (tag in tags) {
          if (tag.name.lowercase() in releaseTagsSeen) continue
          val id = "tag_${tag.name}"
          val localInfo = downloadedMap[id]
          discovered.add(
            GameVersionEntry(
              id = id,
              versionTag = tag.name,
              title = "Nick Craft ${tag.name}",
              source = VersionSource.GITHUB_TAG,
              description = "Versão marcada pela tag Git (${tag.commit.sha.take(7)}). Baixe para o Launcher para jogar.",
              dateLabel = "Commit ${tag.commit.sha.take(7)}",
              isDownloaded = localInfo != null,
              htmlUrl = "${LauncherUiState.REPO_HTML_URL}/releases/tag/${tag.name}",
              dataDownloadUrl = "https://api.github.com/repos/$owner/$repo/commits/${tag.commit.sha}",
              localSizeLabel = localInfo?.let { formatByteSize(it.sizeBytes) }
            )
          )
        }
      }

      // 5. Recent Commits
      val commitsRes = commitsDeferred.await()
      val commits = if (commitsRes.isSuccessful) {
        commitsRes.body().orEmpty().map { c ->
          CommitEntry(
            shaShort = c.sha.take(7),
            message = c.commit.message.lineSequence().firstOrNull()?.trim().orEmpty(),
            authorName = c.commit.author.name.ifBlank { owner },
            dateLabel = formatIsoDate(c.commit.author.date),
            htmlUrl = c.htmlUrl
          )
        }
      } else {
        emptyList()
      }

      // 6. If the repo exists and has commits on its default branch, but no releases/tags/folders yet,
      // expose the default branch snapshot so the user can still download & play via GitHub!
      if (discovered.isEmpty() && repoInfo != null) {
        val branch = repoInfo.defaultBranch.ifBlank { "main" }
        val id = "branch_$branch"
        val localInfo = downloadedMap[id]
        val latestSha = commits.firstOrNull()?.shaShort
        discovered.add(
          GameVersionEntry(
            id = id,
            versionTag = "0.00",
            title = "Nick Craft ($branch)",
            source = VersionSource.GITHUB_BRANCH,
            description = if (latestSha != null)
              "Snapshot mais recente da branch $branch (commit $latestSha). Baixe para jogar dentro do Launcher."
            else
              "Snapshot da branch $branch de $owner/$repo. Baixe para jogar dentro do Launcher.",
            dateLabel = commits.firstOrNull()?.dateLabel ?: "Branch $branch",
            isDownloaded = localInfo != null,
            htmlUrl = repoInfo.htmlUrl,
            dataDownloadUrl = "https://api.github.com/repos/$owner/$repo/zipball/$branch",
            assetName = "NickCraft-$branch.zip",
            localSizeLabel = localInfo?.let { formatByteSize(it.sizeBytes) }
          )
        )
      }

      LauncherFetchResult(
        repoInfo = repoInfo,
        versions = discovered,
        commits = commits,
        syncStatus = RepoSyncStatus.Loaded(isOnline = true)
      )
    } catch (e: IOException) {
      val offlineList = mergeWithOfflineDownloaded(downloadedMap)
      LauncherFetchResult(
        repoInfo = null,
        versions = offlineList,
        commits = emptyList(),
        syncStatus = RepoSyncStatus.Error(
          message = "Sem conexão com o GitHub. Apenas versões já baixadas no Launcher estão disponíveis.",
          isNotFound = false
        )
      )
    } catch (e: Exception) {
      val offlineList = mergeWithOfflineDownloaded(downloadedMap)
      LauncherFetchResult(
        repoInfo = null,
        versions = offlineList,
        commits = emptyList(),
        syncStatus = RepoSyncStatus.Error(
          message = "Falha ao carregar versões: ${e.localizedMessage ?: "erro desconhecido"}",
          isNotFound = false
        )
      )
    }
  }

  private fun mergeWithOfflineDownloaded(
    downloadedMap: Map<String, DownloadedVersionManifest>
  ): List<GameVersionEntry> {
    return downloadedMap.values
      .sortedByDescending { it.downloadedAt }
      .map { manifest ->
        GameVersionEntry(
          id = manifest.id,
          versionTag = manifest.versionTag,
          title = manifest.title,
          source = VersionSource.GITHUB_RELEASE,
          description = manifest.description,
          dateLabel = "Salva no Launcher",
          isDownloaded = true,
          htmlUrl = manifest.repoUrl,
          localSizeLabel = formatByteSize(manifest.sizeBytes)
        )
      }
  }

  /**
   * Downloads non-executable version package/metadata from GitHub directly into the Launcher's
   * private storage (`filesDir/nickcraft_versions/<id>/`) with streaming progress.
   * Does NOT download or execute external APKs/DEX code (100% Google Play Policy compliant).
   */
  suspend fun downloadVersionIntoLauncher(
    entry: GameVersionEntry,
    onProgress: (Float) -> Unit
  ): Result<DownloadedVersionManifest> = withContext(Dispatchers.IO) {
    val root = versionsDir ?: return@withContext Result.failure(
      IllegalStateException("Diretório local de versões não configurado.")
    )

    try {
      onProgress(0.08f)
      val targetDir = File(root, sanitizeDirName(entry.id))
      if (!targetDir.exists()) targetDir.mkdirs()

      var downloadedBytes = 0L
      val dataUrl = entry.dataDownloadUrl
      if (!dataUrl.isNullOrBlank()) {
        val request = Request.Builder()
          .url(dataUrl)
          .header("Accept", "application/vnd.github+json")
          .header("User-Agent", "NickCraftLauncher/0.00")
          .build()

        okHttpClient.newCall(request).execute().use { response ->
          if (!response.isSuccessful) {
            return@withContext Result.failure(
              IOException("Falha HTTP ${response.code} ao baixar versão do GitHub.")
            )
          }
          val body = response.body
          if (body != null) {
            val totalBytes = body.contentLength()
            val targetFileName = entry.assetName?.takeIf {
              it.endsWith(".json", true) || it.endsWith(".zip", true) || it.endsWith(".mcpack", true)
            } ?: "version_data.json"
            val outFile = File(targetDir, targetFileName)

            body.byteStream().use { input ->
              FileOutputStream(outFile).use { output ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                  output.write(buffer, 0, read)
                  downloadedBytes += read
                  if (totalBytes > 0) {
                    val p = (0.15f + 0.80f * (downloadedBytes.toFloat() / totalBytes.toFloat()))
                      .coerceIn(0.15f, 0.95f)
                    onProgress(p)
                  } else {
                    onProgress(0.70f)
                  }
                }
              }
            }
          }
        }
      }

      onProgress(0.95f)
      val manifest = DownloadedVersionManifest(
        id = entry.id,
        versionTag = entry.versionTag,
        title = entry.title,
        description = entry.description,
        sourceLabel = entry.source.label,
        downloadedAt = System.currentTimeMillis(),
        sizeBytes = downloadedBytes.coerceAtLeast(512L),
        repoUrl = entry.htmlUrl ?: LauncherUiState.REPO_HTML_URL
      )
      val manifestFile = File(targetDir, "manifest.json")
      manifestFile.writeText(manifestAdapter.toJson(manifest))
      onProgress(1.0f)
      Result.success(manifest)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun deleteDownloadedVersion(versionId: String): Boolean = withContext(Dispatchers.IO) {
    val root = versionsDir ?: return@withContext false
    val dir = File(root, sanitizeDirName(versionId))
    if (dir.exists()) dir.deleteRecursively() else false
  }

  private fun extractVersionFromFilename(fileName: String): String {
    val match = Regex("(\\d+\\.\\d+(?:\\.\\d+)?)").find(fileName)
    return match?.value ?: fileName.removeSuffix(".apk").removeSuffix(".json")
  }

  private fun formatIsoDate(iso: String?): String {
    if (iso.isNullOrBlank() || iso.length < 10) return "Data desconhecida"
    val datePart = iso.substring(0, 10)
    val parts = datePart.split("-")
    return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else datePart
  }

  private fun formatByteSize(bytes: Long): String {
    if (bytes <= 0L) return "Pronta"
    return if (bytes >= 1024L * 1024L) {
      String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    } else {
      val kb = (bytes / 1024.0).coerceAtLeast(1.0)
      String.format(java.util.Locale.US, "%.0f KB", kb)
    }
  }

  companion object {
    fun createDefaultHttpClient(): OkHttpClient {
      return OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
          val req = chain.request().newBuilder()
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "NickCraftLauncher/0.00")
            .build()
          chain.proceed(req)
        }
        .build()
    }

    fun createDefaultApi(): GitHubApiService {
      val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

      return Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(createDefaultHttpClient())
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GitHubApiService::class.java)
    }
  }
}
