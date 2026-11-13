package com.example

import com.example.minecraft.model.BlockType
import com.example.minecraft.model.GameMode
import com.example.minecraft.model.ItemCategory
import com.example.minecraft.model.ItemStack
import com.example.minecraft.model.ItemType
import com.example.minecraft.model.Player
import com.example.minecraft.model.RecipeBook
import com.example.minecraft.model.VoxelWorld
import com.example.minecraft.model.WorldGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

import com.example.minecraft.launcher.GitHubApiService
import com.example.minecraft.launcher.GitHubAssetDto
import com.example.minecraft.launcher.GitHubCommitAuthorDto
import com.example.minecraft.launcher.GitHubCommitDetailsDto
import com.example.minecraft.launcher.GitHubCommitSummaryDto
import com.example.minecraft.launcher.GitHubContentDto
import com.example.minecraft.launcher.GitHubLauncherRepository
import com.example.minecraft.launcher.GitHubReleaseDto
import com.example.minecraft.launcher.GitHubRepoDto
import com.example.minecraft.launcher.GitHubTagCommitDto
import com.example.minecraft.launcher.GitHubTagDto
import com.example.minecraft.launcher.RepoSyncStatus
import com.example.minecraft.launcher.VersionSource
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

class ExampleUnitTest {
  @Test
  fun testBlockTypeMapping() {
    assertEquals(BlockType.GRASS, BlockType.fromId(1))
    assertEquals(BlockType.BEDROCK, BlockType.fromId(10))
    assertEquals(BlockType.AIR, BlockType.fromId(0))
    assertTrue(BlockType.BEDROCK.isUnbreakable)
    assertFalse(BlockType.AIR.isSolid)
  }

  @Test
  fun testWorldGeneration() {
    val world = VoxelWorld(32, 24, 32)
    WorldGenerator.generate(world, seed = 54321L)

    // Bedrock must be present at y = 0
    for (x in 0 until 32) {
      for (z in 0 until 32) {
        assertEquals(BlockType.BEDROCK, world.getBlock(x, 0, z))
      }
    }

    // World must have solid surface
    var hasGrass = false
    for (x in 0 until 32) {
      for (y in 5 until 20) {
        for (z in 0 until 32) {
          if (world.getBlock(x, y, z) == BlockType.GRASS) {
            hasGrass = true
            break
          }
        }
      }
    }
    assertTrue("World should contain grass blocks", hasGrass)
  }

  @Test
  fun testInventoryAndCrafting() {
    val player = Player()
    player.gameMode = GameMode.SURVIVAL

    // Add 1 Oak Log
    player.inventory.addItem(ItemType.BLOCK_OAK_LOG, 1)
    assertTrue(player.inventory.hasItem(ItemType.BLOCK_OAK_LOG, 1))

    // Craft planks
    val recipe = RecipeBook.RECIPES.first { it.id == "planks_from_log" }
    val canCraft = RecipeBook.canCraft(recipe, player.inventory, hasCraftingTable = false)
    assertTrue(canCraft)

    val crafted = RecipeBook.craft(recipe, player.inventory, hasCraftingTable = false)
    assertTrue(crafted)
    assertFalse(player.inventory.hasItem(ItemType.BLOCK_OAK_LOG, 1))
    assertTrue(player.inventory.hasItem(ItemType.BLOCK_OAK_PLANKS, 4))
  }

  @Test
  fun testPlayerMovementAndDamage() {
    val player = Player(10f, 15f, 10f)
    assertEquals(20f, player.health, 0.001f)

    player.takeDamage(5f)
    assertEquals(15f, player.health, 0.001f)

    player.eat(ItemType.APPLE)
    assertTrue(player.health > 15f)
  }

