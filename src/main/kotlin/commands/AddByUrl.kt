package tech.jamalam.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.option
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import tech.jamalam.*
import tech.jamalam.pack.*

class AddByUrl : CliktCommand(name = "url") {
    private val name by option().prettyPrompt<String>("Enter project name")
    private val url by option()
        .prettyPrompt<Url>("Enter download URL")
    private val filename by option().prettyPrompt<String>("Enter name")
    private val type by option().prettyPrompt<Type>("Select type")

    override fun run() {
        val pack = InMemoryPack(ctx.json)
        val tempFile = runBlocking { downloadFileTemp(url) }
        val contents = tempFile.readBytes()

        val fileManifest = FileManifest(
            filename = filename,
            hashes = FileManifestHashes(
                sha1 = contents.digestSha1(),
                sha512 = contents.digestSha512()
            ),
            fileSize = contents.size,
            sources = FileManifestSources(
                curseforge = null,
                modrinth = null,
                url = FileManifestUrlSource(url.toString())
            )
        )

        val dir = when (type) {
            Type.Mod -> "mods"
        }

        pack.addFileManifest("$dir/$filename.sculk.json", fileManifest)
        echo("Added $name to manifest")
        terminal.println(terminal.theme.info("Saving pack manifest..."))
        pack.save(ctx.json)
    }

    enum class Type {
        Mod
    }
}
