package com.example

import java.nio.file.Files
import java.nio.file.Path

private val projectRootMarkers = listOf(
    "settings.gradle.kts",
    "gradlew",
    ".git",
)

internal fun resolveProjectRoot(start: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()): Path {
    var current = start
    while (true) {
        if (projectRootMarkers.any { marker -> Files.exists(current.resolve(marker)) }) {
            return current
        }
        val parent = current.parent ?: return start
        current = parent
    }
}

internal fun resolveProjectSubdirectory(name: String): Path {
    val directory = resolveProjectRoot().resolve(name).toAbsolutePath().normalize()
    Files.createDirectories(directory)
    return directory
}

internal fun sanitizeFileComponent(value: String, fallback: String = "unknown"): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) {
        return fallback
    }

    return trimmed
        .replace(Regex("[^A-Za-z0-9_-]"), "_")
        .trim('_')
        .ifEmpty { fallback }
}

internal fun isDescendantPath(candidate: Path, parent: Path): Boolean {
    return candidate.toAbsolutePath().normalize().startsWith(parent.toAbsolutePath().normalize())
}
