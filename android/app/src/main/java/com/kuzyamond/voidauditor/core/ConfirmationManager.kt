package com.kuzyamond.voidauditor.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coordinates user confirmation for risky operations.
 *
 * Contract:
 *
 *   CapabilityExecutor
 *        │  awaitConfirmation(...)
 *        ▼
 *   ConfirmationManager            → publishes [currentRequest] (Compose state)
 *        │
 *        ▼
 *   ConfirmationDialog (UI)  — EXECUTE / CANCEL
 *        │
 *        ├── EXECUTE ──► confirm()   → resolves request with `true`
 *        └── CANCEL  ──► cancel()    → resolves request with `false`
 *        │
 *        ▼
 *   awaiting coroutine resumes
 *        │
 *   true  → CapabilityExecutor.executeRaw(...)
 *   false → CANCELLED
 *
 * The UI only renders the request; it never executes the capability itself.
 * [awaitConfirmation] suspends the calling coroutine until the user responds.
 *
 * Resolution safety:
 * - every request resolves at most once (AtomicBoolean guard);
 * - resolution clears [currentRequest] only when it is still the request being
 *   resolved (identity check) so a superseding request is never torn down;
 * - a new request automatically cancels (resolves as false) any pending one.
 */
object ConfirmationManager {
    var currentRequest by mutableStateOf<ConfirmationRequest?>(null)
        private set

    /**
     * Legacy callback-style API — publishes a request whose callbacks are invoked
     * later by the dialog when the user presses EXECUTE/CANCEL. Used by screens
     * that perform their own work inside the confirm callback (e.g. cache cleanup).
     *
     * Supersedes any currently pending request (previous request is cancelled).
     */
    fun requestConfirmation(
        intent: USFPipeline.Capability,
        onConfirm: () -> Unit,
        onCancel: () -> Unit = {},
        requiredPhrase: String? = null
    ) {
        val request = ConfirmationRequest(
            intent = intent,
            requiredPhrase = requiredPhrase,
            deferred = null,
            onConfirmUser = onConfirm,
            onCancelUser = onCancel
        )
        publish(request)
    }

    /**
     * Suspend the calling coroutine until the user resolves [intent].
     *
     * Returns `true` only after the user presses EXECUTE; returns `false` when the
     * user presses CANCEL/dismisses or when this request is superseded by a newer one.
     *
     * Cancellation semantics:
     * - if the *caller coroutine* is cancelled while waiting, the pending request is
     *   cleared from UI state, no capability executes, and CancellationException
     *   propagates to the caller;
     * - user CANCEL side-effects ([onCancelUser], e.g. blockedOps/logging) run
     *   exactly once, only for a genuine user CANCEL.
     */
    suspend fun awaitConfirmation(
        intent: USFPipeline.Capability,
        requiredPhrase: String? = null,
        onCancel: () -> Unit = {}
    ): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        val request = ConfirmationRequest(
            intent = intent,
            requiredPhrase = requiredPhrase,
            deferred = deferred,
            onConfirmUser = {},
            onCancelUser = onCancel
        )
        publish(request)
        return try {
            deferred.await()
        } finally {
            // Never leave stale dialog state behind, even on coroutine cancellation.
            clearIfCurrent(request)
        }
    }

    /** Resolve the current request as confirmed (EXECUTE). */
    fun confirm() {
        currentRequest?.let { resolve(it, confirmed = true) }
    }

    /** Resolve the current request as cancelled (CANCEL / back / outside tap). */
    fun cancel() {
        currentRequest?.let { resolve(it, confirmed = false) }
    }

    /**
     * Compatibility alias — clears the current request without resolving it.
     * Prefer [confirm]/[cancel]; kept for legacy callers that manage their own
     * work inside the callback.
     */
    fun dismiss() {
        currentRequest = null
    }

    private fun publish(request: ConfirmationRequest) {
        val previous = currentRequest
        if (previous != null && previous !== request) {
            // Only one request can be pending at a time; cancel the previous one.
            resolve(previous, confirmed = false)
        }
        currentRequest = request
    }

    private fun resolve(request: ConfirmationRequest, confirmed: Boolean) {
        if (!request.resolved.compareAndSet(false, true)) {
            return // already resolved — ignore duplicate EXECUTE/CANCEL
        }
        if (confirmed) {
            request.onConfirmUser()
        } else {
            request.onCancelUser()
        }
        request.deferred?.complete(confirmed)
        clearIfCurrent(request)
    }

    private fun clearIfCurrent(request: ConfirmationRequest) {
        if (currentRequest === request) {
            currentRequest = null
        }
    }
}

data class ConfirmationRequest internal constructor(
    val intent: USFPipeline.Capability,
    val requiredPhrase: String?,
    internal val deferred: CompletableDeferred<Boolean>?,
    internal val onConfirmUser: () -> Unit,
    internal val onCancelUser: () -> Unit
) {
    internal val resolved = AtomicBoolean(false)
}
