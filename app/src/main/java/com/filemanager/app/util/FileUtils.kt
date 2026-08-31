package com.filemanager.app.util

import java.io.File
import java.io.IOException

/**
 * True when this path is a symbolic link.
 *
 * Recursive operations must not follow links: deleting through a link would
 * destroy the link's target contents rather than the link, and traversing one
 * can loop forever (/a/link -> /a).
 */
fun File.isSymlink(): Boolean = try {
    val canonicalParent = parentFile?.canonicalFile
    if (canonicalParent == null) {
        false
    } else {
        val self = File(canonicalParent, name)
        self.canonicalFile != self.absoluteFile
    }
} catch (e: IOException) {
    // Unresolvable: treat as a link so recursion refuses to descend into it.
    true
}

/** Canonical path, falling back to the absolute path when it can't be resolved. */
fun File.safeCanonicalPath(): String = try {
    canonicalPath
} catch (e: IOException) {
    absolutePath
}

/** True when [child] is [parent] itself or lives anywhere beneath it. */
fun isSameOrInside(parent: File, child: File): Boolean {
    val parentPath = parent.safeCanonicalPath()
    val childPath = child.safeCanonicalPath()
    return childPath == parentPath || childPath.startsWith(parentPath + File.separator)
}

/**
 * A destination inside [directory] that doesn't collide with anything already
 * there: "report.pdf" becomes "report (1).pdf", then "report (2).pdf".
 *
 * Without this, copying a file into its own folder resolves to the file itself,
 * and an overwriting copy deletes the target before reading the source — which
 * destroys the file it was asked to duplicate.
 */
fun uniqueDestination(directory: File, name: String): File {
    val candidate = File(directory, name)
    if (!candidate.exists()) return candidate

    val extension = name.substringAfterLast('.', "")
    val base = name.substringBeforeLast('.', name)
    // A dotfile like ".bashrc" has no real extension — keep the whole name.
    val hasExtension = extension.isNotEmpty() && base.isNotEmpty()
    val stem = if (hasExtension) base else name
    val suffix = if (hasExtension) ".$extension" else ""

    for (index in 1..MAX_NAME_ATTEMPTS) {
        val next = File(directory, "$stem ($index)$suffix")
        if (!next.exists()) return next
    }
    return File(directory, "$stem (${System.currentTimeMillis()})$suffix")
}

private const val MAX_NAME_ATTEMPTS = 999
