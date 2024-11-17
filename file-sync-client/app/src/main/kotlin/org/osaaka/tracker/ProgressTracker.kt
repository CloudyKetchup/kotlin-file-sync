package org.osaaka.tracker

import org.osaaka.util.buildProgressBar

class ProgressTracker {
    fun displayProgress(current: Int, total: Int, progress: Int, relativePath: String) {
        val progressBar = buildProgressBar(progress, relativePath)
        print("\r$progressBar [$current/$total]")
    }
}