  @Test
  fun testGitHubLauncherRepositoryParsesReleasesApksAndTags() = runTest {
    val fakeApi = object : GitHubApiService {
      override suspend fun getRepository(owner: String, repo: String) = Response.success(
        GitHubRepoDto(
          fullName = "nickgamerr44/Nick-Craft",
          description = "Open source voxel game",
          htmlUrl = "https://github.com/nickgamerr44/Nick-Craft",
          stargazersCount = 12,
          forksCount = 3
        )
      )

      override suspend fun getReleases(owner: String, repo: String, perPage: Int) = Response.success(
        listOf(
          GitHubReleaseDto(
            id = 101L,
            tagName = "v0.01",
            name = "Nick Craft 0.01",
            body = "Adicionado sistema de nuvens",
            publishedAt = "2026-11-10T12:00:00Z",
            htmlUrl = "https://github.com/nickgamerr44/Nick-Craft/releases/tag/v0.01",
            assets = listOf(
              GitHubAssetDto(
                name = "NickCraft-0.01.apk",
                size = 10_000_000L,
                browserDownloadUrl = "https://github.com/nickgamerr44/Nick-Craft/releases/download/v0.01/NickCraft-0.01.apk"
              )
            )
          )
        )
      )

      override suspend fun getTags(owner: String, repo: String, perPage: Int) = Response.success(
        listOf(
          GitHubTagDto(name = "v0.01", commit = GitHubTagCommitDto("abcdef123456")),
          GitHubTagDto(name = "0.00", commit = GitHubTagCommitDto("1234567890ab"))
        )
      )

      override suspend fun getContents(owner: String, repo: String, path: String) = when (path) {
        "versions" -> Response.success(
          listOf(
            GitHubContentDto(
              name = "0.02.json",
              path = "versions/0.02.json",
              size = 2_048L,
              type = "file",
              htmlUrl = "https://github.com/nickgamerr44/Nick-Craft/blob/main/versions/0.02.json",
              downloadUrl = "https://raw.githubusercontent.com/nickgamerr44/Nick-Craft/main/versions/0.02.json"
            )
          )
        )
        "apk" -> Response.success(
          listOf(
            GitHubContentDto(
              name = "NickCraft-0.00.apk",
              path = "apk/NickCraft-0.00.apk",
              size = 9_800_000L,
              type = "file",
              htmlUrl = "https://github.com/nickgamerr44/Nick-Craft/blob/main/apk/NickCraft-0.00.apk",
              downloadUrl = "https://raw.githubusercontent.com/nickgamerr44/Nick-Craft/main/apk/NickCraft-0.00.apk"
            )
          )
        )
        else -> Response.error(404, "Not Found".toResponseBody())
      }

      override suspend fun getCommits(owner: String, repo: String, perPage: Int) = Response.success(
        listOf(
          GitHubCommitSummaryDto(
            sha = "abcdef123456",
            commit = GitHubCommitDetailsDto(
              message = "Initial open source upload",
              author = GitHubCommitAuthorDto(name = "nickgamerr44", date = "2026-11-10T12:00:00Z")
            ),
            htmlUrl = "https://github.com/nickgamerr44/Nick-Craft/commit/abcdef1"
          )
        )
      )
    }

    val repo = GitHubLauncherRepository(fakeApi)
    val result = repo.fetchVersions()

    assertTrue(result.syncStatus is RepoSyncStatus.Loaded)
    assertNotNull(result.repoInfo)
    assertEquals(12, result.repoInfo?.stars)
    // v0.01 Release + /versions/0.02.json + /apk/NickCraft-0.00.apk + 0.00 tag (v0.01 tag deduplicated)
    assertEquals(4, result.versions.size)
    assertEquals(VersionSource.GITHUB_RELEASE, result.versions[0].source)
    assertEquals(VersionSource.GITHUB_VERSIONS_DIR, result.versions[1].source)
    assertEquals(VersionSource.GITHUB_APK, result.versions[2].source)
    assertEquals(VersionSource.GITHUB_TAG, result.versions[3].source)
    assertEquals(1, result.commits.size)
  }

