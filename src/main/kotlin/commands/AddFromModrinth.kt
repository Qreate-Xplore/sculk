package tech.jamalam.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.arguments.argument
import kotlinx.coroutines.runBlocking
import tech.jamalam.*
import tech.jamalam.manifest.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class AddFromModrinth : CliktCommand(name = "modrinth") {
    private val query by argument()

    override fun run() {
        val pack = InMemoryPack(ctx.json)
        val projects = runBlocking {
            ctx.modrinth.search(query)
        }

        val project =
            PrettyListPrompt("Select a project", projects.map { it.title }, terminal).ask()
                .let { projects.find { p -> p.title == it } } ?: return

        val versions = runBlocking {
            ctx.modrinth.findVersions(
                project.slug, pack.getManifest().loader.type, pack.getManifest().minecraft
            )
        }.sortedBy {
            LocalDateTime.parse(
                it.publishedDate, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
            )
        }.reversed()

        val version = versions[0] // TODO: error handling
        val modrinthFile = version.files.first { it.primary }

        val tempFile = runBlocking { downloadFileTemp(parseUrl(modrinthFile.url)) }

        val fileManifest = FileManifest(
            filename = modrinthFile.filename, hashes = FileManifestHashes(
                sha1 = modrinthFile.hashes.sha1, sha512 = modrinthFile.hashes.sha512
            ),
            fileSize = tempFile.readBytes().size,
            sources = FileManifestSources(
                curseforge = null, modrinth = FileManifestModrinthSource(
                    projectId = project.id, fileUrl = modrinthFile.url
                ), url = null
            )
        )

        val dir = if (version.loaders.contains("minecraft")) {
            TODO()
        } else {
            "mods"
        }


        pack.addFileManifest("$dir/${project.slug}.sculk.json", fileManifest)
        echo("Added ${fileManifest.filename} to manifest")
        terminal.println(terminal.theme.info("Saving pack manifest..."))
        pack.save(ctx.json)
        echo("Added ${project.title} to manifest")
    }
}
