package com.movieworld.android

/** Breaks the Favourites ↔ CloudSync cycle: local writes ask the hook to schedule a push. */
object SyncHooks {
    @Volatile var schedule: (Long) -> Unit = {}
}
