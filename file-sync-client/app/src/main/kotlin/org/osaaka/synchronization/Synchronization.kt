package org.osaaka.synchronization

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import java.io.File
import java.nio.file.Files
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import org.osaaka.models.DirectoryNodesResponse
import org.osaaka.models.DirectoryTreeNode
import org.osaaka.extensions.basicFileAttributes

val ignoreFiles = listOf("node_modules")

fun buildProgressBar(progress: Int, currentFile: String): String {
    val progressBarLength = 50
    val filledLength = (progressBarLength * progress) / 100
    val bar = "=".repeat(filledLength) + ">" + " ".repeat(progressBarLength - filledLength)
    return "Uploading $currentFile... \n [$bar] $progress%"
}

class Synchronization(private val syncFolder: String) {
    private val httpClient = HttpClient(CIO) {
        install(WebSockets)
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
            })
        }
        defaultRequest {
            host = "127.0.0.1"
            port = 8080
        }
    }

    private suspend fun uploadFiles(files: List<String>) {
        val totalFiles = files.size

        httpClient.webSocket(
                method = HttpMethod.Get,
                host = "127.0.0.1",
                port = 8080,
                path = "/upload"
        ) {
            files.forEachIndexed { index, filePath ->
                val substringPath = filePath.substringAfter(syncFolder)
                println("\r Sending file $substringPath...")
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
                // print("\r$progressBar [$index/$totalFiles]")
            }
            println("\nAll files synced")
            close()
        }
    }
    
    private suspend fun pullFiles() {

    }

    private fun directoryFilesList(): List<File> {
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

        iterateFiles(File(syncFolder), files)

        return files
    }

    private suspend fun localFilesForUpload(
        localFiles: List<File>,
        remoteFiles: List<DirectoryTreeNode>
    ): List<File> {
        fun localNewerThanRemote(local: File, remote: DirectoryTreeNode?): Boolean {
            val timeFormatter = DateTimeFormatter.ISO_DATE_TIME
            val localAttributes = local.basicFileAttributes()
            val localModified = ZonedDateTime.parse(localAttributes.lastModifiedTime().toString(), timeFormatter)
            val remoteModified = ZonedDateTime.parse(remote.dateModified, timeFormatter)

            return localModified.isAfter(remoteModified)
        }

        val filesForUpload = localFiles.filter { local ->
            // find the file on remote, null if not found
            val remote = remoteFiles.find { remote -> remote.path == local.path.substringAfter(syncFolder) }

            (remote == null) ?: localNewerThanRemote(local, remote)
        }

        return filesForUpload
    }

    private suspend fun remoteFilesForPull(

    ) {

    }

    suspend fun sync() {
        val remote: DirectoryNodesResponse? = try {
            httpClient.get("/tree").body()
        } catch (e: Exception) {
            null
        }
        if (remote != null) {
            val local = directoryFilesList()

            // pullFiles(remoteFilesForPull(local, remote.files))
            uploadFiles(localFilesForUpload(local, remote.files).map { it.path })
        }
    }
}