  @Test
  fun testGitHubLauncherRepository404HasNoPlayableVersionBeforeDownload() = runTest {
    val fakeNotFoundApi = object : GitHubApiService {
      override suspend fun getRepository(owner: String, repo: String): Response<GitHubRepoDto> =
        Response.error(404, "Not Found".toResponseBody())
      override suspend fun getReleases(owner: String, repo: String, perPage: Int): Response<List<GitHubReleaseDto>> =
        Response.error(404, "Not Found".toResponseBody())
      override suspend fun getTags(owner: String, repo: String, perPage: Int): Response<List<GitHubTagDto>> =
        Response.error(404, "Not Found".toResponseBody())
      override suspend fun getContents(owner: String, repo: String, path: String): Response<List<GitHubContentDto>> =
        Response.error(404, "Not Found".toResponseBody())
      override suspend fun getCommits(owner: String, repo: String, perPage: Int): Response<List<GitHubCommitSummaryDto>> =
        Response.error(404, "Not Found".toResponseBody())
    }

    val repo = GitHubLauncherRepository(fakeNotFoundApi)
    val result = repo.fetchVersions()

    assertTrue(result.syncStatus is RepoSyncStatus.Error)
    assertTrue((result.syncStatus as RepoSyncStatus.Error).isNotFound)
    assertTrue(result.versions.isEmpty())
  }

  @Test
  fun testDownloadVersionIntoLauncherAndDelete() = runTest {
    val tempDir = kotlin.io.path.createTempDirectory("nickcraft_test_versions").toFile()
    try {
      val fakeNotFoundApi = object : GitHubApiService {
        override suspend fun getRepository(owner: String, repo: String): Response<GitHubRepoDto> =
          Response.error(404, "Not Found".toResponseBody())
        override suspend fun getReleases(owner: String, repo: String, perPage: Int): Response<List<GitHubReleaseDto>> =
          Response.error(404, "Not Found".toResponseBody())
        override suspend fun getTags(owner: String, repo: String, perPage: Int): Response<List<GitHubTagDto>> =
          Response.error(404, "Not Found".toResponseBody())
        override suspend fun getContents(owner: String, repo: String, path: String): Response<List<GitHubContentDto>> =
          Response.error(404, "Not Found".toResponseBody())
        override suspend fun getCommits(owner: String, repo: String, perPage: Int): Response<List<GitHubCommitSummaryDto>> =
          Response.error(404, "Not Found".toResponseBody())
      }

      val repo = GitHubLauncherRepository(
        api = fakeNotFoundApi,
        versionsDir = tempDir
      )
      val remoteEntry = com.example.minecraft.launcher.GameVersionEntry(
        id = "release_555",
        versionTag = "0.02",
        title = "Nick Craft 0.02",
        source = VersionSource.GITHUB_RELEASE,
        description = "Versão com novos recursos",
        dateLabel = "13/11/2026",
        isDownloaded = false,
        htmlUrl = "https://github.com/nickgamerr44/Nick-Craft/releases/tag/0.02",
        dataDownloadUrl = null
      )

      val progressUpdates = mutableListOf<Float>()
      val downloadResult = repo.downloadVersionIntoLauncher(remoteEntry) { progressUpdates.add(it) }
      assertTrue(downloadResult.isSuccess)
      assertTrue(progressUpdates.isNotEmpty())
      assertEquals(1.0f, progressUpdates.last(), 0.001f)

      val manifests = repo.listDownloadedManifests()
      assertTrue(manifests.containsKey("release_555"))
      assertEquals("0.02", manifests["release_555"]?.versionTag)

      // Offline fetch returns only downloaded versions
      val offlineFetch = repo.fetchVersions()
      assertEquals(1, offlineFetch.versions.size)
      assertTrue(offlineFetch.versions.any { it.id == "release_555" && it.isDownloaded })

      // Delete downloaded version
      assertTrue(repo.deleteDownloadedVersion("release_555"))
      assertFalse(repo.listDownloadedManifests().containsKey("release_555"))
      assertTrue(repo.listDownloadedVersionsOffline().isEmpty())
    } finally {
      tempDir.deleteRecursively()
    }
  }
}
