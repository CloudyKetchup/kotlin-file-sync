package org.osaaka.util

fun buildProgressBar(progress: Int, currentFile: String): String {
    val progressBarLength = 50
    val filledLength = (progressBarLength * progress) / 100
    val bar = "=".repeat(filledLength) + ">" + " ".repeat(progressBarLength - filledLength)
    return "Uploading $currentFile... \n [$bar] $progress%"
}