package org.osaaka.synchronization

import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.request.get
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import java.io.File
import org.osaaka.models.DirectoryNodesResponse
import org.osaaka.models.DirectoryTreeNodesRequest
import org.osaaka.extensions.flattenToBytes
import org.osaaka.http.HttpService
import org.osaaka.service.FileService
import org.osaaka.tracker.ProgressTracker

class SyncManager(
    private val httpService: HttpService,
    private val fileService: FileService,
    private val progressTracker: ProgressTracker
) {
    suspend fun uploadFiles(files: List<String>) {
        if (files.isEmpty()) return

        httpService.webSocket("/upload") {
            files.forEachIndexed { index, filePath ->
                val relativePath = filePath.substringAfter(fileService.rootFolder)
                val file = File(filePath)

                if (file.length() == 0L) {
                    send(Frame.Text("FilePath:$relativePath:empty"))
                } else {
                    sendFileInChunks(file, relativePath)
                }

                val progress = (index + 1) * 100 / files.size
                progressTracker.displayProgress(index + 1, files.size, progress, relativePath)
            }
            println("\nAll files synced")
        }
    }

    private suspend fun WebSocketSession.sendFileInChunks(file: File, relativePath: String) {
        val fileBytes = file.readBytes()
        val chunkSize = 16 * 1024

        fileBytes.asSequence().chunked(chunkSize).forEach { chunk ->
            send(Frame.Binary(true, chunk.toByteArray()))
        }
        send(Frame.Text("FilePath:$relativePath"))
    }

    suspend fun pullFiles(files: List<String>) {
        if (files.isEmpty()) return

        httpService.webSocket("/download") {
            sendSerialized(DirectoryTreeNodesRequest(files))

            val chunks = mutableListOf<ByteArray>()
            for (frame in incoming) {
                when (frame) {
                    is Frame.Binary -> chunks.add(frame.readBytes())
                    is Frame.Text -> handleTextFrame(frame.readText(), chunks)
                    else -> Unit
                }
            }
        }
    }

    private fun handleTextFrame(message: String, chunks: MutableList<ByteArray>) {
        val relativePath = message.takeIf { it.startsWith("FilePath:") }?.removePrefix("FilePath:")?.removeSuffix(":empty")
        if (relativePath != null) {
            if (message.endsWith(":empty")) {
                fileService.createEmptyFile(relativePath)
            } else if (chunks.isNotEmpty()) {
                fileService.writeFile(relativePath, chunks.flattenToBytes())
                chunks.clear()
            }
        }
    }

    suspend fun sync() {
        val remote: DirectoryNodesResponse? = try {
            httpService.client.get("/tree").body()
        } catch (e: Exception) {
            println("Error fetching remote tree: ${e.message}")
            null
        }

        if (remote != null) {
            val local = fileService.localFiles()
            val forUpload = local.filter { localFile ->
                remote.files.none { it.path == localFile.path.substringAfter(fileService.rootFolder) } ||
                        fileService.isNewerThan(localFile, remote.files.first { it.path == localFile.path.substringAfter(fileService.rootFolder) })
            }

            val forPull = remote.files.filter { remoteFile ->
                local.none { it.path.substringAfter(fileService.rootFolder) == remoteFile.path } ||
                        !fileService.isNewerThan(local.first { it.path.substringAfter(fileService.rootFolder) == remoteFile.path }, remoteFile)
            }

            pullFiles(forPull.map { it.path })
            uploadFiles(forUpload.map { it.path })
        }
    }
}