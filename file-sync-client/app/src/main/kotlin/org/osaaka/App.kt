package org.osaaka

import kotlinx.coroutines.*
import org.osaaka.http.HttpService
import org.osaaka.service.FileService
import org.osaaka.synchronization.SyncManager
import java.io.File
import org.osaaka.tracker.ProgressTracker

val syncFolder = "${System.getProperty("user.home")}/file-sync-test/test"

fun initiateEnvironment() {
	val folder = File(syncFolder)

	if (!folder.exists()) {
		println("Folder $syncFolder does not exist")
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

    val synchronization = SyncManager(
		HttpService("0.0.0.0", 8080),
		FileService(syncFolder),
		ProgressTracker()
	)

	synchronization.sync()
}
