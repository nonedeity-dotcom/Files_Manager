package com.filemanager.app.util

/**
 * Wraps [argument] in POSIX single quotes so the shell takes it literally.
 *
 * Everything inside single quotes is literal, including spaces, newlines and
 * non-ASCII characters; the only character that needs care is the single quote
 * itself, which is closed, escaped and reopened ('\'').
 *
 * Stripping "unsafe" characters instead of quoting them is not an option: for a
 * path like /storage/emulated/0/Документы it would silently produce
 * /storage/emulated/0/ and point a destructive command at the wrong target.
 */
fun shellQuote(argument: String): String = "'" + argument.replace("'", "'\\''") + "'"
