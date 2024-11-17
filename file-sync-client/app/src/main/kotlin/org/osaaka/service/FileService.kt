package org.osaaka.service

import org.osaaka.extensions.basicFileAttributes
import org.osaaka.models.DirectoryTreeNode
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

val ignoreFiles = listOf("node_modules")

class FileService(val rootFolder: String) {
    fun localFiles(): List<File> =
        File(rootFolder).walk().filter { it.isFile && it.name !in ignoreFiles }.toList()

    fun writeFile(relativePath: String, bytes: ByteArray) {
        print("\r saving file $relativePath")
        val file = File("$rootFolder/$relativePath")
        file.parentFile.mkdirs()
        file.writeBytes(bytes)
    }

    fun createEmptyFile(relativePath: String) {
        val file = File("$rootFolder/$relativePath")
        file.parentFile.mkdirs()
        file.createNewFile()
    }

    fun isNewerThan(local: File, remote: DirectoryTreeNode): Boolean {
        val localModified = local.lastModifiedTime()
        val remoteModified = remote.dateModified.toZonedDateTime()
        return localModified.isAfter(remoteModified)
    }

    private fun String.toZonedDateTime(): ZonedDateTime =
        ZonedDateTime.parse(this, DateTimeFormatter.ISO_DATE_TIME)

    private fun File.lastModifiedTime(): ZonedDateTime =
        ZonedDateTime.ofInstant(basicFileAttributes().lastModifiedTime().toInstant(), ZoneId.systemDefault())
}