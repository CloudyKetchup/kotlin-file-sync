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
import kotlinx.serialization.json.Json
import org.osaaka.models.DirectoryNodesResponse

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

    private suspend fun sendFiles(files: List<String>) {
        val totalFiles = files.size

        httpClient.webSocket(
                method = HttpMethod.Get,
                host = "127.0.0.1",
                port = 8080,
                path = "/upload"
        ) {
            files.forEachIndexed { index, filePath ->
                val substringPath = filePath.substringAfter(syncFolder)
                // println("\r Sending file $substringPath...")
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

                // Display the progress bar
                print("\r$progressBar [$index/$totalFiles]")
            }
            println("All files synced successfully")
            close()
        }
    }

    private fun directoryFilesList(): List<String> {
        val files = mutableListOf<String>()

        fun iterateFiles(directory: File, files: MutableList<String>) {
            directory.listFiles().forEach { file ->
                if (!ignoreFiles.contains(file.name)) {
                    if (file.isDirectory) {
                        iterateFiles(file, files)
                    } else if (file.isFile) {
                        files.add(file.path)
                    }
                }
            }
        }

        iterateFiles(File(syncFolder), files)

        return files
    }

    private suspend fun remoteLocalCompare(): List<String> {
        val remote: DirectoryNodesResponse = httpClient.get("/tree").body()
        val local = directoryFilesList()

        val remoteFiles = remote.files.map { it.path }
        val filesForSync = local.filterNot { it.substringAfter(syncFolder) in remoteFiles }

        return filesForSync
    }

    suspend fun sync() {
        sendFiles(remoteLocalCompare())
    }
}

