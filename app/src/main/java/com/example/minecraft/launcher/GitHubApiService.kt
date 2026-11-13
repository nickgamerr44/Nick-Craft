package com.example.minecraft.launcher

import com.squareup.moshi.Json
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class GitHubLicenseDto(
  val name: String? = null,
  @Json(name = "spdx_id") val spdxId: String? = null
)

data class GitHubRepoDto(
  @Json(name = "full_name") val fullName: String = "",
  val description: String? = null,
  @Json(name = "html_url") val htmlUrl: String = "",
  @Json(name = "stargazers_count") val stargazersCount: Int = 0,
  @Json(name = "forks_count") val forksCount: Int = 0,
  @Json(name = "default_branch") val defaultBranch: String = "main",
  val license: GitHubLicenseDto? = null
)

data class GitHubAssetDto(
  val name: String = "",
  val size: Long = 0L,
  @Json(name = "browser_download_url") val browserDownloadUrl: String = ""
)

data class GitHubReleaseDto(
  val id: Long = 0L,
  @Json(name = "tag_name") val tagName: String = "",
  val name: String? = null,
  val body: String? = null,
  val prerelease: Boolean = false,
  val draft: Boolean = false,
  @Json(name = "published_at") val publishedAt: String? = null,
  @Json(name = "html_url") val htmlUrl: String = "",
  val assets: List<GitHubAssetDto> = emptyList()
)

data class GitHubTagCommitDto(
  val sha: String = ""
)

data class GitHubTagDto(
  val name: String = "",
  val commit: GitHubTagCommitDto = GitHubTagCommitDto(),
  @Json(name = "zipball_url") val zipballUrl: String? = null
)

data class GitHubContentDto(
  val name: String = "",
  val path: String = "",
  val size: Long = 0L,
  val type: String = "",
  @Json(name = "html_url") val htmlUrl: String? = null,
  @Json(name = "download_url") val downloadUrl: String? = null
)

data class GitHubCommitAuthorDto(
  val name: String = "",
  val date: String? = null
)

data class GitHubCommitDetailsDto(
  val message: String = "",
  val author: GitHubCommitAuthorDto = GitHubCommitAuthorDto()
)

data class GitHubCommitSummaryDto(
  val sha: String = "",
  val commit: GitHubCommitDetailsDto = GitHubCommitDetailsDto(),
  @Json(name = "html_url") val htmlUrl: String = ""
)

interface GitHubApiService {
  @GET("repos/{owner}/{repo}")
  suspend fun getRepository(
    @Path("owner") owner: String,
    @Path("repo") repo: String
  ): Response<GitHubRepoDto>

  @GET("repos/{owner}/{repo}/releases")
  suspend fun getReleases(
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Query("per_page") perPage: Int = 50
  ): Response<List<GitHubReleaseDto>>

  @GET("repos/{owner}/{repo}/tags")
  suspend fun getTags(
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Query("per_page") perPage: Int = 50
  ): Response<List<GitHubTagDto>>

  @GET("repos/{owner}/{repo}/contents/{path}")
  suspend fun getContents(
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Path("path") path: String
  ): Response<List<GitHubContentDto>>

  @GET("repos/{owner}/{repo}/commits")
  suspend fun getCommits(
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Query("per_page") perPage: Int = 15
  ): Response<List<GitHubCommitSummaryDto>>
}
