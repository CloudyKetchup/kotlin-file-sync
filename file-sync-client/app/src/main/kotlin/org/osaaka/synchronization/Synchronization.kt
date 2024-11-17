package org.osaaka.synchronization

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import java.io.File
import java.nio.file.Files
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import org.osaaka.models.DirectoryNodesResponse
import org.osaaka.models.DirectoryTreeNodesRequest
import org.osaaka.models.DirectoryTreeNode
import org.osaaka.extensions.basicFileAttributes
import org.osaaka.util.buildProgressBar
import java.io.ByteArrayOutputStream

val ignoreFiles = listOf("node_modules")
const val remoteHost = "127.0.0.1"
const val remotePort = 8080

class Synchronization(private val syncFolder: String) {
    private val httpClient = HttpClient(CIO) {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(Json)
        }
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
            })
        }
        defaultRequest {
            host = remoteHost
            port = remotePort
        }
    }

    private suspend fun uploadFiles(files: List<String>) {
        val totalFiles = files.size

        httpClient.webSocket(
            method = HttpMethod.Get,
            host = remoteHost,
            port = remotePort,
            path = "/upload"
        ) {
            files.forEachIndexed { index, filePath ->
                val substringPath = filePath.substringAfter(syncFolder)
                val file = File(filePath)

                if (file.length() == 0.toLong()) {
                    send(Frame.Text("FilePath:${substringPath}:empty"))
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
                    send(Frame.Text("FilePath:$substringPath"))
                }
                // Calculate the progress percentage
                val progress = (index + 1) * 100 / totalFiles
                // Build the progress bar
                val progressBar = buildProgressBar(progress, substringPath)

                // // Display the progress bar
                print("\r$progressBar [$index/$totalFiles]")
            }
            println("\nAll files synced")
            close()
        }
    }

    suspend fun pullFiles(files: List<String>) {
        fun handleTextFrame(
            message: String,
            chunks: MutableList<ByteArray>
        ): String? {
            val filePath = if (message.startsWith("FilePath:")) {
                val path = message.removePrefix("FilePath:").removeSuffix(":empty")
                val file = File("$syncFolder/$path")
                file.parentFile.mkdirs()
                if (message.endsWith(":empty")) file.createNewFile()
                println("Empty file created: ${file.absolutePath}")
                path
            } else null

            if (chunks.isNotEmpty() && filePath != null) {
                val file = File("$syncFolder/$filePath")
                file.parentFile.mkdirs()
                file.writeBytes(chunks.flattenToBytes())
                println("File saved: ${file.absolutePath}")
                chunks.clear()
            }

            return filePath
        }
    
        httpClient.webSocket(
            method = HttpMethod.Get,
            host = remoteHost,
            port = remotePort,
            path = "/download"
        ) {
            sendSerialized(DirectoryTreeNodesRequest(files))

            val chunks = mutableListOf<ByteArray>()
            // Receive file metadata and data
            for (frame in incoming) {
                when (frame) {
                    is Frame.Binary -> chunks.add(frame.readBytes())
                    is Frame.Text -> { handleTextFrame(frame.readText(), chunks) }
                    else -> Unit
                }
            }
        }
    }

    private fun localFiles(parent: File): List<File> {
        val files = mutableListOf<File>()

        fun iterateFiles(directory: File, files: MutableList<File>) {
            directory.listFiles().forEach { file ->
                if (!ignoreFiles.contains(file.name)) {
                    if (file.isDirectory) {
                        iterateFiles(file, files)
                    } else if (file.isFile) {
                        files.add(file)
                    }
                }
            }
        }

        iterateFiles(parent, files)

        return files
    }

    private fun localNewerThanRemote(local: File, remote: DirectoryTreeNode): Boolean {
        val timeFormatter = DateTimeFormatter.ISO_DATE_TIME
        val localAttributes = local.basicFileAttributes()
        val localModified = ZonedDateTime.parse(localAttributes.lastModifiedTime().toString(), timeFormatter)
        val remoteModified = ZonedDateTime.parse(remote.dateModified, timeFormatter)

        return localModified.isAfter(remoteModified)
    }

    private fun localRemoteCompare(
        localFiles: List<File>,
        remoteFiles: List<DirectoryTreeNode>,
        predicate: (local: File, remote: DirectoryTreeNode?) -> Boolean
    ) = localFiles.filter { local ->
        // find the file on remote, null if not found
        val remote = remoteFiles.find { remote -> remote.path == local.path.substringAfter(syncFolder) }

        predicate(local, remote)
    }

    private fun remoteLocalCompare(
        localFiles: List<File>,
        remoteFiles: List<DirectoryTreeNode>,
        predicate: (local: File?, remote: DirectoryTreeNode) -> Boolean
    ) = remoteFiles.filter { remote ->
        // find the file on remote, null if not found
        val local = localFiles.find { local -> remote.path == local.path.substringAfter(syncFolder) }

        predicate(local, remote)
    }

    suspend fun sync() {
        val remote: DirectoryNodesResponse? = try {
            httpClient.get("/tree").body()
        } catch (e: Exception) {
            null
        }
        if (remote != null) {
            val local = localFiles(File(syncFolder))
            val forUpload = localRemoteCompare(local, remote.files) { local, remote ->
                if (remote == null) true else localNewerThanRemote(local, remote)
            }
            val forPull = remoteLocalCompare(local, remote.files) { local, remote ->
                if (local == null) true else !localNewerThanRemote(local, remote)
            }

            pullFiles(forPull.map{ it.path })
            // uploadFiles(forUpload.map { it.path })
        }
    }
}

fun List<ByteArray>.flattenToBytes(): ByteArray {
    return ByteArrayOutputStream().use { output ->
        this.forEach { output.write(it) }
        output.toByteArray()
    }
}