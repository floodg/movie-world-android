package com.movieworld.android

/** Bounded recovery only for a Play requested by this app. All times are monotonic. */
internal class ResumeRecovery {
    enum class Decision { WAIT, RELOAD, STOP }
    private var requestedAt: Long? = null
    private var reloadedAt: Long? = null
    private var pauseUntil = 0L
    var position: Long = 0
        private set

    @Synchronized fun pause(now: Long) {
        requestedAt = null
        reloadedAt = null
        pauseUntil = now + 6_000
    }

    @Synchronized fun resume(now: Long, seconds: Long) {
        requestedAt = now
        reloadedAt = null
        pauseUntil = 0
        position = seconds.coerceAtLeast(0)
    }

    /** Call for every transport sample, including TRANSITIONING and unknown states.
     * A restored stream is confirmed only after its saved-position seek completes.
     */
    @Synchronized fun observe(state: String, now: Long, seeking: Boolean = false): Boolean {
        if (state == "PLAYING" && !seeking) {
            requestedAt = null
            reloadedAt = null
            pauseUntil = 0
        }
        return expired(now)
    }

    @Synchronized fun expired(now: Long): Boolean {
        val reloaded = reloadedAt
        if (reloaded != null) return now - reloaded >= 15_000
        return requestedAt?.let { now - it >= 30_000 } ?: false
    }

    @Synchronized fun stopped(now: Long): Decision {
        val reloaded = reloadedAt
        if (reloaded != null) return if (now - reloaded < 15_000) Decision.WAIT else Decision.STOP
        val requested = requestedAt ?: return if (now < pauseUntil) Decision.WAIT else Decision.STOP
        val elapsed = now - requested
        if (elapsed < 6_000) return Decision.WAIT
        if (elapsed > 30_000) return Decision.STOP
        reloadedAt = now
        return Decision.RELOAD
    }
}
