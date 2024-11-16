package org.osaaka

import kotlinx.coroutines.*
import java.io.File
import org.osaaka.synchronization.Synchronization

const val tempFolder = "temp"
val syncFolder = "${System.getProperty("user.home")}/file-sync-test/test"

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

suspend fun delayTask(delayTime: Long, task: suspend () -> Unit) {
	while (true) {
		task()
		delay(delayTime)
	}
}

fun main() = runBlocking {
	initiateEnvironment()

    val synchronization = Synchronization(syncFolder)
//	delayTask(10000) {
//		syncDirectory()
//	}
    synchronization.sync()
}
