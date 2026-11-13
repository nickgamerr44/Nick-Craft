package com.example.minecraft.launcher

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Cache
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

private data class CachedRepoSnapshot(
  val repoInfo: GitHubRepoInfo?,
  val versions: List<GameVersionEntry>,
  val commits: List<CommitEntry>
)

class GitHubLauncherRepository(
  api: GitHubApiService? = null,
  private val versionsDir: File? = null,
  okHttpClient: OkHttpClient? = null
) {
  private val httpClient: OkHttpClient = okHttpClient ?: createDefaultHttpClient(
    versionsDir?.let { File(it, ".http_cache") }
  )
  private val api: GitHubApiService = api ?: createDefaultApi(httpClient)

  private val moshi = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .build()

  private val manifestAdapter = moshi.adapter(DownloadedVersionManifest::class.java)
  private val cacheAdapter = moshi.adapter(CachedRepoSnapshot::class.java)

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
            if (!manifest.id.startsWith("apk_") && manifest.sourceLabel != "Build /apk") {
              result[manifest.id] = manifest
            }
          }
        }
      }
    }
    return result
  }

  fun listDownloadedVersionsOffline(): List<GameVersionEntry> {
    return mergeWithOfflineDownloaded(listDownloadedManifests(), readCachedSnapshot())
  }

  private fun readCachedSnapshot(): CachedRepoSnapshot? {
    val root = versionsDir ?: return null
    val cacheFile = File(root, "repo_cache.json")
    if (!cacheFile.exists()) return null
    return runCatching {
      cacheAdapter.fromJson(cacheFile.readText())
    }.getOrNull()
  }

  private fun saveCachedSnapshot(snapshot: CachedRepoSnapshot) {
    val root = versionsDir ?: return
    runCatching {
      if (!root.exists()) root.mkdirs()
      File(root, "repo_cache.json").writeText(cacheAdapter.toJson(snapshot))
    }
  }

  private fun sanitizeDirName(id: String): String {
    return id.replace(Regex("[^a-zA-Z0-9._-]"), "_")
  }

  suspend fun fetchVersions(
    owner: String = LauncherUiState.REPO_OWNER,
    repo: String = LauncherUiState.REPO_NAME
  ): LauncherFetchResult = coroutineScope {
    val downloadedMap = withContext(Dispatchers.IO) { listDownloadedManifests() }
    val cachedSnapshot = withContext(Dispatchers.IO) { readCachedSnapshot() }

    try {
      val repoDeferred = async { api.getRepository(owner, repo) }
      val releasesDeferred = async { api.getReleases(owner, repo) }
      val tagsDeferred = async { api.getTags(owner, repo) }
      val rootContentsDeferred = async { api.getContents(owner, repo, "") }
      val commitsDeferred = async { api.getCommits(owner, repo) }

      val repoRes = repoDeferred.await()
      val releasesRes = releasesDeferred.await()
      val tagsRes = tagsDeferred.await()
      val rootRes = rootContentsDeferred.await()
      val commitsRes = commitsDeferred.await()

      val rateLimitedOrServerError = listOf(repoRes.code(), releasesRes.code(), tagsRes.code(), commitsRes.code())
        .any { code -> code == 403 || code == 429 || code >= 500 }

      if (!repoRes.isSuccessful || rateLimitedOrServerError) {
        val code = listOf(repoRes.code(), releasesRes.code(), tagsRes.code(), commitsRes.code())
          .firstOrNull { it !in 200..299 } ?: repoRes.code()
        // When GitHub REST API hits rate limit (403/429) or transient 5xx, automatically switch to
        // GitHub's rate-limit-free Atom feeds (releases.atom, tags.atom, commits.atom).
        if (code == 403 || code == 429 || code >= 500) {
          val fallback = fetchViaGitHubAtomFeeds(owner, repo, downloadedMap)
          if (fallback != null) {
            return@coroutineScope fallback
          }
        }

        if (!repoRes.isSuccessful) {
          val msg = when (code) {
            404 -> "Repositório $owner/$repo não encontrado ou privado no GitHub (HTTP 404)."
            403 -> "Limite da API do GitHub atingido (HTTP 403). Exibindo versões em cache/instaladas."
            else -> "Erro ao consultar GitHub (HTTP $code)."
          }
          val offlineList = mergeWithOfflineDownloaded(downloadedMap, cachedSnapshot)
          return@coroutineScope LauncherFetchResult(
            repoInfo = cachedSnapshot?.repoInfo,
            versions = offlineList,
            commits = cachedSnapshot?.commits.orEmpty(),
            syncStatus = RepoSyncStatus.Error(message = msg, isNotFound = code == 404)
          )
        }
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

      val rootDirNames = if (rootRes.isSuccessful) {
        rootRes.body().orEmpty()
          .filter { it.type == "dir" }
          .map { it.name.lowercase() }
          .toSet()
      } else {
        emptySet()
      }

      val versionsDirDeferred = if ("versions" in rootDirNames) {
        async { api.getContents(owner, repo, "versions") }
      } else null

      val discovered = mutableListOf<GameVersionEntry>()

      // 1. GitHub Releases
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
          val fallbackArchiveUrl = "https://github.com/$owner/$repo/archive/refs/tags/${rel.tagName}.zip"
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
              dataDownloadUrl = dataAsset?.browserDownloadUrl ?: fallbackArchiveUrl,
              assetName = dataAsset?.name ?: "NickCraft-${rel.tagName}.zip",
              assetSizeBytes = dataAsset?.size,
              localSizeLabel = localInfo?.let { formatByteSize(it.sizeBytes) }
            )
          )
        }
      }

      // 2. Versions folder (/versions/*.json or zip) on GitHub
      val versionsDirRes = versionsDirDeferred?.await()
      if (versionsDirRes != null && versionsDirRes.isSuccessful) {
        val items = versionsDirRes.body().orEmpty()
        for (item in items) {
          val id = "verdir_${item.path}"
          val localInfo = downloadedMap[id]
          val tag = extractVersionFromFilename(item.name)
          val defaultBranch = repoInfo?.defaultBranch?.ifBlank { "main" } ?: "main"
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
                ?: "https://raw.githubusercontent.com/$owner/$repo/$defaultBranch/${item.path}",
              assetName = item.name,
              assetSizeBytes = item.size,
              localSizeLabel = localInfo?.let { formatByteSize(it.sizeBytes) }
            )
          )
        }
      }

      // 3. Git Tags not already covered by Releases
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
              dataDownloadUrl = "https://github.com/$owner/$repo/archive/refs/tags/${tag.name}.zip",
              assetName = "NickCraft-${tag.name}.zip",
              localSizeLabel = localInfo?.let { formatByteSize(it.sizeBytes) }
            )
          )
        }
      }

      // 4. Recent Commits
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

      // 6. Default branch snapshot if repo exists without explicit releases/tags/folders yet
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
            dataDownloadUrl = "https://github.com/$owner/$repo/archive/refs/heads/$branch.zip",
            assetName = "NickCraft-$branch.zip",
            localSizeLabel = localInfo?.let { formatByteSize(it.sizeBytes) }
          )
        )
      }

      withContext(Dispatchers.IO) {
        saveCachedSnapshot(
          CachedRepoSnapshot(
            repoInfo = repoInfo,
            versions = discovered,
            commits = commits
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
      val offlineList = mergeWithOfflineDownloaded(downloadedMap, cachedSnapshot)
      LauncherFetchResult(
        repoInfo = cachedSnapshot?.repoInfo,
        versions = offlineList,
        commits = cachedSnapshot?.commits.orEmpty(),
        syncStatus = RepoSyncStatus.Error(
          message = "Sem conexão com o GitHub. Exibindo versões já baixadas/em cache.",
          isNotFound = false
        )
      )
    } catch (e: Exception) {
      val offlineList = mergeWithOfflineDownloaded(downloadedMap, cachedSnapshot)
      LauncherFetchResult(
        repoInfo = cachedSnapshot?.repoInfo,
        versions = offlineList,
        commits = cachedSnapshot?.commits.orEmpty(),
        syncStatus = RepoSyncStatus.Error(
          message = "Falha ao carregar versões: ${e.localizedMessage ?: "erro desconhecido"}",
          isNotFound = false
        )
      )
    }
  }

  /**
   * Rate-limit-free GitHub Atom Feed fallback (`releases.atom`, `tags.atom`, `commits.atom`).
   * Continues working seamlessly even when `api.github.com` returns HTTP 403!
   */
  private suspend fun fetchViaGitHubAtomFeeds(
    owner: String,
    repo: String,
    downloadedMap: Map<String, DownloadedVersionManifest>
  ): LauncherFetchResult? = withContext(Dispatchers.IO) {
    try {
      val releasesXml = fetchTextUrl("https://github.com/$owner/$repo/releases.atom")
      val tagsXml = fetchTextUrl("https://github.com/$owner/$repo/tags.atom")
      val commitsXml = fetchTextUrl("https://github.com/$owner/$repo/commits.atom")

      // If none of the web feeds succeeded, fallback couldn't reach GitHub
      if (releasesXml == null && tagsXml == null && commitsXml == null) {
        return@withContext null
      }

      val repoInfo = GitHubRepoInfo(
        fullName = "$owner/$repo",
        description = "Repositório público $owner/$repo no GitHub",
        htmlUrl = "https://github.com/$owner/$repo",
        stars = 0,
        forks = 0,
        defaultBranch = "main",
        licenseName = "Open Source"
      )

      val discovered = mutableListOf<GameVersionEntry>()
      val seenTags = mutableSetOf<String>()

      // Parse Releases
      val releaseEntries = releasesXml?.let { GitHubAtomParser.parseEntries(it) }.orEmpty()
      for (rel in releaseEntries) {
        val tag = rel.link.substringAfterLast('/').ifBlank {
          rel.id.substringAfterLast('/').ifBlank { rel.title }
        }
        seenTags.add(tag.lowercase())
        val id = "release_${tag.replace(Regex("[^a-zA-Z0-9._-]"), "_")}"
        val localInfo = downloadedMap[id]
        discovered.add(
          GameVersionEntry(
            id = id,
            versionTag = tag,
            title = rel.title.ifBlank { "Nick Craft $tag" },
            source = VersionSource.GITHUB_RELEASE,
            description = rel.contentText.take(220).ifBlank {
              "Release oficial em $owner/$repo. Baixe no Launcher para jogar."
            },
            dateLabel = formatIsoDate(rel.updated),
            isDownloaded = localInfo != null,
            htmlUrl = rel.link.ifBlank { repoInfo.htmlUrl },
            dataDownloadUrl = "https://github.com/$owner/$repo/archive/refs/tags/$tag.zip",
            assetName = "NickCraft-$tag.zip",
            localSizeLabel = localInfo?.let { formatByteSize(it.sizeBytes) }
          )
        )
      }

      // Parse Tags not already in Releases
      val tagEntries = tagsXml?.let { GitHubAtomParser.parseEntries(it) }.orEmpty()
      for (tagEntry in tagEntries) {
        val tag = tagEntry.link.substringAfterLast('/').ifBlank {
          tagEntry.id.substringAfterLast('/').ifBlank { tagEntry.title }
        }
        if (tag.isBlank() || tag.lowercase() in seenTags) continue
        seenTags.add(tag.lowercase())
        val id = "tag_$tag"
        val localInfo = downloadedMap[id]
        discovered.add(
          GameVersionEntry(
            id = id,
            versionTag = tag,
            title = "Nick Craft $tag",
            source = VersionSource.GITHUB_TAG,
            description = "Versão marcada pela tag Git ($tag). Baixe para o Launcher para jogar.",
            dateLabel = formatIsoDate(tagEntry.updated),
            isDownloaded = localInfo != null,
            htmlUrl = tagEntry.link.ifBlank { "${repoInfo.htmlUrl}/releases/tag/$tag" },
            dataDownloadUrl = "https://github.com/$owner/$repo/archive/refs/tags/$tag.zip",
            assetName = "NickCraft-$tag.zip",
            localSizeLabel = localInfo?.let { formatByteSize(it.sizeBytes) }
          )
        )
      }

      // Parse Commits
      val commitEntries = commitsXml?.let { GitHubAtomParser.parseEntries(it) }.orEmpty()
      val commits = commitEntries.map { c ->
        val fullSha = c.id.substringAfterLast('/').ifBlank { c.link.substringAfterLast('/') }
        CommitEntry(
          shaShort = fullSha.take(7).ifBlank { "commit" },
          message = c.title.lineSequence().firstOrNull()?.trim().orEmpty(),
          authorName = c.authorName.ifBlank { owner },
          dateLabel = formatIsoDate(c.updated),
          htmlUrl = c.link.ifBlank { repoInfo.htmlUrl }
        )
      }

      // Preserve any downloaded version from repo that might have a custom ID (e.g. from earlier REST sync)
      for ((id, manifest) in downloadedMap) {
        if (discovered.none { it.id == id }) {
          val source = VersionSource.entries.firstOrNull { it.label == manifest.sourceLabel }
            ?: VersionSource.GITHUB_BRANCH
          discovered.add(
            GameVersionEntry(
              id = manifest.id,
              versionTag = manifest.versionTag,
              title = manifest.title,
              source = source,
              description = manifest.description,
              dateLabel = "Salva no Launcher",
              isDownloaded = true,
              htmlUrl = manifest.repoUrl,
              dataDownloadUrl = "https://github.com/$owner/$repo/archive/refs/heads/main.zip",
              assetName = "NickCraft-${manifest.versionTag}.zip",
              localSizeLabel = formatByteSize(manifest.sizeBytes)
            )
          )
        }
      }

      // If no releases/tags exist yet, expose main branch snapshot via github.com archive
      if (discovered.isEmpty()) {
        val branch = "main"
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
            dataDownloadUrl = "https://github.com/$owner/$repo/archive/refs/heads/$branch.zip",
            assetName = "NickCraft-$branch.zip",
            localSizeLabel = localInfo?.let { formatByteSize(it.sizeBytes) }
          )
        )
      }

      saveCachedSnapshot(
        CachedRepoSnapshot(
          repoInfo = repoInfo,
          versions = discovered,
          commits = commits
        )
      )

      LauncherFetchResult(
        repoInfo = repoInfo,
        versions = discovered,
        commits = commits,
        syncStatus = RepoSyncStatus.Loaded(isOnline = true)
      )
    } catch (_: Exception) {
      null
    }
  }

  private fun fetchTextUrl(url: String): String? {
    return runCatching {
      val req = Request.Builder()
        .url(url)
        .header("Accept", "application/atom+xml, application/xml, text/xml, */*")
        .header("User-Agent", "NickCraftLauncher/0.00")
        .build()
      httpClient.newCall(req).execute().use { res ->
        if (res.isSuccessful) res.body?.string() else null
      }
    }.getOrNull()
  }

  private fun mergeWithOfflineDownloaded(
    downloadedMap: Map<String, DownloadedVersionManifest>,
    cachedSnapshot: CachedRepoSnapshot?
  ): List<GameVersionEntry> {
    val merged = linkedMapOf<String, GameVersionEntry>()

    downloadedMap.values
      .sortedByDescending { it.downloadedAt }
      .forEach { manifest ->
        val source = VersionSource.entries.firstOrNull { it.label == manifest.sourceLabel }
          ?: VersionSource.GITHUB_RELEASE
        merged[manifest.id] = GameVersionEntry(
          id = manifest.id,
          versionTag = manifest.versionTag,
          title = manifest.title,
          source = source,
          description = manifest.description,
          dateLabel = "Salva no Launcher",
          isDownloaded = true,
          htmlUrl = manifest.repoUrl,
          localSizeLabel = formatByteSize(manifest.sizeBytes)
        )
      }

    cachedSnapshot?.versions?.forEach { cachedEntry ->
      if (!merged.containsKey(cachedEntry.id) && !cachedEntry.id.startsWith("apk_")) {
        merged[cachedEntry.id] = cachedEntry.copy(
          isDownloaded = false,
          downloadProgress = null,
          localSizeLabel = null
        )
      }
    }

    return merged.values.toList()
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
          .header("User-Agent", "NickCraftLauncher/0.00")
          .build()

        httpClient.newCall(request).execute().use { response ->
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
    return match?.value ?: fileName.removeSuffix(".zip").removeSuffix(".json")
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
    fun createDefaultHttpClient(cacheDir: File? = null): OkHttpClient {
      val builder = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
          val original = chain.request()
          val reqBuilder = original.newBuilder()
            .header("User-Agent", "NickCraftLauncher/0.00")
          if (original.url.host == "api.github.com" && original.header("Accept") == null) {
            reqBuilder.header("Accept", "application/vnd.github+json")
          }
          chain.proceed(reqBuilder.build())
        }
      if (cacheDir != null) {
        builder.cache(Cache(cacheDir, 4L * 1024L * 1024L))
      }
      return builder.build()
    }

    fun createDefaultApi(client: OkHttpClient = createDefaultHttpClient()): GitHubApiService {
      val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

      return Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GitHubApiService::class.java)
    }
  }
}
