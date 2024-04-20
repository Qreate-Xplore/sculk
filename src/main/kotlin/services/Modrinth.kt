package tech.jamalam.services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tech.jamalam.pack.ModLoader
import tech.jamalam.pack.Side
import java.net.URLEncoder

class Modrinth(private val client: HttpClient) {
    suspend fun search(query: String): List<ModrinthProject> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        return client.get(
            "https://api.modrinth.com/v2/search?query=$encodedQuery"
        ).body<ModrinthSearchResponse>().hits.map {
            ModrinthProject(
                it.id,
                it.slug,
                it.title,
                it.description,
                it.clientSide,
                it.serverSide
            )
        }
    }

    suspend fun getProject(projectIdOrSlug: String): ModrinthProject? {
        val response = client.get(
            "https://api.modrinth.com/v2/project/$projectIdOrSlug"
        )

        if (response.status == HttpStatusCode.NotFound) {
            return null
        }

        return response.body<ModrinthProject>()
    }

    suspend fun getValidVersions(
        projectId: String, modLoader: ModLoader, gameVersion: String
    ): List<ModrinthVersion> {
        val validLoaders = listOf(
            "minecraft", when (modLoader) {
                ModLoader.Fabric -> "fabric"
                ModLoader.Forge -> "forge"
                ModLoader.Neoforge -> "neoforge"
                ModLoader.Quilt -> "quilt"
            }
        )
        val loaders =
            validLoaders.joinToString(",") { "%22$it%22" }
        val gameVersions = "%22$gameVersion%22"

        return client.get(
            "https://api.modrinth.com/v2/project/$projectId/version?loaders=[$loaders]&game_versions=[$gameVersions]",
        ).body<List<ModrinthVersion>>()
    }

    suspend fun reverseLookupVersion(
        sha1Hash: String
    ): ModrinthVersion? {
        val response = client.get(
            "https://api.modrinth.com/v2/version_file/$sha1Hash",
        )

        if (response.status == HttpStatusCode.NotFound) {
            return null
        }

        return response.body<ModrinthVersion>()
    }
}

@Serializable
data class ModrinthSearchResponse(
    val hits: List<ModrinthSearchProject>,
)

@Serializable
data class ModrinthSearchProject(
    @SerialName("project_id")
    val id: String,
    val slug: String,
    val title: String,
    val description: String,
    @SerialName("client_side")
    val clientSide: ModrinthEnvType,
    @SerialName("server_side")
    val serverSide: ModrinthEnvType,
)

@Serializable
data class ModrinthProject(
    val id: String,
    val slug: String,
    val title: String,
    val description: String,
    @SerialName("client_side")
    val clientSide: ModrinthEnvType,
    @SerialName("server_side")
    val serverSide: ModrinthEnvType,
)

@Serializable
data class ModrinthVersion(
    val name: String,
    @SerialName("project_id")
    val projectId: String,
    val id: String,
    @SerialName("date_published") val publishedDate: String,
    val loaders: List<String>,
    val files: List<ModrinthFile>,
)

@Serializable
data class ModrinthFile(
    val url: String,
    val filename: String,
    val primary: Boolean,
    val hashes: ModrinthFileHashes,
)

@Serializable
data class ModrinthFileHashes(
    val sha1: String,
    val sha512: String,
)

@Serializable
enum class ModrinthEnvType {
    @SerialName("required")
    Required,

    @SerialName("optional")
    Optional,

    @SerialName("unsupported")
    Unsupported
}

fun modrinthEnvTypePairToSide(clientSide: ModrinthEnvType, serverSide: ModrinthEnvType) = when (clientSide to serverSide) {
    ModrinthEnvType.Unsupported to ModrinthEnvType.Required -> Side.ServerOnly
    ModrinthEnvType.Unsupported to ModrinthEnvType.Optional -> Side.ClientOnly
    ModrinthEnvType.Required to ModrinthEnvType.Unsupported -> Side.ClientOnly
    ModrinthEnvType.Optional to ModrinthEnvType.Unsupported -> Side.ServerOnly
    else -> Side.Both
}

fun sideToModrinthEnvTypePair(side: Side) = when (side) {
    Side.ServerOnly -> ModrinthEnvType.Unsupported to ModrinthEnvType.Required
    Side.ClientOnly -> ModrinthEnvType.Required to ModrinthEnvType.Unsupported
    Side.Both -> ModrinthEnvType.Required to ModrinthEnvType.Required
}
