package com.movieworld.android

/** Never display exception messages containing request headers or credentials. */
internal fun safeSetupError(error: Exception): String {
    val text = error.message.orEmpty()
    return if (text.contains("Headers:", true) || text.contains("Authorization", true) ||
        text.contains("Bearer ", true) || text.contains("apikey", true) || text.contains("URL:", true))
        "The service request failed. Your local data is kept."
    else text.take(200).ifBlank { "Setup failed; try again." }
}
