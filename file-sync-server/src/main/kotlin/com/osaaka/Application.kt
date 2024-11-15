package com.osaaka

import com.osaaka.plugins.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.io.files.FileNotFoundException
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import java.io.File

data class StoreConfig(
    val storeFolder: String = "${System.getProperty("user.home")}/.local/share/osaaka.file-sync"
)

val storeConfig = StoreConfig()
val appModule = module {
    single { storeConfig }
}

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(Koin) {
        modules(appModule)
    }
    configureSerialization()
    configureRouting()
    initiateStore()
}

fun Application.initiateStore() {
    println("Checking environment...")

    val file = File(storeConfig.storeFolder)

    if (!file.exists()) {
        println("Environment doesn't exist, creating...")
        file.mkdir()

        if (!file.exists()) throw FileNotFoundException("Failed creating environment folder")

        println("Done!")
    }
}