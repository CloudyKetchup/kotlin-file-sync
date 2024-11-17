package com.osaaka.plugins

import com.osaaka.StoreConfig
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import org.osaaka.models.DirectoryTreeNode
import org.osaaka.models.DirectoryNodesResponse
import org.osaaka.models.DirectoryTreeNodesRequest
import org.osaaka.extensions.basicFileAttributes
import org.osaaka.util.buildProgressBar
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.time.Duration.Companion.seconds

fun Application.configureRouting() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
    }
    routing {
        storeTree()
        fileSync()
    }
}

fun directoryFilesList(directory: File): List<DirectoryTreeNode> {
    return directory.walkTopDown()
        .filter { it.isFile }
        .map { file ->
            val attrs = file.basicFileAttributes()
            DirectoryTreeNode(
                name = file.name,
                path = file.path,
                dateCreated = attrs.creationTime().toString(),
                dateModified = attrs.lastModifiedTime().toString()
            )
        }.toList()
}

fun Route.storeTree() {
    val storeConfig: StoreConfig by inject()

    get("/tree") {
        val store = File(storeConfig.storeFolder)
        val files = directoryFilesList(store).map {
            it.copy(path = it.path.removePrefix(storeConfig.storeFolder))
        }
        call.respond(DirectoryNodesResponse(files))
    }
}

fun Route.fileSync() {
    val storeConfig: StoreConfig by inject()

    webSocket("/upload") {
        println("New upload WebSocket connection established")
        val chunks = mutableListOf<ByteArray>()

        for (frame in incoming) {
            when (frame) {
                is Frame.Binary -> chunks.add(frame.readBytes())
                is Frame.Text -> handleTextFrame(frame.readText(), storeConfig, chunks)
                else -> Unit
            }
        }
    }

    //TODO: cleanup
    webSocket("/download") {
        println("New download WebSocket connection established")

        incoming.consumeEach { frame ->
            when (frame) {
                is Frame.Text -> {
                    val nodesRequest = Json.decodeFromString<DirectoryTreeNodesRequest>(frame.readText())
                    val files = nodesRequest.filePaths
                    val totalFiles = files.size

                    files.forEachIndexed { index, filePath ->
                        val file = File("${storeConfig.storeFolder}/$filePath")

                        if (file.length() == 0.toLong()) {
                            send(Frame.Text("FilePath:${filePath}:empty"))
                        } else {
                            val fileBytes = Files.readAllBytes(file.toPath())
                            val chunkSize = 1024 * 16 // 16 KB per chunk
                            var offset = 0

                            // Send the file in chunks
                            while (offset < fileBytes.size) {
                                val end = (offset + chunkSize).coerceAtMost(fileBytes.size)
                                val chunk = fileBytes.copyOfRange(offset, end)
                                send(Frame.Binary(true, chunk)) // Sending binary frame (chunks)
                                offset = end
                            }
                            // Send the file path metadata
                            send(Frame.Text("FilePath:$filePath"))
                        }
                        // Calculate the progress percentage
                        val progress = (index + 1) * 100 / totalFiles
                        // Build the progress bar
                        val progressBar = buildProgressBar(progress, filePath)

                        // Display the progress bar
                        print("\r$progressBar [$index/$totalFiles]")
                    }
                    if (totalFiles > 0) {
                        send(Frame.Text("End"))
                    }
                }
                else -> Unit
            }
        }
    }
}

private fun handleTextFrame(
    message: String,
    storeConfig: StoreConfig,
    chunks: MutableList<ByteArray>
): String? {
    val filePath = if (message.startsWith("FilePath:")) {
        val path = message.removePrefix("FilePath:").removeSuffix(":empty")
        val file = File("${storeConfig.storeFolder}/$path")
        file.parentFile.mkdirs()
        if (message.endsWith(":empty")) file.createNewFile()
        println("Empty file created: ${file.absolutePath}")
        path
    } else null

    if (chunks.isNotEmpty() && filePath != null) {
        val file = File("${storeConfig.storeFolder}/$filePath")
        file.parentFile.mkdirs()
        file.writeBytes(chunks.flattenToBytes())
        println("File saved: ${file.absolutePath}")
        chunks.clear()
    }

    return filePath
}

private fun List<ByteArray>.flattenToBytes(): ByteArray {
    return ByteArrayOutputStream().use { output ->
        this.forEach { output.write(it) }
        output.toByteArray()
    }
}