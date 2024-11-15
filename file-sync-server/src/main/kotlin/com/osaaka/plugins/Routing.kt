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
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.time.Duration.Companion.seconds

fun Application.configureRouting() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    routing {
        get("/") {
            call.respond("Hui")
        }

        storeTree()
        uploadFiles()
    }
}

fun directoryFilesList(parentDirectory: File): List<String> {
    val files = mutableListOf<String>()

    fun iterateFiles(directory: File, files: MutableList<String>) {
        directory.listFiles().forEach { file ->
            if (file.isFile) {
                files.add(file.path)
            } else if (file.isDirectory) {
                iterateFiles(file, files)
            }
        }
    }

    iterateFiles(parentDirectory, files)

    return files.map { it.substringAfter(storeConfig.storeFolder) }
}

fun Route.storeTree() {
    val storeConfig: StoreConfig by inject()

    @Serializable
    data class DirectoryFilesResponse(val files: List<String>)

    get("/tree") {
        val store = File(storeConfig.storeFolder)

        val response = DirectoryFilesResponse(directoryFilesList(store))

        call.respond(response)
    }
}

fun Route.uploadFiles() {
    val storeConfig: StoreConfig by inject()

    webSocket("/upload") {
            println("New WebSocket connection established")

            // To hold received chunks and reconstruct the file
            val receivedChunks = mutableListOf<ByteArray>()
            var currentFilePath: String? = null

            for (frame in incoming) {
                when (frame) {
                    is Frame.Binary -> {
                        // Add the received chunk to the list of chunks
                        receivedChunks.add(frame.readBytes())
                    }

                    is Frame.Text -> {
                        // Handle the file path and the end of the file transfer (or metadata)
                        val message = frame.readText()

                        // If it's a file metadata message (i.e., path of the file)
                        if (message.startsWith("FilePath:")) {
                            // Extract the file path from the metadata
                            currentFilePath = message.removePrefix("FilePath:")

                            // If the file is marked as empty (message ends with ":empty")
                            if (currentFilePath.endsWith(":empty") == true) {
                                currentFilePath = currentFilePath.removeSuffix(":empty")

                                // Create an empty file if it doesn't exist
                                val file = File("${storeConfig.storeFolder}/${currentFilePath}")
                                val parentDir = file.parentFile

                                if (!parentDir.exists()) {
                                    parentDir.mkdirs()
                                }

                                // Create an empty file
                                file.createNewFile()
                                println("Empty file created: ${file.absolutePath}")
                            }
                        }

                        // If we receive a chunk or end of file, reconstruct the file
                        if (receivedChunks.isNotEmpty()) {
                            if (currentFilePath != null) {
                                val combinedBytes = ByteArrayOutputStream()
                                for (chunk in receivedChunks) {
                                    combinedBytes.write(chunk)
                                }
                                val fileBytes = combinedBytes.toByteArray()
                                val file = File("${storeConfig.storeFolder}/${currentFilePath}")
                                val parentDir = file.parentFile

                                // Create parent directories if they don't exist
                                if (!parentDir.exists()) {
                                    parentDir.mkdirs()
                                }

                                // Write the file to the disk
                                file.writeBytes(fileBytes)
                                println("File received and saved: ${file.absolutePath}")

                                // Clear chunks and prepare for the next file
                                receivedChunks.clear()
                            }
                        }
                    }

                    is Frame.Close -> TODO()
                    is Frame.Ping -> TODO()
                    is Frame.Pong -> TODO()
                }
            }
        }
}