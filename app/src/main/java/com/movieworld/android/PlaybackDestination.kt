package com.movieworld.android

enum class PlaybackDestination { AUTO, TV, DEVICE }

internal fun defaultDestination(pcAvailable: Boolean, rendererAvailable: Boolean): PlaybackDestination =
    if (pcAvailable && rendererAvailable) PlaybackDestination.TV else PlaybackDestination.DEVICE

internal fun destinationLabel(destination: PlaybackDestination) =
    if (destination == PlaybackDestination.TV) "TV" else "this device"
