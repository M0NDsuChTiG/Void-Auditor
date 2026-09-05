package com.kuzyamond.voidauditor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Global script execution scope — survives UI recomposition and tab switches.
 *
 * Problem: using rememberCoroutineScope() in ScriptsScreen meant the coroutine
 * was cancelled whenever the user navigated away, producing SCRIPT_ERR: CANCELLED.
 *
 * Fix: this singleton owns a SupervisorJob-backed scope. Script jobs launched here
 * continue even when the ScriptsScreen composable leaves composition.
 */
object ScriptRunner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    fun isRunning(): Boolean = activeJob?.isActive == true

    fun execute(block: suspend CoroutineScope.() -> Unit): Job {
        // Cancel any previously running script
        activeJob?.cancel()
        val job = scope.launch(block = block)
        activeJob = job
        return job
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
    }
}
