package com.movieworld.android.ui.components

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Material symbols that are not in the core icon set, drawn from their published 24dp paths
 * so the app does not depend on the extended icon artifact.
 */
object AppIcons {
    val Pause: ImageVector by lazy {
        materialIcon("Pause") { materialPath { moveTo(6f, 19f); horizontalLineToRelative(4f); verticalLineTo(5f); horizontalLineTo(6f); verticalLineToRelative(14f); close()
            moveTo(14f, 5f); verticalLineToRelative(14f); horizontalLineToRelative(4f); verticalLineTo(5f); horizontalLineToRelative(-4f); close() } }
    }
    val Stop: ImageVector by lazy {
        materialIcon("Stop") { materialPath { moveTo(6f, 6f); horizontalLineToRelative(12f); verticalLineToRelative(12f); horizontalLineTo(6f); close() } }
    }
    val Cast: ImageVector by lazy {
        materialIcon("Cast") {
            materialPath {
                moveTo(21f, 3f); horizontalLineTo(3f); curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f); verticalLineToRelative(3f); horizontalLineToRelative(2f)
                verticalLineTo(5f); horizontalLineToRelative(18f); verticalLineToRelative(14f); horizontalLineToRelative(-7f); verticalLineToRelative(2f); horizontalLineToRelative(7f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f); verticalLineTo(5f); curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f); close()
                moveTo(1f, 18f); verticalLineToRelative(3f); horizontalLineToRelative(3f); curveToRelative(0f, -1.66f, -1.34f, -3f, -3f, -3f); close()
                moveTo(1f, 14f); verticalLineToRelative(2f); curveToRelative(2.76f, 0f, 5f, 2.24f, 5f, 5f); horizontalLineToRelative(2f); curveToRelative(0f, -3.87f, -3.13f, -7f, -7f, -7f); close()
                moveTo(1f, 10f); verticalLineToRelative(2f); curveToRelative(4.97f, 0f, 9f, 4.03f, 9f, 9f); horizontalLineToRelative(2f); curveToRelative(0f, -6.08f, -4.93f, -11f, -11f, -11f); close()
            }
        }
    }
    val CastConnected: ImageVector by lazy {
        materialIcon("CastConnected") {
            materialPath {
                moveTo(1f, 18f); verticalLineToRelative(3f); horizontalLineToRelative(3f); curveToRelative(0f, -1.66f, -1.34f, -3f, -3f, -3f); close()
                moveTo(1f, 14f); verticalLineToRelative(2f); curveToRelative(2.76f, 0f, 5f, 2.24f, 5f, 5f); horizontalLineToRelative(2f); curveToRelative(0f, -3.87f, -3.13f, -7f, -7f, -7f); close()
                moveTo(19f, 7f); horizontalLineTo(5f); verticalLineToRelative(1.63f); curveToRelative(3.96f, 1.28f, 7.09f, 4.41f, 8.37f, 8.37f); horizontalLineTo(19f); verticalLineTo(7f); close()
                moveTo(1f, 10f); verticalLineToRelative(2f); curveToRelative(4.97f, 0f, 9f, 4.03f, 9f, 9f); horizontalLineToRelative(2f); curveToRelative(0f, -6.08f, -4.93f, -11f, -11f, -11f); close()
                moveTo(21f, 3f); horizontalLineTo(3f); curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f); verticalLineToRelative(3f); horizontalLineToRelative(2f); verticalLineTo(5f)
                horizontalLineToRelative(18f); verticalLineToRelative(14f); horizontalLineToRelative(-7f); verticalLineToRelative(2f); horizontalLineToRelative(7f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f); verticalLineTo(5f); curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f); close()
            }
        }
    }
    val Replay: ImageVector by lazy {
        materialIcon("Replay") {
            materialPath {
                moveTo(12f, 5f); verticalLineTo(1f); lineTo(7f, 6f); lineToRelative(5f, 5f); verticalLineTo(7f); curveToRelative(3.31f, 0f, 6f, 2.69f, 6f, 6f)
                reflectiveCurveToRelative(-2.69f, 6f, -6f, 6f); reflectiveCurveToRelative(-6f, -2.69f, -6f, -6f); horizontalLineTo(4f); curveToRelative(0f, 4.42f, 3.58f, 8f, 8f, 8f)
                reflectiveCurveToRelative(8f, -3.58f, 8f, -8f); reflectiveCurveToRelative(-3.58f, -8f, -8f, -8f); close()
            }
        }
    }
    val Tune: ImageVector by lazy {
        materialIcon("Tune") {
            materialPath {
                moveTo(3f, 17f); verticalLineToRelative(2f); horizontalLineToRelative(6f); verticalLineToRelative(-2f); horizontalLineTo(3f); close()
                moveTo(3f, 5f); verticalLineToRelative(2f); horizontalLineToRelative(10f); verticalLineTo(5f); horizontalLineTo(3f); close()
                moveTo(13f, 21f); verticalLineToRelative(-2f); horizontalLineToRelative(8f); verticalLineToRelative(-2f); horizontalLineToRelative(-8f); verticalLineToRelative(-2f)
                horizontalLineToRelative(-2f); verticalLineToRelative(6f); horizontalLineToRelative(2f); close()
                moveTo(7f, 9f); verticalLineToRelative(2f); horizontalLineTo(3f); verticalLineToRelative(2f); horizontalLineToRelative(4f); verticalLineToRelative(2f); horizontalLineToRelative(2f)
                verticalLineTo(9f); horizontalLineTo(7f); close()
                moveTo(21f, 13f); verticalLineToRelative(-2f); horizontalLineTo(11f); verticalLineToRelative(2f); horizontalLineToRelative(10f); close()
                moveTo(15f, 9f); horizontalLineToRelative(2f); verticalLineTo(7f); horizontalLineToRelative(4f); verticalLineTo(5f); horizontalLineToRelative(-4f); verticalLineTo(3f)
                horizontalLineToRelative(-2f); verticalLineToRelative(6f); close()
            }
        }
    }
    val Film: ImageVector by lazy {
        materialIcon("Film") {
            materialPath {
                moveTo(18f, 4f); lineToRelative(2f, 4f); horizontalLineToRelative(-3f); lineToRelative(-2f, -4f); horizontalLineToRelative(-2f); lineToRelative(2f, 4f)
                horizontalLineToRelative(-3f); lineToRelative(-2f, -4f); horizontalLineTo(8f); lineToRelative(2f, 4f); horizontalLineTo(7f); lineTo(5f, 4f); horizontalLineTo(4f)
                curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f); lineTo(2f, 18f); curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f); horizontalLineToRelative(16f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f); verticalLineTo(4f); horizontalLineToRelative(-4f); close()
            }
        }
    }
    val Tv: ImageVector by lazy {
        materialIcon("Tv") {
            materialPath {
                moveTo(21f, 3f); horizontalLineTo(3f); curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f); verticalLineToRelative(12f); curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(5f); verticalLineToRelative(2f); horizontalLineToRelative(8f); verticalLineToRelative(-2f); horizontalLineToRelative(5f)
                curveToRelative(1.1f, 0f, 1.99f, -0.9f, 1.99f, -2f); lineTo(23f, 5f); curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f); close()
                moveTo(21f, 17f); horizontalLineTo(3f); verticalLineTo(5f); horizontalLineToRelative(18f); verticalLineToRelative(12f); close()
            }
        }
    }
    val Bookmark: ImageVector by lazy {
        materialIcon("Bookmark") {
            materialPath {
                moveTo(17f, 3f); horizontalLineTo(7f); curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f); lineTo(5f, 21f); lineToRelative(7f, -3f); lineToRelative(7f, 3f)
                verticalLineTo(5f); curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f); close()
            }
        }
    }
    val BookmarkBorder: ImageVector by lazy {
        materialIcon("BookmarkBorder") {
            materialPath {
                moveTo(17f, 3f); horizontalLineTo(7f); curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f); lineTo(5f, 21f); lineToRelative(7f, -3f); lineToRelative(7f, 3f)
                verticalLineTo(5f); curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f); close()
                moveTo(17f, 18f); lineToRelative(-5f, -2.18f); lineTo(7f, 18f); verticalLineTo(5f); horizontalLineToRelative(10f); verticalLineToRelative(13f); close()
            }
        }
    }
    val OpenInNew: ImageVector by lazy {
        materialIcon("OpenInNew") {
            materialPath {
                moveTo(19f, 19f); horizontalLineTo(5f); verticalLineTo(5f); horizontalLineToRelative(7f); verticalLineTo(3f); horizontalLineTo(5f)
                curveToRelative(-1.11f, 0f, -2f, 0.9f, -2f, 2f); verticalLineToRelative(14f); curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f); horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f); verticalLineToRelative(-7f); horizontalLineToRelative(-2f); verticalLineToRelative(7f); close()
                moveTo(14f, 3f); verticalLineToRelative(2f); horizontalLineToRelative(3.59f); lineToRelative(-9.83f, 9.83f); lineToRelative(1.41f, 1.41f); lineTo(19f, 6.41f)
                verticalLineTo(10f); horizontalLineToRelative(2f); verticalLineTo(3f); horizontalLineToRelative(-7f); close()
            }
        }
    }
    val VideoFile: ImageVector by lazy {
        materialIcon("VideoFile") {
            materialPath {
                moveTo(4f, 6f); horizontalLineTo(2f); verticalLineToRelative(14f); curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f); horizontalLineToRelative(14f)
                verticalLineToRelative(-2f); horizontalLineTo(4f); verticalLineTo(6f); close()
                moveTo(20f, 2f); horizontalLineTo(8f); curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f); verticalLineToRelative(12f); curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(12f); curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f); verticalLineTo(4f); curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f); close()
                moveTo(12f, 14.5f); verticalLineToRelative(-9f); lineToRelative(6f, 4.5f); lineToRelative(-6f, 4.5f); close()
            }
        }
    }
    val Wifi: ImageVector by lazy {
        materialIcon("Wifi") {
            materialPath {
                moveTo(1f, 9f); lineToRelative(2f, 2f); curveToRelative(4.97f, -4.97f, 13.03f, -4.97f, 18f, 0f); lineToRelative(2f, -2f)
                curveTo(16.93f, 2.93f, 7.08f, 2.93f, 1f, 9f); close()
                moveTo(9f, 17f); lineToRelative(3f, 3f); lineToRelative(3f, -3f); curveToRelative(-1.65f, -1.66f, -4.34f, -1.66f, -6f, 0f); close()
                moveTo(5f, 13f); lineToRelative(2f, 2f); curveToRelative(2.76f, -2.76f, 7.24f, -2.76f, 10f, 0f); lineToRelative(2f, -2f)
                curveTo(15.14f, 9.14f, 8.87f, 9.14f, 5f, 13f); close()
            }
        }
    }
}
