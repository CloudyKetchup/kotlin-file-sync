package org.osaaka

import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files

@Serializable
data class DirectoryFilesResponse(val files: List<String>)

val ignoreFiles = listOf("node_modules")
val syncFolder = "${System.getProperty("user.home")}/file-sync-test/test"
const val tempFolder = "temp"

val client = HttpClient(CIO) {
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

fun initiateEnvironment() {
	val folder = File(syncFolder)
	val temp = File(tempFolder)

	if (!folder.exists()) {
		println("Folder $syncFolder does not exist")
	}
	if (!temp.exists()) {
		println("Creating temp folder...")
		temp.mkdir()
	}
}

suspend fun sendFiles(files: List<String>) {
    val totalFiles = files.size

    client.webSocket(method = HttpMethod.Get, host = "127.0.0.1", port = 8080, path = "/upload") {
        files.forEachIndexed { index, filePath ->
            val substringPath = filePath.substringAfter(syncFolder)
            println("Sending file $substringPath...")
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
                    send(Frame.Binary(true, chunk))  // Sending binary frame (chunks)
                    offset = end
                }
                // Send the file path metadata
                send(Frame.Text("FilePath:$substringPath"))
            }
            // Calculate the progress percentage
            val progress = (index + 1) * 100 / totalFiles
            // Build the progress bar
            val progressBar = buildProgressBar(progress, totalFiles)

            // Display the progress bar
            print("\r$progressBar [$index/$totalFiles]")
        }
        println("All files synced successfully")
        close()
    }
}

fun directoryFilesList(): List<String> {
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

// Function to build a text-based progress bar
fun buildProgressBar(progress: Int, total: Int): String {
    val progressBarLength = 50 // Adjust the length of the progress bar
    val filledLength = (progressBarLength * progress) / 100
    val bar = "=".repeat(filledLength) + ">" + " ".repeat(progressBarLength - filledLength)
    return "[$bar] $progress%"
}

suspend fun remoteLocalCompare(): List<String> {
    val remote: DirectoryFilesResponse = client.get("/tree").body()
    val local = directoryFilesList()

    val filesForSync = local.filterNot { it.substringAfter(syncFolder) in remote.files }

    return filesForSync
}

suspend fun syncDirectory() {
    sendFiles(remoteLocalCompare())
}

suspend fun delayTask(delayTime: Long, task: suspend () -> Unit) {
	while (true) {
		task()
		delay(delayTime)
	}
}

fun main() = runBlocking {
	initiateEnvironment()

//	delayTask(10000) {
//		syncDirectory()
//	}
    syncDirectory()
}
