package com.chat360.chatbot.ui.components.icons

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Hand-drawn vector icons for the chat chrome, kept dependency-free (no Material Icons Extended
 * artifact) since this is a library module. One `ImageVector` per icon - add new icons here as
 * new components need them.
 */
val ArrowUpIcon: ImageVector by lazy {
    materialIcon("ArrowUp") {
        filledPath {
            moveTo(11f, 20f); lineTo(13f, 20f); lineTo(13f, 8f); lineTo(18.5f, 13.5f)
            lineTo(20f, 12f); lineTo(12f, 4f); lineTo(4f, 12f); lineTo(5.5f, 13.5f)
            lineTo(11f, 8f); close()
        }
    }
}

val PlayIcon: ImageVector by lazy {
    materialIcon("Play") {
        filledPath { moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close() }
    }
}

val PauseIcon: ImageVector by lazy {
    materialIcon("Pause") {
        filledPath { moveTo(6f, 5f); lineTo(10f, 5f); lineTo(10f, 19f); lineTo(6f, 19f); close() }
        filledPath { moveTo(14f, 5f); lineTo(18f, 5f); lineTo(18f, 19f); lineTo(14f, 19f); close() }
    }
}

val CheckIcon: ImageVector by lazy {
    materialIcon("Check") {
        filledPath {
            moveTo(9f, 16.17f); lineTo(4.83f, 12f); lineTo(3.41f, 13.41f)
            lineTo(9f, 19f); lineTo(21f, 7f); lineTo(19.59f, 5.59f); close()
        }
    }
}

/** A small equalizer/soundwave glyph for speech-to-text dictation - visually distinct from the solid mic bulb used for voice-message recording. */
val DictateIcon: ImageVector by lazy {
    materialIcon("Dictate") {
        filledPath { moveTo(5f, 10f); lineTo(7f, 10f); lineTo(7f, 16f); lineTo(5f, 16f); close() }
        filledPath { moveTo(9f, 6f); lineTo(11f, 6f); lineTo(11f, 20f); lineTo(9f, 20f); close() }
        filledPath { moveTo(13f, 3f); lineTo(15f, 3f); lineTo(15f, 23f); lineTo(13f, 23f); close() }
        filledPath { moveTo(17f, 8f); lineTo(19f, 8f); lineTo(19f, 18f); lineTo(17f, 18f); close() }
    }
}

val DownloadIcon: ImageVector by lazy {
    materialIcon("Download") {
        filledPath {
            moveTo(11f, 3f); lineTo(13f, 3f); lineTo(13f, 12.2f); lineTo(16.6f, 8.6f)
            lineTo(18f, 10f); lineTo(12f, 16f); lineTo(6f, 10f); lineTo(7.4f, 8.6f)
            lineTo(11f, 12.2f); close()
        }
        filledPath { moveTo(5f, 18f); lineTo(19f, 18f); lineTo(19f, 20f); lineTo(5f, 20f); close() }
    }
}

val ChevronLeftIcon: ImageVector by lazy {
    materialIcon("ChevronLeft") {
        filledPath { moveTo(15f, 6f); lineTo(9f, 12f); lineTo(15f, 18f); lineTo(16.4f, 16.6f); lineTo(11.8f, 12f); lineTo(16.4f, 7.4f); close() }
    }
}

val MenuIcon: ImageVector by lazy {
    materialIcon("Menu") {
        filledPath {
            moveTo(4f, 6f); lineTo(20f, 6f); lineTo(20f, 8f); lineTo(4f, 8f); close()
        }
        filledPath {
            moveTo(4f, 11f); lineTo(20f, 11f); lineTo(20f, 13f); lineTo(4f, 13f); close()
        }
        filledPath {
            moveTo(4f, 16f); lineTo(20f, 16f); lineTo(20f, 18f); lineTo(4f, 18f); close()
        }
    }
}

val AddIcon: ImageVector by lazy {
    materialIcon("Add") {
        filledPath {
            moveTo(11f, 4f); lineTo(13f, 4f); lineTo(13f, 11f); lineTo(20f, 11f); lineTo(20f, 13f);
            lineTo(13f, 13f); lineTo(13f, 20f); lineTo(11f, 20f); lineTo(11f, 13f); lineTo(4f, 13f);
            lineTo(4f, 11f); lineTo(11f, 11f); close()
        }
    }
}

val StarIcon: ImageVector by lazy {
    materialIcon("Star") {
        filledPath {
            moveTo(12f, 17.27f); lineTo(18.18f, 21f); lineTo(16.54f, 13.97f); lineTo(22f, 9.24f)
            lineTo(14.81f, 8.63f); lineTo(12f, 2f); lineTo(9.19f, 8.63f); lineTo(2f, 9.24f)
            lineTo(7.46f, 13.97f); lineTo(5.82f, 21f); close()
        }
    }
}

val ChevronRightIcon: ImageVector by lazy {
    materialIcon("ChevronRight") {
        filledPath { moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f); lineTo(7.6f, 16.6f); lineTo(12.2f, 12f); lineTo(7.6f, 7.4f); close() }
    }
}

private fun ImageVector.Builder.filledPath(block: PathBuilder.() -> Unit) =
    path(fill = SolidColor(androidx.compose.ui.graphics.Color.Black), pathBuilder = block)

private fun materialIcon(
    name: String,
    block: ImageVector.Builder.() -> Unit,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply { block() }.build()
