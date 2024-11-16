package com.osaaka.plugins

import com.osaaka.StoreConfig
import com.osaaka.storeConfig
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
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import org.osaaka.models.DirectoryTreeNode
import org.osaaka.models.DirectoryNodesResponse
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

fun Application.configureRouting() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    routing {
        storeTree()
        uploadFiles()
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

fun Route.uploadFiles() {
    val storeConfig: StoreConfig by inject()

    webSocket("/upload") {
        println("New WebSocket connection established")
        val chunks = mutableListOf<ByteArray>()
        // var filePath: String? = null

        for (frame in incoming) {
            when (frame) {
                is Frame.Binary -> chunks.add(frame.readBytes())
                is Frame.Text -> handleTextFrame(frame.readText(), storeConfig, chunks)
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

fun File.basicFileAttributes(): BasicFileAttributes =
    Files.readAttributes(this.toPath(), BasicFileAttributes::class.java)